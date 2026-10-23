package com.example.demo.security;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import com.example.demo.entity.User;
import com.example.demo.entity.enums.Role;

public record ShopPrincipal(Long id, String email, String password, Role role, boolean active, long version)
    implements UserDetails, Serializable {
    public static ShopPrincipal of(User u) { return new ShopPrincipal(u.getId(),u.getEmail(),u.getPasswordHash(),u.getRole(),u.isActive(),u.getSessionVersion()); }
    public ShopPrincipal withoutPassword() { return new ShopPrincipal(id,email,"",role,active,version); }
    @Override public String getUsername() { return email; }
    @Override public String getPassword() { return password; }
    @Override public boolean isEnabled() { return active; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_"+role.name().toUpperCase(java.util.Locale.ROOT)));
    }
}

