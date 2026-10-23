package com.example.demo.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.math.BigDecimal;
import com.example.demo.entity.enums.PetStatus;

@Entity @Table(name="pets") @Getter @Setter
public class Pet {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false, unique=true) private Long productId;
    private Long supplierId;
    @Column(length=100) private String breed;
    @Column(length=50) private String age;
    @Column(length=10) private String gender;
    @Column(length=50) private String color;
    @Column(precision=6,scale=2) private BigDecimal weight;
    @Column(name="is_neutered",nullable=false) private boolean neutered;
    @Column(length=255) private String vaccinationStatus;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private PetStatus status = PetStatus.available;
    private Long reservedOrderId;
    private Instant reservedUntil;
    @Column(nullable=false,updatable=false) private Instant createdAt = Instant.now();
}

