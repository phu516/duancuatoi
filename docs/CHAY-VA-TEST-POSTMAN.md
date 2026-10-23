> Backend hiện có 65 endpoint, đã đạt 19 kiểm thử tích hợp HTTP/MySQL. Phần 4–10: [hướng dẫn và collection mới](CHUC-NANG-4-10.md). Phiên kiểm thử 22 API / 44 request bên dưới mô tả lần triển khai phần 1–3 trước đó.

# Chạy 22 API và kiểm thử bằng Postman

## Phiên chạy đã chuẩn bị ngày 29/09/2026

API đã được chạy và kiểm tra bằng collection trên `http://localhost:8080/api/v1`, dùng database `pet_shop_api`. Server thử nghiệm đã được dừng để giải phóng cổng 8080 cho nút Run của IDE. Chọn **DemoApplication** (module `demo`, Java 21); sau khi app báo `Started DemoApplication`, import collection cùng [environment đã điền tài khoản thử](../postman/Local-ready.postman_environment.json), chọn **Pet Shop Local - Ready**, rồi Run. Environment này chỉ dùng local và đã được đưa vào `.gitignore`.

Đã xác minh: Maven verify đạt 10/10 kiểm thử tích hợp; Newman đạt 44/44 assertions trên collection (75 HTTP request tính cả các lần tự lấy CSRF). Báo cáo ở `target/surefire-reports` và `target/postman-result.json`; log server ở `target/local-api.log`.

Để dừng server chạy trong IDE, nhấn nút Stop tương ứng trong IDE. Khi chạy lại, database và tài khoản đã tạo được giữ nguyên. File environment mẫu bên dưới dành cho cấu hình mới hoặc máy khác.

## 1. Môi trường và khởi động

Dự án dùng Java 21, Spring Boot 4.1.1, MySQL 8, Maven wrapper. Giữ phiên bản trong pom đã có; đã bổ sung driver MySQL và Flyway MySQL.

Mở PowerShell tại thư mục `demo`. Thay mật khẩu ví dụ bằng giá trị của bạn:

```powershell
$env:JAVA_HOME='C:\Users\nguye\.jdks\ms-21.0.12.1'
$env:DB_URL='jdbc:mysql://localhost:3306/pet_shop_api?createDatabaseIfNotExist=true&connectionTimeZone=UTC'
$env:DB_USERNAME='root'
$env:DB_PASSWORD='<mat-khau-MySQL-cua-ban>'
$env:APP_ADMIN_EMAIL='admin@example.test'
$env:APP_ADMIN_PASSWORD='<mat-khau-admin-it-nhat-8-ky-tu>'
.\mvnw.cmd spring-boot:run
```

Đường dẫn JAVA_HOME ở ví dụ là Java 21 đã thấy trên máy lúc triển khai; dùng đường dẫn Java 21 của bạn nếu khác. Mật khẩu admin tối thiểu 8 ký tự, tối đa 72 byte UTF-8. Không có mật khẩu admin mặc định trong source.

URL mặc định dùng **database riêng pet_shop_api** để thử API. Database `pet_shop` trên máy này đã có 16 bảng nhưng chưa có Flyway history; các API Spring hiện chưa tương thích schema cũ, vì vậy không dùng tên đó để chạy Spring Boot. Cấu hình mới giữ nguyên database cũ và dùng `pet_shop_api`. Flyway tạo schema trong DB API; nếu user MySQL không có quyền CREATE DATABASE, tạo trước DB trống và bỏ `createDatabaseIfNotExist=true`. Với DB cũ cần kế hoạch migration riêng; không bật baseline để bỏ qua lỗi.

Lần đầu, admin được tạo nếu cả APP_ADMIN_EMAIL và APP_ADMIN_PASSWORD đã cấu hình. Nếu email đã tồn tại, initializer giữ nguyên tài khoản và mật khẩu hiện có, không tự nâng quyền. Đổi biến môi trường không đổi mật khẩu tài khoản đã tạo.

Đăng nhập dùng **email**, không dùng trường username của cấu hình cũ. Các biến được đọc từ môi trường process; ứng dụng không tự đọc file .env.

