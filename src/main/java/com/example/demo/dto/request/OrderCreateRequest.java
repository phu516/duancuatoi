package com.example.demo.dto.request;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter
public class OrderCreateRequest extends CheckoutPreviewRequest {
 @NotBlank private String paymentMethod;
}
