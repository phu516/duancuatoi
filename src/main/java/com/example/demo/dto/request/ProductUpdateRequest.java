package com.example.demo.dto.request;
import com.fasterxml.jackson.annotation.*;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;

@Getter
public class ProductUpdateRequest {
    @JsonIgnore private final Set<String> supplied = new HashSet<>();
    private Long categoryId;
    private String name;
    private String description;
    private BigDecimal price;
    private String imageUrl;
    @JsonSetter public void setCategoryId(Long value) { supplied.add("category_id"); categoryId=value; }
    @JsonSetter public void setName(String value) { supplied.add("name"); name=value; }
    @JsonSetter public void setDescription(String value) { supplied.add("description"); description=value; }
    @JsonSetter public void setPrice(BigDecimal value) { supplied.add("price"); price=value; }
    @JsonSetter public void setImageUrl(String value) { supplied.add("image_url"); imageUrl=value; }
    public boolean has(String field) { return supplied.contains(field); }
}

