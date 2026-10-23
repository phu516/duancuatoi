package com.example.demo.dto.request;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record ReviewCreateRequest(@NotNull @Min(1) @Max(5) Integer rating, String comment) {}
