package com.example.demo.config;
import com.example.demo.dto.response.ApiErrorResponse;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.*;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.web.*;
import org.springframework.security.web.context.*;
import org.springframework.security.web.csrf.*;
import org.springframework.security.web.authentication.session.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.*;
import tools.jackson.databind.json.JsonMapper;
import java.util.List;

@Configuration @EnableMethodSecurity
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean UserDetailsService userDetailsService(UserRepository users) {
        return email -> ShopPrincipal.of(users.findByEmail(email).orElseThrow(() -> new UsernameNotFoundException("Invalid credentials")));
    }
    @Bean AuthenticationManager authenticationManager(UserDetailsService details,PasswordEncoder encoder) {
        var provider=new DaoAuthenticationProvider(details);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }
    @Bean SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }
    @Bean CsrfTokenRepository csrfTokenRepository() {
        var repo=new HttpSessionCsrfTokenRepository(); repo.setHeaderName("X-CSRF-TOKEN"); return repo;
    }
    @Bean SessionAuthenticationStrategy sessionAuthenticationStrategy(CsrfTokenRepository csrf) {
        return new CompositeSessionAuthenticationStrategy(List.of(
            new ChangeSessionIdAuthenticationStrategy(),new CsrfAuthenticationStrategy(csrf)));
    }
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http,UserRepository users,
        SecurityContextRepository contexts,CsrfTokenRepository csrf,JsonMapper json) throws Exception {
        http.csrf(c -> c.csrfTokenRepository(csrf).csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()));
        http.securityContext(c -> c.securityContextRepository(contexts).requireExplicitSave(true));
        http.sessionManagement(c -> c.sessionFixation(f -> f.changeSessionId()));
        http.requestCache(c -> c.disable());
        http.formLogin(c -> c.disable()).httpBasic(c -> c.disable()).logout(c -> c.disable());
        http.authorizeHttpRequests(a -> a
            .requestMatchers("/error").permitAll()
            .requestMatchers("/admin", "/admin/**").hasAnyRole("STAFF","ADMIN")
            .requestMatchers("/account", "/account/**", "/cart", "/cart/**", "/checkout", "/checkout/**", "/orders", "/orders/**", "/logout").authenticated()
            .requestMatchers("/api/v1/auth/csrf-token","/api/v1/auth/register","/api/v1/auth/login").permitAll()
            .requestMatchers("/api/v1/users","/api/v1/users/**","/api/v1/auth/logout").authenticated()
            .requestMatchers("/api/v1/cart","/api/v1/cart/**","/api/v1/checkout/**","/api/v1/orders","/api/v1/orders/**",
                "/api/v1/suppliers","/api/v1/suppliers/**","/api/v1/inventory","/api/v1/inventory/**","/api/v1/health-records").authenticated()
            .requestMatchers(HttpMethod.GET,"/api/v1/pets","/api/v1/pets/**").permitAll()
            .requestMatchers(HttpMethod.HEAD,"/api/v1/pets","/api/v1/pets/**").permitAll()
            .requestMatchers("/api/v1/pets","/api/v1/pets/**").authenticated()
            .requestMatchers(HttpMethod.GET,"/api/v1/categories","/api/v1/categories/**","/api/v1/products","/api/v1/products/**").permitAll()
            .requestMatchers(HttpMethod.HEAD,"/api/v1/categories","/api/v1/categories/**","/api/v1/products","/api/v1/products/**").permitAll()
            .requestMatchers("/api/v1/categories","/api/v1/categories/**","/api/v1/products","/api/v1/products/**").authenticated()
            .anyRequest().permitAll());
        http.exceptionHandling(e -> e
            .authenticationEntryPoint((req,res,error) -> {
                if (!req.getRequestURI().startsWith(req.getContextPath()+"/api/")) {
                    res.sendRedirect(req.getContextPath()+"/login"); return;
                }
                res.setStatus(401); res.setContentType("application/json"); res.setCharacterEncoding("UTF-8");
                res.getWriter().write(json.writeValueAsString(ApiErrorResponse.of("AUTHENTICATION_REQUIRED","Bạn cần đăng nhập",(String)req.getAttribute("trace_id"))));
            })
            .accessDeniedHandler((req,res,error) -> {
                if (!req.getRequestURI().startsWith(req.getContextPath()+"/api/")) {
                    res.sendError(403, error instanceof CsrfException ? "Phiên biểu mẫu đã hết hiệu lực. Hãy tải lại trang rồi thử lại." : "Bạn không có quyền thực hiện thao tác này."); return;
                }
                res.setStatus(403); res.setContentType("application/json"); res.setCharacterEncoding("UTF-8");
                boolean csrfError=error instanceof CsrfException;
                res.getWriter().write(json.writeValueAsString(ApiErrorResponse.of(csrfError?"CSRF_INVALID":"FORBIDDEN",
                    csrfError?"CSRF token thiếu hoặc không hợp lệ":"Không đủ quyền",(String)req.getAttribute("trace_id"))));
            }));
        http.addFilterAfter(new SessionVersionFilter(users),SecurityContextHolderFilter.class);
        return http.build();
    }
}
