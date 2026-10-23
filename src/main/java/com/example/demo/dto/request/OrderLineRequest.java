package com.example.demo.dto.request;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record OrderLineRequest(@NotNull @Positive Long productId, @NotNull @Min(1) @Max(1000000) Integer quantity) {}
