package com.example.demo.controller;
import com.example.demo.dto.request.*;
import com.example.demo.dto.response.*;
import com.example.demo.service.AuthService;
import com.example.demo.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.servlet.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.http.ResponseEntity;
import java.net.URI;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService auth;
    private final CurrentUser current;
    public AuthController(AuthService auth,CurrentUser current) {
        this.auth=auth;
        this.current=current;
    }
    @GetMapping("/csrf-token")
    public CsrfResponse csrf(CsrfToken token,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
        return new CsrfResponse(token.getToken(),token.getHeaderName());
    }
    /** Register a customer only. Does not log in automatically. */
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        var user=auth.register(request);
        return ResponseEntity.created(URI.create("/api/v1/users/"+user.id())).body(user);
    }
    /** Authenticate JSON credentials, rotate session ID and clear old CSRF token. */
    @PostMapping("/login")
    public UserResponse login(@Valid @RequestBody LoginRequest body,HttpServletRequest request,HttpServletResponse response) {
        return auth.login(body,request,response);
    }
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request,HttpServletResponse response) {
        current.require(); auth.logout(request,response);
        return ResponseEntity.noContent().build();
    }
}

