package com.example.demo.web;

import com.example.demo.dto.request.ChangePasswordRequest;
import com.example.demo.dto.request.ProfileUpdateRequest;
import com.example.demo.exception.ApiException;
import com.example.demo.service.AuthService;
import com.example.demo.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class WebAccountController {
    private final UserService users;
    private final AuthService auth;
    public WebAccountController(UserService users, AuthService auth) { this.users=users; this.auth=auth; }

    @GetMapping("/account") public String account(Model model) {
        model.addAttribute("account",users.me());
        if(!model.containsAttribute("form")) {
            var u=users.me(); var form=new WebForms.Profile();
            form.setFullName(u.fullName()); form.setPhone(u.phone()); form.setAddress(u.address());
            model.addAttribute("form",form);
        }
        return "account";
    }
    @PostMapping("/account") public String update(@Valid @ModelAttribute("form") WebForms.Profile form,BindingResult errors,
            Model model,RedirectAttributes flash,HttpServletResponse response) {
        if(errors.hasErrors()) { response.setStatus(400); model.addAttribute("account",users.me()); return "account"; }
        try {
            var request=new ProfileUpdateRequest(); request.setFullName(form.getFullName());
            if(form.getPhone()!=null && !form.getPhone().isBlank()) request.setPhone(form.getPhone());
            if(form.getAddress()!=null && !form.getAddress().isBlank()) request.setAddress(form.getAddress());
            users.update(request);
            flash.addFlashAttribute("successMessage","Đã cập nhật hồ sơ."); return "redirect:/account";
        } catch(ApiException e) { response.setStatus(e.getStatus()); model.addAttribute("account",users.me()); model.addAttribute("errorMessage",e.getMessage()); return "account"; }
    }
    @GetMapping("/account/password") public String password(Model model) {
        if(!model.containsAttribute("form")) model.addAttribute("form",new WebForms.Password());
        return "account-password";
    }
    @PostMapping("/account/password") public String password(@Valid @ModelAttribute("form") WebForms.Password form,
            BindingResult errors,Model model,RedirectAttributes flash,HttpServletRequest request,HttpServletResponse response) {
        if(errors.hasErrors()) { response.setStatus(400); return "account-password"; }
        try {
            users.password(new ChangePasswordRequest(form.getOldPassword(),form.getNewPassword(),form.getPasswordConfirmation()));
            auth.logout(request,response);
            flash.addFlashAttribute("successMessage","Đổi mật khẩu thành công. Vui lòng đăng nhập lại."); return "redirect:/login";
        } catch(ApiException e) { response.setStatus(e.getStatus()); model.addAttribute("errorMessage",e.getMessage()); return "account-password"; }
    }
}
