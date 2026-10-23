package com.example.demo;

import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.example.demo.repository.UserRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.net.*;
import java.net.http.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real HTTP + MySQL: creates/drops only a randomly named petshop_test_* database. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties={"app.admin.email=admin@example.test","app.admin.password=AdminTest123!","logging.level.root=WARN","app.orders.expiry-enabled=false",
        "app.rate-limit.login-ip=1000","app.rate-limit.login-email=1000","app.rate-limit.registration=1000","app.rate-limit.orders=1000"})
class ApiIntegrationTests {
    static final String DATABASE="petshop_test_"+UUID.randomUUID().toString().replace("-","");
    static final String HOST=System.getenv().getOrDefault("TEST_DB_HOST","localhost:3306");
    static final String USER=System.getenv().getOrDefault("TEST_DB_USERNAME","root");
    static final String PASSWORD=System.getenv().getOrDefault("TEST_DB_PASSWORD","");
    static final String SERVER="jdbc:mysql://"+HOST+"/?connectionTimeZone=UTC";
    static boolean created;
    @LocalServerPort int port;
    @Autowired JsonMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired com.example.demo.service.OrderService orderService;
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) throws Exception {
        try(var c=DriverManager.getConnection(SERVER,USER,PASSWORD);var s=c.createStatement()) {
            s.execute("CREATE DATABASE `"+DATABASE+"` CHARACTER SET utf8mb4"); created=true;
        }
        r.add("spring.datasource.url",()->"jdbc:mysql://"+HOST+"/"+DATABASE+"?connectionTimeZone=UTC");
        r.add("spring.datasource.username",()->USER); r.add("spring.datasource.password",()->PASSWORD);
    }
    @AfterAll static void cleanDatabase() throws Exception {
        if(!created || !DATABASE.matches("petshop_test_[a-f0-9]{32}")) return;
        try(var c=DriverManager.getConnection("jdbc:mysql://"+HOST+"/"+DATABASE,USER,PASSWORD);var s=c.createStatement()) {
            if(!DATABASE.equals(c.getCatalog())) throw new IllegalStateException("Wrong test database");
            s.execute("DROP DATABASE `"+DATABASE+"`");
        }
    }
    class Client {
        final CookieManager cookies=new CookieManager(null,CookiePolicy.ACCEPT_ALL);
        final HttpClient http=HttpClient.newBuilder().cookieHandler(cookies).build();
        String csrf;
        HttpResponse<String> raw(String method,String path,String body,boolean token) throws Exception {
            return raw(method,path,body,token,null);
        }
        HttpResponse<String> raw(String method,String path,String body,boolean token,String idempotencyKey) throws Exception {
            var b=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1"+path));
            if(idempotencyKey!=null) b.header("Idempotency-Key",idempotencyKey);
            if(token && csrf!=null) b.header("X-CSRF-TOKEN",csrf);
            if(body!=null) b.header("Content-Type","application/json");
            return http.send(b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
        }
        JsonNode call(String method,String path,String body,int status) throws Exception {
            var response=raw(method,path,body,true);
            assertEquals(status,response.statusCode(),method+" "+path+" -> "+response.body());
            if(status==204) { assertEquals("",response.body()); return null; }
            assertTrue(response.headers().firstValue("Content-Type").orElse("").contains("application/json"));
            return json.readTree(response.body());
        }
        void csrf() throws Exception { csrf=call("GET","/auth/csrf-token",null,200).get("csrf_token").asText(); }
        JsonNode login(String email,String password) throws Exception {
            csrf(); var user=call("POST","/auth/login",json.writeValueAsString(Map.of("email",email,"password",password)),200); csrf(); return user;
        }
    }
    Client admin() throws Exception { var c=new Client(); c.login("admin@example.test","AdminTest123!"); return c; }
    String email() { return UUID.randomUUID()+"@example.test"; }
    String registration(String email) throws Exception {
        return json.writeValueAsString(Map.of("full_name","Khách kiểm thử","email",email,"password","Customer123!",
            "password_confirmation","Customer123!","phone","0901234567","address","12 Đường Hoa"));
    }
    Client customer(String email) throws Exception { var c=new Client(); c.csrf(); c.call("POST","/auth/register",registration(email),201); c.login(email,"Customer123!"); return c; }
    long category(Client c) throws Exception { return c.call("POST","/categories","{\"name\":\"Danh mục "+UUID.randomUUID()+"\"}",201).get("id").asLong(); }
    long product(Client c,long category,String type,int stock) throws Exception {
        return c.call("POST","/products",json.writeValueAsString(Map.of("category_id",category,"product_type",type,
            "name","Thức ăn "+UUID.randomUUID(),"price",120000,"stock_quantity",stock)),201).get("id").asLong();
    }

    HttpResponse<String> webGet(Client c,String path) throws Exception {
        return c.http.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).header("Accept","text/html").GET().build(),HttpResponse.BodyHandlers.ofString());
    }
    HttpResponse<String> webPost(Client c,String path,Map<String,String> fields,boolean csrf) throws Exception {
        var values=new LinkedHashMap<>(fields); if(csrf) values.put("_csrf",c.csrf);
        String body=values.entrySet().stream().map(e->URLEncoder.encode(e.getKey(),java.nio.charset.StandardCharsets.UTF_8)+"="+URLEncoder.encode(e.getValue(),java.nio.charset.StandardCharsets.UTF_8)).collect(java.util.stream.Collectors.joining("&"));
        return c.http.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).header("Accept","text/html").header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    String html(HttpResponse<String> response,int status) {
        assertEquals(status,response.statusCode(),response.body());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").contains("text/html"));
        assertFalse(response.body().contains("th:text")); return response.body();
    }
    String hidden(String body,String name) {
        var matcher=java.util.regex.Pattern.compile("name=\""+name+"\"[^>]*value=\"([^\"]+)\"").matcher(body);
        assertTrue(matcher.find(),"Missing form field "+name+": "+body); return matcher.group(1);
    }
    Map<String,String> shippingForm() { return Map.of("recipientName","Khách giao diện","recipientPhone","0901234567","deliveryMethod","pickup","address","12 Đường Hoa","note","Gọi trước","couponCode",""); }

    @Test void webPagesRenderDatabaseContentAndEscapeText() throws Exception {
        var a=admin(); long cat=category(a),p=product(a,cat,"food",7);
        var c=new Client();
        for(String path:List.of("/","/products","/products?keyword=&category_id=&min_price=&sort=newest","/login","/register")) html(webGet(c,path),200);
        var productBody=html(webGet(c,"/products/"+p),200); assertTrue(productBody.contains("120.000"));
        a.call("PATCH","/products/"+p,"{\"price\":130000,\"description\":\"<script>alert(1)</script>\"}",200);
        var updated=html(webGet(c,"/products/"+p),200); assertTrue(updated.contains("130.000")); assertTrue(updated.contains("&lt;script&gt;"));
        assertEquals(200,webGet(c,"/css/storefront.css").statusCode());
        html(webGet(c,"/products/999999999"),404); html(webGet(c,"/products?min_price=abc"),400);
        assertEquals(302,webGet(c,"/cart").statusCode()); assertEquals(302,webGet(c,"/orders").statusCode());
    }

    @Test void webRegistrationLoginAndCsrfRotation() throws Exception {
        var c=new Client(); c.csrf=hidden(html(webGet(c,"/register"),200),"_csrf"); String email=email();
        var fields=new LinkedHashMap<>(Map.of("fullName","Khách web","email",email,"phone","0901234567","address","12 Đường Hoa","password","Customer123!","passwordConfirmation","Customer123!"));
        html(webPost(c,"/register",fields,false),403);
        fields.put("email","invalid"); html(webPost(c,"/register",fields,true),400); fields.put("email",email);
        assertEquals(302,webPost(c,"/register",fields,true).statusCode());
        assertTrue(encoder.matches("Customer123!",users.findByEmail(email).orElseThrow().getPasswordHash()));
        html(webPost(c,"/register",fields,true),409);
        html(webPost(c,"/login",Map.of("email",email,"password","bad"),true),401);
        assertEquals(302,webPost(c,"/login",Map.of("email",email,"password","Customer123!"),true).statusCode());
        html(webPost(c,"/logout",Map.of(),true),403);
        c.csrf=hidden(html(webGet(c,"/cart"),200),"_csrf");
        assertEquals(302,webPost(c,"/logout",Map.of(),true).statusCode()); assertEquals(302,webGet(c,"/cart").statusCode());
    }

    @Test void webCheckoutIsOwnedServerPricedAndIdempotent() throws Exception {
        var a=admin(); long p=product(a,category(a),"food",8); String email=email(); var c=customer(email);
        assertEquals(302,webPost(c,"/cart/add",Map.of("productId",Long.toString(p),"quantity","2"),true).statusCode());
        html(webGet(c,"/cart"),200); html(webGet(c,"/checkout"),200);
        String quote=html(webPost(c,"/checkout/preview",shippingForm(),true),200),key=hidden(quote,"checkoutKey");
        assertTrue(quote.contains("240.000"));
        var placed=webPost(c,"/checkout/place",Map.of("checkoutKey",key,"total_amount","1","user_id","1"),true);
        assertEquals(302,placed.statusCode(),placed.body()); String location=URI.create(placed.headers().firstValue("Location").orElseThrow()).getPath();
        assertTrue(location.startsWith("/orders/"));
        assertEquals(location,URI.create(webPost(c,"/checkout/place",Map.of("checkoutKey",key),true).headers().firstValue("Location").orElseThrow()).getPath());
        long id=Long.parseLong(location.substring("/orders/".length()));
        assertEquals(240000,jdbc.queryForObject("SELECT total_amount FROM orders WHERE id=?",Integer.class,id));
        assertEquals(6,jdbc.queryForObject("SELECT stock_quantity FROM products WHERE id=?",Integer.class,p));
        assertTrue(html(webGet(c,location),200).contains("240.000")); html(webGet(c,"/orders"),200);
        var other=customer(email()); html(webGet(other,location),404); html(webPost(other,location+"/cancel",Map.of("reason","Hủy"),true),404);
        html(webGet(a,location),404); // Storefront remains personal even for an administrator.
        assertEquals(302,webPost(other,"/checkout/place",Map.of("checkoutKey",key),true).statusCode());
        assertEquals(302,webPost(c,location+"/cancel",Map.of("reason","Thay đổi kế hoạch"),true).statusCode());
        assertEquals(8,jdbc.queryForObject("SELECT stock_quantity FROM products WHERE id=?",Integer.class,p));
        assertTrue(html(webGet(c,location),200).contains("Thay đổi kế hoạch"));
    }

    @Test void webCheckoutRejectsChangedPriceAndInvalidInput() throws Exception {
        var a=admin(); long p=product(a,category(a),"food",5); String email=email(); var c=customer(email);
        html(webGet(c,"/checkout"),200);
        html(webPost(c,"/checkout/preview",shippingForm(),true),400);
        assertEquals(302,webPost(c,"/cart/add",Map.of("productId",Long.toString(p)),true).statusCode());
        var bad=new LinkedHashMap<>(shippingForm()); bad.put("recipientPhone","not-a-phone"); html(webPost(c,"/checkout/preview",bad,true),400);
        bad=new LinkedHashMap<>(shippingForm()); bad.put("couponCode","NO_SUCH_COUPON");
        var invalidQuote=html(webPost(c,"/checkout/preview",bad,true),200); assertFalse(invalidQuote.contains("id=\"place-order\""));
        String key=hidden(html(webPost(c,"/checkout/preview",shippingForm(),true),200),"checkoutKey");
        a.call("PATCH","/products/"+p,"{\"price\":140000}",200);
        var place=webPost(c,"/checkout/place",Map.of("checkoutKey",key),true); assertEquals("/checkout",URI.create(place.headers().firstValue("Location").orElseThrow()).getPath());
        assertTrue(html(webGet(c,"/checkout"),200).contains("Giá hoặc ưu đãi đã thay đổi"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE user_id=?",Integer.class,users.findByEmail(email).orElseThrow().getId()));
        assertEquals(5,jdbc.queryForObject("SELECT stock_quantity FROM products WHERE id=?",Integer.class,p));
    }

    @Test void webPetHealthAndReviewsAreRendered() throws Exception {
        var a=admin(); long p=product(a,category(a),"pet",1);
        long pet=a.call("POST","/pets","{\"product_id\":"+p+",\"breed\":\"Poodle\",\"gender\":\"female\"}",201).get("id").asLong();
        a.call("POST","/pets/"+pet+"/health-records","{\"record_date\":\"2026-01-01\",\"record_type\":\"vaccination\",\"description\":\"Đã tiêm phòng\"}",201);
        a.call("PATCH","/products/"+p+"/status","{\"is_active\":true}",200);
        var c=new Client(); String page=html(webGet(c,"/products/"+p),200); assertTrue(page.contains("Poodle")); assertTrue(page.contains("Đã tiêm phòng"));
        assertTrue(html(webGet(c,"/pets?status=available&breed=Poodle"),200).contains("Poodle"));
        html(webGet(c,"/pets?status=unknown"),400);
    }

    @Test void accountAndManagementPagesRenderAndRespectRoles() throws Exception {
        var a=admin(); long cat=category(a), food=product(a,cat,"food",4), animal=product(a,cat,"pet",1);
        long pet=a.call("POST","/pets","{\"product_id\":"+animal+",\"breed\":\"Poodle\"}",201).get("id").asLong();
        var c=customer(email());
        html(webGet(c,"/account"),200); html(webGet(c,"/account/password"),200);
        assertEquals(403,webGet(c,"/admin").statusCode());
        for(String path:List.of("/admin","/admin/orders","/admin/inventory","/admin/users","/admin/categories",
                "/admin/products","/admin/products/new","/admin/products/"+food,"/admin/pets","/admin/pets/"+pet,
                "/admin/health","/admin/suppliers","/admin/inventory/"+food)) html(webGet(a,path),200);
        assertEquals(302,webPost(c,"/account",Map.of("fullName","Tên mới","phone","0901234567","address","Địa chỉ mới"),true).statusCode());
        assertTrue(html(webGet(c,"/account"),200).contains("Tên mới"));
        assertEquals(302,webPost(c,"/account/password",Map.of("oldPassword","Customer123!","newPassword","Changed123!",
            "passwordConfirmation","Changed123!"),true).statusCode());
        assertEquals(302,webGet(c,"/account").statusCode());
    }

    @Test void managementAndCustomerWebActionsUseExistingBusinessRules() throws Exception {
        var a=admin(); long cat=category(a), p=product(a,cat,"food",5); var c=customer(email());
        assertEquals(302,webPost(a,"/admin/inventory/"+p+"/adjust",Map.of("change","3","reason","Nhập thêm"),true).statusCode());
        assertEquals(8,jdbc.queryForObject("SELECT stock_quantity FROM products WHERE id=?",Integer.class,p));
        assertEquals(302,webPost(a,"/admin/products/create",Map.of("categoryId",Long.toString(cat),"productType","pet",
            "name","Pet nháp","description","Pet kiểm thử","price","500000","stockQuantity","1","imageUrl",""),true).statusCode());
        long draft=jdbc.queryForObject("SELECT id FROM products WHERE name='Pet nháp' ORDER BY id DESC LIMIT 1",Long.class);
        assertEquals(302,webPost(a,"/admin/pets/create",Map.of("productId",Long.toString(draft),"breed","Poodle","age","2"),true).statusCode());
        assertEquals(302,webPost(a,"/admin/products/"+draft+"/status",Map.of("active","true"),true).statusCode());
        assertEquals(302,webPost(c,"/cart/add",Map.of("productId",Long.toString(p)),true).statusCode());
        String key=hidden(html(webPost(c,"/checkout/preview",shippingForm(),true),200),"checkoutKey");
        long order=Long.parseLong(URI.create(webPost(c,"/checkout/place",Map.of("checkoutKey",key),true)
            .headers().firstValue("Location").orElseThrow()).getPath().substring("/orders/".length()));
        html(webGet(a,"/admin/orders/"+order),200);
        assertEquals(302,webPost(a,"/admin/orders/"+order+"/status",Map.of("status","confirmed"),true).statusCode());
        assertEquals(302,webPost(a,"/admin/orders/"+order+"/status",Map.of("status","shipping"),true).statusCode());
        assertEquals(302,webPost(a,"/admin/orders/"+order+"/payment",Map.of("paymentStatus","paid","note","Đã thu tiền"),true).statusCode());
        assertEquals(302,webPost(a,"/admin/orders/"+order+"/status",Map.of("status","completed"),true).statusCode());
        assertTrue(html(webGet(c,"/products/"+p),200).contains("Viết đánh giá"));
        assertEquals(302,webPost(c,"/products/"+p+"/reviews",Map.of("rating","5","comment","Rất tốt"),true).statusCode());
        assertTrue(html(webGet(c,"/products/"+p),200).contains("Đánh giá của tôi"));
        assertTrue(html(webGet(c,"/products/"+p+"?sort=rating_desc"),200).contains("Rất tốt"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM reviews WHERE product_id=?",Integer.class,p));
    }

    @Test void customerCanCommentOnProductWithoutAnOrder() throws Exception {
        var a=admin(); long p=product(a,category(a),"food",5);
        assertTrue(html(webGet(new Client(),"/products/"+p),200).contains("Đăng nhập để đánh giá"));
        var c=customer(email());
        assertTrue(html(webGet(c,"/products/"+p),200).contains("Viết đánh giá"));
        assertEquals(302,webPost(c,"/products/"+p+"/reviews",Map.of("rating","5","comment","Bình luận đầu tiên"),true).statusCode());
        assertTrue(html(webGet(c,"/products/"+p),200).contains("Bình luận đầu tiên"));
        assertTrue(html(webGet(c,"/products/"+p),200).contains("Đánh giá của tôi"));
        c.call("POST","/products/"+p+"/reviews","{\"rating\":4,\"comment\":\"Lặp\"}",409);
        a.call("POST","/products/"+p+"/reviews","{\"rating\":5,\"comment\":\"Admin\"}",403);
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM reviews WHERE product_id=?",Integer.class,p));
    }

    @Test void staffAndAdminWebPermissionsAndManagementMutations() throws Exception {
        var a=admin(); String address=email(); var staff=customer(address);
        long staffId=staff.call("GET","/users/me",null,200).get("id").asLong();
        assertEquals(302,webPost(a,"/admin/users/"+staffId+"/role",Map.of("role","staff"),true).statusCode());
        assertEquals(302,webGet(staff,"/admin").statusCode()); // Role change invalidates old session.
        staff.login(address,"Customer123!");
        html(webGet(staff,"/admin/orders"),200);
        html(webGet(staff,"/admin/inventory"),200);
        html(webGet(staff,"/admin/health"),200);
        assertEquals(403,webGet(staff,"/admin/users").statusCode());
        assertEquals(403,webPost(staff,"/admin/categories/create",Map.of("name","Không được tạo"),true).statusCode());
        long adminId=a.call("GET","/users/me",null,200).get("id").asLong();
        assertEquals(302,webPost(a,"/admin/users/"+adminId+"/status",Map.of("active","false"),true).statusCode());
        assertEquals(1,jdbc.queryForObject("SELECT is_active FROM users WHERE id=?",Integer.class,adminId));
        assertEquals(302,webPost(a,"/admin/categories/create",Map.of("name","Danh mục web"),true).statusCode());
        long cat=jdbc.queryForObject("SELECT id FROM categories WHERE name='Danh mục web' ORDER BY id DESC LIMIT 1",Long.class);
        assertEquals(302,webPost(a,"/admin/suppliers/create",Map.of("name","Nhà cung cấp web","contactInfo","0901234567","address","Hà Nội"),true).statusCode());
        long supplier=jdbc.queryForObject("SELECT id FROM suppliers WHERE name='Nhà cung cấp web' ORDER BY id DESC LIMIT 1",Long.class);
        assertEquals(302,webPost(a,"/admin/categories/"+cat+"/update",Map.of("name","Danh mục web mới"),true).statusCode());
        assertEquals(302,webPost(a,"/admin/suppliers/"+supplier+"/update",Map.of("name","Nhà cung cấp mới","contactInfo","","address",""),true).statusCode());
        assertEquals(302,webPost(a,"/admin/categories/"+cat+"/delete",Map.of(),true).statusCode());
        assertEquals(302,webPost(a,"/admin/suppliers/"+supplier+"/delete",Map.of(),true).statusCode());
    }

    @Test void registrationAuthenticationAndCsrf() throws Exception {
        var c=new Client(); c.call("GET","/users/me",null,401);
        assertEquals(403,c.raw("POST","/auth/register",registration(email()),false).statusCode());
        c.csrf(); String email=email();
        var created=c.call("POST","/auth/register",registration(email),201);
        assertEquals("customer",created.get("role").asText()); assertTrue(created.get("is_active").asBoolean());
        assertFalse(created.has("password")); assertFalse(created.has("password_hash")); assertFalse(created.has("session_version"));
        assertTrue(encoder.matches("Customer123!",users.findByEmail(email).orElseThrow().getPasswordHash()));
        c.call("GET","/users/me",null,401);
        c.call("POST","/auth/register",registration(email.toUpperCase(Locale.ROOT)),409);
        c.call("POST","/auth/register",registration(email()).replace("\"full_name\"","\"role\":\"admin\",\"full_name\""),400);
        c.call("POST","/auth/login","{\"email\":\""+email+"\",\"password\":\"WrongPassword\"}",401);
        String oldSession=c.cookies.getCookieStore().getCookies().stream().filter(x->x.getName().equals("JSESSIONID")).findFirst().orElseThrow().getValue();
        String oldCsrf=c.csrf;
        c.call("POST","/auth/login","{\"email\":\""+email+"\",\"password\":\"Customer123!\"}",200);
        String newSession=c.cookies.getCookieStore().getCookies().stream().filter(x->x.getName().equals("JSESSIONID")).findFirst().orElseThrow().getValue();
        assertNotEquals(oldSession,newSession);
        c.csrf=oldCsrf; c.call("PATCH","/users/me","{\"full_name\":\"Test\"}",403);
        c.csrf(); c.call("GET","/users/me",null,200);
        c.call("POST","/auth/logout",null,204); c.call("GET","/users/me",null,401);
    }
    @Test void profilePartialUpdateAndStrictInput() throws Exception {
        var c=customer(email());
        c.call("PATCH","/users/me","{\"full_name\":\"  Tên mới  \"}",200);
        assertEquals("Tên mới",c.call("GET","/users/me",null,200).get("full_name").asText());
        assertEquals("0901234567",c.call("PATCH","/users/me","{\"phone\":\"090-123-4567\"}",200).get("phone").asText());
        c.call("PATCH","/users/me","{}",400);
        c.call("PATCH","/users/me","{\"full_name\":null}",400);
        c.call("PATCH","/users/me","{\"full_name\":\"   \"}",400);
        c.call("PATCH","/users/me","{\"email\":\"other@example.test\"}",400);
        c.call("PATCH","/users/me","{\"phone\":12345}",400);
        c.call("PATCH","/users/me","{\"full_name\":\"A\",\"full_name\":\"B\"}",400);
    }
    @Test void ownershipAdminFiltersRolesAndSessionRevocation() throws Exception {
        var a=admin(); String email=email(); var c=customer(email); var second=new Client(); second.login(email,"Customer123!");
        long id=c.call("GET","/users/me",null,200).get("id").asLong();
        long adminId=a.call("GET","/users/me",null,200).get("id").asLong();
        c.call("GET","/users",null,403); c.call("GET","/users/"+adminId,null,404);
        c.call("GET","/users/"+id,null,200); a.call("GET","/users/"+id,null,200);
        a.call("GET","/users?keyword="+email+"&role=customer&is_active=true&page=0&size=1",null,200);
        a.call("GET","/users?role=bad",null,400); a.call("GET","/users?is_active=1",null,400);
        a.call("PATCH","/users/"+adminId+"/role","{\"role\":\"staff\"}",403);
        a.call("PATCH","/users/"+adminId+"/status","{\"is_active\":false}",403);
        a.call("PATCH","/users/"+id+"/role","{\"role\":\"staff\"}",200);
        c.call("GET","/users/me",null,401); second.call("GET","/users/me",null,401);
        c.login(email,"Customer123!"); c.call("GET","/products?is_active=false",null,200);
        c.call("POST","/categories","{\"name\":\"Forbidden\"}",403);
        a.call("PATCH","/users/"+id+"/status","{\"is_active\":false}",200);
        c.call("GET","/users/me",null,401); c.csrf();
        c.call("POST","/auth/login","{\"email\":\""+email+"\",\"password\":\"Customer123!\"}",401);
        a.call("PATCH","/users/"+id+"/status","{\"is_active\":true}",200);
        c.call("GET","/users/me",null,401); c.login(email,"Customer123!");
        a.call("PATCH","/users/"+id+"/role","{\"role\":1}",400);
        a.call("PATCH","/users/"+id+"/status","{\"is_active\":\"true\"}",400);
    }
    @Test void passwordChangeInvalidatesEverySession() throws Exception {
        String email=email(); var c=customer(email); var other=new Client(); other.login(email,"Customer123!");
        c.call("PUT","/users/me/password","{\"old_password\":\"wrong\",\"new_password\":\"NewPassword123!\",\"password_confirmation\":\"NewPassword123!\"}",400);
        c.call("PUT","/users/me/password","{\"old_password\":\"Customer123!\",\"new_password\":\"Customer123!\",\"password_confirmation\":\"Customer123!\"}",400);
        c.call("PUT","/users/me/password","{\"old_password\":\"Customer123!\",\"new_password\":\"NewPassword123!\",\"password_confirmation\":\"NewPassword123!\"}",204);
        c.call("GET","/users/me",null,401); other.call("GET","/users/me",null,401);
        c.csrf(); c.call("POST","/auth/login","{\"email\":\""+email+"\",\"password\":\"Customer123!\"}",401);
        c.login(email,"NewPassword123!");
    }
    @Test void categoryCrudAndInactiveProductBlocksDeletion() throws Exception {
        var a=admin(); var publicClient=new Client(); long id=category(a);
        publicClient.call("GET","/categories/"+id,null,200);
        a.call("PATCH","/categories/"+id,"{\"name\":\"Tên danh mục mới\"}",200);
        publicClient.call("GET","/categories?keyword=notfound&page=0&size=20",null,200);
        long p=product(a,id,"food",0);
        a.call("PATCH","/products/"+p+"/status","{\"is_active\":false}",200);
        assertEquals("RESOURCE_IN_USE",a.call("DELETE","/categories/"+id,null,409).at("/error/code").asText());
        a.call("DELETE","/products/"+p,null,204); a.call("DELETE","/categories/"+id,null,204);
        publicClient.call("GET","/categories/"+id,null,404);
    }
    @Test void productCrudVisibilityFilterAndPatchRules() throws Exception {
        var a=admin(); var c=customer(email()); var pub=new Client(); long category=category(a);
        long p=product(a,category,"accessory",0);
        var detail=pub.call("GET","/products/"+p,null,200); assertTrue(detail.get("pet").isNull());
        assertEquals(0,detail.at("/review_summary/count").asInt()); assertTrue(detail.at("/review_summary/average").isNull());
        a.call("PATCH","/products/"+p,"{\"name\":\"Dây dắt\",\"price\":115000,\"description\":\"Mô tả\",\"image_url\":\"https://example.test/photo.jpg\"}",200);
        var updated=a.call("PATCH","/products/"+p,"{\"description\":null,\"image_url\":null}",200);
        assertTrue(updated.get("description").isNull()); assertTrue(updated.get("image_url").isNull()); assertEquals("Dây dắt",updated.get("name").asText());
        a.call("PATCH","/products/"+p,"{\"name\":null}",400);
        a.call("PATCH","/products/"+p,"{\"price\":\"100\"}",400);
        a.call("PATCH","/products/"+p,"{\"category_id\":1.2}",400);
        a.call("PATCH","/products/"+p,"{\"stock_quantity\":5}",400);
        a.call("PATCH","/products/"+p,"{\"product_type\":\"food\"}",400);
        a.call("PATCH","/products/"+p,"{}",400);
        a.call("PATCH","/products/"+p,"{\"price\":0}",400);
        a.call("PATCH","/products/"+p,"{\"image_url\":\"javascript:alert(1)\"}",400);
        c.call("PATCH","/products/"+p,"{\"name\":\"No\"}",403);
        var list=pub.call("GET","/products?category_id="+category+"&product_type=accessory&min_price=100000&max_price=120000&sort=price_asc",null,200);
        assertEquals(1,list.at("/pagination/total_elements").asInt());
        a.call("PATCH","/products/"+p+"/status","{\"is_active\":false}",200);
        pub.call("GET","/products/"+p,null,404); c.call("GET","/products/"+p,null,404); a.call("GET","/products/"+p,null,200);
        pub.call("GET","/products?is_active=false",null,403);
        assertEquals(1,a.call("GET","/products?category_id="+category+"&is_active=false",null,200).at("/pagination/total_elements").asInt());
        a.call("PATCH","/products/"+p+"/status","{\"is_active\":true}",200);
        a.call("DELETE","/products/"+p,null,204); pub.call("GET","/products/"+p,null,404);
    }
    @Test void petReadinessStockHistoryAndReviewSummary() throws Exception {
        var a=admin(); long category=category(a);
        long stockProduct=product(a,category,"food",5);
        assertEquals(5,jdbc.queryForObject("SELECT quantity_after FROM inventory_movements WHERE product_id=?",Integer.class,stockProduct));
        a.call("DELETE","/products/"+stockProduct,null,409);
        long p=product(a,category,"pet",1);
        assertFalse(a.call("GET","/products/"+p,null,200).get("is_active").asBoolean());
        assertEquals("PRODUCT_NOT_READY",a.call("PATCH","/products/"+p+"/status","{\"is_active\":true}",409).at("/error/code").asText());
        jdbc.update("INSERT INTO pets(product_id,breed,status) VALUES (?, 'Poodle','available')",p);
        a.call("PATCH","/products/"+p+"/status","{\"is_active\":true}",200);
        var pet=new Client().call("GET","/products/"+p,null,200).get("pet");
        assertEquals("Poodle",pet.get("breed").asText()); assertFalse(pet.has("reserved_order_id")); assertFalse(pet.has("supplier_id"));
        a.call("POST","/products","{\"category_id\":"+category+",\"product_type\":\"pet\",\"name\":\"Pet\",\"price\":1000,\"stock_quantity\":2}",400);
        a.call("POST","/products","{\"category_id\":"+category+",\"product_type\":\"pet\",\"name\":\"Pet\",\"price\":1000}",400);
        long uid=a.call("GET","/users/me",null,200).get("id").asLong();
        jdbc.update("INSERT INTO reviews(product_id,user_id,rating) VALUES (?,?,4)",stockProduct,uid);
        assertEquals(4.0,new Client().call("GET","/products/"+stockProduct,null,200).at("/review_summary/average").asDouble());
    }
    @Test void invalidQueriesPathsMethodsAndTypes() throws Exception {
        var a=admin(); var c=new Client();
        for(String path:List.of("/categories?page=-1","/categories?size=101","/categories?keyword=","/products?min_price=20&max_price=10",
            "/products?sort=oops","/products?product_type=bad","/products?size=1&size=2","/categories?unexpected=1",
            "/products/abc","/products/0","/products?category_id=-1","/categories/1?unexpected=1")) c.call("GET",path,null,400);
        c.call("GET","/missing",null,404);
        a.call("PUT","/categories","{}",405);
        long category=category(a);
        a.call("POST","/products","{\"category_id\":"+category+",\"product_type\":\"food\",\"name\":\"A\",\"price\":1.234}",400);
        a.call("POST","/products","{\"category_id\":"+category+",\"product_type\":\"food\",\"name\":\"A\",\"price\":1,\"stock_quantity\":\"2\"}",400);
        a.call("POST","/categories","{\"name\":123}",400);
        a.call("POST","/categories",json.writeValueAsString(Map.of("name","A".repeat(66000))),413);
        c.call("GET","/categories?page=100000",null,200);
    }
    @Test void orderHistoryPreventsDeleteEvenWithZeroStock() throws Exception {
        var a=admin(); long category=category(a), p=product(a,category,"food",0);
        long user=a.call("GET","/users/me",null,200).get("id").asLong();
        jdbc.update("INSERT INTO orders(user_id,payment_method,subtotal,total_amount) VALUES (?,'cash',1,1)",user);
        long order=jdbc.queryForObject("SELECT MAX(id) FROM orders WHERE user_id=?",Long.class,user);
        jdbc.update("INSERT INTO order_items(order_id,product_id,product_name,quantity,unit_price) VALUES (?,?,'Snapshot',1,1)",order,p);
        a.call("DELETE","/products/"+p,null,409);
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE id=?",Integer.class,p));
    }
    @Test void simultaneousRegistrationCreatesOnlyOneUser() throws Exception {
        var first=new Client(); var second=new Client(); first.csrf(); second.csrf();
        String email=email(), body=registration(email);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var ready=new CountDownLatch(1);
            var f=pool.submit(()->{ready.await();return first.raw("POST","/auth/register",body,true).statusCode();});
            var s=pool.submit(()->{ready.await();return second.raw("POST","/auth/register",body,true).statusCode();});
            ready.countDown(); var statuses=new ArrayList<>(List.of(f.get(),s.get())); Collections.sort(statuses);
            assertEquals(List.of(201,409),statuses);
        }
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email=?",Integer.class,email));
    }

    String checkoutBody(Long product, int quantity, String payment) throws Exception {
        var body=new LinkedHashMap<String,Object>();
        if(product!=null) body.put("items",List.of(Map.of("product_id",product,"quantity",quantity)));
        body.put("shipping",Map.of("recipient_name","Người nhận","recipient_phone","0901234567","delivery_method","pickup"));
        if(payment!=null) body.put("payment_method",payment);
        return json.writeValueAsString(body);
    }
    JsonNode order(Client client,String key,String body,int status) throws Exception {
        var response=client.raw("POST","/orders",body,true,key);
        assertEquals(status,response.statusCode(),response.body());
        if(status==201) assertTrue(response.headers().firstValue("Location").orElse("").contains("/orders/"));
        if(status==200) assertEquals("true",response.headers().firstValue("Idempotency-Replayed").orElse(""));
        return json.readTree(response.body());
    }
    long pet(Client admin,long product) throws Exception {
        return admin.call("POST","/pets",json.writeValueAsString(Map.of("product_id",product,"breed","Poodle","weight",2.5)),201).get("id").asLong();
    }
    @Test void petsHealthSuppliersVisibilityAndDeletion() throws Exception {
        var a=admin(); var pub=new Client(); var c=customer(email()); long cat=category(a),p=product(a,cat,"pet",1);
        a.call("POST","/pets","{\"product_id\":"+p+",\"is_neutered\":null}",400);
        long supplier=a.call("POST","/suppliers","{\"name\":\"Trại thú cưng\",\"contact_info\":\"Riêng tư\"}",201).get("id").asLong();
        long pet=a.call("POST","/pets","{\"product_id\":"+p+",\"supplier_id\":"+supplier+",\"breed\":\"Poodle\"}",201).get("id").asLong();
        a.call("POST","/pets","{\"product_id\":"+p+"}",409);
        pub.call("GET","/pets/"+pet,null,404); c.call("GET","/suppliers",null,403); pub.call("GET","/suppliers",null,401);
        long record=a.call("POST","/pets/"+pet+"/health-records","{\"record_date\":\"2026-01-01\",\"record_type\":\"vaccination\"}",201).get("id").asLong();
        pub.call("GET","/pets/"+pet+"/health-records",null,404);
        a.call("PATCH","/products/"+p+"/status","{\"is_active\":true}",200);
        var visible=pub.call("GET","/pets/"+pet,null,200);
        assertFalse(visible.has("supplier_id")); assertFalse(visible.has("reserved_order_id"));
        assertEquals(supplier,a.call("GET","/pets/"+pet,null,200).get("supplier_id").asLong());
        assertEquals(1,pub.call("GET","/pets?supplier_id="+supplier+"&breed=Poodle&status=available",null,200).at("/pagination/total_elements").asInt());
        pub.call("GET","/pets/"+pet+"/health-records/"+record,null,200);
        a.call("PATCH","/pets/"+pet,"{\"is_neutered\":true,\"weight\":3.25,\"gender\":\"female\"}",200);
        a.call("PATCH","/pets/"+pet,"{\"breed\":null}",200);
        for(String body:List.of("{}","{\"status\":\"sold\"}","{\"product_id\":1}","{\"is_neutered\":null}","{\"weight\":0}","{\"gender\":\"unknown\"}")) a.call("PATCH","/pets/"+pet,body,400);
        a.call("PATCH","/pets/"+pet+"/health-records/"+record,"{\"description\":\"Đã kiểm tra\"}",200);
        a.call("POST","/pets/"+pet+"/health-records","{\"record_date\":\"2999-01-01\",\"record_type\":\"x\"}",400);
        long second=pet(a,product(a,cat,"pet",1));
        a.call("GET","/pets/"+second+"/health-records/"+record,null,404);
        a.call("PATCH","/pets/"+second+"/health-records/"+record,"{\"record_type\":\"x\"}",404);
        a.call("GET","/health-records?pet_id="+pet,null,200);
        a.call("DELETE","/suppliers/"+supplier,null,204);
        assertTrue(a.call("GET","/pets/"+pet,null,200).get("supplier_id").isNull());
        pub.call("GET","/pets/"+pet+"/health-records/"+record,null,200);
        a.call("DELETE","/pets/"+pet,null,204);
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM pet_health_records WHERE pet_id=?",Integer.class,pet));
        assertFalse(a.call("GET","/products/"+p,null,200).get("is_active").asBoolean());
        assertEquals(1,jdbc.queryForObject("SELECT stock_quantity FROM products WHERE id=?",Integer.class,p));
    }
    @Test void cartAddReplaceDeleteAndPreviewDoNotReserveStock() throws Exception {
        var a=admin(); var c=customer(email()); long p=product(a,category(a),"food",20);
        long user=c.call("GET","/users/me",null,200).get("id").asLong();
        assertEquals(0,c.call("GET","/cart",null,200).get("items").size());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM carts WHERE user_id=?",Integer.class,user));
        c.call("POST","/cart/items","{\"product_id\":"+p+",\"quantity\":3}",200);
        assertEquals(5,c.call("POST","/cart/items","{\"product_id\":"+p+",\"quantity\":2}",200).at("/items/0/quantity").asInt());
        c.call("PUT","/cart/items/"+p,"{\"quantity\":2}",200);
        assertEquals(2,c.call("PUT","/cart/items/"+p,"{\"quantity\":2}",200).at("/items/0/quantity").asInt());
        var preview=c.call("POST","/checkout/preview",checkoutBody(null,1,null),200);
        assertTrue(preview.get("can_place_order").asBoolean()); assertEquals(240000,preview.get("subtotal").asInt());
        assertEquals(20,jdbc.queryForObject("SELECT stock_quantity FROM products WHERE id=?",Integer.class,p));
        c.call("PUT","/cart/items/"+p,"{\"quantity\":0}",400);
        c.call("PUT","/cart/items/"+p,"{\"quantity\":21}",409);
        var shortStock=c.call("POST","/checkout/preview",checkoutBody(p,21,null),200);
        assertFalse(shortStock.get("can_place_order").asBoolean()); assertFalse(shortStock.get("subtotal").isNull());
        a.call("PATCH","/products/"+p+"/status","{\"is_active\":false}",200);
        assertFalse(c.call("GET","/cart",null,200).at("/items/0/available").asBoolean());
        var hidden=c.call("POST","/checkout/preview",checkoutBody(p,1,null),200);
        assertTrue(hidden.at("/items/0/name").isNull()); assertTrue(hidden.get("total_amount").isNull());
        assertEquals("PRODUCT_UNAVAILABLE",hidden.at("/issues/0/code").asText());
        c.call("DELETE","/cart/items/"+p,null,204); c.call("DELETE","/cart/items/"+p,null,204);
        c.call("POST","/checkout/preview",checkoutBody(null,1,null),400);
        c.call("DELETE","/cart/items",null,204);
    }
    @Test void orderReplayCartConsumptionSnapshotOwnershipAndCancellation() throws Exception {
        var a=admin(); var c=customer(email()); var other=customer(email()); long p=product(a,category(a),"food",10);
        c.call("PUT","/cart/items/"+p,"{\"quantity\":4}",200);
        String body=checkoutBody(null,1,"cash"),key=UUID.randomUUID().toString();
        c.call("POST","/orders",body,400);
        var made=order(c,key,body,201); long order=made.get("id").asLong();
        assertEquals(6,jdbc.queryForObject("SELECT stock_quantity FROM products WHERE id=?",Integer.class,p));
        assertEquals(0,c.call("GET","/cart",null,200).get("items").size());
        a.call("PATCH","/products/"+p,"{\"price\":99,\"name\":\"New catalog name\"}",200);
        assertEquals(120000,c.call("GET","/orders/"+order+"/items",null,200).get(0).get("unit_price").asInt());
        assertEquals(order,order(c,key,body,200).get("id").asLong());
        order(c,key,checkoutBody(p,1,"cash"),409);
        for(String suffix:List.of("","/items","/events","/shipping","/cancellation")) other.call("GET","/orders/"+order+suffix,null,404);
        c.call("GET","/orders?user_id=1",null,403);
        c.call("GET","/orders?date_from=2026-10-01&date_to=2026-01-01",null,400);
        c.call("GET","/orders/"+order+"/cancellation",null,404);
        c.call("PATCH","/orders/"+order+"/shipping","{\"note\":\"Gọi trước\"}",200);
        c.call("PATCH","/orders/"+order+"/shipping","{\"delivery_method\":\"shipping\"}",400);
        a.call("PATCH","/orders/"+order+"/status","{\"status\":\"cancelled\"}",400);
        var cancelled=c.call("PUT","/orders/"+order+"/cancellation","{\"reason\":\"Đổi ý\"}",201);
        assertEquals("Đổi ý",c.call("PUT","/orders/"+order+"/cancellation","{\"reason\":\"Lý do mới\"}",200).get("reason").asText());
        assertEquals(10,jdbc.queryForObject("SELECT stock_quantity FROM products WHERE id=?",Integer.class,p));
        assertEquals("cancelled",order(c,key,body,200).get("status").asText());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM inventory_movements WHERE order_id=? AND quantity_change>0",Integer.class,order));
        assertFalse(cancelled.get("cancelled_at").isNull());
    }
    @Test void petOrderLifecyclePaymentAndReviewOwnership() throws Exception {
        var a=admin(); var c=customer(email()); var other=customer(email()); long p=product(a,category(a),"pet",1),pet=pet(a,p);
        a.call("PATCH","/products/"+p+"/status","{\"is_active\":true}",200);
        c.call("POST","/cart/items","{\"product_id\":"+p+",\"quantity\":1}",200);
        c.call("POST","/cart/items","{\"product_id\":"+p+",\"quantity\":1}",409);
        long order=order(c,UUID.randomUUID().toString(),checkoutBody(p,1,"bank_transfer"),201).get("id").asLong();
        assertEquals("reserved",a.call("GET","/pets/"+pet,null,200).get("status").asText());
        a.call("PATCH","/orders/"+order+"/status","{\"status\":\"confirmed\"}",409);
        c.call("PATCH","/orders/"+order+"/payment-status","{\"payment_status\":\"paid\",\"note\":\"x\"}",403);
        a.call("PATCH","/orders/"+order+"/payment-status","{\"payment_status\":\"paid\",\"note\":\"Đã đối chiếu\"}",200);
        assertNull(jdbc.queryForObject("SELECT reserved_until FROM pets WHERE id=?",Timestamp.class,pet));
        a.call("PATCH","/orders/"+order+"/payment-status","{\"payment_status\":\"paid\",\"note\":\"Lặp\"}",200);
        a.call("PATCH","/orders/"+order+"/status","{\"status\":\"shipping\"}",409);
        a.call("PATCH","/orders/"+order+"/status","{\"status\":\"confirmed\"}",200);
        c.call("PUT","/orders/"+order+"/cancellation","{\"reason\":\"x\"}",409);
        a.call("PATCH","/orders/"+order+"/status","{\"status\":\"shipping\"}",200);
        c.call("PATCH","/orders/"+order+"/shipping","{\"note\":\"x\"}",409);
        a.call("PATCH","/orders/"+order+"/status","{\"status\":\"completed\"}",200);
        a.call("PATCH","/orders/"+order+"/status","{\"status\":\"completed\"}",200);
        assertEquals("sold",a.call("GET","/pets/"+pet,null,200).get("status").asText());
        a.call("DELETE","/pets/"+pet,null,409);
        a.call("PATCH","/products/"+p+"/status","{\"is_active\":false}",200);
        long review=c.call("POST","/products/"+p+"/reviews","{\"rating\":5,\"comment\":\"Rất tốt\"}",201).get("id").asLong();
        c.call("POST","/products/"+p+"/reviews","{\"rating\":4}",409);
        other.call("PATCH","/products/"+p+"/reviews/"+review,"{\"rating\":1}",404);
        a.call("PATCH","/products/"+p+"/reviews/"+review,"{\"rating\":1}",404);
        c.call("PATCH","/products/"+p+"/reviews/"+review,"{\"comment\":null,\"rating\":4}",200);
        a.call("PATCH","/products/"+p+"/status","{\"is_active\":true}",200);
        var publicReview=new Client().call("GET","/products/"+p+"/reviews/"+review,null,200);
        assertEquals(1,publicReview.get("author").size()); assertTrue(publicReview.get("author").has("display_name"));
        new Client().call("GET","/products/"+p+"/reviews?sort=rating_desc",null,200);
        a.call("DELETE","/products/"+p+"/reviews/"+review,null,204);
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM order_events WHERE order_id=? AND event_type='payment'",Integer.class,order));
    }
    @Test void inventoryAdjustmentsHistoryAndReservedCapacity() throws Exception {
        var a=admin(); var c=customer(email()); long p=product(a,category(a),"food",10);
        c.call("GET","/inventory",null,403);
        long movement=a.call("POST","/inventory/"+p+"/adjustments","{\"change\":5,\"reason\":\"Nhập hàng\",\"reference\":\"PN-1\"}",201).get("id").asLong();
        assertEquals(15,a.call("GET","/inventory/"+p,null,200).get("stock_quantity").asInt());
        a.call("GET","/inventory?low_stock=15",null,200);
        a.call("GET","/inventory?low_stock=-1",null,400);
        a.call("GET","/inventory/"+p+"/movements/"+movement,null,200);
        long second=product(a,category(a),"food",0);
        a.call("GET","/inventory/"+second+"/movements/"+movement,null,404);
        a.call("POST","/inventory/"+p+"/adjustments","{\"change\":-16,\"reason\":\"x\"}",409);
        a.call("POST","/inventory/"+p+"/adjustments","{\"change\":0,\"reason\":\"x\"}",400);
        long order=order(c,UUID.randomUUID().toString(),checkoutBody(p,10,"cash"),201).get("id").asLong();
        a.call("POST","/inventory/"+p+"/adjustments","{\"change\":999990,\"reason\":\"x\"}",409);
        c.call("PUT","/orders/"+order+"/cancellation","{\"reason\":\"x\"}",201);
        assertEquals(15,a.call("GET","/inventory/"+p,null,200).get("stock_quantity").asInt());
        long petProduct=product(a,category(a),"pet",1);
        a.call("POST","/inventory/"+petProduct+"/adjustments","{\"change\":1,\"reason\":\"x\"}",409);
    }
    @Test void concurrentOrdersOnlyOneBuyerCanReservePet() throws Exception {
        var a=admin(); var first=customer(email()); var second=customer(email()); long p=product(a,category(a),"pet",1); pet(a,p);
        a.call("PATCH","/products/"+p+"/status","{\"is_active\":true}",200);
        String body=checkoutBody(p,1,"cash");
        try(var pool=Executors.newFixedThreadPool(2)) {
            var gate=new CountDownLatch(1);
            var f=pool.submit(()->{gate.await();return first.raw("POST","/orders",body,true,UUID.randomUUID().toString()).statusCode();});
            var s=pool.submit(()->{gate.await();return second.raw("POST","/orders",body,true,UUID.randomUUID().toString()).statusCode();});
            gate.countDown(); var statuses=new ArrayList<>(List.of(f.get(),s.get())); Collections.sort(statuses);
            assertEquals(List.of(201,409),statuses);
        }
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM order_items WHERE product_id=?",Integer.class,p));
        assertEquals(0,jdbc.queryForObject("SELECT stock_quantity FROM products WHERE id=?",Integer.class,p));
    }
    @Test void concurrentSameKeyCreatesOnceAndReorderedItemsReplay() throws Exception {
        var a=admin(); String email=email(); var first=customer(email); var second=new Client(); second.login(email,"Customer123!");
        long cat=category(a),p=product(a,cat,"food",10),p2=product(a,cat,"food",10);
        var bodyNode=(tools.jackson.databind.node.ObjectNode)json.readTree(checkoutBody(p,2,"cash"));
        bodyNode.putArray("items").addObject().put("product_id",p).put("quantity",2);
        ((tools.jackson.databind.node.ArrayNode)bodyNode.get("items")).addObject().put("product_id",p2).put("quantity",1);
        String body=json.writeValueAsString(bodyNode),key=UUID.randomUUID().toString();
        try(var pool=Executors.newFixedThreadPool(2)) {
            var gate=new CountDownLatch(1);
            var f=pool.submit(()->{gate.await();return first.raw("POST","/orders",body,true,key).statusCode();});
            var s=pool.submit(()->{gate.await();return second.raw("POST","/orders",body,true,key).statusCode();});
            gate.countDown(); var statuses=new ArrayList<>(List.of(f.get(),s.get())); Collections.sort(statuses);
            assertEquals(List.of(200,201),statuses);
        }
        assertEquals(8,jdbc.queryForObject("SELECT stock_quantity FROM products WHERE id=?",Integer.class,p));
        var reversed=bodyNode.putArray("items"); reversed.addObject().put("quantity",1).put("product_id",p2); reversed.addObject().put("quantity",2).put("product_id",p);
        order(first,key,json.writeValueAsString(bodyNode),200);
    }
    @Test void expiryRestoresOnceAndPaidCancellationRequiresManualRefund() throws Exception {
        var a=admin(); var c=customer(email()); long p=product(a,category(a),"pet",1),pet=pet(a,p);
        a.call("PATCH","/products/"+p+"/status","{\"is_active\":true}",200);
        long order=order(c,UUID.randomUUID().toString(),checkoutBody(p,1,"card"),201).get("id").asLong();
        jdbc.update("UPDATE orders SET reserved_until=DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 1 MINUTE) WHERE id=?",order);
        a.call("PATCH","/orders/"+order+"/payment-status","{\"payment_status\":\"paid\",\"note\":\"x\"}",409);
        orderService.expire(order); orderService.expire(order);
        assertEquals("cancelled",c.call("GET","/orders/"+order,null,200).get("status").asText());
        assertEquals("available",a.call("GET","/pets/"+pet,null,200).get("status").asText());
        assertEquals(1,jdbc.queryForObject("SELECT stock_quantity FROM products WHERE id=?",Integer.class,p));
        a.call("DELETE","/pets/"+pet,null,409);
        long paid=order(c,UUID.randomUUID().toString(),checkoutBody(p,1,"card"),201).get("id").asLong();
        a.call("PATCH","/orders/"+paid+"/payment-status","{\"payment_status\":\"paid\",\"note\":\"Thu tiền\"}",200);
        orderService.expire(paid);
        assertEquals("pending",c.call("GET","/orders/"+paid,null,200).get("status").asText());
        a.call("PATCH","/orders/"+paid+"/payment-status","{\"payment_status\":\"refunded\",\"note\":\"x\"}",409);
        c.call("PUT","/orders/"+paid+"/cancellation","{\"reason\":\"Đổi ý\"}",201);
        assertEquals("paid",c.call("GET","/orders/"+paid,null,200).get("payment_status").asText());
        a.call("PATCH","/orders/"+paid+"/payment-status","{\"payment_status\":\"refunded\",\"note\":\"Đã hoàn\"}",200);
    }
    @Test void checkoutStrictValidationCouponAndTransactionalRollback() throws Exception {
        var a=admin(); var c=customer(email()); long p=product(a,category(a),"food",10);
        String code="TEST"+UUID.randomUUID().toString().replace("-","");
        jdbc.update("INSERT INTO coupons(code,discount_type,discount_value,min_subtotal,starts_at,ends_at,usage_limit) VALUES (?,'percent',10,0,DATE_SUB(NOW(),INTERVAL 1 DAY),DATE_ADD(NOW(),INTERVAL 1 DAY),1)",code);
        var body=(tools.jackson.databind.node.ObjectNode)json.readTree(checkoutBody(p,2,"cash")); body.put("coupon_code",code);
        String request=json.writeValueAsString(body),key=UUID.randomUUID().toString();
        var made=order(c,key,request,201); assertEquals(24000,made.get("discount_amount").asInt()); assertEquals(216000,made.get("total_amount").asInt());
        order(c,key,request,200);
        assertEquals(1,jdbc.queryForObject("SELECT used_count FROM coupons WHERE code=?",Integer.class,code));
        order(c,UUID.randomUUID().toString(),request,409);
        assertEquals(8,jdbc.queryForObject("SELECT stock_quantity FROM products WHERE id=?",Integer.class,p));
        body.remove("payment_method");
        var invalid=c.call("POST","/checkout/preview",json.writeValueAsString(body),200);
        assertFalse(invalid.get("can_place_order").asBoolean()); assertTrue(invalid.get("subtotal").isNull());
        body.remove("coupon_code"); body.putNull("items"); c.call("POST","/checkout/preview",json.writeValueAsString(body),400);
        body.putArray("items"); c.call("POST","/checkout/preview",json.writeValueAsString(body),400);
        body.putArray("items").addObject().put("product_id",p).put("quantity",1);
        ((tools.jackson.databind.node.ArrayNode)body.get("items")).addObject().put("product_id",p).put("quantity",1);
        c.call("POST","/checkout/preview",json.writeValueAsString(body),400);
        c.call("POST","/orders",checkoutBody(p,1,"cash").replace("\"payment_method\"","\"user_id\":1,\"payment_method\""),400);
        c.call("POST","/checkout/preview",checkoutBody(p,1,null).replace("\"quantity\":1","\"quantity\":\"1\""),400);
    }
}
