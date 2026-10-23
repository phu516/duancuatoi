package com.example.demo.dto.response;
import java.time.Instant;
import com.example.demo.entity.Category;
public record CategoryResponse(Long id, String name, Instant createdAt) {
    public static CategoryResponse of(Category c) { return new CategoryResponse(c.getId(), c.getName(), c.getCreatedAt()); }
}

