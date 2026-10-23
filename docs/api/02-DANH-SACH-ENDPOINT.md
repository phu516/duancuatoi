> Đã triển khai phần 1–10 (65 endpoint). Xem [hướng dẫn chức năng mới và Postman](../CHUC-NANG-4-10.md). NEW-API được giữ làm tham chiếu thiết kế ban đầu.

# 02. Danh sách endpoint

Mọi URL bên dưới có prefix **`/api/v1`**. Các tên request/response được định nghĩa trong [file 03](03-DU-LIEU-VA-VI-DU.md). Lỗi và quyền theo [file 01](01-QUY-UOC.md); quy tắc có điều kiện theo [file 04](04-QUY-TAC-NGHIEP-VU.md). `Page<T>` là response phân trang; `—` nghĩa là không có body.

## 1. Tài khoản và xác thực

| Method | URL | Chức năng | Quyền | Body / query | Thành công |
|---|---|---|---|---|---|
| GET | `/auth/csrf-token` | Khởi tạo phiên bảo mật/lấy CSRF | Public | — | 200 CsrfResponse |
| POST | `/auth/register` | Đăng ký customer | Public | RegisterRequest | 201 UserResponse |
| POST | `/auth/login` | Đăng nhập JSON và tạo phiên | Public | LoginRequest | 200 UserResponse |
| POST | `/auth/logout` | Đăng xuất phiên hiện tại | Authenticated | — | 204 |
| GET | `/users/me` | Xem hồ sơ chính mình | Authenticated | — | 200 UserResponse |
| PATCH | `/users/me` | Sửa hồ sơ chính mình | Authenticated | ProfileUpdateRequest | 200 UserResponse |
| PUT | `/users/me/password` | Đổi mật khẩu | Authenticated | ChangePasswordRequest | 204; phải đăng nhập lại |
| GET | `/users` | Liệt kê người dùng | Admin | keyword, role, is_active, page, size | 200 Page<UserResponse> |
| GET | `/users/{userId}` | Xem user cụ thể | Owner/Admin | — | 200 UserResponse |
| PATCH | `/users/{userId}/role` | Đổi vai trò | Admin | RoleUpdateRequest | 200 UserResponse |
| PATCH | `/users/{userId}/status` | Khóa hoặc mở khóa tài khoản | Admin | ActiveStatusRequest | 200 UserResponse |

Không có DELETE user. Admin không tự khóa hoặc đổi vai trò của chính mình. keyword tìm theo tên/email; role và is_active phải đúng kiểu/enum.

## 2. Danh mục

| Method | URL | Chức năng | Quyền | Body / query | Thành công |
|---|---|---|---|---|---|
| GET | `/categories` | Danh sách danh mục | Public | keyword, page, size | 200 Page<CategoryResponse> |
| GET | `/categories/{categoryId}` | Chi tiết danh mục | Public | — | 200 CategoryResponse |
| POST | `/categories` | Tạo danh mục | Admin | CategoryCreateRequest | 201 CategoryResponse |
| PATCH | `/categories/{categoryId}` | Đổi tên danh mục | Admin | CategoryUpdateRequest | 200 CategoryResponse |
| DELETE | `/categories/{categoryId}` | Xóa danh mục rỗng | Admin | — | 204 |

Còn bất kỳ sản phẩm nào thuộc danh mục, kể cả ngừng bán: 409 RESOURCE_IN_USE.

## 3. Sản phẩm

| Method | URL | Chức năng | Quyền | Body / query | Thành công |
|---|---|---|---|---|---|
| GET | `/products` | Tìm kiếm/lọc sản phẩm | Public | category_id, product_type, keyword, min_price, max_price, sort, page, size; is_active chỉ Staff | 200 Page<ProductSummaryResponse> |
| GET | `/products/{productId}` | Chi tiết và hồ sơ pet nếu có | Public | — | 200 ProductDetailResponse |
| POST | `/products` | Tạo sản phẩm | Admin | ProductCreateRequest | 201 ProductDetailResponse |
| PATCH | `/products/{productId}` | Sửa thông tin sản phẩm | Admin | ProductUpdateRequest | 200 ProductDetailResponse |
| PATCH | `/products/{productId}/status` | Ngừng/mở bán | Admin | ActiveStatusRequest | 200 ProductDetailResponse |
| DELETE | `/products/{productId}` | Xóa sản phẩm chưa có lịch sử | Admin | — | 204 |

