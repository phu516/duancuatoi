package com.example.demo.web;

import com.example.demo.dto.request.*;
import com.example.demo.dto.response.*;
import com.example.demo.entity.enums.*;
import com.example.demo.exception.ApiException;
import com.example.demo.security.CurrentUser;
import com.example.demo.service.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** Thin Thymeleaf adapter over the same services used by the public API. */
@Controller @RequestMapping("/admin")
public class WebAdminController {
    private final CurrentUser current; private final UserService users; private final CategoryService categories;
    private final ProductService products; private final PetService pets; private final SupplierService suppliers;
    private final OrderService orders; private final InventoryService inventory;
    public WebAdminController(CurrentUser current,UserService users,CategoryService categories,ProductService products,
            PetService pets,SupplierService suppliers,OrderService orders,InventoryService inventory) {
        this.current=current; this.users=users; this.categories=categories; this.products=products;
        this.pets=pets; this.suppliers=suppliers; this.orders=orders; this.inventory=inventory;
    }
    private boolean admin() { return current.require().role()==Role.admin; }
    private void staff() { if(!current.staff()) throw ApiException.forbidden(); }
    private void adminOnly() { current.admin(); }
    private long number(String value) { try { long n=Long.parseLong(value); if(n>0) return n; } catch(Exception ignored) {} throw ApiException.invalid("ID không hợp lệ"); }
    private int integer(String value) { try { return Integer.parseInt(value); } catch(Exception e) { throw ApiException.invalid("Số lượng không hợp lệ"); } }
    private BigDecimal money(String value) { try { return new BigDecimal(value); } catch(Exception e) { throw ApiException.invalid("Giá không hợp lệ"); } }
    private String optional(String value) { return value==null || value.isBlank()?null:value.trim(); }
    private Long optionalId(String value) { return optional(value)==null?null:number(value); }
    private MultiValueMap<String,String> params(Map<String,String> values,String... names) {
        var q=new LinkedMultiValueMap<String,String>();
        for(String name:names) if(optional(values.get(name))!=null) q.set(name,values.get(name).trim());
        return q;
    }
    private Map<String,Object> fields(Object... values) {
        var result=new LinkedHashMap<String,Object>(); for(int i=0;i<values.length;i+=2) result.put((String)values[i],values[i+1]); return result;
    }
    private String done(RedirectAttributes flash,String path,String message) {
        flash.addFlashAttribute("successMessage",message); return "redirect:"+path;
    }
    private String fail(RedirectAttributes flash,String path,ApiException e) {
        flash.addFlashAttribute("errorMessage",e.getMessage()); return "redirect:"+path;
    }
    @GetMapping public String home(Model model) { staff(); model.addAttribute("isAdmin",admin()); return "admin-home"; }

