package com.example.demo.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import com.example.demo.entity.enums.Role;

@Entity @Table(name = "users") @Getter @Setter
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable=false, length=100) private String fullName;
    @Column(nullable=false, unique=true, length=100) private String email;
    @Column(nullable=false, length=100) private String passwordHash;
    @Column(length=20) private String phone;
    @Column(length=255) private String address;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private Role role = Role.customer;
    @Column(name="is_active", nullable=false) private boolean active = true;
    @Column(nullable=false) private long sessionVersion = 0;
    @Column(nullable=false, updatable=false) private Instant createdAt = Instant.now();
}

