package com.example.demo.service;
import com.example.demo.entity.*;
import com.example.demo.entity.enums.*;
import com.example.demo.dto.request.*;
import com.example.demo.dto.response.*;
import com.example.demo.exception.ApiException;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Sort;
import org.springframework.util.MultiValueMap;
import java.util.ArrayList;
import jakarta.persistence.criteria.Predicate;

@Service
@Transactional(readOnly=true)
public class UserService {
    private final UserRepository users; private final CurrentUser current; private final PasswordEncoder passwords;
    public UserService(UserRepository users,CurrentUser current,PasswordEncoder passwords) { this.users=users; this.current=current; this.passwords=passwords; }
    public UserResponse me() { return UserResponse.of(users.findById(current.require().id()).orElseThrow(ApiException::missing)); }
    public UserResponse detail(long id) {
        InputRules.id(id); var actor=current.require();
        if(actor.id()!=id && actor.role()!=Role.admin) throw ApiException.missing();
        return UserResponse.of(users.findById(id).orElseThrow(ApiException::missing));
    }
    public PageResponse<UserResponse> list(MultiValueMap<String,String> params) {
        current.admin(); var q=new QueryParameters(params,"keyword","role","is_active");
        String keyword=q.keyword(); Role role=q.enumeration("role",Role.class); Boolean active=q.bool("is_active");
        Specification<User> spec=(root,query,cb) -> {
            var predicates=new ArrayList<Predicate>();
            if(keyword!=null) predicates.add(cb.or(cb.like(cb.lower(root.get("fullName")),QueryParameters.contains(keyword),'!'),
                cb.like(cb.lower(root.get("email")),QueryParameters.contains(keyword),'!')));
            if(role!=null) predicates.add(cb.equal(root.get("role"),role));
            if(active!=null) predicates.add(cb.equal(root.get("active"),active));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return PageResponse.of(users.findAll(spec,q.page(Sort.by("id").descending())).map(UserResponse::of));
    }
    @Transactional public UserResponse update(ProfileUpdateRequest r) {
        long id=current.require().id(); User u=users.lockById(id).orElseThrow(ApiException::missing);
        current.require();
        if(r.fullName()==null && r.phone()==null && r.address()==null) throw ApiException.invalid("PATCH cần ít nhất một trường");
        if(r.fullName()!=null) u.setFullName(InputRules.text(r.fullName(),100,"full_name"));
        if(r.phone()!=null) u.setPhone(InputRules.phone(r.phone()));
        if(r.address()!=null) u.setAddress(InputRules.text(r.address(),255,"address"));
        return UserResponse.of(u);
    }
    @Transactional public void password(ChangePasswordRequest r) {
        User u=users.lockById(current.require().id()).orElseThrow(ApiException::missing); current.require();
        InputRules.password(r.newPassword(),r.passwordConfirmation());
        if(!passwords.matches(r.oldPassword(),u.getPasswordHash())) throw ApiException.invalid("Mật khẩu cũ không đúng");
        if(passwords.matches(r.newPassword(),u.getPasswordHash())) throw ApiException.invalid("Mật khẩu mới phải khác mật khẩu cũ");
        u.setPasswordHash(passwords.encode(r.newPassword())); u.setSessionVersion(u.getSessionVersion()+1);
    }
    @Transactional public UserResponse role(long id,RoleUpdateRequest r) {
        InputRules.id(id); current.admin();
        var admins=users.lockActiveAdmins(Role.admin);
        current.admin();
        if(current.require().id()==id) throw ApiException.forbidden();
        User u=users.lockById(id).orElseThrow(ApiException::missing);
        if(r.role()==Role.admin && u.getRole()!=Role.admin)
            throw ApiException.conflict("SINGLE_ADMIN","Hệ thống chỉ có một quản trị viên. Có thể phân quyền nhân viên hoặc khách hàng.");
        if(u.getRole()==Role.admin && u.isActive() && r.role()!=Role.admin && admins.size()<=1)
            throw ApiException.conflict("LAST_ADMIN","Phải còn ít nhất một admin hoạt động");
        u.setRole(r.role()); u.setSessionVersion(u.getSessionVersion()+1);
        return UserResponse.of(u);
    }
    @Transactional public UserResponse status(long id,ActiveStatusRequest r) {
        InputRules.id(id); current.admin();
        var admins=users.lockActiveAdmins(Role.admin); current.admin();
        if(current.require().id()==id && !r.isActive()) throw ApiException.forbidden();
        User u=users.lockById(id).orElseThrow(ApiException::missing);
        if(u.getRole()==Role.admin && u.isActive() && !r.isActive() && admins.size()<=1)
            throw ApiException.conflict("LAST_ADMIN","Phải còn ít nhất một admin hoạt động");
        u.setActive(r.isActive()); u.setSessionVersion(u.getSessionVersion()+1);
        return UserResponse.of(u);
    }
}
