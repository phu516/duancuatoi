package com.example.demo.repository;

import com.example.demo.dto.response.PageResponse;
import com.example.demo.exception.ApiException;
import com.example.demo.service.QueryParameters;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import java.sql.*;
import java.math.BigDecimal;
import java.util.*;

/** SQL identifiers are server-owned constants. All client values use bind parameters. */
@Repository
public class ShopRepository {
    private final JdbcTemplate jdbc;
    public ShopRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public List<Map<String,Object>> rows(String sql,Object... args) {
        return jdbc.query(sql,(rs,n)-> {
            var row=new LinkedHashMap<String,Object>();
            for(int i=1;i<=rs.getMetaData().getColumnCount();i++) {
                Object value=rs.getObject(i);
                if(value instanceof Timestamp t) value=t.toInstant();
                else if(value instanceof java.sql.Date d) value=d.toLocalDate();
                else if(value instanceof java.time.LocalDateTime d) value=d.toInstant(java.time.ZoneOffset.UTC);
                String key=rs.getMetaData().getColumnLabel(i).toLowerCase(Locale.ROOT);
                if(key.startsWith("is_") && value instanceof Number x) value=x.intValue()!=0;
                row.put(key,value);
            }
            return row;
        },args);
    }
    public Map<String,Object> one(String sql,Object... args) {
        var rows=rows(sql,args); if(rows.isEmpty()) throw ApiException.missing(); return rows.getFirst();
    }
    public long count(String sql,Object... args) { return jdbc.queryForObject(sql,Long.class,args); }
    public int update(String sql,Object... args) { return jdbc.update(sql,args); }
    public long insert(String sql,Object... args) {
        var key=new GeneratedKeyHolder();
        jdbc.update(c->{
            var statement=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);
            for(int i=0;i<args.length;i++) statement.setObject(i+1,args[i]);
            return statement;
        },key);
        return Objects.requireNonNull(key.getKey()).longValue();
    }
    public void patch(String table,long id,Map<String,Object> fields) {
        if(fields.isEmpty()) throw ApiException.invalid("PATCH cần ít nhất một trường");
        var values=new ArrayList<>(fields.values()); values.add(id);
        update("UPDATE "+table+" SET "+String.join(",",fields.keySet().stream().map(k->k+"=?").toList())+" WHERE id=?",values.toArray());
    }
    public PageResponse<Map<String,Object>> page(String columns,String from,String where,String order,
            QueryParameters q,List<Object> args) {
        long total=count("SELECT COUNT(*) "+from+where,args.toArray());
        int page=q.integer("page",0,0,100000),size=q.integer("size",20,1,100);
        var params=new ArrayList<>(args); params.add(size); params.add((long)page*size);
        var data=rows("SELECT "+columns+" "+from+where+" ORDER BY "+order+" LIMIT ? OFFSET ?",params.toArray());
        return new PageResponse<>(data,new PageResponse.Pagination(page,size,total,(int)((total+size-1)/size)));
    }
    public static long id(Map<String,Object> row,String key) { return ((Number)row.get(key)).longValue(); }
    public static int integer(Map<String,Object> row,String key) { return ((Number)row.get(key)).intValue(); }
    public static BigDecimal money(Map<String,Object> row,String key) { return (BigDecimal)row.get(key); }
    public static boolean bool(Map<String,Object> row,String key) { return Boolean.TRUE.equals(row.get(key)); }
    public static Map<String,Object> fields(Object... pairs) {
        var result=new LinkedHashMap<String,Object>();
        for(int i=0;i<pairs.length;i+=2) result.put((String)pairs[i],pairs[i+1]);
        return result;
    }
    public static Map<String,Object> pick(Map<String,Object> row,String columns) {
        var result=new LinkedHashMap<String,Object>();
        for(String key:columns.split(",")) result.put(key,row.get(key));
        return result;
    }
}
