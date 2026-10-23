package com.example.demo.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.math.BigDecimal;
import com.example.demo.entity.enums.ProductType;

@Entity @Table(name="products") @Getter @Setter
public class Product {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private Long categoryId;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private ProductType productType;
    @Column(nullable=false, length=150) private String name;
    @Column(length=10000) private String description;
    @Column(nullable=false, precision=12, scale=2) private BigDecimal price;
    @Column(nullable=false) private int stockQuantity;
    @Column(length=1000) private String imageUrl;
    @Column(name="is_active", nullable=false) private boolean active;
    @Column(nullable=false, updatable=false) private Instant createdAt = Instant.now();
}

