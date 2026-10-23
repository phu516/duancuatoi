package com.example.demo.dto.response;
import java.util.List;
import org.springframework.data.domain.Page;
public record PageResponse<T>(List<T> data, Pagination pagination) {
    public record Pagination(int page, int size, long totalElements, int totalPages) {}
    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), new Pagination(page.getNumber(), page.getSize(),
            page.getTotalElements(), page.getTotalPages()));
    }
}

