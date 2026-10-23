package com.example.demo.web;

import org.springframework.stereotype.Component;
import java.text.NumberFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component("display")
public class Display {
    public String money(Object value) {
        return value instanceof Number n ? NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN")).format(n)+" ₫" : "—";
    }
    public String date(Object value) {
        if(value instanceof Instant i) return DateTimeFormatter.ofPattern("dd/MM/yyyy · HH:mm").withZone(ZoneId.of("Asia/Ho_Chi_Minh")).format(i);
        if(value instanceof LocalDate d) return d.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        return value==null?"—":value.toString();
    }
    public String image(Object value) {
        if(value instanceof String s && s.matches("(?i)^https?://[^\\s]+$")) return s;
        return "/images/placeholder.svg";
    }
    public String label(Object value) {
        if(value==null) return "Chưa cập nhật";
        return switch(value.toString()) {
            case "pet" -> "Thú cưng"; case "food" -> "Thức ăn"; case "accessory" -> "Phụ kiện";
            case "pending" -> "Chờ xác nhận"; case "confirmed" -> "Đã xác nhận";
            case "shipping" -> "Giao hàng"; case "completed" -> "Hoàn tất"; case "cancelled" -> "Đã hủy";
            case "unpaid" -> "Chưa thanh toán"; case "paid" -> "Đã thanh toán"; case "refunded" -> "Đã hoàn tiền";
            case "cash" -> "Thanh toán khi nhận hàng"; case "bank_transfer" -> "Chuyển khoản";
            case "card" -> "Thẻ"; case "e_wallet" -> "Ví điện tử";
            case "pickup" -> "Nhận tại cửa hàng"; case "pet_taxi" -> "Vận chuyển thú cưng";
            case "available" -> "Sẵn sàng về nhà"; case "reserved" -> "Đang được giữ"; case "sold" -> "Đã có chủ";
            case "male" -> "Đực"; case "female" -> "Cái"; case "unknown" -> "Chưa xác định";
            case "vaccination" -> "Tiêm phòng"; case "checkup" -> "Khám sức khỏe"; case "treatment" -> "Điều trị";
            default -> value.toString();
        };
    }
}