Dừng bằng Ctrl+C. Dữ liệu MySQL vẫn còn sau khi tắt ứng dụng. Không cần frontend/JSP để gọi các endpoint.

## 2. Import và chạy Postman

Import hai file:

- [Pet-Shop.postman_collection.json](../postman/Pet-Shop.postman_collection.json).
- [Local.postman_environment.json](../postman/Local.postman_environment.json).

Chọn environment **Pet Shop Local**. Điền `admin_email` và `admin_password` đúng tài khoản đã khởi tạo. Giữ `base_url=http://localhost:8080/api/v1`. Bật cookie jar của Postman; không nhập Bearer token. Dùng nhất quán localhost, không đổi qua 127.0.0.1 giữa các request vì cookie phụ thuộc host.

Chạy cả collection theo thứ tự 01 → 04. Collection có **44 request**, phủ 22 endpoint duy nhất. Script tự lấy CSRF trước POST/PUT/PATCH/DELETE và gắn header X-CSRF-TOKEN; cookie phiên được Postman lưu. Request đăng ký tạo email customer mới cho mỗi lần chạy collection.

| Folder | Nội dung |
|---|---|
| 01 — Customer | Lấy CSRF, đăng ký, login, xem/sửa hồ sơ, kiểm tra thiếu quyền, logout |
| 02 — Admin | Login admin, list/detail user, đổi role, khóa/mở, chặn tự khóa |
| 03 — Catalog | CRUD category/product, tìm kiếm, ngừng/mở bán, xóa có/không có liên kết, pet nháp |
| 04 — Password | Customer đổi mật khẩu, phiên cũ 401, login với mật khẩu mới, logout |

Một số request **chủ ý mong đợi 400/401/403/404/409**; hãy đọc tab Tests thay vì coi mọi mã khác 200 là lỗi. Collection để lại sản phẩm có lịch sử, pet nháp và danh mục của chúng nhằm kiểm tra quy tắc không xóa. Sản phẩm thử xóa thành công được tạo với stock_quantity=0 và chưa có lịch sử.

Khi chạy riêng một request, cần chạy trước request tạo dữ liệu/login tương ứng để có các biến customer_id/category_id/product_id. Không tự điền ID của database khác. Có thể xem giá trị hiện tại ở Collection Variables.

## 3. Gọi thủ công

1. GET `/api/v1/auth/csrf-token`: giữ cookie JSESSIONID và lấy csrf_token.
2. POST `/api/v1/auth/login`: gửi cookie, Content-Type application/json, X-CSRF-TOKEN.
3. GET csrf-token lần nữa sau login để lấy token của phiên mới.
4. Gửi cookie trên mọi request cần xác thực; gửi CSRF trên mọi POST/PUT/PATCH/DELETE.
5. Sau logout/đổi mật khẩu, lấy CSRF mới trước lần login tiếp theo.

Body đăng nhập:

```json
{
  "email": "admin@example.test",
  "password": "mat-khau-admin-ban-da-cau-hinh"
}
```

Ví dụ tạo sản phẩm accessory, thay category_id bằng ID danh mục đã tạo:

```json
{
  "category_id": 1,
  "product_type": "accessory",
  "name": "Dây dắt chó",
  "price": 120000,
  "stock_quantity": 0,
  "description": "Dây dắt dài 1,5 m",
  "image_url": "https://example.test/day-dat.jpg"
}
```

PATCH nhận các trường cần đổi. Trường không gửi giữ nguyên; description/image_url có thể null để xóa giá trị. name/price/category_id không nhận null. Không gửi stock_quantity, product_type hoặc is_active vào PATCH thông tin product; dùng endpoint /status cho is_active.

## 4. Những kết quả cần hiểu đúng

