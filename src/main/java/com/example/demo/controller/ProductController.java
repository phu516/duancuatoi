package com.example.demo.controller;
import com.example.demo.dto.request.*;
import com.example.demo.dto.response.*;
import com.example.demo.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import java.net.URI;

@RestController @RequestMapping("/api/v1/products")
public class ProductController {
    private final ProductService products;
    public ProductController(ProductService products) {
        this.products=products;
    }
    @GetMapping
    public PageResponse<ProductSummaryResponse> list(@RequestParam MultiValueMap<String,String> params) {
        return products.list(params);
    }
    @GetMapping("/{productId}")
    public ProductDetailResponse detail(@PathVariable long productId) {
        return products.detail(productId);
    }
    @PostMapping @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductDetailResponse> create(@Valid @RequestBody ProductCreateRequest r) {
        var p=products.create(r); return ResponseEntity.created(URI.create("/api/v1/products/"+p.id())).body(p);
    }
    @PatchMapping("/{productId}") @PreAuthorize("hasRole('ADMIN')")
    public ProductDetailResponse update(@PathVariable long productId,@Valid @RequestBody ProductUpdateRequest r) {
        return products.update(productId,r);
    }
    @PatchMapping("/{productId}/status") @PreAuthorize("hasRole('ADMIN')")
    public ProductDetailResponse status(@PathVariable long productId,@Valid @RequestBody ActiveStatusRequest r) {
        return products.status(productId,r);
    }
    @DeleteMapping("/{productId}") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable long productId) {
        products.delete(productId); return ResponseEntity.noContent().build();
    }
}

