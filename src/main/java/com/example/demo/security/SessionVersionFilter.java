package com.example.demo.security;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.core.context.SecurityContextHolder;
import com.example.demo.repository.UserRepository;

public class SessionVersionFilter extends OncePerRequestFilter {
    private final UserRepository users;
    public SessionVersionFilter(UserRepository users) { this.users=users; }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
        throws ServletException,IOException {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth!=null && auth.getPrincipal() instanceof ShopPrincipal p) {
            var user=users.findById(p.id());
            if(user.isEmpty() || !user.get().isActive() || user.get().getSessionVersion()!=p.version()) {
                var session=request.getSession(false);
                if(session!=null) session.invalidate();
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request,response);
    }
}