Public/customer chỉ thấy sản phẩm is_active=true; staff mặc định cũng thấy đang bán, có thể lọc is_active=false để quản trị. Gửi is_active khi không đủ quyền: 403. Chi tiết hàng ngừng bán chỉ Staff xem được. Có đơn hoặc movement: DELETE trả 409, dùng PATCH status để ngừng bán. Không sửa stock_quantity qua PATCH sản phẩm.

## 4. Thú cưng

| Method | URL | Chức năng | Quyền | Body / query | Thành công |
|---|---|---|---|---|---|
| GET | `/pets` | Danh sách cá thể thú cưng | Public | status, breed, supplier_id, page, size | 200 Page<PetSummaryResponse> |
| GET | `/pets/{petId}` | Chi tiết thú cưng kèm sản phẩm | Public | — | 200 PetDetailResponse |
| POST | `/pets` | Tạo hồ sơ cho product loại pet | Admin | PetCreateRequest | 201 PetDetailResponse |
| PATCH | `/pets/{petId}` | Sửa đặc điểm thú cưng | Admin | PetUpdateRequest | 200 PetDetailResponse |
| DELETE | `/pets/{petId}` | Xóa hồ sơ chưa có lịch sử đơn | Admin | — | 204 |

Không có API sửa tự do status/reserved_order_id. Public chỉ thấy pet có product đang bán; staff có thể xem mọi pet. supplier_id là bộ lọc ID, không cấp quyền xem thông tin liên hệ supplier. Một product chỉ có một hồ sơ pet. Không chuyển hồ sơ pet sang product khác bằng PATCH.

## 5. Hồ sơ sức khỏe

| Method | URL | Chức năng | Quyền | Body / query | Thành công |
|---|---|---|---|---|---|
| GET | `/pets/{petId}/health-records` | Hồ sơ sức khỏe của một pet | Public | page, size | 200 Page<HealthRecordResponse> |
| GET | `/pets/{petId}/health-records/{recordId}` | Một bản ghi sức khỏe của pet | Public | — | 200 HealthRecordResponse |
| POST | `/pets/{petId}/health-records` | Thêm bản ghi sức khỏe | Admin | HealthRecordCreateRequest | 201 HealthRecordResponse |
| PATCH | `/pets/{petId}/health-records/{recordId}` | Sửa bản ghi sức khỏe | Admin | HealthRecordUpdateRequest | 200 HealthRecordResponse |
| DELETE | `/pets/{petId}/health-records/{recordId}` | Xóa bản ghi sức khỏe | Admin | — | 204 |
| GET | `/health-records` | Tra cứu toàn bộ hồ sơ | Staff | pet_id, page, size | 200 Page<HealthRecordResponse> |

pet_id lấy từ path khi ghi, không lặp trong body. recordId phải thuộc petId, không khớp trả 404. Quyền xem public tuân theo quyền xem pet; staff có thể đọc hồ sơ pet ngừng bán. Không lưu dữ liệu cá nhân của người mua trong nội dung sức khỏe công khai.

## 6. Nhà cung cấp

| Method | URL | Chức năng | Quyền | Body / query | Thành công |
|---|---|---|---|---|---|
| GET | `/suppliers` | Danh sách nhà cung cấp | Staff | keyword, page, size | 200 Page<SupplierResponse> |
| GET | `/suppliers/{supplierId}` | Chi tiết nhà cung cấp | Staff | — | 200 SupplierResponse |
| POST | `/suppliers` | Tạo nhà cung cấp | Admin | SupplierCreateRequest | 201 SupplierResponse |
| PATCH | `/suppliers/{supplierId}` | Sửa nhà cung cấp | Admin | SupplierUpdateRequest | 200 SupplierResponse |
| DELETE | `/suppliers/{supplierId}` | Xóa nhà cung cấp | Admin | — | 204 |

