package com.example.demo.service;
import com.example.demo.dto.request.OrderLineRequest;
import com.example.demo.exception.ApiException;
import com.example.demo.repository.ShopRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;
import static com.example.demo.repository.ShopRepository.*;

@Service @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
public class CartService {
    private final ShopRepository db; private final ShopAccess access; private final InventoryService inventory;
    public CartService(ShopRepository db,ShopAccess access,InventoryService inventory) { this.db=db; this.access=access; this.inventory=inventory; }
    public List<OrderLineRequest> lines(long user) {
        return db.rows("SELECT i.product_id,i.quantity FROM cart_items i JOIN carts c ON c.id=i.cart_id WHERE c.user_id=? ORDER BY i.product_id",user)
            .stream().map(r->new OrderLineRequest(id(r,"product_id"),integer(r,"quantity"))).toList();
    }
    public Map<String,Object> get() { return response(access.user()); }
    private Map<String,Object> response(long user) {
        var items=new ArrayList<Map<String,Object>>(); var subtotal=BigDecimal.ZERO;
        for(var line:lines(user)) {
            var product=access.product(line.productId(),false); var price=money(product,"price");
            var total=ShopRules.money(price.multiply(BigDecimal.valueOf(line.quantity())));
            String code=inventory.availability(product,line.quantity());
            items.add(fields("product_id",line.productId(),"name",product.get("name"),"product_type",product.get("product_type"),"image_url",product.get("image_url"),
                "quantity",line.quantity(),"unit_price",price,"line_total",total,"stock_quantity",product.get("stock_quantity"),"available",code==null,"availability_code",code));
            subtotal=ShopRules.money(subtotal.add(total));
        }
        return fields("items",items,"subtotal",subtotal,"currency","VND");
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public Map<String,Object> put(long productId,int quantity,boolean add) {
        long user=access.lockUser(); InputRules.id(productId); ShopRules.quantity(quantity);
        var carts=db.rows("SELECT id FROM carts WHERE user_id=?",user);
        long cart=carts.isEmpty()?db.insert("INSERT INTO carts(user_id) VALUES (?)",user):id(carts.getFirst(),"id");
        var old=db.rows("SELECT quantity FROM cart_items WHERE cart_id=? AND product_id=?",cart,productId);
        if(add && !old.isEmpty()) quantity=ShopRules.quantity(quantity+integer(old.getFirst(),"quantity"));
        if(old.isEmpty() && db.count("SELECT COUNT(*) FROM cart_items WHERE cart_id=?",cart)>=100) throw ApiException.invalid("Giỏ tối đa 100 sản phẩm");
        var product=access.product(productId,true); String code=inventory.availability(product,quantity);
        if(code!=null) throw ApiException.conflict(code,"Sản phẩm không đủ điều kiện thêm vào giỏ");
        if(old.isEmpty()) db.update("INSERT INTO cart_items(cart_id,product_id,quantity) VALUES (?,?,?)",cart,productId,quantity);
        else db.update("UPDATE cart_items SET quantity=? WHERE cart_id=? AND product_id=?",quantity,cart,productId);
        return response(user);
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public void delete(Long product) {
        long user=access.lockUser(); if(product!=null) InputRules.id(product);
        String sql="DELETE FROM cart_items WHERE cart_id IN (SELECT id FROM carts WHERE user_id=?)";
        if(product==null) db.update(sql,user); else db.update(sql+" AND product_id=?",user,product);
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public void consume(long user,List<OrderLineRequest> lines) {
        for(var line:lines) {
            db.update("DELETE FROM cart_items WHERE cart_id IN (SELECT id FROM carts WHERE user_id=?) AND product_id=? AND quantity<=?",user,line.productId(),line.quantity());
            db.update("UPDATE cart_items SET quantity=quantity-? WHERE cart_id IN (SELECT id FROM carts WHERE user_id=?) AND product_id=?",line.quantity(),user,line.productId());
        }
    }
}
