# Mô tả API theo cấu trúc back-end

**Đã triển khai 65 endpoint thuộc phần 1–10.** Xem [chức năng 4–10](CHUC-NANG-4-10.md) để biết cấu trúc service/repository thực tế và phạm vi kiểm thử. Base URL: `http://localhost:8080/api/v1`. Bảng kiến trúc bên dưới được giữ làm tham chiếu thiết kế.

Bộ mô tả chi tiết được đặt ngay trong back-end:

- [Method, URL, quyền, request và response của từng endpoint](api/02-DANH-SACH-ENDPOINT.md).
- [Các trường DTO, validation và ví dụ JSON](api/03-DU-LIEU-VA-VI-DU.md).
- [HTTP, lỗi, session/CSRF và phân trang](api/01-QUY-UOC.md).
- [Điều kiện nghiệp vụ và transaction](api/04-QUY-TAC-NGHIEP-VU.md).

## 1. API nào sẽ nằm ở lớp nào?

| Controller dự kiến | Nhóm path | Chức năng | Service chính | Entity/repository liên quan |
|---|---|---|---|---|
| AuthController | /auth/csrf-token, /auth/register, /auth/login, /auth/logout | Lấy CSRF; đăng ký, đăng nhập, đăng xuất | AuthService | User |
| UserController | /users, /users/me, /users/me/password, /users/{userId}, /users/{userId}/role, /users/{userId}/status | Hồ sơ, mật khẩu, danh sách và quản lý quyền/khóa | UserService | User |
| CategoryController | /categories, /categories/{categoryId} | Danh sách, chi tiết, tạo, sửa, xóa danh mục rỗng | CategoryService | Category, Product |
| ProductController | /products, /products/{productId}, /products/{productId}/status | Tìm kiếm/lọc/sắp xếp, CRUD và mở/ngừng bán | ProductService | Product, Category, Pet, Review, InventoryMovement |
| PetController | /pets, /pets/{petId} | Hồ sơ từng cá thể thú cưng | PetService | Pet, Product, Supplier |
| HealthRecordController | /pets/{petId}/health-records, /pets/{petId}/health-records/{recordId}, /health-records | Hồ sơ sức khỏe theo pet và tra cứu cho staff | HealthRecordService | HealthRecord, Pet |
| SupplierController | /suppliers, /suppliers/{supplierId} | CRUD nhà cung cấp | SupplierService | Supplier, Pet |
| CartController | /cart, /cart/items, /cart/items/{productId} | Xem, thêm/cộng, thay số lượng, xóa dòng/giỏ | CartService | Cart, CartItem, Product, Pet |
| CheckoutController | /checkout/preview | Tính thử tiền và cảnh báo trước đặt | CheckoutService, PricingService, CouponService | Product, Pet, Cart, Coupon; không tạo Order |
| OrderController | /orders, /orders/{orderId}, /orders/{orderId}/items, /events, /status, /payment-status, /cancellation | Tạo/xem đơn, lịch sử, xử lý trạng thái/thanh toán/hủy | OrderService, IdempotencyService | Order, OrderItem, OrderEvent; phối hợp kho/pet/giỏ/coupon |
| ShippingController | /orders/{orderId}/shipping | Xem/sửa thông tin người nhận của đơn | ShippingService | ShippingInfo, Order, OrderEvent |
| InventoryController | /inventory, /inventory/{productId}, /inventory/{productId}/movements, /movements/{movementId}, /adjustments | Xem tồn, lịch sử, điều chỉnh tồn accessory/food | InventoryService | Product, InventoryMovement |
| ReviewController | /products/{productId}/reviews, /products/{productId}/reviews/{reviewId} | Xem, tạo, sửa, xóa đánh giá | ReviewService | Review, Product, Order, OrderItem |

Trong dòng OrderController, `/events`, `/status`, `/payment-status`, `/cancellation` đều nối sau `/orders/{orderId}`. Trong dòng InventoryController, `/movements/{movementId}` và `/adjustments` nối sau `/inventory/{productId}`. Controller/service là cách tổ chức dự kiến; endpoint đầy đủ ở file 02 mới là hợp đồng với client.

Không tạo CouponController, PaymentGatewayController hoặc API đổi trạng thái pet tự do trong v1. Trạng thái pet thay đổi trong đặt/hủy/hoàn tất đơn. Logout có thể do filter Spring Security xử lý cùng hợp đồng HTTP; không tạo mapping trùng giữa filter và controller.

## 2. Quyền và định dạng dùng chung

| Quyền | Ý nghĩa |
|---|---|
| Public | Khách chưa đăng nhập có thể đọc; đăng ký/login vẫn cần CSRF |
| Authenticated | User đang hoạt động có phiên hợp lệ |
| Owner | Chủ tài nguyên, xác định từ phiên và dữ liệu DB |
| Staff | staff hoặc admin |
| Admin | Chỉ admin |

