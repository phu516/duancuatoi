package com.example.demo.service;
import com.example.demo.dto.request.InventoryAdjustmentRequest;
import com.example.demo.dto.response.PageResponse;
import com.example.demo.exception.ApiException;
import com.example.demo.repository.ShopRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import java.util.*;
import static com.example.demo.repository.ShopRepository.*;

@Service @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
public class InventoryService {
    private static final String STOCK="id AS product_id,name,product_type,stock_quantity,is_active";
    private static final String MOVEMENT="id,product_id,order_id,actor_id,quantity_change,quantity_before,quantity_after,reason,reference,created_at";
    private final ShopRepository db; private final ShopAccess access;
    public InventoryService(ShopRepository db,ShopAccess access) { this.db=db; this.access=access; }
    public PageResponse<Map<String,Object>> list(MultiValueMap<String,String> params) {
        access.staff(); var q=new QueryParameters(params,"low_stock"); int threshold=q.integer("low_stock",0,0,1000000);
        return db.page(STOCK,"FROM products",q.has("low_stock")?" WHERE stock_quantity<=?":"","id DESC",q,q.has("low_stock")?List.of(threshold):List.of());
    }
    public Map<String,Object> detail(long product) { access.staff(); return db.one("SELECT "+STOCK+" FROM products WHERE id=?",InputRules.id(product)); }
    public PageResponse<Map<String,Object>> movements(long product,MultiValueMap<String,String> params) {
        detail(product); var q=new QueryParameters(params);
        return db.page(MOVEMENT,"FROM inventory_movements"," WHERE product_id=?","id DESC",q,List.of(product));
    }
    public Map<String,Object> movement(long product,long movement) {
        detail(product); return db.one("SELECT "+MOVEMENT+" FROM inventory_movements WHERE product_id=? AND id=?",product,InputRules.id(movement));
    }
    public String availability(Map<String,Object> product,int quantity) {
        if(!bool(product,"is_active")) return "PRODUCT_UNAVAILABLE";
        if("pet".equals(product.get("product_type"))) {
            var pets=db.rows("SELECT status FROM pets WHERE product_id=?",id(product,"id"));
            if(quantity!=1 || pets.isEmpty() || !"available".equals(pets.getFirst().get("status"))) return "PET_UNAVAILABLE";
        }
        return integer(product,"stock_quantity")<quantity?"OUT_OF_STOCK":null;
    }
    /** Caller holds the product row lock. Movement and stock update share its transaction. */
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public long change(Map<String,Object> product,int change,Long order,Long actor,String reason,String reference) {
        int before=integer(product,"stock_quantity"); long after=(long)before+change;
        if(after<0 || after>1000000) throw ApiException.conflict("OUT_OF_STOCK","Tồn kho ngoài giới hạn");
        db.update("UPDATE products SET stock_quantity=? WHERE id=?",after,id(product,"id"));
        product.put("stock_quantity",(int)after);
        return db.insert("INSERT INTO inventory_movements(product_id,order_id,actor_id,quantity_change,quantity_before,quantity_after,reason,reference) VALUES (?,?,?,?,?,?,?,?)",
            id(product,"id"),order,actor,change,before,after,reason,reference);
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public Map<String,Object> adjust(long productId,InventoryAdjustmentRequest r) {
        access.staff(); long actor=access.lockUser(); var product=access.product(productId,true);
        if("pet".equals(product.get("product_type"))) throw ApiException.conflict("PET_UNAVAILABLE","Không điều chỉnh tồn product pet");
        if(r.change()==0) throw ApiException.invalid("change phải khác 0");
        // Include stock held by open orders so a later cancellation can always restore it.
        long held=db.count("SELECT COALESCE(SUM(i.quantity),0) FROM order_items i JOIN orders o ON o.id=i.order_id WHERE i.product_id=? AND o.status IN ('pending','confirmed','shipping')",productId);
        if((long)integer(product,"stock_quantity")+r.change()+held>1000000) throw ApiException.conflict("OUT_OF_STOCK","Tổng tồn khả dụng và hàng đang giữ vượt giới hạn");
        long movement=change(product,r.change(),null,actor,InputRules.text(r.reason(),255,"reason"),InputRules.optionalText(r.reference(),100,"reference"));
        return movement(productId,movement);
    }
}
