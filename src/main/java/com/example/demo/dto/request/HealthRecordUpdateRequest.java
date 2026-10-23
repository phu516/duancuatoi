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
public class HealthRecordUpdateRequest {
 @JsonIgnore private final Set<String> supplied = new HashSet<>();
 private LocalDate recordDate;
 private String recordType;
 private String description;
 private String vetName;
 @JsonSetter public void setRecordDate(LocalDate value) { supplied.add("record_date"); recordDate=value; }
 @JsonSetter public void setRecordType(String value) { supplied.add("record_type"); recordType=value; }
 @JsonSetter public void setDescription(String value) { supplied.add("description"); description=value; }
 @JsonSetter public void setVetName(String value) { supplied.add("vet_name"); vetName=value; }
 public boolean has(String field) { return supplied.contains(field); }
}
