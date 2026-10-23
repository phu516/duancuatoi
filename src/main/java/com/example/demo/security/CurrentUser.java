package com.example.demo.security;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import com.example.demo.entity.User;
import com.example.demo.entity.enums.Role;
import com.example.demo.repository.UserRepository;
import com.example.demo.exception.ApiException;

@Component
public class CurrentUser {
    private final UserRepository users;
    public CurrentUser(UserRepository users) { this.users=users; }
    public ShopPrincipal principalOrNull() {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        return auth!=null && auth.getPrincipal() instanceof ShopPrincipal p ? p : null;
    }
    public ShopPrincipal require() {
        ShopPrincipal p=principalOrNull();
        if(p==null) throw new ApiException(401,"AUTHENTICATION_REQUIRED","Bạn cần đăng nhập");
        User u=users.findById(p.id()).orElseThrow(() -> new ApiException(401,"SESSION_EXPIRED","Phiên đã hết hiệu lực"));
        if(!u.isActive() || u.getSessionVersion()!=p.version()) throw new ApiException(401,"SESSION_EXPIRED","Phiên đã hết hiệu lực");
        return p;
    }
    public void admin() { if(require().role()!=Role.admin) throw ApiException.forbidden(); }
    public boolean staff() { var p=principalOrNull(); return p!=null && (p.role()==Role.staff || p.role()==Role.admin); }
}

