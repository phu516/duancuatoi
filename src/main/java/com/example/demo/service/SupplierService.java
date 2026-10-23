package com.example.demo.service;
import com.example.demo.dto.request.*;
import com.example.demo.dto.response.PageResponse;
import com.example.demo.repository.ShopRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import java.util.*;

@Service @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
public class SupplierService {
    private static final String COLUMNS="id,name,contact_info,address,created_at";
    private final ShopRepository db; private final ShopAccess access;
    public SupplierService(ShopRepository db,ShopAccess access) { this.db=db; this.access=access; }
    public PageResponse<Map<String,Object>> list(MultiValueMap<String,String> params) {
        access.staff(); var q=new QueryParameters(params,"keyword"); String keyword=q.keyword();
        return db.page(COLUMNS,"FROM suppliers",keyword==null?"":" WHERE LOWER(name) LIKE ? ESCAPE '!'","id DESC",q,
            keyword==null?List.of():List.of(QueryParameters.contains(keyword)));
    }
    public Map<String,Object> detail(long id) { access.staff(); return db.one("SELECT "+COLUMNS+" FROM suppliers WHERE id=?",InputRules.id(id)); }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public Map<String,Object> create(SupplierCreateRequest r) {
        access.admin(); access.lockUser();
        return detail(db.insert("INSERT INTO suppliers(name,contact_info,address) VALUES (?,?,?)",InputRules.text(r.name(),150,"name"),
            InputRules.optionalText(r.contactInfo(),255,"contact_info"),InputRules.optionalText(r.address(),255,"address")));
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public Map<String,Object> update(long id,SupplierUpdateRequest r) {
        access.admin(); access.lockUser(); db.one("SELECT id FROM suppliers WHERE id=? FOR UPDATE",InputRules.id(id));
        var fields=new LinkedHashMap<String,Object>();
        if(r.has("name")) fields.put("name",InputRules.text(r.getName(),150,"name"));
        if(r.has("contact_info")) fields.put("contact_info",InputRules.optionalText(r.getContactInfo(),255,"contact_info"));
        if(r.has("address")) fields.put("address",InputRules.optionalText(r.getAddress(),255,"address"));
        db.patch("suppliers",id,fields); return detail(id);
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public void delete(long id) {
        access.admin(); access.lockUser(); detail(id);
        // The existing FK uses ON DELETE SET NULL; pet and health history are retained.
        db.update("DELETE FROM suppliers WHERE id=?",id);
    }
}
