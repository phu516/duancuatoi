package com.example.demo.controller;
import com.example.demo.dto.request.*;
import com.example.demo.dto.response.PageResponse;
import com.example.demo.service.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import java.net.URI;
import java.util.*;

@RestController @RequestMapping("/api/v1/pets")
public class PetController {
 private final PetService pets;
 public PetController(PetService pets) { this.pets=pets; }
 @GetMapping public PageResponse<Map<String,Object>> list(@RequestParam MultiValueMap<String,String> q) { return pets.list(q); }
 @GetMapping("/{petId}") public Map<String,Object> detail(@PathVariable long petId) { return pets.detail(petId); }
 @PostMapping @PreAuthorize("hasRole('ADMIN')")
 public ResponseEntity<?> create(@Valid @RequestBody PetCreateRequest r) {
  var pet=pets.create(r); return ResponseEntity.created(URI.create("/api/v1/pets/"+pet.get("id"))).body(pet);
 }
 @PatchMapping("/{petId}") @PreAuthorize("hasRole('ADMIN')")
 public Map<String,Object> update(@PathVariable long petId,@Valid @RequestBody PetUpdateRequest r) { return pets.update(petId,r); }
 @DeleteMapping("/{petId}") @PreAuthorize("hasRole('ADMIN')")
 public ResponseEntity<Void> delete(@PathVariable long petId) { pets.delete(petId); return ResponseEntity.noContent().build(); }
}
