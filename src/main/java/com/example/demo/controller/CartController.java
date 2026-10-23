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

@RestController @RequestMapping("/api/v1/cart") @PreAuthorize("isAuthenticated()")
public class CartController {
 private final CartService carts;
 public CartController(CartService carts) { this.carts=carts; }
 @GetMapping public Map<String,Object> get() { return carts.get(); }
 @PostMapping("/items") public Map<String,Object> add(@Valid @RequestBody CartItemCreateRequest r) { return carts.put(r.productId(),r.quantity(),true); }
 @PutMapping("/items/{productId}") public Map<String,Object> replace(@PathVariable long productId,@Valid @RequestBody CartItemReplaceRequest r) { return carts.put(productId,r.quantity(),false); }
 @DeleteMapping("/items/{productId}") public ResponseEntity<Void> delete(@PathVariable long productId) { carts.delete(productId); return ResponseEntity.noContent().build(); }
 @DeleteMapping("/items") public ResponseEntity<Void> clear() { carts.delete(null); return ResponseEntity.noContent().build(); }
}
