package com.example.demo.dto.request;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.example.demo.entity.enums.*;
import java.math.BigDecimal;
public record RegisterRequest(@NotBlank @Size(max=100) String fullName, @NotBlank @Email @Size(max=100) String email,
    @NotBlank String password, @NotBlank String passwordConfirmation, @NotBlank @Size(max=30) String phone,
    @NotBlank @Size(max=255) String address) {
    public RegisterRequest { if(email!=null) email=email.trim().toLowerCase(java.util.Locale.ROOT); }
}
