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

@RestController @RequestMapping("/api/v1/checkout") @PreAuthorize("isAuthenticated()")
public class CheckoutController {
 private final CheckoutService checkout;
 public CheckoutController(CheckoutService checkout) { this.checkout=checkout; }
 @PostMapping("/preview") public Map<String,Object> preview(@Valid @RequestBody CheckoutPreviewRequest r) { return checkout.preview(r); }
}