| Kết quả | Nguyên nhân / cách kiểm tra |
|---|---|
| 401 AUTHENTICATION_REQUIRED | Chưa login, session hết hạn hoặc bị thu hồi; lấy CSRF và login lại |
| 401 INVALID_CREDENTIALS | Sai email/password hoặc tài khoản khóa |
| 403 CSRF_INVALID | Thiếu cookie/token, hoặc dùng token trước login/logout |
| 403 FORBIDDEN | Customer/staff đang gọi thao tác chỉ dành cho admin |
| 404 khi đọc product | Không có product hoặc product ngừng bán và người gọi không phải staff/admin |
| 409 RESOURCE_IN_USE khi xóa category | Vẫn có product, kể cả inactive |
| 409 RESOURCE_IN_USE khi xóa product mới | stock_quantity > 0 đã tạo movement opening_stock; dùng /status để ngừng bán |
| 409 PRODUCT_NOT_READY với pet | Chưa có hồ sơ pet. API tạo hồ sơ pet chưa thuộc đợt triển khai này |
| 400 khi gửi số dạng chuỗi | Phải gửi JSON number, ví dụ 2 thay cho "2" |
| 413 PAYLOAD_TOO_LARGE | JSON request vượt 64 KiB |
| Flyway từ chối schema có sẵn | Dùng DB thử mới/trống, hoặc thực hiện migration có kiểm soát cho DB cũ |
| Không kết nối MySQL | Kiểm tra service MySQL, port, DB_USERNAME và DB_PASSWORD |

Chỉ admin ghi catalog. Staff/admin được xem product ngừng bán; list mặc định vẫn active=true, truyền is_active=false để liệt kê hàng ngừng bán. Admin không tự đổi role hoặc tự khóa. Đổi role/status/mật khẩu thu hồi mọi phiên cũ của user bị tác động.

Migration tạo thêm bảng hỗ trợ suppliers/pets/orders/order_items/inventory_movements/reviews để các kiểm tra và response product có dữ liệu thật. Các bảng đó chưa có API ghi riêng trong đợt này; không có luồng mua hàng hoặc thanh toán hoạt động chỉ vì bảng orders đã tồn tại.

## 5. Chạy kiểm thử tự động

```powershell
$env:JAVA_HOME='C:\Users\nguye\.jdks\ms-21.0.12.1'
$env:TEST_DB_HOST='localhost:3306'
$env:TEST_DB_USERNAME='root'
$env:TEST_DB_PASSWORD='<mat-khau-MySQL-test>'
.\mvnw.cmd test
```

User MySQL test cần CREATE/DROP DATABASE. Mỗi lượt test tạo DB ngẫu nhiên petshop_test_<uuid>, chạy migration, khởi động HTTP ở port ngẫu nhiên và chỉ xóa đúng DB đã tạo khi kết thúc. Nếu JVM bị dừng cưỡng bức, DB test có thể còn lại; không có tác vụ tự xóa các DB khác.

Bộ test kiểm tra response/validation, session fixation/CSRF, nhiều phiên, quyền sở hữu, lọc/phân trang, sản phẩm ẩn, nullable PATCH, pet chưa sẵn sàng, lịch sử tồn/đơn và đăng ký đồng thời. Các module ngoài 22 API này chưa được kiểm thử như API đã triển khai.

## 6. Cấu trúc đã viết

```text
com.example.demo
├── controller    AuthController, UserController, CategoryController, ProductController
├── dto
│   ├── request   DTO đăng ký/login, sửa user/category/product, role/status
│   └── response  User, Category, Product, Page, CSRF, lỗi
├── entity        User, Category, Product, Pet, enums
├── exception     ApiException, GlobalExceptionHandler
├── repository    User, Category, Product, Pet, ProductHistory
├── service       AuthService, UserService, CategoryService, ProductService, validation/query
├── config        Security, JSON, bootstrap admin, trace và kiểm tra đầu vào
├── security      Principal, CurrentUser, kiểm tra session_version
└── DemoApplication
resources/db/migration/V1__account_catalog.sql
postman/*.json
```

Cấu hình JSON theo [hướng dẫn Spring Boot](https://docs.spring.io/spring-boot/how-to/spring-mvc.html); login chủ động lưu SecurityContext theo [tài liệu Spring Security](https://docs.spring.io/spring-security/reference/servlet/authentication/persistence.html).
