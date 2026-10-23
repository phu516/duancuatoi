package com.example.demo.dto.request;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.example.demo.entity.enums.*;
import java.math.BigDecimal;
public record ChangePasswordRequest(@NotBlank @Size(max=1024) String oldPassword, @NotBlank String newPassword, @NotBlank String passwordConfirmation) {}