Chi tiết JSON trả DTO trực tiếp; danh sách dùng `data` và `pagination`. JSON dùng snake_case, enum lowercase; page mặc định 0, size mặc định 20 và tối đa 100. Lỗi trả `error.code`, `error.message`, `error.field_errors`, `error.trace_id`. Mã chính: 400 sai đầu vào, 401 chưa xác thực, 403 thiếu quyền/CSRF, 404 không thấy tài nguyên, 409 xung đột nghiệp vụ. 204 không có body.

Mỗi API ghi cần cookie phiên và `X-CSRF-TOKEN`; cả login/register cũng lấy CSRF trước. POST /orders thêm `Idempotency-Key`. Client không gửi giá, tổng tiền hoặc user_id để quyết định chủ đơn/giỏ/review.

## 3. Ví dụ mô tả một API khi viết controller

### POST /api/v1/orders — đặt đơn

**Lớp:** OrderController → OrderService. **Quyền:** Authenticated. **Body:** OrderCreateRequest. **Header:** Content-Type application/json, X-CSRF-TOKEN, Idempotency-Key; gửi kèm cookie phiên. Khóa chống trùng dài 8–100 ký tự chữ/số/gạch ngang/gạch dưới.

```json
{
  "items": [{ "product_id": 101, "quantity": 2 }],
  "payment_method": "cash",
  "shipping": {
    "recipient_name": "Nguyễn Minh An",
    "recipient_phone": "0901234567",
    "delivery_method": "pickup",
    "address": null,
    "note": "Nhận tại cửa hàng"
  },
  "coupon_code": null
}
```

Ví dụ trên chỉ dùng product accessory/food có tồn ít nhất 2. Với product pet, quantity bắt buộc 1. Bỏ `items` để lấy giỏ hiện tại; `items=[]` hoặc `items=null` là lỗi 400.

| Kết quả | Ý nghĩa |
|---|---|
| 201 + OrderDetailResponse + Location | Đơn mới được tạo, tồn/giỏ/pet/lịch sử commit cùng giao dịch |
| 200 + OrderDetailResponse + Idempotency-Replayed: true | Cùng user/khóa/nội dung, trả lại đơn đã có |
| 400 VALIDATION_ERROR | Thiếu/sai trường, khóa sai định dạng hoặc giá trị vượt giới hạn |
| 401 / 403 | Phiên không hợp lệ hoặc CSRF/quyền không hợp lệ |
| 409 OUT_OF_STOCK / PET_UNAVAILABLE | Hàng không còn khả dụng; không tạo đơn một phần |
| 409 IDEMPOTENCY_CONFLICT | Dùng lại khóa cũ cho nội dung khác |
| 409 COUPON_INVALID | Coupon không thể dùng khi đặt đơn |

OrderDetailResponse gồm id, user_id, order_date, status, total_amount, payment_method, payment_status, reserved_until, subtotal, shipping_fee, discount_amount, coupon_code, currency, items, shipping và cancellation. Định nghĩa từng trường và response mẫu ở file 03. Response replay phản ánh đơn đã có, kể cả trạng thái đã hủy, không tạo đơn mới.

Khi viết JavaDoc/OpenAPI cho mỗi method, ghi rõ chức năng, quyền, DTO, status thành công, lỗi nghiệp vụ và tác động dữ liệu tương tự ví dụ này. Không coi annotation mô tả là thay thế kiểm tra quyền hoặc transaction.

## 4. Luồng kiểm tra API trước khi làm frontend

1. GET /auth/csrf-token để lấy cookie và CSRF.
2. POST /auth/register tạo customer; POST /auth/login xác thực; lấy CSRF mới sau login.
3. GET /categories và /products để chọn sản phẩm đã được admin tạo/mở bán.
4. POST /cart/items; GET /cart để xem giá/tồn hiện tại. Giỏ chưa giữ hàng.
5. POST /checkout/preview với shipping, bỏ items để dùng giỏ; đọc can_place_order và issues.
6. POST /orders với shipping/payment_method và Idempotency-Key mới cho ý định mua. Backend kiểm tra lại tồn và giá.
7. GET /orders/{orderId}; khách chỉ thấy đơn của mình. Staff dùng phiên riêng để ghi nhận paid và chuyển trạng thái hợp lệ.
8. Khách hàng đã đăng nhập có thể POST /products/{productId}/reviews cho sản phẩm đang bán, kể cả trước khi mua. Nếu đơn còn pending và muốn hủy, dùng PUT /orders/{orderId}/cancellation với reason.

Luồng đơn: pending → confirmed → shipping → completed; chỉ pending được hủy. Đơn trả trước phải paid trước confirmed; mọi đơn phải paid trước completed. Hủy đơn paid không tự chuyển tiền hoàn trả.

## 5. Cách giữ mô tả API khớp code

Mỗi endpoint trong file 02 phải có method controller hoặc security handler tương ứng, DTO theo file 03, rule theo file 04 và test hợp đồng/quyền phù hợp. Khi bổ sung OpenAPI, đối chiếu từng route với bảng endpoint và JSON thực tế; không chỉ sinh tài liệu rồi mặc định là đúng. Bộ tài liệu hiện tại là Markdown để triển khai, chưa phải collection hay OpenAPI có thể import tự động.
