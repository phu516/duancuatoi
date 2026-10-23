# Chức năng 4–10 đã triển khai

Thêm 43 endpoint vào backend hiện có, nâng tổng số lên 65. Prefix: `/api/v1`.

| Phần | Endpoint | Controller | Service |
|---|---:|---|---|
| 4. Thú cưng | 5 | PetController | PetService |
| 5. Hồ sơ sức khỏe | 6 | HealthRecordController | PetService |
| 6. Nhà cung cấp | 5 | SupplierController | SupplierService |
| 7. Giỏ và tính thử đơn | 6 | CartController, CheckoutController | CartService, CheckoutService |
| 8. Đơn và giao hàng | 11 | OrderController | OrderService |
| 9. Tồn kho | 5 | InventoryController | InventoryService |
| 10. Đánh giá | 5 | ReviewController | ReviewService |

Request được khai báo trong `dto/request`, gồm record cho thao tác tạo và lớp theo dõi trường được gửi cho PATCH. Response mới dùng phép chiếu với danh sách trường cố định trong service; không trả entity hoặc toàn bộ hàng SQL trực tiếp. JSON giữ nguyên hợp đồng snake_case trong [đặc tả DTO](api/03-DU-LIEU-VA-VI-DU.md).

`ShopRepository` thực hiện truy vấn JDBC có bind parameter trên schema Flyway sẵn có. JDBC tham gia cùng transaction Spring đang quản lý JPA. Các service mới dùng READ_COMMITTED để đọc dữ liệu mới sau khi chờ khóa của giao dịch khác. Không cần tạo database hoặc nhập lại dữ liệu khi nâng cấp.

## Chạy

Khởi động lại `DemoApplication` hoặc chạy `./mvnw.cmd spring-boot:run` trong thư mục `demo`. Flyway chạy migration `V4__commerce_query_indexes.sql`, chỉ thêm index. Các phiên kết nối MySQL dùng UTC để timestamp từ SQL và Java thống nhất.

Các cấu hình bổ sung:

| Biến môi trường | Mặc định | Ý nghĩa |
|---|---:|---|
| PET_TAXI_FEE | 100000 | Phí vận chuyển thú cưng theo mỗi đơn, VND |
| ORDER_EXPIRY_ENABLED | true | Chạy tác vụ xử lý đơn quá hạn |
| ORDER_EXPIRY_DELAY_MS | 60000 | Khoảng nghỉ giữa các lượt xử lý |

Nhận tại cửa hàng có phí 0. Vận chuyển thú cưng cộng `PET_TAXI_FEE` vào tổng thanh toán; có thể đổi mức phí qua cấu hình khi vận hành. Các phương thức card, bank_transfer, e_wallet chỉ ghi nhận phương thức; thanh toán và hoàn tiền vẫn do staff ghi nhận thủ công.

## Thử bằng Postman

Import [collection phần 4–10](../postman/Commerce.postman_collection.json) và [environment Local](../postman/Local.postman_environment.json). Điền `admin_email`, `admin_password`, chọn environment rồi chạy **toàn bộ 62 request theo thứ tự**. Collection tự lấy CSRF và dùng cookie jar.

Collection tự tạo category, hàng mẫu, supplier, pet, hồ sơ sức khỏe và customer riêng. Sau đó chạy giỏ → preview → tạo đơn → replay → thanh toán/giao hàng → review, và tạo thêm đơn để thử hủy lặp. Các request 404 chủ ý được kiểm tra theo kết quả mong đợi. Đơn và lịch sử tồn mẫu được giữ lại theo chính sách không xóa lịch sử; nên chạy collection trên database phát triển.

Bộ Postman này được tạo và kiểm tra cấu trúc JSON; xác minh hành vi backend thực hiện bằng kiểm thử HTTP/MySQL tự động bên dưới.

## Các quy tắc đã thực hiện

- Public chỉ đọc pet/sức khỏe của product đang mở bán. Chi tiết pet public không có supplier_id/reserved_order_id; thông tin liên hệ supplier chỉ staff/admin đọc được.
- Một product pet chỉ có một hồ sơ; không đổi product/status/reservation bằng PATCH. Xóa hồ sơ chưa từng có đơn sẽ ngừng bán product và xóa hồ sơ sức khỏe trong cùng giao dịch. Xóa supplier giữ pet với supplier_id=null.
- Giỏ lấy chủ từ session, tối đa 100 dòng, thêm POST cộng số lượng, PUT thay số lượng; không trừ tồn hoặc giữ pet. GET giỏ trống không tạo record.
- Preview tính giá từ database, kiểm tra tồn/coupon, trả issues nhưng không giữ hàng. Dòng sản phẩm ẩn không lộ tên/giá. Shipping không pickup bắt buộc địa chỉ.
- Tạo đơn khóa user, product theo ID và pet/coupon liên quan; lưu snapshot tên/giá, trừ tồn, giữ pet, cập nhật coupon, giỏ và nhật ký nguyên khối.
- Idempotency-Key được kiểm tra trước khi đọc giỏ. Hash dùng request chuẩn hóa, sắp items theo product_id; replay không trừ tồn hoặc coupon lần nữa. Khóa khác nội dung trả 409.
- Đơn chỉ đi pending → confirmed → shipping → completed. Trả trước phải paid khi xác nhận; hoàn tất luôn phải paid. Hủy pending qua tài nguyên cancellation; gọi lại trả nguyên lý do cũ và không hoàn tồn lần hai.
- Scheduler kiểm tra lại đơn dưới khóa, hủy đơn pending/unpaid quá hạn 15 phút, từng đơn trong transaction riêng. Đơn đã paid không bị tác vụ hủy; đơn cash không có hạn 15 phút.
- Ghi nhận refunded chỉ sau khi hủy đơn paid. Hủy không tự nhận rằng tiền đã được hoàn.
- Tồn kho có nhật ký before/change/after. Adjustment không áp dụng pet; cộng tồn tính cả lượng đang giữ để chừa khả năng hoàn tồn khi hủy.
- Customer đã đăng nhập có thể đánh giá sản phẩm đang bán mà không cần đơn completed. Mỗi user/product một review; sửa chỉ chủ, xóa chủ/admin. Chủ review vẫn có thể sửa đánh giá khi sản phẩm ngừng bán.

## Kiểm thử

`ApiIntegrationTests` có 19 bài kiểm thử tích hợp HTTP và MySQL, gồm 10 bài cũ và 9 bài mới. Test tạo database `petshop_test_<uuid>`, chạy Flyway rồi chỉ xóa database thử đó.

```powershell
$env:TEST_DB_PASSWORD='<mat-khau-MySQL>'
.\mvnw.cmd test
```

Các bài mới bao phủ quyền public/owner/admin, PATCH và quan hệ cha–con, giỏ/preview, snapshot đơn, replay, hủy lặp, chuyển trạng thái/thanh toán, review sau mua, tồn kho và giới hạn hoàn tồn, coupon, hai người mua cùng pet, hai request cùng Idempotency-Key và hết hạn thanh toán.

Không nằm trong phần bổ sung này: cổng thanh toán/webhook, API quản trị coupon, Swagger/OpenAPI và các chức năng sau v1 trong đặc tả. Giới hạn tần suất HTTP 429 đã được bổ sung cho đăng nhập, đăng ký và đặt đơn; xem `THYMELEAF-FRONTEND.md`.
