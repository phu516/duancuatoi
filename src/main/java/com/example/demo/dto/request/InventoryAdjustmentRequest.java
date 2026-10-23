package com.example.demo.dto.request;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record InventoryAdjustmentRequest(@NotNull @Min(-1000000) @Max(1000000) Integer change, @NotBlank String reason, String reference) {}
