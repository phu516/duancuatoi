# Pet Shop — Spring Boot + Thymeleaf

Giao diện **PetVibe Thymeleaf** đã được nối với MySQL: cửa hàng, thú cưng, tài khoản, giỏ hàng, thanh toán, đơn hàng và khu vực quản trị. Chạy `DemoApplication`, mở **http://localhost:8080/**. Xem [hướng dẫn giao diện](docs/THYMELEAF-FRONTEND.md).

Đã triển khai **65 API thuộc phần 1–10**, gồm tài khoản, danh mục, sản phẩm, thú cưng, sức khỏe, nhà cung cấp, giỏ/checkout, đơn/giao hàng, tồn kho và đánh giá. Mã nguồn ở package `com.example.demo`, theo cấu trúc `controller → service → repository`. Base URL: `http://localhost:8080/api/v1`.

Xem [chức năng 4–10 và cách thử](docs/CHUC-NANG-4-10.md), cùng [collection Postman mới](postman/Commerce.postman_collection.json) gồm 62 request.

[Checklist test Postman toàn bộ 65 API](docs/POSTMAN-TEST-TOAN-BO.md) có quyền truy cập, body/query mẫu, HTTP mong đợi, cách lấy CSRF, lưu ID và thứ tự test từng luồng.

[Dữ liệu mẫu Faker đã nạp](docs/DU-LIEU-MAU-POSTMAN.md): 10 tài khoản, 32 product, 11 pet, 14 đơn nhiều trạng thái và 5 coupon; có environment chứa ID/tài khoản cùng collection để test ngay.

## Chạy và thử ngay

Để chạy bằng nút Run trong IDE, chọn **DemoApplication** (module `demo`, Java 21). Tôi đã dừng server Postman nền, nên cổng 8080 hiện trống. Bạn có thể import collection và [environment sẵn dùng](postman/Local-ready.postman_environment.json), chọn **Pet Shop Local - Ready**, rồi Run collection sau khi server khởi động.

1. Mở [hướng dẫn chạy và Postman](docs/CHAY-VA-TEST-POSTMAN.md), cấu hình Java 21 và biến môi trường MySQL/admin.
2. Chạy `.\mvnw.cmd spring-boot:run` trong thư mục `demo`.
3. Import [Postman collection](postman/Pet-Shop.postman_collection.json) và [environment Local](postman/Local.postman_environment.json).
4. Điền mật khẩu admin trong environment, chọn **Pet Shop Local**, chạy toàn bộ collection theo thứ tự.

Collection có 44 request, bao gồm trường hợp thành công và lỗi dự kiến. Tự lấy CSRF, dùng cookie jar và lưu ID từ response. Không cần chép token bằng tay khi dùng collection.

Kiểm thử HTTP/MySQL nằm trong `ApiIntegrationTests`; kiểm thử giới hạn tần suất nằm trong `RateLimitServiceTests`. Chạy `.\mvnw.cmd test` với `TEST_DB_PASSWORD` đã cấu hình. Collection cũ phần 1–3 từng được xác minh bằng Newman với 44/44 assertions; collection mới phần 4–10 chưa chạy qua Newman.

## Đã có

| Nhóm | API |
|---|---|
| Auth — 4 | CSRF, đăng ký, đăng nhập JSON, đăng xuất |
| Users — 7 | me, sửa hồ sơ, đổi password, danh sách/chi tiết, đổi role, khóa/mở khóa |
| Categories — 5 | Danh sách, chi tiết, tạo, sửa, xóa |
| Products — 6 | Lọc/tìm/sắp xếp, chi tiết, tạo, sửa, mở/ngừng bán, xóa |
| Pets — 5 | Danh sách/chi tiết, tạo/sửa/xóa hồ sơ cá thể |
| Health records — 6 | CRUD theo pet, tra cứu toàn bộ cho staff |
| Suppliers — 5 | CRUD, xóa giữ pet và sức khỏe |
| Cart/Checkout — 6 | Giỏ riêng, cộng/thay số lượng, preview tiền/tồn/coupon |
| Orders/Shipping — 11 | Đặt đơn chống trùng, xem, lịch sử, trạng thái, thu/hoàn tiền, hủy, giao hàng |
| Inventory — 5 | Tồn khả dụng, nhật ký, điều chỉnh hàng hóa |
| Reviews — 5 | Đọc, đánh giá sau mua, sửa/xóa theo quyền |

Session/Cookie + CSRF; BCrypt; thu hồi các phiên cũ khi đổi mật khẩu/role/status. JSON snake_case, phân trang, validation và JSON lỗi thống nhất. Product pet được tạo nháp, chưa mở bán khi chưa có hồ sơ pet. Sản phẩm có lịch sử không được xóa; danh mục còn bất kỳ sản phẩm nào không được xóa.

## Chưa triển khai trong đợt này

API quản trị coupon, cổng thanh toán/webhook và Swagger/OpenAPI. Checkout đã áp dụng coupon có sẵn trong database và không tự tạo coupon thử. Giới hạn JSON 64 KiB và giới hạn tần suất đăng nhập, đăng ký, tạo đơn trả HTTP 429 đã có. Bộ giới hạn tần suất lưu trong bộ nhớ của một instance; khi chạy nhiều instance cần thay bằng kho chia sẻ.

## Tài liệu

- [Chạy dự án và test Postman](docs/CHAY-VA-TEST-POSTMAN.md).
- [Danh sách endpoint](docs/api/02-DANH-SACH-ENDPOINT.md): nhóm 1–10 đã triển khai.
- [DTO và JSON mẫu](docs/api/03-DU-LIEU-VA-VI-DU.md).
- [Quy ước HTTP và bảo mật](docs/api/01-QUY-UOC.md).
- [Cấu trúc tổng quát dự kiến cho toàn bộ back-end](docs/CAU-TRUC-BACKEND.md).
- [Kế hoạch toàn dự án](docs/KE-HOACH-BACKEND.md).

Kiểm thử tự động ở [ApiIntegrationTests.java](src/test/java/com/example/demo/ApiIntegrationTests.java) chạy HTTP thật và MySQL riêng. Test tự tạo database `petshop_test_<uuid>`, sau đó chỉ xóa database đó. Cần cấu hình `TEST_DB_PASSWORD`; không chạy test trên database cửa hàng.
