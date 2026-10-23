package com.example.demo.migration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** One-off, additive import from pet_shop. Disabled unless the legacy-import profile is active. */
@Component
@Profile("legacy-import")
public class LegacyPetShopImporter implements ApplicationRunner {
    private static final String SOURCE_SCHEMA = "pet_shop";
    private final DataSource targetDataSource;
    private final PasswordEncoder passwordEncoder;
    private final String sourceUrl;
    private final String sourceUsername;
    private final String sourcePassword;
    private final boolean apply;

    public LegacyPetShopImporter(DataSource targetDataSource, PasswordEncoder passwordEncoder,
            @Value("${app.legacy.source-url:jdbc:mysql://localhost:3306/pet_shop?connectionTimeZone=UTC}") String sourceUrl,
            @Value("${app.legacy.source-username:${DB_USERNAME:root}}") String sourceUsername,
            @Value("${app.legacy.source-password:${DB_PASSWORD:123456}}") String sourcePassword,
            @Value("${app.legacy.import-apply:false}") boolean apply) {
        this.targetDataSource = targetDataSource;
        this.passwordEncoder = passwordEncoder;
        this.sourceUrl = sourceUrl;
        this.sourceUsername = sourceUsername;
        this.sourcePassword = sourcePassword;
        this.apply = apply;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!apply) {
            throw new IllegalStateException("Legacy import profile is active but app.legacy.import-apply is not true");
        }
        try (Connection source = DriverManager.getConnection(sourceUrl, sourceUsername, sourcePassword);
                Connection target = targetDataSource.getConnection()) {
            source.setReadOnly(true);
            source.setAutoCommit(false);
            target.setAutoCommit(false);
            try {
                requireSchema(source, SOURCE_SCHEMA);
                requireSchema(target, "pet_shop_api");
                requireMigrations(target);
                refuseUnmappedLegacyData(source);

                Map<Long, Long> users = importUsers(source, target);
                Map<Long, Long> categories = importCategories(source, target);
                Map<Long, Long> products = importProducts(source, target, categories);
                Map<Long, Long> pets = importPets(source, target, products);
                Map<Long, Long> carts = importCarts(source, target, users);
                int cartItems = importCartItems(source, target, carts, products);
                target.commit();
                source.rollback();

                System.out.printf("Legacy import complete: users=%d, categories=%d, products=%d, pets=%d, carts=%d, cart_items=%d%n",
                        users.size(), categories.size(), products.size(), pets.size(), carts.size(), cartItems);
            } catch (Exception ex) {
                target.rollback();
                source.rollback();
                throw ex;
            }
        }
    }

    private static void requireSchema(Connection connection, String expected) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT DATABASE()");
                ResultSet result = statement.executeQuery()) {
            if (!result.next() || !expected.equals(result.getString(1))) {
                throw new IllegalStateException("Connected to an unexpected database; expected " + expected);
            }
        }
    }

    private static void requireMigrations(Connection target) throws SQLException {
        try (PreparedStatement statement = target.prepareStatement(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success=1 AND version IN ('1','2','3')");
                ResultSet result = statement.executeQuery()) {
            result.next();
            if (result.getInt(1) != 3) throw new IllegalStateException("Target database must have Flyway migrations V1, V2, and V3");
        }
    }

    private static void refuseUnmappedLegacyData(Connection source) throws SQLException {
        String[] emptyTables = { "suppliers", "orders", "order_items", "inventory_movements", "order_events",
                "pet_health_records", "reviews", "shipping_info", "coupons" };
        for (String table : emptyTables) {
            if (count(source, table) != 0) {
                throw new IllegalStateException("Source table " + table + " contains rows; import stopped before writes to prevent partial migration");
            }
        }
        try (PreparedStatement statement = source.prepareStatement(
                "SELECT COUNT(*) FROM pets WHERE supplier_id IS NOT NULL OR reserved_order_id IS NOT NULL");
                ResultSet result = statement.executeQuery()) {
            result.next();
            if (result.getInt(1) != 0) throw new IllegalStateException("Source pets reference suppliers/orders; import stopped because those source tables are not empty");
        }
    }

    private static long count(Connection connection, String table) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM `" + table + "`")) {
            try (ResultSet result = statement.executeQuery()) { result.next(); return result.getLong(1); }
        }
    }

    private Map<Long, Long> importUsers(Connection source, Connection target) throws SQLException {
        Map<Long, Long> ids = new LinkedHashMap<>();
        for (Map<String, Object> row : rows(source, "SELECT id,full_name,email,password,phone,address,role,is_active,session_version,created_at FROM users ORDER BY id")) {
            long sourceId = number(row, "id");
            Long targetId = mappedId(target, "users", Long.toString(sourceId));
            String action = "already_mapped";
            if (targetId == null) {
                targetId = findId(target, "SELECT id FROM users WHERE LOWER(TRIM(email))=LOWER(TRIM(?)) LIMIT 1", row.get("email"));
                if (targetId != null) action = "matched_email";
                else {
                    String oldPassword = (String) row.get("password");
                    String hash = oldPassword.matches("^\\$2[aby]\\$[0-9]{2}\\$[./A-Za-z0-9]{53}$")
                            ? oldPassword : passwordEncoder.encode(oldPassword);
                    boolean keepId = !exists(target, "users", sourceId);
                    String sql = keepId
                            ? "INSERT INTO users(id,full_name,email,password_hash,phone,address,role,is_active,session_version,created_at) VALUES(?,?,?,?,?,?,?,?,?,?)"
                            : "INSERT INTO users(full_name,email,password_hash,phone,address,role,is_active,session_version,created_at) VALUES(?,?,?,?,?,?,?,?,?)";
                    try (PreparedStatement insert = target.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
                        int i = 1;
                        if (keepId) insert.setLong(i++, sourceId);
                        insert.setObject(i++, row.get("full_name")); insert.setObject(i++, row.get("email"));
                        insert.setString(i++, hash); insert.setObject(i++, row.get("phone")); insert.setObject(i++, row.get("address"));
                        insert.setObject(i++, row.get("role")); insert.setObject(i++, row.get("is_active"));
                        insert.setObject(i++, row.get("session_version")); insert.setObject(i, row.get("created_at"));
                        insert.executeUpdate();
                        targetId = keepId ? sourceId : generatedId(insert);
                    }
                    action = keepId ? "inserted_id_preserved" : "inserted_id_remapped";
                }
                saveMap(target, "users", Long.toString(sourceId), targetId, action);
            }
            ids.put(sourceId, targetId);
        }
        return ids;
    }

    private Map<Long, Long> importCategories(Connection source, Connection target) throws SQLException {
        Map<Long, Long> ids = new LinkedHashMap<>();
        for (Map<String, Object> row : rows(source, "SELECT id,name,created_at FROM categories ORDER BY id")) {
            long sourceId = number(row, "id");
            Long targetId = mappedId(target, "categories", Long.toString(sourceId));
            if (targetId == null) {
                targetId = findId(target, "SELECT id FROM categories WHERE LOWER(TRIM(name))=LOWER(TRIM(?)) LIMIT 1", row.get("name"));
                String action = targetId == null ? "inserted" : "matched_name";
                if (targetId == null) {
                    boolean keepId = !exists(target, "categories", sourceId);
                    String sql = keepId ? "INSERT INTO categories(id,name,created_at) VALUES(?,?,?)" : "INSERT INTO categories(name,created_at) VALUES(?,?)";
                    try (PreparedStatement insert = target.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
                        int i=1; if (keepId) insert.setLong(i++,sourceId);
                        insert.setObject(i++,row.get("name")); insert.setObject(i,row.get("created_at")); insert.executeUpdate();
                        targetId = keepId ? sourceId : generatedId(insert);
                    }
                    action = keepId ? "inserted_id_preserved" : "inserted_id_remapped";
                }
                saveMap(target,"categories",Long.toString(sourceId),targetId,action);
            }
            ids.put(sourceId,targetId);
        }
        return ids;
    }

    private Map<Long, Long> importProducts(Connection source, Connection target, Map<Long, Long> categoryIds) throws SQLException {
        Map<Long, Long> ids = new LinkedHashMap<>();
        for (Map<String, Object> row : rows(source, "SELECT id,category_id,product_type,name,description,price,stock_quantity,image_url,created_at,is_active FROM products ORDER BY id")) {
            long sourceId=number(row,"id");
            Long targetId=mappedId(target,"products",Long.toString(sourceId));
            if (targetId==null) {
                Long categoryId=categoryIds.get(number(row,"category_id"));
                if (categoryId==null) throw new IllegalStateException("A source product references a category that was not imported");
                boolean keepId=!exists(target,"products",sourceId);
                String sql=keepId
                    ? "INSERT INTO products(id,category_id,product_type,name,description,price,stock_quantity,image_url,created_at,is_active) VALUES(?,?,?,?,?,?,?,?,?,?)"
                    : "INSERT INTO products(category_id,product_type,name,description,price,stock_quantity,image_url,created_at,is_active) VALUES(?,?,?,?,?,?,?,?,?)";
                try(PreparedStatement insert=target.prepareStatement(sql,PreparedStatement.RETURN_GENERATED_KEYS)) {
                    int i=1; if(keepId) insert.setLong(i++,sourceId);
                    insert.setLong(i++,categoryId); insert.setObject(i++,row.get("product_type")); insert.setObject(i++,row.get("name"));
                    insert.setObject(i++,row.get("description")); insert.setObject(i++,row.get("price")); insert.setObject(i++,row.get("stock_quantity"));
                    insert.setObject(i++,row.get("image_url")); insert.setObject(i++,row.get("created_at")); insert.setObject(i,row.get("is_active"));
                    insert.executeUpdate(); targetId=keepId?sourceId:generatedId(insert);
                }
                saveMap(target,"products",Long.toString(sourceId),targetId,keepId?"inserted_id_preserved":"inserted_id_remapped");
            }
            ids.put(sourceId,targetId);
        }
        return ids;
    }

    private Map<Long, Long> importPets(Connection source, Connection target, Map<Long, Long> productIds) throws SQLException {
        Map<Long, Long> ids=new LinkedHashMap<>();
        for(Map<String,Object> row:rows(source,"SELECT id,product_id,supplier_id,breed,age,gender,color,weight,is_neutered,vaccination_status,status,reserved_order_id,reserved_until,created_at FROM pets ORDER BY id")) {
            long sourceId=number(row,"id"); Long targetId=mappedId(target,"pets",Long.toString(sourceId));
            if(targetId==null) {
                Long productId=productIds.get(number(row,"product_id"));
                if(productId==null) throw new IllegalStateException("A source pet references a product that was not imported");
                targetId=findId(target,"SELECT id FROM pets WHERE product_id=? LIMIT 1",productId);
                String action=targetId==null?"inserted":"matched_product";
                if(targetId==null) {
                    boolean keepId=!exists(target,"pets",sourceId);
                    String sql=keepId
                        ? "INSERT INTO pets(id,product_id,supplier_id,breed,age,gender,color,weight,is_neutered,vaccination_status,status,reserved_order_id,reserved_until,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
                        : "INSERT INTO pets(product_id,supplier_id,breed,age,gender,color,weight,is_neutered,vaccination_status,status,reserved_order_id,reserved_until,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)";
                    try(PreparedStatement insert=target.prepareStatement(sql,PreparedStatement.RETURN_GENERATED_KEYS)) {
                        int i=1;if(keepId)insert.setLong(i++,sourceId); insert.setLong(i++,productId);
                        insert.setObject(i++,null); insert.setObject(i++,row.get("breed")); insert.setObject(i++,row.get("age"));
                        insert.setObject(i++,row.get("gender")); insert.setObject(i++,row.get("color")); insert.setObject(i++,row.get("weight"));
                        insert.setObject(i++,row.get("is_neutered")); insert.setObject(i++,row.get("vaccination_status")); insert.setObject(i++,row.get("status"));
                        insert.setObject(i++,null); insert.setObject(i++,null); insert.setObject(i,row.get("created_at")); insert.executeUpdate();
                        targetId=keepId?sourceId:generatedId(insert);
                    }
                    action=keepId?"inserted_id_preserved":"inserted_id_remapped";
                }
                saveMap(target,"pets",Long.toString(sourceId),targetId,action);
            }
            ids.put(sourceId,targetId);
        }
        return ids;
    }

    private Map<Long, Long> importCarts(Connection source, Connection target, Map<Long, Long> userIds) throws SQLException {
        Map<Long, Long> ids=new LinkedHashMap<>();
        for(Map<String,Object> row:rows(source,"SELECT id,user_id,created_at FROM carts ORDER BY id")) {
            long sourceId=number(row,"id"); Long targetId=mappedId(target,"carts",Long.toString(sourceId));
            if(targetId==null) {
                Long userId=userIds.get(number(row,"user_id")); if(userId==null) throw new IllegalStateException("A source cart references a user that was not imported");
                targetId=findId(target,"SELECT id FROM carts WHERE user_id=? LIMIT 1",userId);
                String action=targetId==null?"inserted":"matched_user";
                if(targetId==null) {
                    boolean keepId=!exists(target,"carts",sourceId);
                    String sql=keepId?"INSERT INTO carts(id,user_id,created_at) VALUES(?,?,?)":"INSERT INTO carts(user_id,created_at) VALUES(?,?)";
                    try(PreparedStatement insert=target.prepareStatement(sql,PreparedStatement.RETURN_GENERATED_KEYS)) {
                        int i=1;if(keepId)insert.setLong(i++,sourceId);insert.setLong(i++,userId);insert.setObject(i,row.get("created_at"));insert.executeUpdate();
                        targetId=keepId?sourceId:generatedId(insert);
                    }
                    action=keepId?"inserted_id_preserved":"inserted_id_remapped";
                }
                saveMap(target,"carts",Long.toString(sourceId),targetId,action);
            }
            ids.put(sourceId,targetId);
        }
        return ids;
    }

    private int importCartItems(Connection source, Connection target, Map<Long, Long> cartIds, Map<Long, Long> productIds) throws SQLException {
        int processed=0;
        for(Map<String,Object> row:rows(source,"SELECT cart_id,product_id,quantity FROM cart_items ORDER BY cart_id,product_id")) {
            long sourceCart=number(row,"cart_id"),sourceProduct=number(row,"product_id");
            String key=sourceCart+":"+sourceProduct;
            if(mappedId(target,"cart_items",key)==null) {
                Long cartId=cartIds.get(sourceCart),productId=productIds.get(sourceProduct);
                if(cartId==null||productId==null)throw new IllegalStateException("A source cart item references a row that was not imported");
                if(!existsCartItem(target,cartId,productId)) {
                    try(PreparedStatement insert=target.prepareStatement("INSERT INTO cart_items(cart_id,product_id,quantity) VALUES(?,?,?)")) {
                        insert.setLong(1,cartId);insert.setLong(2,productId);insert.setObject(3,row.get("quantity"));insert.executeUpdate();
                    }
                    saveMap(target,"cart_items",key,cartId,"inserted");
                } else saveMap(target,"cart_items",key,cartId,"matched_existing");
            }
            processed++;
        }
        return processed;
    }

    private static List<Map<String,Object>> rows(Connection connection,String sql)throws SQLException {
        List<Map<String,Object>> rows=new ArrayList<>();
        try(PreparedStatement statement=connection.prepareStatement(sql);ResultSet result=statement.executeQuery()) {
            int columns=result.getMetaData().getColumnCount();
            while(result.next()) { Map<String,Object> row=new LinkedHashMap<>(); for(int i=1;i<=columns;i++)row.put(result.getMetaData().getColumnLabel(i),result.getObject(i)); rows.add(row); }
        }
        return rows;
    }

    private static Long mappedId(Connection target,String entity,String key)throws SQLException {
        try(PreparedStatement statement=target.prepareStatement("SELECT target_id FROM legacy_import_map WHERE source_schema=? AND entity_name=? AND source_key=?")) {
            statement.setString(1,SOURCE_SCHEMA);statement.setString(2,entity);statement.setString(3,key);
            try(ResultSet result=statement.executeQuery()){return result.next()?result.getLong(1):null;}
        }
    }

    private static void saveMap(Connection target,String entity,String key,long targetId,String action)throws SQLException {
        try(PreparedStatement statement=target.prepareStatement("INSERT INTO legacy_import_map(source_schema,entity_name,source_key,target_id,action_taken) VALUES(?,?,?,?,?)")) {
            statement.setString(1,SOURCE_SCHEMA);statement.setString(2,entity);statement.setString(3,key);statement.setLong(4,targetId);statement.setString(5,action);statement.executeUpdate();
        }
    }

    private static boolean exists(Connection target,String table,long id)throws SQLException {
        try(PreparedStatement statement=target.prepareStatement("SELECT 1 FROM `"+table+"` WHERE id=?")){statement.setLong(1,id);try(ResultSet result=statement.executeQuery()){return result.next();}}
    }

    private static boolean existsCartItem(Connection target,long cartId,long productId)throws SQLException {
        try(PreparedStatement statement=target.prepareStatement("SELECT 1 FROM cart_items WHERE cart_id=? AND product_id=?")) {
            statement.setLong(1,cartId);statement.setLong(2,productId);try(ResultSet result=statement.executeQuery()){return result.next();}
        }
    }

    private static Long findId(Connection target,String sql,Object value)throws SQLException {
        try(PreparedStatement statement=target.prepareStatement(sql)){statement.setObject(1,value);try(ResultSet result=statement.executeQuery()){return result.next()?result.getLong(1):null;}}
    }

    private static long generatedId(PreparedStatement statement)throws SQLException {
        try(ResultSet result=statement.getGeneratedKeys()){if(!result.next())throw new SQLException("Database did not return a generated ID");return result.getLong(1);}
    }

    private static long number(Map<String,Object> row,String name){return ((Number)row.get(name)).longValue();}
}
