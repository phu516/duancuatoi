package com.example.demo.web;

import com.example.demo.dto.request.*;
import com.example.demo.exception.ApiException;
import com.example.demo.security.CurrentUser;
import com.example.demo.service.AuthService;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class WebAuthController {
    private final AuthService auth; private final CurrentUser current;
    public WebAuthController(AuthService auth,CurrentUser current) { this.auth=auth; this.current=current; }
    @GetMapping("/login") public String login(Model model) {
        if(current.principalOrNull()!=null) return "redirect:/";
        model.addAttribute("form",new WebForms.Login()); return "login";
    }
    @PostMapping("/login") public String login(@Valid @ModelAttribute("form") WebForms.Login form,BindingResult errors,
            HttpServletRequest request,HttpServletResponse response,Model model) {
        if(errors.hasErrors()) { response.setStatus(400); return "login"; }
        try { auth.login(new LoginRequest(form.getEmail(),form.getPassword()),request,response); return "redirect:/"; }
        catch(ApiException e) { if(e instanceof com.example.demo.exception.RateLimitException limited) response.setHeader("Retry-After",Long.toString(limited.getRetryAfterSeconds())); response.setStatus(e.getStatus()); model.addAttribute("errorMessage",e.getMessage()); return "login"; }
    }
    @GetMapping("/register") public String register(Model model) {
        if(current.principalOrNull()!=null) return "redirect:/";
        model.addAttribute("form",new WebForms.Registration()); return "register";
    }
    @PostMapping("/register") public String register(@Valid @ModelAttribute("form") WebForms.Registration f,BindingResult errors,
            Model model,HttpServletResponse response,RedirectAttributes flash) {
        if(errors.hasErrors()) { response.setStatus(400); return "register"; }
        try {
            auth.register(new RegisterRequest(f.getFullName(),f.getEmail(),f.getPassword(),f.getPasswordConfirmation(),f.getPhone(),f.getAddress()));
            flash.addFlashAttribute("successMessage","Tạo tài khoản thành công. Bạn có thể đăng nhập ngay."); return "redirect:/login";
        } catch(ApiException e) { if(e instanceof com.example.demo.exception.RateLimitException limited) response.setHeader("Retry-After",Long.toString(limited.getRetryAfterSeconds())); response.setStatus(e.getStatus()); model.addAttribute("errorMessage",e.getMessage()); return "register"; }
    }
    @PostMapping("/logout") public String logout(HttpServletRequest request,HttpServletResponse response) {
        auth.logout(request,response); return "redirect:/login";
    }
}
