package com.example.demo.service;
import com.example.demo.exception.ApiException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.util.Locale;

public final class InputRules {
    private InputRules() {}
    public static String text(String value, int max, String field) {
        if(value==null || value.isBlank() || value.trim().length()>max) throw ApiException.invalid(field + " không hợp lệ");
        return value.trim();
    }
    public static String optionalText(String value, int max, String field) {
        if(value==null) return null;
        if(value.length()>max) throw ApiException.invalid(field + " quá dài");
        return value.trim();
    }
    public static String email(String value) { return text(value,100,"email").toLowerCase(Locale.ROOT); }
    public static String phone(String value) {
        String normalized = text(value,30,"phone").replaceAll("[\\s-]","");
        if(!normalized.matches("(0|\\+84)[0-9]{9,10}")) throw ApiException.invalid("phone không hợp lệ");
        return normalized;
    }
    public static void password(String value, String confirmation) {
        if(value==null || value.codePointCount(0,value.length())<8 || value.getBytes(StandardCharsets.UTF_8).length>72)
            throw ApiException.invalid("Mật khẩu cần ít nhất 8 ký tự và tối đa 72 byte UTF-8");
        if(!value.equals(confirmation)) throw ApiException.invalid("Xác nhận mật khẩu không khớp");
    }
    public static BigDecimal price(BigDecimal value) {
        if(value==null || value.signum()<=0 || value.scale()>2 || value.compareTo(new BigDecimal("9999999999.99"))>0)
            throw ApiException.invalid("price phải dương, tối đa 9999999999.99 và 2 chữ số thập phân");
        return value;
    }
    public static String imageUrl(String value) {
        if(value==null) return null;
        String result = text(value,1000,"image_url");
        try {
            URI uri = URI.create(result);
            if(!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())) || uri.getHost()==null)
                throw new IllegalArgumentException();
        } catch(IllegalArgumentException e) { throw ApiException.invalid("image_url phải là URL http/https tuyệt đối"); }
        return result;
    }
    public static long id(Long id) { if(id==null || id<=0) throw ApiException.invalid("ID phải là số nguyên dương"); return id; }
}

