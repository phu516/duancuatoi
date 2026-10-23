package com.example.demo.service;
import org.springframework.util.MultiValueMap;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import com.example.demo.exception.ApiException;
import java.util.*;
import java.math.BigDecimal;

public final class QueryParameters {
    private final MultiValueMap<String,String> values;
    public QueryParameters(MultiValueMap<String,String> values, String... allowed) {
        this.values=values;
        Set<String> names = new HashSet<>(Arrays.asList(allowed));
        names.addAll(List.of("page","size"));
        values.forEach((key,list) -> {
            if(!names.contains(key) || list.size()!=1 || list.getFirst()==null || list.getFirst().isBlank())
                throw ApiException.invalid("Query không hợp lệ: " + key);
        });
    }
    public boolean has(String name) { return values.containsKey(name); }
    public String get(String name) { return values.getFirst(name); }
    public String keyword() { return has("keyword") ? InputRules.text(get("keyword"),150,"keyword") : null; }
    public int integer(String key,int fallback,int min,int max) {
        if(!has(key)) return fallback;
        try { String s=get(key); if(!s.matches("[0-9]+")) throw new NumberFormatException();
            int n=Integer.parseInt(s); if(n<min||n>max) throw new NumberFormatException(); return n;
        } catch(NumberFormatException e) { throw ApiException.invalid(key + " ngoài giới hạn"); }
    }
    public Long id(String key) {
        if(!has(key)) return null;
        try { if(!get(key).matches("[0-9]+")) throw new NumberFormatException();
            return InputRules.id(Long.parseLong(get(key)));
        } catch(NumberFormatException e) { throw ApiException.invalid(key+" không hợp lệ"); }
    }
    public Boolean bool(String key) {
        if(!has(key)) return null;
        if(!Set.of("true","false").contains(get(key))) throw ApiException.invalid(key+" phải là true/false");
        return Boolean.valueOf(get(key));
    }
    public <E extends Enum<E>> E enumeration(String key,Class<E> type) {
        if(!has(key)) return null;
        try { return Enum.valueOf(type,get(key)); }
        catch(IllegalArgumentException e) { throw ApiException.invalid(key+" không hợp lệ"); }
    }
    public BigDecimal money(String key) {
        if(!has(key)) return null;
        try { BigDecimal n=new BigDecimal(get(key));
            if(n.signum()<0 || n.scale()>2 || n.compareTo(new BigDecimal("9999999999.99"))>0) throw new NumberFormatException();
            return n;
        } catch(NumberFormatException e) { throw ApiException.invalid(key+" không hợp lệ"); }
    }
    public PageRequest page(Sort sort) { return PageRequest.of(integer("page",0,0,100000),integer("size",20,1,100),sort); }
    public static String contains(String text) { return "%" + text.toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_") + "%"; }
}

