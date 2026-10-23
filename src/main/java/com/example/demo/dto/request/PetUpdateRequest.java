package com.example.demo.dto.request;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.*;
import java.util.*;
import lombok.Getter;

@Getter
public class PetUpdateRequest {
 @JsonIgnore private final Set<String> supplied = new HashSet<>();
 private Long supplierId;
 private String breed;
 private String age;
 private String gender;
 private String color;
 private BigDecimal weight;
 private Boolean isNeutered;
 private String vaccinationStatus;
 @JsonSetter public void setSupplierId(Long value) { supplied.add("supplier_id"); supplierId=value; }
 @JsonSetter public void setBreed(String value) { supplied.add("breed"); breed=value; }
 @JsonSetter public void setAge(String value) { supplied.add("age"); age=value; }
 @JsonSetter public void setGender(String value) { supplied.add("gender"); gender=value; }
 @JsonSetter public void setColor(String value) { supplied.add("color"); color=value; }
 @JsonSetter public void setWeight(BigDecimal value) { supplied.add("weight"); weight=value; }
 @JsonSetter public void setIsNeutered(Boolean value) { supplied.add("is_neutered"); isNeutered=value; }
 @JsonSetter public void setVaccinationStatus(String value) { supplied.add("vaccination_status"); vaccinationStatus=value; }
 public boolean has(String field) { return supplied.contains(field); }
}
