package com.example.demo.config;
import com.example.demo.entity.User;
import com.example.demo.entity.enums.Role;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.InputRules;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/** Explicit bootstrap: only configured email/password, never changes an existing account. */
@Component
public class AdminInitializer implements ApplicationRunner {
    private final UserRepository users; private final PasswordEncoder passwords; private final String email,password;
    public AdminInitializer(UserRepository users,PasswordEncoder passwords,
        @Value("${app.admin.email:}") String email,@Value("${app.admin.password:}") String password) {
        this.users=users;this.passwords=passwords;this.email=email;this.password=password;
    }
    @Override @Transactional public void run(ApplicationArguments args) {
        if(email.isBlank() && password.isBlank()) return;
        if(email.isBlank() || password.isBlank()) throw new IllegalStateException("Cần APP_ADMIN_EMAIL và APP_ADMIN_PASSWORD để tạo admin");
        String normalized=InputRules.email(email);
        if(!normalized.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw new IllegalStateException("APP_ADMIN_EMAIL không hợp lệ");
        if(users.existsByRole(Role.admin)) return;
        if(users.existsByEmail(normalized)) throw new IllegalStateException("APP_ADMIN_EMAIL đã thuộc tài khoản không có quyền admin");
        InputRules.password(password,password);
        User admin=new User(); admin.setFullName("Quản trị viên"); admin.setEmail(normalized);
        admin.setPasswordHash(passwords.encode(password)); admin.setRole(Role.admin);
        users.saveAndFlush(admin);
    }
}
