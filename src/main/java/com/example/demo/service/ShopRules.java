package com.example.demo.service;
import com.example.demo.exception.ApiException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class ShopRules {
    private ShopRules() {}
    public static String choice(String value,String field,String... choices) {
        if(value==null || !List.of(choices).contains(value)) throw ApiException.invalid(field+" không hợp lệ");
        return value;
    }
    public static int quantity(Integer value) {
        if(value==null || value<1 || value>1000000) throw ApiException.invalid("quantity phải từ 1 đến 1000000");
        return value;
    }
    public static BigDecimal money(BigDecimal value) {
        if(value.signum()<0 || value.compareTo(new BigDecimal("9999999999.99"))>0)
            throw ApiException.invalid("Giá trị tiền vượt giới hạn");
        return value;
    }
    public static LocalDate date(LocalDate value) {
        if(value==null || value.isAfter(LocalDate.now(ZoneOffset.UTC))) throw ApiException.invalid("record_date không hợp lệ");
        return value;
    }
    public static LocalDate queryDate(String value) {
        if(value==null) return null;
        try { if(!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new IllegalArgumentException(); return LocalDate.parse(value); }
        catch(Exception e) { throw ApiException.invalid("Ngày phải có dạng YYYY-MM-DD"); }
    }
}
