package com.example.demo.service;

import com.example.demo.exception.RateLimitException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.util.*;

/** Sliding-window limits for this single-instance session deployment. */
@Service
public class RateLimitService {
    private final Map<String,Deque<Long>> attempts=new HashMap<>();
    private final int loginIp,loginEmail,registration,orders;
    private final long loginWindow,registrationWindow,orderWindow;
    private long checks;
    public RateLimitService(@Value("${app.rate-limit.login-ip:20}") int loginIp,
            @Value("${app.rate-limit.login-email:8}") int loginEmail,
            @Value("${app.rate-limit.registration:5}") int registration,
            @Value("${app.rate-limit.orders:10}") int orders,
            @Value("${app.rate-limit.login-window-seconds:900}") long loginWindow,
            @Value("${app.rate-limit.registration-window-seconds:3600}") long registrationWindow,
            @Value("${app.rate-limit.order-window-seconds:60}") long orderWindow) {
        if(loginIp<1 || loginEmail<1 || registration<1 || orders<1 || loginWindow<1 || registrationWindow<1 || orderWindow<1)
            throw new IllegalArgumentException("Rate limit configuration must be positive");
        this.loginIp=loginIp; this.loginEmail=loginEmail; this.registration=registration; this.orders=orders;
        this.loginWindow=loginWindow*1000; this.registrationWindow=registrationWindow*1000; this.orderWindow=orderWindow*1000;
    }
    private String ip() {
        var attrs=RequestContextHolder.getRequestAttributes();
        if(attrs instanceof ServletRequestAttributes a) {
            HttpServletRequest r=a.getRequest();
            // The container's remote address is used; forwarded headers require explicit trusted-proxy setup.
            return r.getRemoteAddr();
        }
        return "local";
    }
    public void login(String normalizedEmail) {
        check("login-ip:"+ip(),loginIp,loginWindow);
        check("login-email:"+normalizedEmail,loginEmail,loginWindow);
    }
    public void registration() { check("register-ip:"+ip(),registration,registrationWindow); }
    public void order(long user) { check("order-user:"+user,orders,orderWindow); }
    private synchronized void check(String key,int limit,long window) {
        long now=System.currentTimeMillis();
        if(++checks%1000==0) attempts.entrySet().removeIf(e -> { prune(e.getValue(),now-window); return e.getValue().isEmpty(); });
        if(attempts.size()>=50000 && !attempts.containsKey(key)) throw new RateLimitException(60);
        var times=attempts.computeIfAbsent(key,k->new ArrayDeque<>());
        prune(times,now-window);
        if(times.size()>=limit) throw new RateLimitException(Math.max(1,(times.peekFirst()+window-now+999)/1000));
        times.addLast(now);
    }
    private void prune(Deque<Long> times,long oldest) { while(!times.isEmpty() && times.peekFirst()<=oldest) times.removeFirst(); }
}
