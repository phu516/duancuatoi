> Cập nhật: 22 API nhóm tài khoản, danh mục và sản phẩm đã được triển khai. Xem [hướng dẫn chạy hiện tại](CHAY-VA-TEST-POSTMAN.md). Phần khảo sát/kế hoạch dưới đây ghi lại thời điểm trước triển khai; nguồn Servlet và thư mục API cũ không còn trong workspace.

# Kế hoạch viết back-end trước

## 1. Kết quả đọc dự án

| Nguồn | Điều đã xác định từ file | Ý nghĩa cho kế hoạch |
|---|---|---|
| [demo/pom.xml](../pom.xml) | Java 21, Boot 4.1.1; có starter Web MVC, Security, JPA, Validation, Flyway; chưa khai báo MySQL JDBC driver | Kiểm tra build/dependency và bổ sung driver trước khi viết CRUD |
| [Cấu hình demo](../src/main/resources/application.properties) | URL MySQL, ddl-auto mặc định update; có giá trị mặc định cho mật khẩu DB/admin | Chuyển sang env, migration có phiên bản và schema validation khi triển khai |
| [Mã nguồn demo](../src/main/java/com/example/demo/DemoApplication.java) | Có entry point; sáu package nghiệp vụ đang trống | Đây là back-end đích, chưa có API nghiệp vụ |
| Controller Servlet (nguồn Servlet đã đọc trước khi thư mục được gỡ khỏi workspace) và web.xml (nguồn Servlet đã đọc trước khi thư mục được gỡ khỏi workspace) | Bản cũ dùng Servlet, /api/*, GET/POST và action/thao tác theo path | Port quy tắc nghiệp vụ sang Spring, không chép nguyên route cũ |
| Checkout cũ (nguồn Servlet đã đọc trước khi thư mục được gỡ khỏi workspace) | Preview gọi expireReservations và tính giá qua JDBC | Preview mới chỉ tính/kiểm tra, tác vụ hết hạn tách sang scheduler |
| Migration cũ (nguồn Servlet đã đọc trước khi thư mục được gỡ khỏi workspace) và runner (nguồn Servlet đã đọc trước khi thư mục được gỡ khỏi workspace) | SQL bổ sung trên bảng có sẵn; có câu ADD COLUMN/CONSTRAINT lặp; runner tự bỏ qua phần đã có | Không đưa nguyên file này vào Flyway V1 cho DB rỗng |
| Test tích hợp cũ (nguồn Servlet đã đọc trước khi thư mục được gỡ khỏi workspace) | Có kịch bản session, quyền, tranh mua pet, replay, rollback, expiry, review | Dùng làm danh sách tình huống để viết test Spring; không coi test cũ chứng minh demo đúng |
| [NEW-API](../../NEW-API/README.md) | Đặc tả REST v1 đã có quy ước, endpoint, DTO, nghiệp vụ | Kế thừa trong docs/api; chọn lại cấu trúc package theo ảnh |

Khảo sát này dựa trên source và tài liệu, chưa truy cập database thực, chưa chạy build hoặc test. Phạm vi lần này là tài liệu API và kế hoạch/cấu trúc; các checkbox dưới đây dành cho lần triển khai code.

## 2. Quyết định thiết kế cho demo

- Một ứng dụng Spring Boot, package `com.example.demo`, chia theo tầng như [cây cấu trúc](CAU-TRUC-BACKEND.md).
- API `/api/v1`, JSON snake_case, phân trang bắt đầu từ 0; trả DTO trực tiếp cho chi tiết và PageResponse cho danh sách.
- Session/Cookie + CSRF theo đặc tả sẵn có; đăng nhập bằng email/password. Không tự chuyển sang JWT.
- Giữ v1 của cửa hàng: mỗi pet là một cá thể, đơn trả trước giữ 15 phút, cash không có hạn này; thanh toán/hoàn tiền ghi nhận thủ công.
- Backend có đủ endpoint và kiểm thử để frontend gọi sau. Không chuyển JSP cũ sang demo trong giai đoạn API.
- Giữ bộ docs/api của demo làm hợp đồng thực hiện; khi đổi method, DTO, quyền hoặc nghiệp vụ phải cập nhật tài liệu và test tương ứng.

## 3. Thứ tự triển khai và điều kiện hoàn thành

| Giai đoạn | Các phần sẽ viết | Kết quả cần đạt trước khi chuyển bước |
|---|---|---|
| 1. Khởi động và DB | Rà dependency tương thích với pom thực tế; driver MySQL và hỗ trợ Flyway/MySQL; cấu hình môi trường; migration; entity/repository nền | Build được với Java 21; DB mới tạo đủ schema; ứng dụng dùng migration và validate schema; test dùng DB riêng |
| 2. Quy ước HTTP | Request/response DTO chung, mapper, JsonConfig, GlobalExceptionHandler, trace ID, giới hạn body và query | JSON snake_case; lỗi 400/404/405/413/415/500 thống nhất; không lộ SQL; phân biệt PATCH missing/null |
| 3. Auth và user | SecurityConfig, principal, login JSON, CSRF, session_version; AuthController/UserController; rate limit | Đăng ký customer, login/logout/me, sửa hồ sơ/đổi password, admin đổi role/khóa; 401/403 JSON; phiên cũ mất hiệu lực |
| 4. Catalog | Category, Supplier, Product, Pet, HealthRecord cùng DTO/service/repository/controller | Tạo pet qua product nháp → hồ sơ → mở bán; public không đọc hàng ẩn; xóa theo ràng buộc |
| 5. Kho | InventoryService/Controller, movement, khóa product; nối tạo product với movement ban đầu | Điều chỉnh không âm/vượt giới hạn; có lịch sử; không adjustment pet; không chỉnh stock qua sửa product |
| 6. Giỏ và preview | Cart, CartItem; CartService, PricingService, CouponService, CheckoutService | Giỏ giữ sau logout; POST cộng/PUT thay; preview có issues và không thay dữ liệu nghiệp vụ; tổng tiền do server tính |
| 7. Đặt đơn và replay | Order/OrderItem/ShippingInfo/OrderEvent, OrderService, IdempotencyService | Một transaction; snapshot đúng; khóa trùng không tạo thêm đơn; tranh mua pet chỉ một người thành công |
| 8. Hủy và xử lý đơn | Cancellation, chuyển status/payment, ShippingService, OrderExpiryService, scheduler | Hoàn tồn một lần; không hủy đơn đã confirmed; paid không bị quét hết hạn; completed mới sold; mỗi đơn hết hạn xử lý độc lập |
| 9. Review | ReviewController/Service/Repository và DTO | Customer đăng nhập được đánh giá sản phẩm đang bán; unique user/product; đúng quyền sửa/xóa |
| 10. Bàn giao API | Kiểm thử HTTP/MySQL, tài liệu OpenAPI/Swagger, hướng dẫn chạy/cấu hình, dữ liệu dev tùy chọn | Toàn bộ endpoint trong docs/api có implementation và test tương ứng; frontend có thể nối API theo tài liệu |

Kiểm thử được viết cùng từng giai đoạn; giai đoạn 10 đối chiếu toàn bộ luồng. Chưa ấn định số ngày vì khả năng build, cấu trúc DB thực và môi trường MySQL/Docker chưa được kiểm tra.

## 4. Kế hoạch database

Với **DB mới**, viết migration tạo đủ 15 bảng nghiệp vụ trong [mô hình dữ liệu](CAU-TRUC-BACKEND.md), sau đó thêm index/ràng buộc theo phiên bản. Khi có quan hệ vòng giữa pet và order, tạo bảng trước rồi thêm FK khi các bảng tham chiếu đã tồn tại. Bổ sung unique/FK/CHECK trước khi kiểm thử concurrency. Seed chỉ chạy chủ ý ở dev.

Với **DB cũ cần giữ dữ liệu**, trước hết đọc schema thực tế và lịch sử migration. Lập danh sách chênh lệch với schema đích, đối chiếu pet đang giữ, đơn mở, các snapshot thiếu và bản ghi trùng trước khi thêm unique. Thử baseline/migration nâng cấp trên bản sao và kiểm tra số lượng/quan hệ dữ liệu. Chỉ chọn baseline version sau khi chứng minh DB đã tương ứng với version đó; không bật baseline tự động để bỏ qua schema chưa rõ. Không chạy migration tạo DB mới lên các bảng cũ có dữ liệu.

Đây là kế hoạch có hai tình huống, không phải tuyên bố DB cũ đã được chuyển. File V1 Servlet phụ thuộc các bảng gốc và cơ chế bỏ qua lệnh lặp của SchemaMigration nên không dùng nguyên trạng cho Flyway.

## 5. Các khác biệt phải xử lý khi chuyển bản cũ

| Bản cũ/tham chiếu | Hợp đồng demo dự kiến |
|---|---|
| /api/* và POST kèm action | /api/v1/*; GET, POST, PATCH, PUT, DELETE đúng danh sách endpoint |
| idempotency_key trong body | Idempotency-Key trong header |
| POST /orders/{id}/cancel | PUT /orders/{orderId}/cancellation; có GET xem lại |
| POST status có thể nhận cancelled | PATCH status chỉ confirmed/shipping/completed; hủy dùng cancellation |
| Preview gọi quét đơn hết hạn | Preview không thay nghiệp vụ; scheduler xử lý hết hạn |
| Validation 422 trong một số tài liệu cũ | Validation 400, lỗi nghiệp vụ 409, cấu trúc error thống nhất |
| Hướng dẫn NEW-API chia theo module | Demo giữ controller/dto/entity/exception/repository/service theo ảnh |

Không mặc định frontend JSP cũ tương thích các thay đổi trên. Khi nối frontend sau này cần cập nhật URL, method, header CSRF/idempotency và shape response.

## 6. Kiểm thử nghiệm thu

- [ ] HTTP: mỗi route/method đúng DTO, role, status, Location; API chưa đăng nhập trả JSON; dữ liệu sai/trường thừa bị từ chối.
- [ ] Session: login đổi session; token CSRF mới sau login/logout; đổi mật khẩu/role/khóa thu hồi phiên; customer không truy cập đơn của người khác.
- [ ] Catalog: pet thiếu hồ sơ không mở bán; sản phẩm có lịch sử không bị xóa; public không lộ dữ liệu nội bộ.
- [ ] Giá và coupon: tính bằng BigDecimal, kiểm tra precision từng thành phần; preview không tiêu thụ coupon; tranh lượt cuối chỉ một đơn dùng được.
- [ ] Concurrency: hai khách đặt cùng pet chỉ một thành công; hai request cùng khóa chỉ một đơn; lỗi một dòng rollback cả đơn/tồn/movement/coupon/giỏ.
- [ ] Replay: thử lại sau mất response, giỏ đã rỗng hoặc đơn đã hủy vẫn trả cùng đơn; cùng khóa khác nội dung trả 409.
- [ ] Lifecycle: hủy lặp không hoàn tồn hai lần; scheduler cạnh tranh paid/cancel vẫn đúng; cash không tự hết hạn; paid vẫn reserved và completed mới sold.
- [ ] Shipping/review: thông tin nhận là snapshot; chỉ sửa shipping pending/confirmed; customer đăng nhập được review sản phẩm đang bán; hai review đồng thời bị unique chặn.
- [ ] Migration: DB mới và bản sao DB cũ nếu chuyển dữ liệu; xác minh FK, unique, stock/pet/order sau nâng cấp.

Unit test cho giá và chuyển trạng thái; HTTP/security test cho session, quyền và hợp đồng JSON; integration test trên MySQL riêng cho migration, khóa và rollback. Không dùng kết quả H2 để kết luận khóa/FK MySQL đúng. Trước mắt không chạy các test Servlet cũ trên cấu hình DB chưa được xác định.

## 7. Thế nào là xong phần back-end?

Back-end hoàn tất khi các lớp trong cấu trúc được triển khai theo nhu cầu thực tế, mọi endpoint có quyền/validation/lỗi đúng hợp đồng, test cần thiết đạt, có hướng dẫn khởi động từ DB rỗng và bộ API có thể tra cứu/thử. Chức năng ngoài v1 trong file nghiệp vụ được ghi riêng; không tính là API đã làm.

Bước code đầu tiên theo kế hoạch này là hoàn thiện Maven/datasource/migration và cấu trúc lỗi; sau đó triển khai Auth/User rồi Catalog. Đây là kế hoạch để xem trước cấu trúc hoàn chỉnh, chưa phải báo cáo đã viết xong back-end.

