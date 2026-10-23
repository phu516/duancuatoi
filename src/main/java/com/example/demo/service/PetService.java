package com.example.demo.service;

import com.example.demo.dto.request.*;
import com.example.demo.dto.response.PageResponse;
import com.example.demo.entity.enums.PetStatus;
import com.example.demo.exception.ApiException;
import com.example.demo.repository.ShopRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import java.math.BigDecimal;
import java.util.*;
import static com.example.demo.repository.ShopRepository.*;

@Service @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
public class PetService {
    private static final String PET="id,product_id,breed,age,gender,color,weight,is_neutered,vaccination_status,status,created_at";
    private static final String PRODUCT="id,category_id,product_type,name,price,stock_quantity,image_url,is_active,created_at";
    private static final String HEALTH="id,pet_id,record_date,record_type,description,vet_name,created_at";
    private final ShopRepository db;
    private final ShopAccess access;
    public PetService(ShopRepository db,ShopAccess access) { this.db=db; this.access=access; }
    public PageResponse<Map<String,Object>> list(MultiValueMap<String,String> params) {
        var q=new QueryParameters(params,"status","breed","supplier_id");
        var args=new ArrayList<Object>(); var where=new StringBuilder(" WHERE 1=1");
        if(!access.isStaff()) where.append(" AND p.is_active=true");
        var status=q.enumeration("status",PetStatus.class);
        if(status!=null) { where.append(" AND t.status=?"); args.add(status.name()); }
        if(q.has("breed")) { where.append(" AND LOWER(t.breed) LIKE ? ESCAPE '!'"); args.add(QueryParameters.contains(InputRules.text(q.get("breed"),100,"breed"))); }
        Long supplier=q.id("supplier_id");
        if(supplier!=null) { where.append(" AND t.supplier_id=?"); args.add(supplier); }
        var page=db.page("t.*","FROM pets t JOIN products p ON p.id=t.product_id",where.toString(),"t.id DESC",q,args);
        return new PageResponse<>(page.data().stream().map(p->response(p,false)).toList(),page.pagination());
    }
    public Map<String,Object> detail(long petId) { return response(read(petId,false),true); }
    private Map<String,Object> response(Map<String,Object> pet,boolean detail) {
        var result=pick(pet,PET);
        result.put("product",pick(access.product(id(pet,"product_id"),false),detail?PRODUCT:"id,name,price,image_url,is_active"));
        if(detail && access.isStaff()) result.put("supplier_id",pet.get("supplier_id"));
        return result;
    }
    private Map<String,Object> read(long petId,boolean lock) {
        var pet=db.one("SELECT * FROM pets WHERE id=?",InputRules.id(petId));
        // All pet writers lock product before pet, matching checkout and inventory.
        var product=access.product(id(pet,"product_id"),lock);
        if(lock) pet=db.one("SELECT * FROM pets WHERE id=? FOR UPDATE",petId);
        access.visible(product); return pet;
    }
    private void supplier(Long supplierId) {
        if(supplierId!=null) db.one("SELECT id FROM suppliers WHERE id=? FOR SHARE",InputRules.id(supplierId));
    }
    private String gender(String value) { return value==null?null:ShopRules.choice(value,"gender","male","female"); }
    private BigDecimal weight(BigDecimal value) {
        if(value!=null && (value.signum()<=0 || value.scale()>2 || value.compareTo(new BigDecimal("9999.99"))>0)) throw ApiException.invalid("weight không hợp lệ");
        return value;
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public Map<String,Object> create(PetCreateRequest r) {
        access.admin(); access.lockUser(); var product=access.product(r.getProductId(),true);
        if(!"pet".equals(product.get("product_type")) || bool(product,"is_active") || integer(product,"stock_quantity")!=1)
            throw ApiException.conflict("PRODUCT_NOT_READY","Cần product pet chưa mở bán với tồn 1");
        if(db.count("SELECT COUNT(*) FROM pets WHERE product_id=?",r.getProductId())>0) throw ApiException.conflict("RESOURCE_IN_USE","Product đã có hồ sơ pet");
        supplier(r.getSupplierId());
        long petId=db.insert("INSERT INTO pets(product_id,supplier_id,breed,age,gender,color,weight,is_neutered,vaccination_status) VALUES (?,?,?,?,?,?,?,?,?)",
            r.getProductId(),r.getSupplierId(),InputRules.optionalText(r.getBreed(),100,"breed"),InputRules.optionalText(r.getAge(),50,"age"),gender(r.getGender()),
            InputRules.optionalText(r.getColor(),50,"color"),weight(r.getWeight()),Boolean.TRUE.equals(r.getIsNeutered()),InputRules.optionalText(r.getVaccinationStatus(),255,"vaccination_status"));
        return detail(petId);
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public Map<String,Object> update(long petId,PetUpdateRequest r) {
        access.admin(); access.lockUser(); read(petId,true); var fields=new LinkedHashMap<String,Object>();
        if(r.has("supplier_id")) { supplier(r.getSupplierId()); fields.put("supplier_id",r.getSupplierId()); }
        if(r.has("breed")) fields.put("breed",InputRules.optionalText(r.getBreed(),100,"breed"));
        if(r.has("age")) fields.put("age",InputRules.optionalText(r.getAge(),50,"age"));
        if(r.has("gender")) fields.put("gender",gender(r.getGender()));
        if(r.has("color")) fields.put("color",InputRules.optionalText(r.getColor(),50,"color"));
        if(r.has("weight")) fields.put("weight",weight(r.getWeight()));
        if(r.has("vaccination_status")) fields.put("vaccination_status",InputRules.optionalText(r.getVaccinationStatus(),255,"vaccination_status"));
        if(r.has("is_neutered")) { if(r.getIsNeutered()==null) throw ApiException.invalid("is_neutered không được null"); fields.put("is_neutered",r.getIsNeutered()); }
        db.patch("pets",petId,fields); return detail(petId);
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public void delete(long petId) {
        access.admin(); access.lockUser(); var pet=read(petId,true); long product=id(pet,"product_id");
        if(!"available".equals(pet.get("status")) || db.count("SELECT COUNT(*) FROM order_items WHERE product_id=?",product)>0)
            throw ApiException.conflict("RESOURCE_IN_USE","Pet đã có lịch sử đơn hoặc đang được giữ/bán");
        db.update("UPDATE products SET is_active=false WHERE id=?",product);
        db.update("DELETE FROM pet_health_records WHERE pet_id=?",petId);
        db.update("DELETE FROM pets WHERE id=?",petId);
    }
    public PageResponse<Map<String,Object>> healthList(Long petId,MultiValueMap<String,String> params) {
        var q=petId==null?new QueryParameters(params,"pet_id"):new QueryParameters(params);
        if(petId==null) { access.staff(); petId=q.id("pet_id"); }
        else read(petId,false);
        return db.page(HEALTH,"FROM pet_health_records",petId==null?"":" WHERE pet_id=?","id DESC",q,petId==null?List.of():List.of(petId));
    }
    public Map<String,Object> health(long petId,long recordId) {
        read(petId,false);
        return db.one("SELECT "+HEALTH+" FROM pet_health_records WHERE pet_id=? AND id=?",petId,InputRules.id(recordId));
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public Map<String,Object> addHealth(long petId,HealthRecordCreateRequest r) {
        access.admin(); access.lockUser(); read(petId,true);
        long record=db.insert("INSERT INTO pet_health_records(pet_id,record_date,record_type,description,vet_name) VALUES (?,?,?,?,?)",petId,
            ShopRules.date(r.recordDate()),InputRules.text(r.recordType(),100,"record_type"),InputRules.optionalText(r.description(),10000,"description"),InputRules.optionalText(r.vetName(),100,"vet_name"));
        return health(petId,record);
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public Map<String,Object> updateHealth(long petId,long recordId,HealthRecordUpdateRequest r) {
        access.admin(); access.lockUser(); read(petId,true); health(petId,recordId); var fields=new LinkedHashMap<String,Object>();
        if(r.has("record_date")) fields.put("record_date",ShopRules.date(r.getRecordDate()));
        if(r.has("record_type")) fields.put("record_type",InputRules.text(r.getRecordType(),100,"record_type"));
        if(r.has("description")) fields.put("description",InputRules.optionalText(r.getDescription(),10000,"description"));
        if(r.has("vet_name")) fields.put("vet_name",InputRules.optionalText(r.getVetName(),100,"vet_name"));
        db.patch("pet_health_records",recordId,fields); return health(petId,recordId);
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public void deleteHealth(long petId,long recordId) {
        access.admin(); access.lockUser(); read(petId,true); health(petId,recordId);
        db.update("DELETE FROM pet_health_records WHERE id=?",recordId);
    }
}
