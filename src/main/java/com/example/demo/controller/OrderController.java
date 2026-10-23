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

@RestController @RequestMapping("/api/v1/orders") @PreAuthorize("isAuthenticated()")
public class OrderController {
 private final OrderService orders;
 public OrderController(OrderService orders) { this.orders=orders; }
 @PostMapping public ResponseEntity<?> create(@RequestHeader(value="Idempotency-Key",required=false) String key,@Valid @RequestBody OrderCreateRequest r) {
  var result=orders.create(key,r);
  if(result.replayed()) return ResponseEntity.ok().header("Idempotency-Replayed","true").body(result.body());
  return ResponseEntity.created(URI.create("/api/v1/orders/"+result.body().get("id"))).body(result.body());
 }
 @GetMapping public PageResponse<Map<String,Object>> list(@RequestParam MultiValueMap<String,String> q) { return orders.list(q); }
 @GetMapping("/{orderId}") public Map<String,Object> detail(@PathVariable long orderId) { return orders.detail(orderId); }
 @GetMapping("/{orderId}/items") public List<Map<String,Object>> items(@PathVariable long orderId) { return orders.items(orderId); }
 @GetMapping("/{orderId}/events") public PageResponse<Map<String,Object>> events(@PathVariable long orderId,@RequestParam MultiValueMap<String,String> q) { return orders.events(orderId,q); }
 @PatchMapping("/{orderId}/status") @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
 public Map<String,Object> status(@PathVariable long orderId,@Valid @RequestBody OrderStatusUpdateRequest r) { return orders.status(orderId,r); }
 @PatchMapping("/{orderId}/payment-status") @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
 public Map<String,Object> payment(@PathVariable long orderId,@Valid @RequestBody PaymentStatusUpdateRequest r) { return orders.payment(orderId,r); }
 @PutMapping("/{orderId}/cancellation") public ResponseEntity<?> cancel(@PathVariable long orderId,@Valid @RequestBody OrderCancellationRequest r) {
  var result=orders.cancel(orderId,r);
  return result.replayed()?ResponseEntity.ok(result.body()):ResponseEntity.created(URI.create("/api/v1/orders/"+orderId+"/cancellation")).body(result.body());
 }
 @GetMapping("/{orderId}/cancellation") public Map<String,Object> cancellation(@PathVariable long orderId) { return orders.cancellation(orderId); }
 @GetMapping("/{orderId}/shipping") public Map<String,Object> shipping(@PathVariable long orderId) { return orders.shipping(orderId); }
 @PatchMapping("/{orderId}/shipping") public Map<String,Object> updateShipping(@PathVariable long orderId,@Valid @RequestBody ShippingUpdateRequest r) { return orders.updateShipping(orderId,r); }
}
