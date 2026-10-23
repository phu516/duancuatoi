package com.example.demo.controller;
import com.example.demo.dto.request.*;
import com.example.demo.dto.response.*;
import com.example.demo.service.*;
import jakarta.validation.Valid;
import jakarta.servlet.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserService users;
    private final AuthService auth;
    public UserController(UserService users,AuthService auth) {
        this.users=users; this.auth=auth;
    }
    @GetMapping("/me")
    public UserResponse me() {
        return users.me();
    }
    @PatchMapping("/me")
    public UserResponse profile(@Valid @RequestBody ProfileUpdateRequest r) {
        return users.update(r);
    }
    /** Changes password and invalidates all sessions, including this one. */
    @PutMapping("/me/password")
    public ResponseEntity<Void> password(@Valid @RequestBody ChangePasswordRequest r,HttpServletRequest req,HttpServletResponse res) {
        users.password(r); auth.logout(req,res); return ResponseEntity.noContent().build();
    }
    @GetMapping @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<UserResponse> list(@RequestParam MultiValueMap<String,String> params) {
        return users.list(params);
    }
    @GetMapping("/{userId}") public UserResponse detail(@PathVariable long userId) {
        return users.detail(userId);
    }
    @PatchMapping("/{userId}/role") @PreAuthorize("hasRole('ADMIN')")
    public UserResponse role(@PathVariable long userId,@Valid @RequestBody RoleUpdateRequest r) {
        return users.role(userId,r);
    }
    @PatchMapping("/{userId}/status") @PreAuthorize("hasRole('ADMIN')")
    public UserResponse status(@PathVariable long userId,@Valid @RequestBody ActiveStatusRequest r) {
        return users.status(userId,r);
    }
}

