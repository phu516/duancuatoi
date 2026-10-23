package com.example.demo.service;

import com.example.demo.dto.request.*;
import com.example.demo.exception.ApiException;
import com.example.demo.repository.ShopRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import static com.example.demo.repository.ShopRepository.*;

@Service @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
public class CheckoutService {
    public record Prepared(List<OrderLineRequest> items,Map<String,Object> shipping,String coupon,String payment,String hash) {}
    public record Calculation(List<OrderLineRequest> lines,Map<Long,Map<String,Object>> products,Map<String,Object> response) {}
    private final ShopRepository db; private final ShopAccess access; private final CartService carts;
    private final InventoryService inventory; private final JsonMapper json;
    private final BigDecimal petTaxiFee;
    public CheckoutService(ShopRepository db,ShopAccess access,CartService carts,InventoryService inventory,JsonMapper json,
        @Value("${app.shipping.pet-taxi-fee:100000}") BigDecimal petTaxiFee) {
        this.db=db; this.access=access; this.carts=carts; this.inventory=inventory; this.json=json;
        this.petTaxiFee=ShopRules.money(petTaxiFee.setScale(2,RoundingMode.UNNECESSARY));
    }
    public Prepared prepare(CheckoutPreviewRequest request,String payment) {
        List<OrderLineRequest> items=null;
        if(request.isItemsSupplied()) {
            if(request.getItems()==null || request.getItems().isEmpty() || request.getItems().size()>100) throw ApiException.invalid("items cần từ 1 đến 100 dòng");
            var seen=new HashSet<Long>();
            for(var line:request.getItems()) {
                if(line==null) throw ApiException.invalid("items không được có null");
                InputRules.id(line.productId()); ShopRules.quantity(line.quantity());
                if(!seen.add(line.productId())) throw ApiException.invalid("product_id bị trùng");
            }
            items=request.getItems().stream().sorted(Comparator.comparing(OrderLineRequest::productId)).toList();
        }
        var s=request.getShipping(); if(s==null) throw ApiException.invalid("shipping bắt buộc");
        String method=ShopRules.choice(s.deliveryMethod(),"delivery_method","pickup","pet_taxi");
        var shipping=fields("recipient_name",InputRules.text(s.recipientName(),100,"recipient_name"),"recipient_phone",InputRules.phone(s.recipientPhone()),
            "delivery_method",method,"address","pickup".equals(method)?InputRules.optionalText(s.address(),255,"address"):InputRules.text(s.address(),255,"address"),
            "note",InputRules.optionalText(s.note(),255,"note"));
        String coupon=InputRules.optionalText(request.getCouponCode(),50,"coupon_code"); if(coupon!=null && coupon.isBlank()) coupon=null;
        if(payment!=null) ShopRules.choice(payment,"payment_method","cash","bank_transfer","card","e_wallet");
        var fingerprint=fields("source",items==null?"cart":"items","items",items,"shipping",shipping,"coupon_code",coupon,"payment_method",payment);
        try {
            String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsString(fingerprint).getBytes(StandardCharsets.UTF_8)));
            return new Prepared(items,shipping,coupon,payment,hash);
        } catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public Map<String,Object> preview(CheckoutPreviewRequest request) {
        long user=access.user(); return calculate(prepare(request,null),user,false).response();
    }
    /** With lock=true, called inside the order transaction, after locking the current user. */
    public Calculation calculate(Prepared prepared,long user,boolean lock) {
        var lines=prepared.items()==null?carts.lines(user):prepared.items();
        if(lines.isEmpty()) throw new ApiException(400,"EMPTY_CART","Giỏ hàng trống");
        if(lines.size()>100) throw ApiException.invalid("Đơn tối đa 100 sản phẩm");
        var products=new LinkedHashMap<Long,Map<String,Object>>();
        var items=new ArrayList<Map<String,Object>>(); var issues=new ArrayList<Map<String,Object>>();
        BigDecimal subtotal=BigDecimal.ZERO; boolean completePrice=true;
        for(var line:lines) {
            ShopRules.quantity(line.quantity());
            var found=db.rows("SELECT * FROM products WHERE id=?"+(lock?" FOR UPDATE":""),line.productId());
            Map<String,Object> p=found.isEmpty()?null:found.getFirst();
            String code;
            if(p==null || !bool(p,"is_active")) { code="PRODUCT_UNAVAILABLE"; completePrice=false; }
            else {
                products.put(line.productId(),p);
                if(lock && "pet".equals(p.get("product_type"))) db.rows("SELECT id FROM pets WHERE product_id=? FOR UPDATE",line.productId());
                code=inventory.availability(p,line.quantity());
            }
            boolean visible=p!=null && bool(p,"is_active");
            BigDecimal price=visible?money(p,"price"):null;
            BigDecimal total=price==null?null:ShopRules.money(price.multiply(BigDecimal.valueOf(line.quantity())));
            if(total!=null) subtotal=ShopRules.money(subtotal.add(total));
            items.add(fields("product_id",line.productId(),"name",visible?p.get("name"):null,"product_type",visible?p.get("product_type"):null,
                "quantity",line.quantity(),"unit_price",price,"line_total",total,"available",code==null));
            if(code!=null) issues.add(fields("code",code,"product_id",line.productId(),"message","Sản phẩm không khả dụng với số lượng yêu cầu"));
        }
        BigDecimal fee=switch((String)prepared.shipping().get("delivery_method")) { case "pickup"->BigDecimal.ZERO; case "pet_taxi"->petTaxiFee; default->throw new IllegalStateException("Hình thức nhận hàng không hợp lệ"); };
        BigDecimal discount=BigDecimal.ZERO;
        if(prepared.coupon()!=null) {
            var coupons=db.rows("SELECT * FROM coupons WHERE code=?"+(lock?" FOR UPDATE":""),prepared.coupon());
            var c=coupons.isEmpty()?null:coupons.getFirst(); Instant now=Instant.now();
            boolean valid=c!=null && bool(c,"is_active") && !now.isBefore((Instant)c.get("starts_at")) && now.isBefore((Instant)c.get("ends_at"))
                && (c.get("usage_limit")==null || integer(c,"used_count")<integer(c,"usage_limit"));
            if(valid && completePrice) valid=subtotal.compareTo(money(c,"min_subtotal"))>=0;
            if(!valid) {
                issues.add(fields("code","COUPON_INVALID","product_id",null,"message","Mã giảm giá không hợp lệ")); completePrice=false;
            } else if(completePrice) {
                discount="percent".equals(c.get("discount_type"))?subtotal.multiply(money(c,"discount_value")).divide(new BigDecimal("100"),2,RoundingMode.HALF_UP):money(c,"discount_value");
                discount=ShopRules.money(discount.min(subtotal));
            }
        }
        BigDecimal total=completePrice?ShopRules.money(subtotal.add(fee).subtract(discount)):null;
        return new Calculation(lines,products,fields("can_place_order",issues.isEmpty(),"items",items,"subtotal",completePrice?subtotal:null,
            "shipping_fee",completePrice?fee:null,"discount_amount",completePrice?discount:null,"total_amount",total,
            "coupon_code",prepared.coupon(),"currency","VND","issues",issues));
    }
}
