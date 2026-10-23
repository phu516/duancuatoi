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
public class ReviewUpdateRequest {
 @JsonIgnore private final Set<String> supplied = new HashSet<>();
 private Integer rating;
 private String comment;
 @JsonSetter public void setRating(Integer value) { supplied.add("rating"); rating=value; }
 @JsonSetter public void setComment(String value) { supplied.add("comment"); comment=value; }
 public boolean has(String field) { return supplied.contains(field); }
}
