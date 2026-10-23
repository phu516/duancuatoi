package com.example.demo.service;
import com.example.demo.entity.Category;
import com.example.demo.dto.request.*;
import com.example.demo.dto.response.*;
import com.example.demo.repository.*;
import com.example.demo.security.CurrentUser;
import com.example.demo.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Sort;
import org.springframework.util.MultiValueMap;

@Service @Transactional(readOnly=true)
public class CategoryService {
    private final CategoryRepository categories; private final ProductRepository products; private final CurrentUser current;
    public CategoryService(CategoryRepository categories,ProductRepository products,CurrentUser current) {
        this.categories=categories; this.products=products; this.current=current;
    }
    public PageResponse<CategoryResponse> list(MultiValueMap<String,String> params) {
        var q=new QueryParameters(params,"keyword"); String keyword=q.keyword();
        Specification<Category> spec=(root,query,cb) -> keyword==null ? cb.conjunction() :
            cb.like(cb.lower(root.get("name")),QueryParameters.contains(keyword),'!');
        return PageResponse.of(categories.findAll(spec,q.page(Sort.by("id").descending())).map(CategoryResponse::of));
    }
    public CategoryResponse detail(long id) {
        return CategoryResponse.of(categories.findById(InputRules.id(id)).orElseThrow(ApiException::missing));
    }
    @Transactional public CategoryResponse create(CategoryCreateRequest r) {
        current.admin(); Category c=new Category(); c.setName(InputRules.text(r.name(),100,"name"));
        return CategoryResponse.of(categories.saveAndFlush(c));
    }
    @Transactional public CategoryResponse update(long id,CategoryUpdateRequest r) {
        current.admin(); Category c=categories.lockById(InputRules.id(id)).orElseThrow(ApiException::missing);
        c.setName(InputRules.text(r.name(),100,"name")); return CategoryResponse.of(c);
    }
    @Transactional public void delete(long id) {
        current.admin(); Category c=categories.lockById(InputRules.id(id)).orElseThrow(ApiException::missing);
        if(products.existsByCategoryId(id)) throw ApiException.conflict("RESOURCE_IN_USE","Danh mục còn sản phẩm, kể cả sản phẩm ngừng bán");
        categories.delete(c); categories.flush();
    }
}

