package com.example.demo.dto.request;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record HealthRecordCreateRequest(@NotNull @PastOrPresent LocalDate recordDate, @NotBlank String recordType, String description, String vetName) {}
