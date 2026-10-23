package com.example.demo.dto.request;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.example.demo.entity.enums.*;
import java.math.BigDecimal;
public record ProductCreateRequest(@NotNull @Positive Long categoryId, @NotNull ProductType productType,
    @NotBlank @Size(max=150) String name, @Size(max=10000) String description,
    @NotNull @DecimalMin(value="0",inclusive=false) @DecimalMax("9999999999.99") @Digits(integer=10,fraction=2) BigDecimal price,
    @Min(0) @Max(1000000) Integer stockQuantity, @Size(max=1000) String imageUrl) {}

