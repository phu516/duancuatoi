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
public class SupplierUpdateRequest {
 @JsonIgnore private final Set<String> supplied = new HashSet<>();
 private String name;
 private String contactInfo;
 private String address;
 @JsonSetter public void setName(String value) { supplied.add("name"); name=value; }
 @JsonSetter public void setContactInfo(String value) { supplied.add("contact_info"); contactInfo=value; }
 @JsonSetter public void setAddress(String value) { supplied.add("address"); address=value; }
 public boolean has(String field) { return supplied.contains(field); }
}
