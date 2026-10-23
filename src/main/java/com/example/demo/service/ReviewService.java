package com.example.demo.service;
import com.example.demo.dto.request.*;
import com.example.demo.dto.response.PageResponse;
import com.example.demo.entity.enums.Role;
import com.example.demo.exception.ApiException;
import com.example.demo.repository.ShopRepository;
import com.example.demo.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import java.util.*;
import static com.example.demo.repository.ShopRepository.*;

@Service @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
public class ReviewService {
    private static final String COLUMNS="r.id,r.product_id,r.rating,r.comment,r.created_at,u.full_name AS display_name";
    private static final String FROM="FROM reviews r JOIN users u ON u.id=r.user_id";
    private final ShopRepository db; private final ShopAccess access; private final CurrentUser current;
    public ReviewService(ShopRepository db,ShopAccess access,CurrentUser current) { this.db=db; this.access=access; this.current=current; }
    private Map<String,Object> response(Map<String,Object> row) {
        var result=pick(row,"id,product_id,rating,comment,created_at"); result.put("author",fields("display_name",row.get("display_name"))); return result;
    }
    public PageResponse<Map<String,Object>> list(long product,MultiValueMap<String,String> params) {
        access.visible(access.product(product,false)); var q=new QueryParameters(params,"sort");
        String sort=q.has("sort")?q.get("sort"):"newest";
        String order=switch(sort) { case "newest"->"r.id DESC"; case "rating_asc"->"r.rating ASC,r.id DESC"; case "rating_desc"->"r.rating DESC,r.id DESC"; default->throw ApiException.invalid("sort không hợp lệ"); };
        var page=db.page(COLUMNS,FROM," WHERE r.product_id=?",order,q,List.of(product));
        return new PageResponse<>(page.data().stream().map(this::response).toList(),page.pagination());
    }
    public Map<String,Object> detail(long product,long review) {
        access.visible(access.product(product,false)); return read(product,review);
    }
    private Map<String,Object> read(long product,long review) {
        return response(db.one("SELECT "+COLUMNS+" "+FROM+" WHERE r.product_id=? AND r.id=?",product,InputRules.id(review)));
    }
    public Long mine(long product) {
        long user=access.user();
        var rows=db.rows("SELECT id FROM reviews WHERE product_id=? AND user_id=?",product,user);
        return rows.isEmpty()?null:id(rows.getFirst(),"id");
    }
    public Map<String,Object> mineDetail(long product) {
        Long review=mine(product); return review==null?null:read(product,review);
    }
    public boolean canReview() {
        return current.require().role()==Role.customer;
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public Map<String,Object> create(long product,ReviewCreateRequest r) {
        if(!canReview()) throw ApiException.forbidden();
        long user=access.lockUser(); access.visible(access.product(product,true));
        if(db.count("SELECT COUNT(*) FROM reviews WHERE user_id=? AND product_id=?",user,product)>0)
            throw ApiException.conflict("REVIEW_ALREADY_EXISTS","Bạn đã đánh giá sản phẩm này");
        long review=db.insert("INSERT INTO reviews(product_id,user_id,rating,comment) VALUES (?,?,?,?)",product,user,r.rating(),InputRules.optionalText(r.comment(),10000,"comment"));
        return read(product,review);
    }
    private void owner(long product,long review,boolean deleting,long user) {
        var row=db.one("SELECT user_id FROM reviews WHERE product_id=? AND id=? FOR UPDATE",InputRules.id(product),InputRules.id(review));
        if(id(row,"user_id")!=user && !(deleting && current.require().role()==Role.admin)) throw ApiException.missing();
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public Map<String,Object> update(long product,long review,ReviewUpdateRequest r) {
        long user=access.lockUser(); owner(product,review,false,user); var changes=new LinkedHashMap<String,Object>();
        if(r.has("rating")) {
            if(r.getRating()==null || r.getRating()<1 || r.getRating()>5) throw ApiException.invalid("rating phải từ 1 đến 5"); changes.put("rating",r.getRating());
        }
        if(r.has("comment")) changes.put("comment",InputRules.optionalText(r.getComment(),10000,"comment"));
        db.patch("reviews",review,changes); return read(product,review);
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public void delete(long product,long review) {
        long user=access.lockUser(); owner(product,review,true,user); db.update("DELETE FROM reviews WHERE id=?",review);
    }
}
