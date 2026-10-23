package com.example.demo.dto.response;
import com.example.demo.entity.Product;
import com.example.demo.entity.Pet;
import com.example.demo.entity.enums.*;
import java.time.Instant;
import java.math.BigDecimal;
public record ProductDetailResponse(Long id, Long categoryId, ProductType productType, String name,
    BigDecimal price, int stockQuantity, String imageUrl, boolean isActive, Instant createdAt,
    String description, PetProfile pet, ReviewSummary reviewSummary) {
    public record ReviewSummary(long count, BigDecimal average) {}
    public record PetProfile(Long id, Long productId, String breed, String age, String gender, String color,
        BigDecimal weight, boolean isNeutered, String vaccinationStatus, PetStatus status, Instant createdAt) {
        public static PetProfile of(Pet p) { return new PetProfile(p.getId(),p.getProductId(),p.getBreed(),
            p.getAge(),p.getGender(),p.getColor(),p.getWeight(),p.isNeutered(),p.getVaccinationStatus(),p.getStatus(),p.getCreatedAt()); }
    }
    public static ProductDetailResponse of(Product p, Pet pet, ReviewSummary reviews) {
        return new ProductDetailResponse(p.getId(),p.getCategoryId(),p.getProductType(),p.getName(),p.getPrice(),
            p.getStockQuantity(),p.getImageUrl(),p.isActive(),p.getCreatedAt(),p.getDescription(),
            pet == null ? null : PetProfile.of(pet),reviews);
    }
}

