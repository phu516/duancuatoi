package com.example.demo.web;

import com.example.demo.repository.ShopRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Read models for the storefront; business writes go through the existing services. */
@Service @Transactional(readOnly=true)
public class StorefrontData {
    private final ShopRepository db;
    public StorefrontData(ShopRepository db) { this.db=db; }
    public List<Map<String,Object>> categories() {
        return db.rows("SELECT c.id,c.name,COUNT(p.id) AS product_count,MIN(p.image_url) AS image_url FROM categories c LEFT JOIN products p ON p.category_id=c.id AND p.is_active=true GROUP BY c.id,c.name ORDER BY c.name");
    }
    public List<Map<String,Object>> testimonials() {
        return db.rows("SELECT r.rating,r.comment,r.created_at,u.full_name,p.name AS product_name FROM reviews r JOIN users u ON u.id=r.user_id JOIN products p ON p.id=r.product_id WHERE p.is_active=true AND r.comment IS NOT NULL AND r.comment<>'' ORDER BY r.id DESC LIMIT 3");
    }
}
