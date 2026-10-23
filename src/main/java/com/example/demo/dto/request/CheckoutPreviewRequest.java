package com.example.demo.dto.request;
import com.fasterxml.jackson.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
@Getter
public class CheckoutPreviewRequest {
 @JsonIgnore private boolean itemsSupplied;
 private List<@NotNull @Valid OrderLineRequest> items;
 @Setter @NotNull @Valid private ShippingCreateRequest shipping;
 @Setter private String couponCode;
 @JsonSetter public void setItems(List<OrderLineRequest> value) { itemsSupplied=true; items=value; }
}