Theo quan hệ supplier tùy chọn: xóa supplier giữ pet, đặt supplier_id=null; không xóa thú cưng hay lịch sử sức khỏe.

## 7. Giỏ hàng và tính thử đơn

| Method | URL | Chức năng | Quyền | Body / query | Thành công |
|---|---|---|---|---|---|
| GET | `/cart` | Xem giỏ hiện tại | Authenticated | — | 200 CartResponse |
| POST | `/cart/items` | Thêm/cộng số lượng vào giỏ | Authenticated | CartItemCreateRequest | 200 CartResponse |
| PUT | `/cart/items/{productId}` | Đặt số lượng tuyệt đối; tạo dòng nếu chưa có | Authenticated | CartItemReplaceRequest | 200 CartResponse |
| DELETE | `/cart/items/{productId}` | Xóa một dòng khỏi giỏ | Authenticated | — | 204 |
| DELETE | `/cart/items` | Xóa tất cả các dòng | Authenticated | — | 204 |
| POST | `/checkout/preview` | Tính tiền và kiểm tra trước khi đặt | Authenticated | CheckoutPreviewRequest | 200 CheckoutPreviewResponse |

POST thêm 2 vào dòng có 3 thì thành 5; gửi lại POST có thể cộng thêm, không tự retry như PUT. PUT quantity=2 nhiều lần vẫn là 2. PUT trả giỏ hiện tại với 200, kể cả khi thêm mới dòng theo hợp đồng này. DELETE dòng không tồn tại trong giỏ của chính mình trả 204. Giỏ trống trả items=[] và subtotal=0; GET không bắt buộc tạo record carts.

## 8. Đơn hàng và giao hàng

| Method | URL | Chức năng | Quyền | Body / query | Thành công |
|---|---|---|---|---|---|
| POST | `/orders` | Đặt hàng; bắt buộc Idempotency-Key | Authenticated | OrderCreateRequest | 201 OrderDetailResponse; replay 200 |
| GET | `/orders` | Xem danh sách đơn | Authenticated | status, payment_status, date_from, date_to, page, size; user_id chỉ Staff | 200 Page<OrderSummaryResponse> |
| GET | `/orders/{orderId}` | Chi tiết đơn | Owner/Staff | — | 200 OrderDetailResponse |
| GET | `/orders/{orderId}/items` | Các dòng hàng đã mua | Owner/Staff | — | 200 OrderItemResponse[] |
| GET | `/orders/{orderId}/events` | Lịch sử trạng thái | Owner/Staff | page, size | 200 Page<OrderEventResponse> |
| PATCH | `/orders/{orderId}/status` | Cập nhật tiến trình đơn | Staff | OrderStatusUpdateRequest | 200 OrderDetailResponse |
| PATCH | `/orders/{orderId}/payment-status` | Ghi nhận thu/hoàn tiền thủ công | Staff | PaymentStatusUpdateRequest | 200 OrderDetailResponse |
| PUT | `/orders/{orderId}/cancellation` | Hủy đơn pending | Owner/Staff | OrderCancellationRequest | 201 CancellationResponse; lặp 200 |
| GET | `/orders/{orderId}/cancellation` | Xem thông tin hủy đã lưu | Owner/Staff | — | 200 CancellationResponse |
| GET | `/orders/{orderId}/shipping` | Xem người nhận/giao hàng | Owner/Staff | — | 200 ShippingResponse |
| PATCH | `/orders/{orderId}/shipping` | Sửa người nhận/địa chỉ/ghi chú | Owner/Staff | ShippingUpdateRequest | 200 ShippingResponse |

