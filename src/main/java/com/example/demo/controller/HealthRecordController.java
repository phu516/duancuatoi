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

@RestController @RequestMapping("/api/v1")
public class HealthRecordController {
 private final PetService pets;
 public HealthRecordController(PetService pets) { this.pets=pets; }
 @GetMapping("/pets/{petId}/health-records")
 public PageResponse<Map<String,Object>> list(@PathVariable long petId,@RequestParam MultiValueMap<String,String> q) { return pets.healthList(petId,q); }
 @GetMapping("/health-records") @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
 public PageResponse<Map<String,Object>> all(@RequestParam MultiValueMap<String,String> q) { return pets.healthList(null,q); }
 @GetMapping("/pets/{petId}/health-records/{recordId}")
 public Map<String,Object> detail(@PathVariable long petId,@PathVariable long recordId) { return pets.health(petId,recordId); }
 @PostMapping("/pets/{petId}/health-records") @PreAuthorize("hasRole('ADMIN')")
 public ResponseEntity<?> create(@PathVariable long petId,@Valid @RequestBody HealthRecordCreateRequest r) {
  var record=pets.addHealth(petId,r); return ResponseEntity.created(URI.create("/api/v1/pets/"+petId+"/health-records/"+record.get("id"))).body(record);
 }
 @PatchMapping("/pets/{petId}/health-records/{recordId}") @PreAuthorize("hasRole('ADMIN')")
 public Map<String,Object> update(@PathVariable long petId,@PathVariable long recordId,@Valid @RequestBody HealthRecordUpdateRequest r) { return pets.updateHealth(petId,recordId,r); }
 @DeleteMapping("/pets/{petId}/health-records/{recordId}") @PreAuthorize("hasRole('ADMIN')")
 public ResponseEntity<Void> delete(@PathVariable long petId,@PathVariable long recordId) { pets.deleteHealth(petId,recordId); return ResponseEntity.noContent().build(); }
}
