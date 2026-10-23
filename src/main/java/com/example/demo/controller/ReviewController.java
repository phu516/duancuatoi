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

@RestController @RequestMapping("/api/v1/products/{productId}/reviews")
public class ReviewController {
 private final ReviewService reviews;
 public ReviewController(ReviewService reviews) { this.reviews=reviews; }
 @GetMapping public PageResponse<Map<String,Object>> list(@PathVariable long productId,@RequestParam MultiValueMap<String,String> q) { return reviews.list(productId,q); }
 @GetMapping("/{reviewId}") public Map<String,Object> detail(@PathVariable long productId,@PathVariable long reviewId) { return reviews.detail(productId,reviewId); }
 @PostMapping @PreAuthorize("isAuthenticated()") public ResponseEntity<?> create(@PathVariable long productId,@Valid @RequestBody ReviewCreateRequest r) {
  var review=reviews.create(productId,r); return ResponseEntity.created(URI.create("/api/v1/products/"+productId+"/reviews/"+review.get("id"))).body(review);
 }
 @PatchMapping("/{reviewId}") @PreAuthorize("isAuthenticated()")
 public Map<String,Object> update(@PathVariable long productId,@PathVariable long reviewId,@Valid @RequestBody ReviewUpdateRequest r) { return reviews.update(productId,reviewId,r); }
 @DeleteMapping("/{reviewId}") @PreAuthorize("isAuthenticated()")
 public ResponseEntity<Void> delete(@PathVariable long productId,@PathVariable long reviewId) { reviews.delete(productId,reviewId); return ResponseEntity.noContent().build(); }
}
