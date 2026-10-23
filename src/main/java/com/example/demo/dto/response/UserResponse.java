package com.example.demo.dto.response;
import java.time.Instant;
import com.example.demo.entity.User;
import com.example.demo.entity.enums.Role;
public record UserResponse(Long id, String fullName, String email, String phone, String address,
                           Role role, boolean isActive, Instant createdAt) {
    public static UserResponse of(User u) { return new UserResponse(u.getId(), u.getFullName(), u.getEmail(),
        u.getPhone(), u.getAddress(), u.getRole(), u.isActive(), u.getCreatedAt()); }
}

