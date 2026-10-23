package com.example.demo.web;

import com.example.demo.security.CurrentUser;
import com.example.demo.service.*;
import com.example.demo.exception.ApiException;
import jakarta.servlet.http.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.BindException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@ControllerAdvice(basePackages="com.example.demo.web") @Order(0)
public class WebAdvice {
    private final CurrentUser current; private final UserService users; private final CartService carts; private final StorefrontData data;
    public WebAdvice(CurrentUser current,UserService users,CartService carts,StorefrontData data) {
        this.current=current; this.users=users; this.carts=carts; this.data=data;
    }
    @ModelAttribute
    public void common(Model model,HttpServletRequest request,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
        model.addAttribute("display",new Display());
        model.addAttribute("categories",data.categories());
        model.addAttribute("currentPath",request.getRequestURI());
        if(current.principalOrNull()!=null) {
            model.addAttribute("viewer",users.me());
            model.addAttribute("cartCount",carts.lines(current.require().id()).stream().mapToInt(x->x.quantity()).sum());
        } else model.addAttribute("cartCount",0);
    }
    @ExceptionHandler(ApiException.class)
    public String business(ApiException error,Model model,HttpServletResponse response) {
        if(error instanceof com.example.demo.exception.RateLimitException limited)
            response.setHeader("Retry-After",Long.toString(limited.getRetryAfterSeconds()));
        if(error.getStatus()==401) return "redirect:/login";
        response.setStatus(error.getStatus()); model.addAttribute("status",error.getStatus());
        model.addAttribute("message",error.getMessage()); return "error";
    }
    @ExceptionHandler({BindException.class,MethodArgumentTypeMismatchException.class})
    public String invalid(Exception error,Model model,HttpServletResponse response) {
        response.setStatus(400); model.addAttribute("status",400); model.addAttribute("message","Thông tin gửi lên chưa hợp lệ. Hãy kiểm tra rồi thử lại."); return "error";
    }
}
