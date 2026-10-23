package com.example.demo.repository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import com.example.demo.dto.response.ProductDetailResponse.ReviewSummary;

@Repository
public class ProductHistoryRepository {
    private final JdbcTemplate jdbc;
    public ProductHistoryRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public boolean hasHistory(long productId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM order_items WHERE product_id = ?", Long.class, productId) > 0
            || jdbc.queryForObject("SELECT COUNT(*) FROM inventory_movements WHERE product_id = ?", Long.class, productId) > 0;
    }
    public ReviewSummary reviews(long productId) {
        return jdbc.queryForObject("SELECT COUNT(*) AS cnt, AVG(rating) AS average_rating FROM reviews WHERE product_id = ?",
            (rs, n) -> new ReviewSummary(rs.getLong("cnt"), rs.getBigDecimal("average_rating") == null ? null :
                rs.getBigDecimal("average_rating").setScale(2, java.math.RoundingMode.HALF_UP)), productId);
    }
    public void initialStock(long productId, long actorId, int quantity) {
        if (quantity == 0) return;
        jdbc.update("INSERT INTO inventory_movements(product_id, actor_id, quantity_change, quantity_before, quantity_after, reason, created_at) VALUES (?, ?, ?, 0, ?, 'opening_stock', CURRENT_TIMESTAMP)",
            productId, actorId, quantity, quantity);
    }
}
