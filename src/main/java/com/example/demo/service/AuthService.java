package com.example.demo.service;
import com.example.demo.dto.request.*;
import com.example.demo.dto.response.*;
import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.exception.ApiException;
import com.example.demo.security.ShopPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.*;
import org.springframework.security.core.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import jakarta.servlet.http.*;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final AuthenticationManager manager;
    private final SecurityContextRepository contexts;
    private final SessionAuthenticationStrategy sessions;
    private final RateLimitService rateLimits;
    public AuthService(UserRepository users,PasswordEncoder passwords,AuthenticationManager manager,
        SecurityContextRepository contexts,SessionAuthenticationStrategy sessions,RateLimitService rateLimits) {
        this.users=users; this.passwords=passwords; this.manager=manager; this.contexts=contexts; this.sessions=sessions; this.rateLimits=rateLimits;
    }
    @Transactional
    public UserResponse register(RegisterRequest r) {
        rateLimits.registration();
        InputRules.password(r.password(),r.passwordConfirmation());
        String email=InputRules.email(r.email());
        if(users.existsByEmail(email)) throw ApiException.conflict("EMAIL_ALREADY_EXISTS","Email đã được sử dụng");
        User u=new User(); u.setFullName(InputRules.text(r.fullName(),100,"full_name")); u.setEmail(email);
        u.setPhone(InputRules.phone(r.phone())); u.setAddress(InputRules.text(r.address(),255,"address"));
        u.setPasswordHash(passwords.encode(r.password()));
        try { return UserResponse.of(users.saveAndFlush(u)); }
        catch(DataIntegrityViolationException e) { throw ApiException.conflict("EMAIL_ALREADY_EXISTS","Email đã được sử dụng"); }
    }
    public UserResponse login(LoginRequest r,HttpServletRequest request,HttpServletResponse response) {
        String email=InputRules.email(r.email()); rateLimits.login(email);
        Authentication authenticated;
        try { authenticated=manager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(email,r.password())); }
        catch(AuthenticationException | IllegalArgumentException e) { throw new ApiException(401,"INVALID_CREDENTIALS","Email hoặc mật khẩu không đúng"); }
        ShopPrincipal p=((ShopPrincipal)authenticated.getPrincipal()).withoutPassword();
        var clean=UsernamePasswordAuthenticationToken.authenticated(p,null,p.getAuthorities());
        sessions.onAuthentication(clean,request,response);
        var context=SecurityContextHolder.createEmptyContext(); context.setAuthentication(clean);
        SecurityContextHolder.setContext(context); contexts.saveContext(context,request,response);
        return UserResponse.of(users.findById(p.id()).orElseThrow(ApiException::missing));
    }
    public void logout(HttpServletRequest request,HttpServletResponse response) {
        var handler=new SecurityContextLogoutHandler();
        handler.setSecurityContextRepository(contexts);
        handler.logout(request,response,SecurityContextHolder.getContext().getAuthentication());
        response.addHeader("Set-Cookie","JSESSIONID=; Path="+(request.getContextPath().isEmpty()?"/":request.getContextPath())+"; Max-Age=0; HttpOnly; SameSite=Lax"+(request.isSecure()?"; Secure":""));
    }
}
