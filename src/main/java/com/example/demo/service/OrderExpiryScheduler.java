package com.example.demo.service;
import com.example.demo.repository.ShopRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import static com.example.demo.repository.ShopRepository.id;

@Component @EnableScheduling
@ConditionalOnProperty(name="app.orders.expiry-enabled",havingValue="true",matchIfMissing=true)
public class OrderExpiryScheduler {
    private static final Logger log=LoggerFactory.getLogger(OrderExpiryScheduler.class);
    private final ShopRepository db; private final OrderService orders;
    public OrderExpiryScheduler(ShopRepository db,OrderService orders) { this.db=db; this.orders=orders; }
    @Scheduled(fixedDelayString="${app.orders.expiry-delay-ms:60000}",initialDelayString="${app.orders.expiry-delay-ms:60000}")
    public void run() {
        // Each order gets its own transaction and is rechecked under a row lock.
        for(var row:db.rows("SELECT id FROM orders WHERE status='pending' AND payment_status='unpaid' AND reserved_until<=CURRENT_TIMESTAMP ORDER BY id")) {
            long order=id(row,"id");
            try { orders.expire(order); } catch(Exception e) { log.error("Cannot expire order {}",order,e); }
        }
    }
}
