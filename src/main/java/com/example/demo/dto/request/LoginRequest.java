package com.example.demo.dto.request;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.example.demo.entity.enums.*;
import java.math.BigDecimal;
public record LoginRequest(@NotBlank @Email @Size(max=100) String email, @NotBlank @Size(max=1024) String password) {
    public LoginRequest { if(email!=null) email=email.trim().toLowerCase(java.util.Locale.ROOT); }
}
