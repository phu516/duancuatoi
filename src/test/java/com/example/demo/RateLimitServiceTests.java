package com.example.demo;

import com.example.demo.exception.RateLimitException;
import com.example.demo.service.RateLimitService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RateLimitServiceTests {
    @Test void limitsLoginRegistrationAndOrderWithRetryAfter() {
        var limits=new RateLimitService(2,2,1,1,60,60,60);
        limits.login("one@example.test");
        limits.login("two@example.test");
        var login=assertThrows(RateLimitException.class,()->limits.login("three@example.test"));
        assertEquals(429,login.getStatus()); assertTrue(login.getRetryAfterSeconds()>0);
        limits.registration();
        assertEquals(429,assertThrows(RateLimitException.class,limits::registration).getStatus());
        limits.order(1);
        assertEquals(429,assertThrows(RateLimitException.class,()->limits.order(1)).getStatus());
        limits.order(2);
    }
}