    @GetMapping("/{section}") public String list(@PathVariable String section,@RequestParam Map<String,String> query,Model model) {
        staff(); if(!Set.of("orders","inventory","health").contains(section)) adminOnly();
        var q=switch(section) {
            case "orders" -> params(query,"status","payment_status","date_from","date_to","user_id","page","size");
            case "inventory" -> params(query,"low_stock","page","size");
            case "users" -> params(query,"keyword","role","is_active","page","size");
            case "categories","suppliers" -> params(query,"keyword","page","size");
            case "products" -> params(query,"keyword","category_id","product_type","is_active","sort","page","size");
            case "pets" -> params(query,"status","breed","supplier_id","page","size");
            case "health" -> params(query,"pet_id","page","size");
            default -> throw ApiException.missing();
        };
        PageResponse<?> page=switch(section) {
            case "orders" -> orders.list(q);
            case "inventory" -> inventory.list(q);
            case "users" -> users.list(q);
            case "categories" -> categories.list(q);
            case "products" -> products.list(q);
            case "pets" -> pets.list(q);
            case "health" -> pets.healthList(null,q);
            case "suppliers" -> suppliers.list(q);
            default -> throw ApiException.missing();
        };
        var rows=new ArrayList<Map<String,Object>>();
        for(Object item:page.data()) rows.add(row(item));
        model.addAttribute("section",section); model.addAttribute("rows",rows);
        model.addAttribute("sectionTitle",switch(section) {
            case "orders" -> "Đơn hàng"; case "inventory" -> "Tồn kho"; case "users" -> "Người dùng";
            case "categories" -> "Danh mục"; case "products" -> "Sản phẩm"; case "pets" -> "Thú cưng";
            case "health" -> "Hồ sơ sức khỏe"; case "suppliers" -> "Nhà cung cấp"; default -> section;
        });
        model.addAttribute("sectionDescription",switch(section) {
            case "orders" -> "Theo dõi tiến trình, thanh toán và thông tin giao nhận của tất cả đơn hàng.";
            case "inventory" -> "Kiểm tra số lượng hiện có và mở lịch sử để điều chỉnh tồn kho.";
            case "users" -> "Tìm tài khoản, phân quyền và khóa hoặc mở khóa khi cần.";
            case "categories" -> "Sắp xếp nhóm sản phẩm để khách hàng dễ tìm kiếm.";
            case "products" -> "Quản lý mặt hàng, giá bán và trạng thái hiển thị trong cửa hàng.";
            case "pets" -> "Tạo và cập nhật hồ sơ riêng cho từng thú cưng.";
            case "health" -> "Tra cứu các lần khám và thông tin chăm sóc của thú cưng.";
            case "suppliers" -> "Quản lý thông tin đơn vị cung cấp sản phẩm và thú cưng.";
            default -> "";
        });
        model.addAttribute("columnLabels",Map.ofEntries(
            Map.entry("id","ID"),Map.entry("user_id","Khách hàng"),Map.entry("product_id","Sản phẩm"),
            Map.entry("pet_id","Pet"),Map.entry("status","Trạng thái"),Map.entry("payment_status","Thanh toán"),
            Map.entry("total_amount","Tổng tiền"),Map.entry("order_date","Ngày đặt"),Map.entry("name","Tên"),
            Map.entry("product_type","Loại"),Map.entry("stock_quantity","Tồn kho"),Map.entry("is_active","Hoạt động"),
            Map.entry("full_name","Họ tên"),Map.entry("email","Email"),Map.entry("role","Vai trò"),
            Map.entry("price","Giá"),Map.entry("breed","Giống"),Map.entry("record_date","Ngày"),
            Map.entry("record_type","Loại khám"),Map.entry("description","Nội dung"),
            Map.entry("contact_info","Liên hệ"),Map.entry("address","Địa chỉ")));
        model.addAttribute("columns",switch(section) {
            case "orders" -> List.of("id","user_id","status","payment_status","total_amount","order_date");
            case "inventory" -> List.of("product_id","name","product_type","stock_quantity","is_active");
            case "users" -> List.of("id","full_name","email","role","is_active");
            case "categories" -> List.of("id","name");
            case "products" -> List.of("id","name","product_type","price","stock_quantity","is_active");
            case "pets" -> List.of("id","product_id","breed","status");
            case "health" -> List.of("id","pet_id","record_date","record_type","description");
            case "suppliers" -> List.of("id","name","contact_info","address");
            default -> List.of();
        });
        model.addAttribute("pagination",page.pagination()); model.addAttribute("query",query);
        var previous=new LinkedMultiValueMap<String,String>(q); previous.set("page",Integer.toString(Math.max(0,page.pagination().page()-1)));
        var next=new LinkedMultiValueMap<String,String>(q); next.set("page",Integer.toString(page.pagination().page()+1));
        model.addAttribute("previousUrl",org.springframework.web.util.UriComponentsBuilder.fromPath("/admin/"+section).queryParams(previous).build().encode().toUriString());
        model.addAttribute("nextUrl",org.springframework.web.util.UriComponentsBuilder.fromPath("/admin/"+section).queryParams(next).build().encode().toUriString());
        model.addAttribute("isAdmin",admin()); return "admin-list";
    }
    @SuppressWarnings("unchecked") private Map<String,Object> row(Object item) {
        if(item instanceof Map<?,?> map) return (Map<String,Object>)map;
        if(item instanceof UserResponse u) return fields("id",u.id(),"full_name",u.fullName(),"email",u.email(),"role",u.role(),"is_active",u.isActive());
        if(item instanceof CategoryResponse c) return fields("id",c.id(),"name",c.name());
        if(item instanceof ProductSummaryResponse p) return fields("id",p.id(),"name",p.name(),"product_type",p.productType(),"price",p.price(),"stock_quantity",p.stockQuantity(),"is_active",p.isActive());
        throw new IllegalArgumentException("Unsupported admin row");
    }

