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

@RestController @RequestMapping("/api/v1/suppliers") @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
public class SupplierController {
 private final SupplierService suppliers;
 public SupplierController(SupplierService suppliers) { this.suppliers=suppliers; }
 @GetMapping public PageResponse<Map<String,Object>> list(@RequestParam MultiValueMap<String,String> q) { return suppliers.list(q); }
 @GetMapping("/{supplierId}") public Map<String,Object> detail(@PathVariable long supplierId) { return suppliers.detail(supplierId); }
 @PostMapping @PreAuthorize("hasRole('ADMIN')") public ResponseEntity<?> create(@Valid @RequestBody SupplierCreateRequest r) {
  var supplier=suppliers.create(r); return ResponseEntity.created(URI.create("/api/v1/suppliers/"+supplier.get("id"))).body(supplier);
 }
 @PatchMapping("/{supplierId}") @PreAuthorize("hasRole('ADMIN')")
 public Map<String,Object> update(@PathVariable long supplierId,@Valid @RequestBody SupplierUpdateRequest r) { return suppliers.update(supplierId,r); }
 @DeleteMapping("/{supplierId}") @PreAuthorize("hasRole('ADMIN')")
 public ResponseEntity<Void> delete(@PathVariable long supplierId) { suppliers.delete(supplierId); return ResponseEntity.noContent().build(); }
}
