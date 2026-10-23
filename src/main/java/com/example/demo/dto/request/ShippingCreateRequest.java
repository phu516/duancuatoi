package com.example.demo.dto.request;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record ShippingCreateRequest(@NotBlank String recipientName, @NotBlank String recipientPhone, @NotBlank String deliveryMethod, String address, String note) {}
