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

@RestController @RequestMapping("/api/v1/inventory") @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
public class InventoryController {
 private final InventoryService inventory;
 public InventoryController(InventoryService inventory) { this.inventory=inventory; }
 @GetMapping public PageResponse<Map<String,Object>> list(@RequestParam MultiValueMap<String,String> q) { return inventory.list(q); }
 @GetMapping("/{productId}") public Map<String,Object> detail(@PathVariable long productId) { return inventory.detail(productId); }
 @GetMapping("/{productId}/movements") public PageResponse<Map<String,Object>> movements(@PathVariable long productId,@RequestParam MultiValueMap<String,String> q) { return inventory.movements(productId,q); }
 @GetMapping("/{productId}/movements/{movementId}") public Map<String,Object> movement(@PathVariable long productId,@PathVariable long movementId) { return inventory.movement(productId,movementId); }
 @PostMapping("/{productId}/adjustments") public ResponseEntity<?> adjust(@PathVariable long productId,@Valid @RequestBody InventoryAdjustmentRequest r) {
  var movement=inventory.adjust(productId,r); return ResponseEntity.created(URI.create("/api/v1/inventory/"+productId+"/movements/"+movement.get("id"))).body(movement);
 }
}
