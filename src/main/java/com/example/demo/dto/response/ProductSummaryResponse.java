package com.example.demo.dto.response;
import com.example.demo.entity.Product;
import com.example.demo.entity.enums.ProductType;
import java.time.Instant;
import java.math.BigDecimal;
public record ProductSummaryResponse(Long id, Long categoryId, ProductType productType, String name,
    BigDecimal price, int stockQuantity, String imageUrl, boolean isActive, Instant createdAt) {
    public static ProductSummaryResponse of(Product p) { return new ProductSummaryResponse(p.getId(), p.getCategoryId(),
        p.getProductType(), p.getName(), p.getPrice(), p.getStockQuantity(), p.getImageUrl(), p.isActive(), p.getCreatedAt()); }
}

