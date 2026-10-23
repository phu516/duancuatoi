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
public class ShippingUpdateRequest {
 @JsonIgnore private final Set<String> supplied = new HashSet<>();
 private String recipientName;
 private String recipientPhone;
 private String address;
 private String note;
 @JsonSetter public void setRecipientName(String value) { supplied.add("recipient_name"); recipientName=value; }
 @JsonSetter public void setRecipientPhone(String value) { supplied.add("recipient_phone"); recipientPhone=value; }
 @JsonSetter public void setAddress(String value) { supplied.add("address"); address=value; }
 @JsonSetter public void setNote(String value) { supplied.add("note"); note=value; }
 public boolean has(String field) { return supplied.contains(field); }
}
