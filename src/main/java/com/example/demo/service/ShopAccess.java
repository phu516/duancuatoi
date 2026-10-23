package com.example.demo.service;

import com.example.demo.repository.ShopRepository;
import com.example.demo.security.CurrentUser;
import com.example.demo.exception.ApiException;
import org.springframework.stereotype.Component;
import java.util.Map;
import static com.example.demo.repository.ShopRepository.*;

@Component
public class ShopAccess {
    private final ShopRepository db;
    private final CurrentUser current;
    public ShopAccess(ShopRepository db,CurrentUser current) { this.db=db; this.current=current; }
    public long user() { return current.require().id(); }
    public void staff() { current.require(); if(!current.staff()) throw ApiException.forbidden(); }
    public void admin() { current.admin(); }
    public boolean isStaff() { return current.staff(); }
    /** Serialize cart/order writes for one user, including the first cart creation. */
    public long lockUser() {
        var p=current.require();
        var row=db.one("SELECT is_active,session_version FROM users WHERE id=? FOR UPDATE",p.id());
        if(!bool(row,"is_active") || id(row,"session_version")!=p.version())
            throw new ApiException(401,"SESSION_EXPIRED","Phiên đã hết hiệu lực");
        return p.id();
    }
    public Map<String,Object> product(long productId,boolean lock) {
        return db.one("SELECT * FROM products WHERE id=?"+(lock?" FOR UPDATE":""),InputRules.id(productId));
    }
    public void visible(Map<String,Object> product) {
        if(!bool(product,"is_active") && !isStaff()) throw ApiException.missing();
    }
    public Map<String,Object> order(long orderId,boolean lock) {
        long user=user();
        var order=db.one("SELECT * FROM orders WHERE id=?"+(lock?" FOR UPDATE":""),InputRules.id(orderId));
        if(id(order,"user_id")!=user && !isStaff()) throw ApiException.missing();
        return order;
    }
}