Customer chỉ thấy đơn của mình, không được truyền user_id để chọn người khác. Staff xem mọi đơn hoặc lọc user_id. Không có API xóa đơn, sửa item/giá/tổng của đơn đã tạo. Thông tin hủy chưa tồn tại trả 404. PATCH status không nhận cancelled; phải dùng tài nguyên cancellation để có lý do và hoàn tồn đúng giao dịch.

## 9. Tồn kho

| Method | URL | Chức năng | Quyền | Body / query | Thành công |
|---|---|---|---|---|---|
| GET | `/inventory` | Danh sách tồn khả dụng | Staff | low_stock, page, size | 200 Page<InventoryResponse> |
| GET | `/inventory/{productId}` | Tồn khả dụng một sản phẩm | Staff | — | 200 InventoryResponse |
| GET | `/inventory/{productId}/movements` | Nhật ký tồn của sản phẩm | Staff | page, size | 200 Page<InventoryMovementResponse> |
| GET | `/inventory/{productId}/movements/{movementId}` | Một biến động tồn cụ thể | Staff | — | 200 InventoryMovementResponse |
| POST | `/inventory/{productId}/adjustments` | Nhập/điều chỉnh tồn | Staff | InventoryAdjustmentRequest | 201 InventoryMovementResponse |

low_stock là ngưỡng số nguyên 0–1000000; có tham số thì lọc stock_quantity <= low_stock. adjustment không áp dụng product loại pet. movementId không thuộc productId trả 404. POST adjustment trả Location tới movement đã tạo; gọi lặp có thể điều chỉnh nhiều lần, không tự retry nếu chưa đối chiếu kết quả.

## 10. Đánh giá

| Method | URL | Chức năng | Quyền | Body / query | Thành công |
|---|---|---|---|---|---|
| GET | `/products/{productId}/reviews` | Danh sách đánh giá | Public | sort, page, size | 200 Page<ReviewResponse> |
| GET | `/products/{productId}/reviews/{reviewId}` | Một đánh giá cụ thể | Public | — | 200 ReviewResponse |
| POST | `/products/{productId}/reviews` | Đánh giá và bình luận sản phẩm đang bán | Customer đã đăng nhập | ReviewCreateRequest | 201 ReviewResponse |
| PATCH | `/products/{productId}/reviews/{reviewId}` | Sửa đánh giá của mình | Owner | ReviewUpdateRequest | 200 ReviewResponse |
| DELETE | `/products/{productId}/reviews/{reviewId}` | Xóa đánh giá | Owner/Admin | — | 204 |

Public chỉ đọc review của sản phẩm đang bán; staff có thể đọc sản phẩm ngừng bán. Khách hàng đăng nhập được tạo review cho sản phẩm đang bán mà không cần đơn đã hoàn tất. Chủ review vẫn có thể sửa review của mình khi sản phẩm ngừng bán. Admin không được sửa nội dung review của khách. reviewId phải thuộc productId. Một user chỉ có một review cho mỗi product.

## 11. Kiểm tra bao phủ yêu cầu gốc

| Nhóm trong API/api-shop-test.md | Nhóm tương ứng ở đây |
|---|---|
| 1. Tài khoản | 1. Auth/users |
| 2. Danh mục | 2. Categories |
| 3. Sản phẩm | 3. Products |
| 4. Giỏ hàng | 7. Cart/checkout |
| 5. Pet, sức khỏe, nhà cung cấp | 4, 5, 6 |
| 6. Đặt/hủy đơn | 8 và quy tắc transaction trong file 04 |
| 7. Người nhận/giao hàng | 8. Order shipping |
| 8. Tồn kho | 9. Inventory |
| 9. Đánh giá | 10. Reviews |

Không cung cấp endpoint thay đổi trạng thái pet độc lập; chức năng đó được thực hiện trong luồng đơn như yêu cầu gốc. Bổ sung một số GET chi tiết/lịch sử để tài nguyên tạo mới có URL tra cứu rõ ràng.


