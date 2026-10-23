package com.example.demo.dto.request;
import java.math.BigDecimal;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class PetCreateRequest {
    @NotNull @Positive private Long productId;
    @Positive private Long supplierId;
    private String breed;
    private String age;
    private String gender;
    private String color;
    private BigDecimal weight;
    @JsonSetter(nulls=Nulls.FAIL) private Boolean isNeutered=false;
    private String vaccinationStatus;
}
