package com.example.demo.service;

import com.example.demo.dto.request.*;
import com.example.demo.dto.response.PageResponse;
import com.example.demo.exception.ApiException;
import com.example.demo.repository.ShopRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import static com.example.demo.repository.ShopRepository.*;

@Service @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
public class OrderService {
    private static final String SUMMARY="id,user_id,order_date,status,total_amount,payment_method,payment_status,reserved_until";
    private static final String DETAIL=SUMMARY+",subtotal,shipping_fee,discount_amount,coupon_code";
    private static final String SHIPPING="order_id,recipient_name,recipient_phone,delivery_method,address,note";
    public record Created(Map<String,Object> body,boolean replayed) {}
    private final ShopRepository db; private final ShopAccess access; private final CheckoutService checkout;
    private final CartService carts; private final InventoryService inventory;
    private final RateLimitService rateLimits;
    public OrderService(ShopRepository db,ShopAccess access,CheckoutService checkout,CartService carts,InventoryService inventory,RateLimitService rateLimits) {
        this.db=db; this.access=access; this.checkout=checkout; this.carts=carts; this.inventory=inventory; this.rateLimits=rateLimits;
    }
    public PageResponse<Map<String,Object>> list(MultiValueMap<String,String> params) {
        long user=access.user(); var q=new QueryParameters(params,"status","payment_status","date_from","date_to","user_id");
        if(q.has("user_id") && !access.isStaff()) throw ApiException.forbidden();
        Long owner=access.isStaff()?q.id("user_id"):Long.valueOf(user);
        var where=new StringBuilder(" WHERE 1=1"); var args=new ArrayList<Object>();
        if(owner!=null) { where.append(" AND user_id=?"); args.add(owner); }
        if(q.has("status")) { where.append(" AND status=?"); args.add(ShopRules.choice(q.get("status"),"status","pending","confirmed","shipping","completed","cancelled")); }
        if(q.has("payment_status")) { where.append(" AND payment_status=?"); args.add(ShopRules.choice(q.get("payment_status"),"payment_status","unpaid","paid","refunded")); }
        var from=ShopRules.queryDate(q.get("date_from")); var to=ShopRules.queryDate(q.get("date_to"));
        if(from!=null && to!=null && from.isAfter(to)) throw ApiException.invalid("date_from phải <= date_to");
        if(from!=null) { where.append(" AND order_date>=?"); args.add(Timestamp.from(from.atStartOfDay().toInstant(ZoneOffset.UTC))); }
        if(to!=null) { where.append(" AND order_date<?"); args.add(Timestamp.from(to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC))); }
        return db.page(SUMMARY,"FROM orders",where.toString(),"id DESC",q,args);
    }
    public Map<String,Object> detail(long id) { return response(access.order(id,false)); }
    private Map<String,Object> response(Map<String,Object> order) {
        long id=id(order,"id"); var result=pick(order,DETAIL); result.put("currency","VND");
        result.put("items",readItems(id)); result.put("shipping",readShipping(id));
        result.put("cancellation",order.get("cancelled_at")==null?null:cancelResponse(order)); return result;
    }
    private List<Map<String,Object>> readItems(long order) {
        return db.rows("SELECT id,product_id,product_name,quantity,unit_price,quantity*unit_price AS line_total FROM order_items WHERE order_id=? ORDER BY id",order);
    }
    public List<Map<String,Object>> items(long order) { access.order(order,false); return readItems(order); }
    public PageResponse<Map<String,Object>> events(long order,MultiValueMap<String,String> params) {
        access.order(order,false); var q=new QueryParameters(params);
        return db.page("id,order_id,event_type,old_value,new_value,note,created_at","FROM order_events"," WHERE order_id=?","id DESC",q,List.of(order));
    }
    private Map<String,Object> readShipping(long order) { return db.one("SELECT "+SHIPPING+" FROM shipping_info WHERE order_id=?",order); }
    public Map<String,Object> shipping(long order) { access.order(order,false); return readShipping(order); }
    private Map<String,Object> cancelResponse(Map<String,Object> order) {
        return fields("order_id",order.get("id"),"cancelled_at",order.get("cancelled_at"),"cancelled_by",order.get("cancelled_by"),"reason",order.get("cancel_reason"));
    }
    public Map<String,Object> cancellation(long id) {
        var order=access.order(id,false); if(order.get("cancelled_at")==null) throw ApiException.missing(); return cancelResponse(order);
    }
    private void event(long order,Long actor,String type,String old,String value,String note) {
        db.update("INSERT INTO order_events(order_id,actor_id,event_type,old_value,new_value,note) VALUES (?,?,?,?,?,?)",order,actor,type,old,value,note);
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public Created create(String key,OrderCreateRequest r) {
        return create(key,r,null);
    }
    /** The web confirmation is bound to its server-side quote; compare after locking inventory. */
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Created create(String key,OrderCreateRequest r,java.math.BigDecimal expectedTotal) {
        if(key==null || !key.matches("[A-Za-z0-9_-]{8,100}")) throw ApiException.invalid("Idempotency-Key cần 8–100 ký tự chữ/số, _ hoặc -");
        var prepared=checkout.prepare(r,r.getPaymentMethod()); long user=access.lockUser();
        var existing=db.rows("SELECT * FROM orders WHERE user_id=? AND idempotency_key=? FOR UPDATE",user,key);
        if(!existing.isEmpty()) {
            var order=existing.getFirst();
            if(!prepared.hash().equals(order.get("request_hash"))) throw ApiException.conflict("IDEMPOTENCY_CONFLICT","Khóa đã dùng với nội dung khác");
            return new Created(response(order),true);
        }
        rateLimits.order(user);
        var calculation=checkout.calculate(prepared,user,true); var totals=calculation.response();
        if(!Boolean.TRUE.equals(totals.get("can_place_order"))) {
            @SuppressWarnings("unchecked") var issues=(List<Map<String,Object>>)totals.get("issues");
            throw ApiException.conflict((String)issues.getFirst().get("code"),"Không thể đặt đơn; hãy kiểm tra lại sản phẩm/mã giảm giá");
        }
        if(expectedTotal!=null && expectedTotal.compareTo((java.math.BigDecimal)totals.get("total_amount"))!=0)
            throw ApiException.conflict("PRICE_CHANGED","Giá hoặc ưu đãi đã thay đổi. Hãy xem lại tổng tiền trước khi đặt hàng.");
        Timestamp expires="cash".equals(prepared.payment())?null:Timestamp.from(Instant.now().plusSeconds(900));
        long order=db.insert("INSERT INTO orders(user_id,payment_method,subtotal,shipping_fee,discount_amount,total_amount,coupon_code,reserved_until,idempotency_key,request_hash) VALUES (?,?,?,?,?,?,?,?,?,?)",
            user,prepared.payment(),totals.get("subtotal"),totals.get("shipping_fee"),totals.get("discount_amount"),totals.get("total_amount"),prepared.coupon(),expires,key,prepared.hash());
        var shipping=prepared.shipping();
        db.update("INSERT INTO shipping_info(order_id,recipient_name,recipient_phone,delivery_method,address,note) VALUES (?,?,?,?,?,?)",order,
            shipping.get("recipient_name"),shipping.get("recipient_phone"),shipping.get("delivery_method"),shipping.get("address"),shipping.get("note"));
        for(var line:calculation.lines()) {
            var product=calculation.products().get(line.productId());
            db.update("INSERT INTO order_items(order_id,product_id,product_name,quantity,unit_price) VALUES (?,?,?,?,?)",order,line.productId(),product.get("name"),line.quantity(),product.get("price"));
            inventory.change(product,-line.quantity(),order,user,"order_created",null);
            if("pet".equals(product.get("product_type"))) {
                int changed=db.update("UPDATE pets SET status='reserved',reserved_order_id=?,reserved_until=? WHERE product_id=? AND status='available'",order,expires,line.productId());
                if(changed!=1) throw ApiException.conflict("PET_UNAVAILABLE","Pet đã được giữ");
            }
        }
        if(prepared.coupon()!=null) db.update("UPDATE coupons SET used_count=used_count+1 WHERE code=?",prepared.coupon());
        carts.consume(user,calculation.lines()); event(order,user,"status",null,"pending",null);
        return new Created(response(db.one("SELECT * FROM orders WHERE id=?",order)),false);
    }
    private boolean expired(Map<String,Object> order) {
        return order.get("reserved_until") instanceof Instant until && !until.isAfter(Instant.now()) && "unpaid".equals(order.get("payment_status"));
    }
    private void notExpired(Map<String,Object> order) { if(expired(order)) throw ApiException.conflict("ORDER_EXPIRED","Đơn đã hết hạn chờ thanh toán"); }
    private ApiException transition() { return ApiException.conflict("INVALID_ORDER_TRANSITION","Không thể chuyển trạng thái đơn"); }
    private List<Map<String,Object>> lockProducts(long order) {
        var products=new ArrayList<Map<String,Object>>();
        for(var item:db.rows("SELECT product_id,quantity FROM order_items WHERE order_id=? ORDER BY product_id",order)) {
            var p=access.product(id(item,"product_id"),true); p.put("order_quantity",item.get("quantity")); products.add(p);
            if("pet".equals(p.get("product_type"))) {
                var pet=db.one("SELECT status,reserved_order_id FROM pets WHERE product_id=? FOR UPDATE",id(p,"id"));
                if(!"reserved".equals(pet.get("status")) || pet.get("reserved_order_id")==null || id(pet,"reserved_order_id")!=order)
                    throw ApiException.conflict("PET_UNAVAILABLE","Lượt giữ pet không thuộc đơn");
            }
        }
        return products;
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public Map<String,Object> status(long orderId,OrderStatusUpdateRequest r) {
        access.staff(); long actor=access.lockUser();
        String next=ShopRules.choice(r.status(),"status","confirmed","shipping","completed");
        var order=access.order(orderId,true); String old=(String)order.get("status");
        if(next.equals(old)) return response(order);
        String allowed=switch(old) { case "pending"->"confirmed"; case "confirmed"->"shipping"; case "shipping"->"completed"; default->""; };
        if(!next.equals(allowed)) throw transition(); notExpired(order);
        if((next.equals("confirmed") && !"cash".equals(order.get("payment_method")) || next.equals("completed")) && !"paid".equals(order.get("payment_status")))
            throw ApiException.conflict("INVALID_ORDER_TRANSITION","Đơn phải được thanh toán trước bước này");
        var products=lockProducts(orderId);
        if(next.equals("completed")) for(var p:products) if("pet".equals(p.get("product_type")))
            db.update("UPDATE pets SET status='sold',reserved_order_id=NULL,reserved_until=NULL WHERE product_id=?",id(p,"id"));
        db.update("UPDATE orders SET status=? WHERE id=?",next,orderId); event(orderId,actor,"status",old,next,null);
        return detail(orderId);
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public Map<String,Object> payment(long orderId,PaymentStatusUpdateRequest r) {
        access.staff(); long actor=access.lockUser(); String next=ShopRules.choice(r.paymentStatus(),"payment_status","paid","refunded");
        String note=InputRules.optionalText(r.note(),255,"note"); if(note!=null && note.isBlank()) note=null;
        var order=access.order(orderId,true); String old=(String)order.get("payment_status");
        if(next.equals(old)) return response(order);
        if(next.equals("paid")) {
            if(!old.equals("unpaid") || "cancelled".equals(order.get("status"))) throw transition(); notExpired(order);
            lockProducts(orderId);
            db.update("UPDATE pets SET reserved_until=NULL WHERE reserved_order_id=?",orderId);
        } else if(!old.equals("paid") || !"cancelled".equals(order.get("status"))) throw transition();
        db.update("UPDATE orders SET payment_status=?,reserved_until=NULL WHERE id=?",next,orderId);
        event(orderId,actor,"payment",old,next,note); return detail(orderId);
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public Created cancel(long orderId,OrderCancellationRequest r) {
        long actor=access.lockUser(); String reason=InputRules.text(r.reason(),255,"reason"); var order=access.order(orderId,true);
        if("cancelled".equals(order.get("status"))) return new Created(cancelResponse(order),true);
        cancelLocked(order,actor,reason); return new Created(cancellation(orderId),false);
    }
    private void cancelLocked(Map<String,Object> order,Long actor,String reason) {
        if(!"pending".equals(order.get("status"))) throw transition(); long orderId=id(order,"id");
        for(var product:lockProducts(orderId)) {
            inventory.change(product,integer(product,"order_quantity"),orderId,actor,"order_cancelled",null);
            if("pet".equals(product.get("product_type"))) db.update("UPDATE pets SET status='available',reserved_order_id=NULL,reserved_until=NULL WHERE product_id=? AND reserved_order_id=?",id(product,"id"),orderId);
        }
        db.update("UPDATE orders SET status='cancelled',reserved_until=NULL,cancelled_at=CURRENT_TIMESTAMP(6),cancelled_by=?,cancel_reason=? WHERE id=?",actor,reason,orderId);
        event(orderId,actor,"status","pending","cancelled",reason);
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public void expire(long orderId) {
        var order=db.one("SELECT * FROM orders WHERE id=? FOR UPDATE",orderId);
        if("pending".equals(order.get("status")) && expired(order)) cancelLocked(order,null,"Hết hạn chờ thanh toán");
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public Map<String,Object> updateShipping(long orderId,ShippingUpdateRequest r) {
        long actor=access.lockUser(); var order=access.order(orderId,true);
        if(!List.of("pending","confirmed").contains(order.get("status"))) throw transition();
        if(r.getSupplied().isEmpty()) throw ApiException.invalid("PATCH cần ít nhất một trường");
        var old=readShipping(orderId); var merged=new LinkedHashMap<>(old);
        if(r.has("recipient_name")) merged.put("recipient_name",InputRules.text(r.getRecipientName(),100,"recipient_name"));
        if(r.has("recipient_phone")) merged.put("recipient_phone",InputRules.phone(r.getRecipientPhone()));
        if(r.has("address")) merged.put("address",InputRules.optionalText(r.getAddress(),255,"address"));
        if(r.has("note")) merged.put("note",InputRules.optionalText(r.getNote(),255,"note"));
        // Validate the merged resource, including legacy rows with missing recipient fields.
        InputRules.text((String)merged.get("recipient_name"),100,"recipient_name"); InputRules.phone((String)merged.get("recipient_phone"));
        if(!"pickup".equals(merged.get("delivery_method"))) InputRules.text((String)merged.get("address"),255,"address");
        if(!old.equals(merged)) {
            db.update("UPDATE shipping_info SET recipient_name=?,recipient_phone=?,address=?,note=? WHERE order_id=?",merged.get("recipient_name"),merged.get("recipient_phone"),merged.get("address"),merged.get("note"),orderId);
            event(orderId,actor,"shipping",null,"updated",null);
        }
        return merged;
    }
}
