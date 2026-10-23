package com.example.demo.controller;
import com.example.demo.dto.request.*;
import com.example.demo.dto.response.*;
import com.example.demo.service.CategoryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import java.net.URI;

@RestController @RequestMapping("/api/v1/categories")
public class CategoryController {
    private final CategoryService categories;
    public CategoryController(CategoryService categories) {
        this.categories=categories;
    }
    @GetMapping
    public PageResponse<CategoryResponse> list(@RequestParam MultiValueMap<String,String> params) {
        return categories.list(params);
    }
    @GetMapping("/{categoryId}")
    public CategoryResponse detail(@PathVariable long categoryId) {
        return categories.detail(categoryId);
    }
    @PostMapping @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CategoryCreateRequest r) {
        var c=categories.create(r); return ResponseEntity.created(URI.create("/api/v1/categories/"+c.id())).body(c);
    }
    @PatchMapping("/{categoryId}") @PreAuthorize("hasRole('ADMIN')")
    public CategoryResponse update(@PathVariable long categoryId,@Valid @RequestBody CategoryUpdateRequest r) {
        return categories.update(categoryId,r);
    }
    @DeleteMapping("/{categoryId}") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable long categoryId) {
        categories.delete(categoryId); return ResponseEntity.noContent().build();
    }
}

