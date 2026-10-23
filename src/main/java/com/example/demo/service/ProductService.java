package com.example.demo.service;
import com.example.demo.entity.*;
import com.example.demo.entity.enums.*;
import com.example.demo.dto.request.*;
import com.example.demo.dto.response.*;
import com.example.demo.exception.ApiException;
import com.example.demo.repository.*;
import com.example.demo.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Sort;
import org.springframework.util.MultiValueMap;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.math.BigDecimal;

@Service @Transactional(readOnly=true)
public class ProductService {
    private final ProductRepository products; private final CategoryRepository categories;
    private final PetRepository pets; private final ProductHistoryRepository history; private final CurrentUser current;
    public ProductService(ProductRepository products,CategoryRepository categories,PetRepository pets,
        ProductHistoryRepository history,CurrentUser current) {
        this.products=products; this.categories=categories; this.pets=pets; this.history=history; this.current=current;
    }
    public PageResponse<ProductSummaryResponse> list(MultiValueMap<String,String> params) {
        var q=new QueryParameters(params,"category_id","product_type","keyword","min_price","max_price","sort","is_active");
        if(q.has("is_active") && !current.staff()) throw ApiException.forbidden();
        Boolean requestedActive=q.bool("is_active"); boolean active=requestedActive==null || requestedActive;
        Long category=q.id("category_id"); ProductType type=q.enumeration("product_type",ProductType.class);
        String keyword=q.keyword(); BigDecimal min=q.money("min_price"), max=q.money("max_price");
        if(min!=null && max!=null && min.compareTo(max)>0) throw ApiException.invalid("min_price phải <= max_price");
        String sorting=q.has("sort")?q.get("sort"):"newest";
        Sort sort=switch(sorting) {
            case "newest" -> Sort.by("id").descending();
            case "price_asc" -> Sort.by("price").ascending().and(Sort.by("id").descending());
            case "price_desc" -> Sort.by("price").descending().and(Sort.by("id").descending());
            default -> throw ApiException.invalid("sort không hợp lệ");
        };
        Specification<Product> spec=(root,query,cb) -> {
            var predicates=new ArrayList<Predicate>(); predicates.add(cb.equal(root.get("active"),active));
            if(category!=null) predicates.add(cb.equal(root.get("categoryId"),category));
            if(type!=null) predicates.add(cb.equal(root.get("productType"),type));
            if(keyword!=null) predicates.add(cb.like(cb.lower(root.get("name")),QueryParameters.contains(keyword),'!'));
            if(min!=null) predicates.add(cb.greaterThanOrEqualTo(root.get("price"),min));
            if(max!=null) predicates.add(cb.lessThanOrEqualTo(root.get("price"),max));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return PageResponse.of(products.findAll(spec,q.page(sort)).map(ProductSummaryResponse::of));
    }
    public ProductDetailResponse detail(long id) {
        Product p=products.findById(InputRules.id(id)).orElseThrow(ApiException::missing);
        if(!p.isActive() && !current.staff()) throw ApiException.missing();
        return response(p);
    }
    private ProductDetailResponse response(Product p) {
        return ProductDetailResponse.of(p,pets.findByProductId(p.getId()).orElse(null),history.reviews(p.getId()));
    }
    @Transactional public ProductDetailResponse create(ProductCreateRequest r) {
        current.admin(); long actor=current.require().id();
        categories.lockById(InputRules.id(r.categoryId())).orElseThrow(ApiException::missing);
        int stock=r.stockQuantity()==null?0:r.stockQuantity();
        if(r.productType()==ProductType.pet && (r.stockQuantity()==null || stock!=1))
            throw ApiException.invalid("Sản phẩm pet bắt buộc stock_quantity=1");
        Product p=new Product(); p.setCategoryId(r.categoryId()); p.setProductType(r.productType());
        p.setName(InputRules.text(r.name(),150,"name")); p.setDescription(InputRules.optionalText(r.description(),10000,"description"));
        p.setPrice(InputRules.price(r.price())); p.setStockQuantity(stock); p.setImageUrl(InputRules.imageUrl(r.imageUrl()));
        p.setActive(r.productType()!=ProductType.pet);
        products.saveAndFlush(p); history.initialStock(p.getId(),actor,stock);
        return response(p);
    }
    @Transactional public ProductDetailResponse update(long id,ProductUpdateRequest r) {
        current.admin(); Product p=products.lockById(InputRules.id(id)).orElseThrow(ApiException::missing);
        if(r.getSupplied().isEmpty()) throw ApiException.invalid("PATCH cần ít nhất một trường");
        if(r.has("category_id")) {
            long categoryId=InputRules.id(r.getCategoryId()); categories.lockById(categoryId).orElseThrow(ApiException::missing);
            p.setCategoryId(categoryId);
        }
        if(r.has("name")) p.setName(InputRules.text(r.getName(),150,"name"));
        if(r.has("description")) p.setDescription(InputRules.optionalText(r.getDescription(),10000,"description"));
        if(r.has("price")) p.setPrice(InputRules.price(r.getPrice()));
        if(r.has("image_url")) p.setImageUrl(InputRules.imageUrl(r.getImageUrl()));
        return response(p);
    }
    @Transactional public ProductDetailResponse status(long id,ActiveStatusRequest r) {
        current.admin(); Product p=products.lockById(InputRules.id(id)).orElseThrow(ApiException::missing);
        if(r.isActive() && p.getProductType()==ProductType.pet) {
            Pet pet=pets.findByProductId(id).orElseThrow(() -> ApiException.conflict("PRODUCT_NOT_READY","Cần hồ sơ pet trước khi mở bán"));
            int expectedStock=pet.getStatus()==PetStatus.available?1:0;
            if(p.getStockQuantity()!=expectedStock) throw ApiException.conflict("PRODUCT_NOT_READY","Tồn kho và trạng thái pet không khớp");
        }
        p.setActive(r.isActive()); return response(p);
    }
    @Transactional public void delete(long id) {
        current.admin(); Product p=products.lockById(InputRules.id(id)).orElseThrow(ApiException::missing);
        if(history.hasHistory(id) || pets.findByProductId(id).isPresent())
            throw ApiException.conflict("RESOURCE_IN_USE","Sản phẩm có lịch sử hoặc hồ sơ pet; hãy ngừng bán hoặc xử lý hồ sơ phụ thuộc");
        products.delete(p); products.flush();
    }
}