    @GetMapping("/orders/{id}") public String order(@PathVariable long id,@RequestParam(defaultValue="0") int page,Model model) {
        staff(); model.addAttribute("order",orders.detail(id));
        model.addAttribute("events",orders.events(id,params(Map.of("page",Integer.toString(page),"size","20"),"page","size")));
        return "admin-order";
    }
    @PostMapping("/orders/{id}/status") public String status(@PathVariable long id,@RequestParam String status,RedirectAttributes flash) {
        staff(); String path="/admin/orders/"+id;
        try { orders.status(id,new OrderStatusUpdateRequest(status)); return done(flash,path,"Đã cập nhật tiến trình đơn."); }
        catch(ApiException e) { return fail(flash,path,e); }
    }
    @PostMapping("/orders/{id}/payment") public String payment(@PathVariable long id,@RequestParam String paymentStatus,
            @RequestParam(required=false) String note,RedirectAttributes flash) {
        staff(); String path="/admin/orders/"+id;
        try { orders.payment(id,new PaymentStatusUpdateRequest(paymentStatus,note)); return done(flash,path,"Đã cập nhật thanh toán."); }
        catch(ApiException e) { return fail(flash,path,e); }
    }
    @PostMapping("/orders/{id}/cancel") public String cancel(@PathVariable long id,@RequestParam String reason,RedirectAttributes flash) {
        staff(); String path="/admin/orders/"+id;
        try { orders.cancel(id,new OrderCancellationRequest(reason)); return done(flash,path,"Đã hủy đơn."); }
        catch(ApiException e) { return fail(flash,path,e); }
    }
    @PostMapping("/orders/{id}/shipping") public String shipping(@PathVariable long id,@RequestParam Map<String,String> values,RedirectAttributes flash) {
        staff(); String path="/admin/orders/"+id;
        try {
            var r=new ShippingUpdateRequest(); r.setRecipientName(values.get("recipientName")); r.setRecipientPhone(values.get("recipientPhone"));
            r.setAddress(values.get("address")); r.setNote(values.get("note")); orders.updateShipping(id,r);
            return done(flash,path,"Đã cập nhật người nhận.");
        } catch(ApiException e) { return fail(flash,path,e); }
    }
    @GetMapping("/inventory/{id}") public String stock(@PathVariable long id,@RequestParam(defaultValue="0") int page,Model model) {
        staff(); model.addAttribute("stock",inventory.detail(id));
        model.addAttribute("movements",inventory.movements(id,params(Map.of("page",Integer.toString(page)),"page")));
        return "admin-inventory";
    }
    @PostMapping("/inventory/{id}/adjust") public String adjust(@PathVariable long id,@RequestParam String change,
            @RequestParam String reason,@RequestParam(required=false) String reference,RedirectAttributes flash) {
        staff(); String path="/admin/inventory/"+id;
        try { inventory.adjust(id,new InventoryAdjustmentRequest(integer(change),reason,reference)); return done(flash,path,"Đã điều chỉnh tồn và ghi nhật ký."); }
        catch(ApiException e) { return fail(flash,path,e); }
    }
    @PostMapping("/users/{id}/role") public String role(@PathVariable long id,@RequestParam Role role,RedirectAttributes flash) {
        adminOnly(); try { users.role(id,new RoleUpdateRequest(role)); return done(flash,"/admin/users","Đã đổi vai trò."); }
        catch(ApiException e) { return fail(flash,"/admin/users",e); }
    }
    @PostMapping("/users/{id}/status") public String userStatus(@PathVariable long id,@RequestParam boolean active,RedirectAttributes flash) {
        adminOnly(); try { users.status(id,new ActiveStatusRequest(active)); return done(flash,"/admin/users","Đã cập nhật tài khoản."); }
        catch(ApiException e) { return fail(flash,"/admin/users",e); }
    }
    @PostMapping("/categories/create") public String createCategory(@RequestParam String name,RedirectAttributes flash) {
        adminOnly(); try { categories.create(new CategoryCreateRequest(name)); return done(flash,"/admin/categories","Đã tạo danh mục."); }
        catch(ApiException e) { return fail(flash,"/admin/categories",e); }
    }
    @PostMapping("/categories/{id}/update") public String updateCategory(@PathVariable long id,@RequestParam String name,RedirectAttributes flash) {
        adminOnly(); try { categories.update(id,new CategoryUpdateRequest(name)); return done(flash,"/admin/categories","Đã sửa danh mục."); }
        catch(ApiException e) { return fail(flash,"/admin/categories",e); }
    }
    @PostMapping("/categories/{id}/delete") public String deleteCategory(@PathVariable long id,RedirectAttributes flash) {
        adminOnly(); try { categories.delete(id); return done(flash,"/admin/categories","Đã xóa danh mục."); }
        catch(ApiException e) { return fail(flash,"/admin/categories",e); }
    }
    @PostMapping("/suppliers/create") public String createSupplier(@RequestParam Map<String,String> f,RedirectAttributes flash) {
        adminOnly(); try { suppliers.create(new SupplierCreateRequest(f.get("name"),f.get("contactInfo"),f.get("address"))); return done(flash,"/admin/suppliers","Đã tạo nhà cung cấp."); }
        catch(ApiException e) { return fail(flash,"/admin/suppliers",e); }
    }
    @PostMapping("/suppliers/{id}/update") public String updateSupplier(@PathVariable long id,@RequestParam Map<String,String> f,RedirectAttributes flash) {
        adminOnly(); try { var r=new SupplierUpdateRequest(); r.setName(f.get("name")); r.setContactInfo(f.get("contactInfo")); r.setAddress(f.get("address"));
            suppliers.update(id,r); return done(flash,"/admin/suppliers","Đã sửa nhà cung cấp."); }
        catch(ApiException e) { return fail(flash,"/admin/suppliers",e); }
    }
    @PostMapping("/suppliers/{id}/delete") public String deleteSupplier(@PathVariable long id,RedirectAttributes flash) {
        adminOnly(); try { suppliers.delete(id); return done(flash,"/admin/suppliers","Đã xóa nhà cung cấp."); }
        catch(ApiException e) { return fail(flash,"/admin/suppliers",e); }
    }
    @GetMapping("/products/new") public String newProduct(Model model) { adminOnly(); model.addAttribute("product",null); model.addAttribute("categories",categories.list(params(Map.of("size","100"),"size")).data()); return "admin-product"; }
    @GetMapping("/products/{id}") public String product(@PathVariable long id,Model model) {
        adminOnly(); model.addAttribute("product",products.detail(id)); model.addAttribute("categories",categories.list(params(Map.of("size","100"),"size")).data()); return "admin-product";
    }
    @PostMapping("/products/create") public String createProduct(@RequestParam Map<String,String> f,RedirectAttributes flash) {
        adminOnly(); try {
            var p=products.create(new ProductCreateRequest(number(f.get("categoryId")),ProductType.valueOf(f.get("productType")),f.get("name"),f.get("description"),
                money(f.get("price")),integer(f.get("stockQuantity")),optional(f.get("imageUrl"))));
            return done(flash,"/admin/products/"+p.id(),"Đã tạo sản phẩm.");
        } catch(ApiException | IllegalArgumentException e) { return fail(flash,"/admin/products",e instanceof ApiException a?a:ApiException.invalid("Thông tin sản phẩm không hợp lệ")); }
    }
    @PostMapping("/products/{id}/update") public String updateProduct(@PathVariable long id,@RequestParam Map<String,String> f,RedirectAttributes flash) {
        adminOnly(); String path="/admin/products/"+id;
        try { var r=new ProductUpdateRequest(); r.setCategoryId(number(f.get("categoryId"))); r.setName(f.get("name")); r.setDescription(f.get("description"));
            r.setPrice(money(f.get("price"))); r.setImageUrl(optional(f.get("imageUrl"))); products.update(id,r); return done(flash,path,"Đã sửa sản phẩm."); }
        catch(ApiException e) { return fail(flash,path,e); }
    }
    @PostMapping("/products/{id}/status") public String productStatus(@PathVariable long id,@RequestParam boolean active,RedirectAttributes flash) {
        adminOnly(); String path="/admin/products/"+id;
        try { products.status(id,new ActiveStatusRequest(active)); return done(flash,path,"Đã cập nhật trạng thái bán."); }
        catch(ApiException e) { return fail(flash,path,e); }
    }
    @PostMapping("/products/{id}/delete") public String deleteProduct(@PathVariable long id,RedirectAttributes flash) {
        adminOnly(); try { products.delete(id); return done(flash,"/admin/products","Đã xóa sản phẩm."); }
        catch(ApiException e) { return fail(flash,"/admin/products/"+id,e); }
    }
    @PostMapping("/pets/create") public String createPet(@RequestParam Map<String,String> f,RedirectAttributes flash) {
        adminOnly(); try { var r=petCreate(f); var pet=pets.create(r); return done(flash,"/admin/pets/"+pet.get("id"),"Đã tạo hồ sơ pet. Có thể mở bán sản phẩm."); }
        catch(ApiException e) { return fail(flash,"/admin/pets",e); }
    }
    @GetMapping("/pets/{id}") public String pet(@PathVariable long id,@RequestParam(defaultValue="0") int page,Model model) {
        staff(); model.addAttribute("pet",pets.detail(id));
        model.addAttribute("health",pets.healthList(id,params(Map.of("page",Integer.toString(page),"size","20"),"page","size")));
        model.addAttribute("isAdmin",admin()); return "admin-pet";
    }
    @PostMapping("/pets/{id}/update") public String updatePet(@PathVariable long id,@RequestParam Map<String,String> f,RedirectAttributes flash) {
        adminOnly(); String path="/admin/pets/"+id;
        try { var r=new PetUpdateRequest(); r.setSupplierId(optionalId(f.get("supplierId"))); r.setBreed(f.get("breed")); r.setAge(f.get("age"));
            r.setGender(optional(f.get("gender"))); r.setColor(f.get("color")); r.setWeight(optional(f.get("weight"))==null?null:money(f.get("weight")));
            r.setIsNeutered(f.containsKey("isNeutered")); r.setVaccinationStatus(f.get("vaccinationStatus")); pets.update(id,r);
            return done(flash,path,"Đã sửa hồ sơ pet."); } catch(ApiException e) { return fail(flash,path,e); }
    }
    @PostMapping("/pets/{id}/delete") public String deletePet(@PathVariable long id,RedirectAttributes flash) {
        adminOnly(); try { pets.delete(id); return done(flash,"/admin/pets","Đã xóa hồ sơ pet."); }
        catch(ApiException e) { return fail(flash,"/admin/pets/"+id,e); }
    }
    @PostMapping("/pets/{id}/health/create") public String addHealth(@PathVariable long id,@RequestParam Map<String,String> f,RedirectAttributes flash) {
        adminOnly(); String path="/admin/pets/"+id;
        try { pets.addHealth(id,new HealthRecordCreateRequest(LocalDate.parse(f.get("recordDate")),f.get("recordType"),f.get("description"),f.get("vetName")));
            return done(flash,path,"Đã thêm hồ sơ sức khỏe."); }
        catch(ApiException | IllegalArgumentException e) { return fail(flash,path,e instanceof ApiException a?a:ApiException.invalid("Ngày khám không hợp lệ")); }
    }
    @PostMapping("/pets/{id}/health/{record}/update") public String updateHealth(@PathVariable long id,@PathVariable long record,@RequestParam Map<String,String> f,RedirectAttributes flash) {
        adminOnly(); String path="/admin/pets/"+id;
        try { var r=new HealthRecordUpdateRequest(); r.setRecordDate(LocalDate.parse(f.get("recordDate"))); r.setRecordType(f.get("recordType"));
            r.setDescription(f.get("description")); r.setVetName(f.get("vetName")); pets.updateHealth(id,record,r); return done(flash,path,"Đã sửa hồ sơ sức khỏe."); }
        catch(ApiException | IllegalArgumentException e) { return fail(flash,path,e instanceof ApiException a?a:ApiException.invalid("Thông tin khám không hợp lệ")); }
    }
    @PostMapping("/pets/{id}/health/{record}/delete") public String deleteHealth(@PathVariable long id,@PathVariable long record,RedirectAttributes flash) {
        adminOnly(); String path="/admin/pets/"+id;
        try { pets.deleteHealth(id,record); return done(flash,path,"Đã xóa hồ sơ sức khỏe."); }
        catch(ApiException e) { return fail(flash,path,e); }
    }
    private PetCreateRequest petCreate(Map<String,String> f) {
        var r=new PetCreateRequest(); r.setProductId(number(f.get("productId"))); r.setSupplierId(optionalId(f.get("supplierId")));
        r.setBreed(f.get("breed")); r.setAge(f.get("age")); r.setGender(optional(f.get("gender"))); r.setColor(f.get("color"));
        r.setWeight(optional(f.get("weight"))==null?null:money(f.get("weight"))); r.setIsNeutered(f.containsKey("isNeutered"));
        r.setVaccinationStatus(f.get("vaccinationStatus")); return r;
    }
}
