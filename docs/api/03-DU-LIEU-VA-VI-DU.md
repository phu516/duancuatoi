> Hợp đồng toàn bộ back-end: hiện đã triển khai nhóm 1–3 (22 API tài khoản, danh mục, sản phẩm); các nhóm còn lại là kế hoạch. Xem [trạng thái và hướng dẫn chạy](../CHAY-VA-TEST-POSTMAN.md). Kế thừa từ [NEW-API/03-DU-LIEU-VA-VI-DU.md](../../../NEW-API/03-DU-LIEU-VA-VI-DU.md) ngày 29/09/2026. Khi triển khai `demo`, cập nhật đồng bộ bộ tài liệu trong `demo/docs/api`; bản NEW-API được giữ làm tham chiếu thiết kế ban đầu.

# 03. Dữ liệu request, response và ví dụ

Tất cả ví dụ là dữ liệu minh họa cho backend sẽ triển khai. Tên kiểu DTO dưới đây để dùng thống nhất khi viết Java; không bắt buộc tên entity/database giống DTO.

## 1. Quy ước kiểu và validation

- `id`: số nguyên dương; tham chiếu không tồn tại trả 404, tham chiếu sai loại/trạng thái trả 409.
- `string(n)`: chuỗi tối đa n ký tự; trường bắt buộc không được blank. Trim chuỗi thông thường, không trim mật khẩu.
- `money`: number thập phân không âm, tối đa 2 chữ số thập phân, không vượt 9.999.999.999,99; price phải > 0.
- `quantity`: số nguyên 1–1000000; tồn khả dụng 0–1000000. Đơn và giỏ tối đa 100 sản phẩm khác nhau.
- `phone`: chuỗi tối đa 20; v1 chấp nhận định dạng `(0|+84)` rồi 9–10 chữ số, chuẩn hóa khoảng trắng/dấu gạch nối trước kiểm tra.
- `password`: 8–72 byte UTF-8 và ít nhất 8 ký tự. Giới hạn byte áp dụng khi dùng BCrypt; không âm thầm cắt chuỗi. Đây là lựa chọn mới cho v1.
- PATCH: mọi trường trong DTO update đều có thể bỏ qua; phải gửi ít nhất một trường. Null chỉ dùng xóa giá trị trường nullable. Giá trị thiếu và null phải được phân biệt trong implementation.
- Boolean phải là true/false; số phải là JSON number. Trường readonly/không khai báo bị từ chối.

Enum:

| Trường | Giá trị |
|---|---|
| role | customer, staff, admin |
| product_type | pet, accessory, food |
| gender | male, female; nullable |
| pet status | available, reserved, sold |
| order status | pending, confirmed, shipping, completed, cancelled |
| payment_method | cash, bank_transfer, card, e_wallet |
| payment_status | unpaid, paid, refunded |
| delivery_method | pickup, pet_taxi |

## 2. DTO tài khoản

| DTO | Trường |
|---|---|
| RegisterRequest | Bắt buộc: full_name string(100), email string(100) đúng định dạng, password, password_confirmation giống password, phone, address string(255). Không nhận role/is_active |
| LoginRequest | Bắt buộc: email, password. Đăng nhập không áp lại quy tắc độ dài mật khẩu mới; kiểm tra không rỗng và giới hạn đầu vào hợp lý, rồi xác thực hash |
| ProfileUpdateRequest | full_name, phone, address theo giới hạn khi đăng ký; không nhận email, role hoặc password |
| ChangePasswordRequest | Bắt buộc: old_password, new_password, password_confirmation; hai trường mới phải khớp và khác mật khẩu cũ |
| RoleUpdateRequest | Bắt buộc: role |
| ActiveStatusRequest | Bắt buộc: is_active boolean |
| CsrfResponse | csrf_token string, header_name string luôn là X-CSRF-TOKEN |
| UserResponse | id, full_name, email, phone, address, role, is_active, created_at; phone/address có thể null với dữ liệu cũ |

Email được trim và chuyển lowercase trước lưu/tìm kiếm; unique theo email chuẩn hóa. Đăng ký trùng: 409 EMAIL_ALREADY_EXISTS. Login sai email/password hoặc tài khoản bị khóa dùng cùng thông báo 401 INVALID_CREDENTIALS, không tiết lộ tài khoản nào tồn tại.

Đăng ký:

```json
{
  "full_name": "Nguyễn Minh An",
  "email": "an@example.test",
  "password": "PetShopDemo123!",
  "password_confirmation": "PetShopDemo123!",
  "phone": "0901234567",
  "address": "12 Đường Hoa, Thành phố Hồ Chí Minh"
}
```

Response 201; Location: `/api/v1/users/12`:

```json
{
  "id": 12,
  "full_name": "Nguyễn Minh An",
  "email": "an@example.test",
  "phone": "0901234567",
  "address": "12 Đường Hoa, Thành phố Hồ Chí Minh",
  "role": "customer",
  "is_active": true,
  "created_at": "2026-09-28T12:00:00Z"
}
```

Sau đăng ký, client gọi login với email/password. Response login là UserResponse trực tiếp, cookie do server gửi qua Set-Cookie; không trả mật khẩu hoặc token Bearer giả lập.

## 3. DTO danh mục và sản phẩm

| DTO | Trường |
|---|---|
| CategoryCreateRequest | Bắt buộc: name string(100) |
| CategoryUpdateRequest | name string(100) |
| CategoryResponse | id, name, created_at |
| ProductCreateRequest | Bắt buộc: category_id, product_type, name string(150), price. Tùy chọn: description string(10000) nullable, stock_quantity (mặc định 0 cho accessory/food; pet bắt buộc 1), image_url string(1000) nullable |
| ProductUpdateRequest | category_id, name, description, price, image_url. Không nhận product_type, stock_quantity, is_active |
| ProductSummaryResponse | id, category_id, product_type, name, price, stock_quantity, image_url, is_active, created_at |
| ProductDetailResponse | Các trường summary + description, pet (PetProfileResponse hoặc null), review_summary {count, average} |

image_url khi có giá trị phải là URL tuyệt đối http/https; backend không tải URL đó để tạo sản phẩm. Không hỗ trợ data/javascript URL. min_price <= max_price nếu có cả hai; bộ lọc sai trả 400. Chưa có endpoint upload file.

Sản phẩm accessory/food mới mặc định active; sản phẩm pet mới mặc định inactive cho tới khi có hồ sơ pet và được admin mở bán. Đây là lựa chọn của v1 để tránh hiển thị thú cưng chưa có hồ sơ.

Ví dụ tạo phụ kiện, `POST /products`:

```json
{
  "category_id": 3,
  "product_type": "accessory",
  "name": "Dây dắt chó màu xanh",
  "description": "Dây dắt dài 1,5 m",
  "price": 120000.00,
  "stock_quantity": 30,
  "image_url": "https://example.test/images/day-dat-xanh.jpg"
}
```

Sửa giá, `PATCH /products/101`:

```json
{ "price": 115000.00 }
```

Ngừng bán, `PATCH /products/101/status`:

```json
{ "is_active": false }
```

## 4. DTO thú cưng, sức khỏe và nhà cung cấp

| DTO | Trường |
|---|---|
| PetCreateRequest | Bắt buộc: product_id. Tùy chọn: supplier_id nullable, breed string(100) nullable, age string(50) nullable, gender nullable, color string(50) nullable, weight number > 0 và <= 9999.99 nullable, is_neutered boolean mặc định false, vaccination_status string(255) nullable |
| PetUpdateRequest | supplier_id, breed, age, gender, color, weight, is_neutered, vaccination_status; không nhận product_id/status/reserved_order_id/reserved_until |
| PetProfileResponse | id, product_id, breed, age, gender, color, weight, is_neutered, vaccination_status, status, created_at |
| PetSummaryResponse | PetProfileResponse + product {id, name, price, image_url, is_active} |
| PetDetailResponse | PetProfileResponse + product là ProductSummaryResponse; Staff có thêm supplier_id nullable |
| HealthRecordCreateRequest | Bắt buộc: record_date ngày hợp lệ không ở tương lai, record_type string(100). Tùy chọn: description string(10000) nullable, vet_name string(100) nullable |
| HealthRecordUpdateRequest | record_date, record_type, description, vet_name; không nhận pet_id |
| HealthRecordResponse | id, pet_id, record_date, record_type, description, vet_name, created_at |
| SupplierCreateRequest | Bắt buộc: name string(150). Tùy chọn: contact_info string(255) nullable, address string(255) nullable |
| SupplierUpdateRequest | name, contact_info, address |
| SupplierResponse | id, name, contact_info, address, created_at |

ProductDetail.pet chỉ chứa PetProfileResponse để tránh nhúng lặp product → pet → product vô hạn. Public response không chứa reserved_order_id. Khi gửi supplier_id để tạo/sửa pet phải kiểm tra supplier tồn tại.

Ví dụ `POST /pets` sau khi tạo product 201 loại pet:

```json
{
  "product_id": 201,
  "supplier_id": 2,
  "breed": "Poodle",
  "age": "3 tháng",
  "gender": "female",
  "color": "Nâu",
  "weight": 2.50,
  "is_neutered": false,
  "vaccination_status": "Đã tiêm mũi đầu"
}
```

Sau đó mở bán bằng PATCH /products/201/status. Giữ age text để tương thích phạm vi dữ liệu gốc; ngày sinh/ngày sinh ước lượng là cải tiến về sau.

Ví dụ `POST /pets/21/health-records`:

```json
{
  "record_date": "2026-09-28",
  "record_type": "vaccination",
  "description": "Ghi nhận tiêm phòng theo hồ sơ của cơ sở thú y",
  "vet_name": "Bác sĩ phụ trách"
}
```

record_type là chuỗi trong v1, chưa giới hạn enum; không dùng trường này để lập lịch điều trị tương lai.

## 5. DTO giỏ hàng và giao hàng

| DTO | Trường |
|---|---|
| CartItemCreateRequest | Bắt buộc: product_id, quantity |
| CartItemReplaceRequest | Bắt buộc: quantity; productId nằm ở path |
| CartItemResponse | product_id, name, product_type, image_url, quantity, unit_price, line_total, stock_quantity, available, availability_code nullable |
| CartResponse | items là CartItemResponse[], subtotal, currency="VND" |
| ShippingCreateRequest | Bắt buộc: recipient_name string(100), recipient_phone, delivery_method. address string(255) bắt buộc trừ pickup, pickup cho null; note string(255) nullable |
| ShippingUpdateRequest | recipient_name, recipient_phone, address, note. Không nhận delivery_method vì thay đổi có thể ảnh hưởng phí |
| ShippingResponse | order_id + toàn bộ trường ShippingCreateRequest; dữ liệu lịch sử chưa có người nhận có thể null |

Line_total = unit_price × quantity, subtotal giỏ chỉ là giá hàng hiện tại, chưa có phí/giảm giá. Dòng hết hàng vẫn ở trong giỏ với available=false; thêm/sửa số lượng không hợp lệ bị 409. Đổi giỏ không giữ hàng và không giảm tồn.

Ví dụ `POST /cart/items`:

```json
{ "product_id": 101, "quantity": 2 }
```

Ví dụ `PUT /cart/items/101`:

```json
{ "quantity": 3 }
```

## 6. DTO preview và tạo đơn

| DTO | Trường |
|---|---|
| OrderLineRequest | product_id, quantity bắt buộc; không nhận unit_price/name |
| CheckoutPreviewRequest | shipping là ShippingCreateRequest bắt buộc; items OrderLineRequest[] tùy chọn; coupon_code string(50) nullable |
| OrderCreateRequest | Toàn bộ CheckoutPreviewRequest + payment_method bắt buộc. Khóa chống trùng nằm ở header, không có idempotency_key trong body |
| CheckoutLineResponse | product_id, name, product_type, quantity, unit_price, line_total, available; giá/name có thể null nếu sản phẩm không còn được bán/không được phép xem |
| CheckoutIssueResponse | code, product_id nullable, message |
| CheckoutPreviewResponse | can_place_order, items CheckoutLineResponse[], subtotal, shipping_fee, discount_amount, total_amount, coupon_code nullable, currency, issues CheckoutIssueResponse[] |

Nếu có items: 1–100 dòng, không lặp product_id. Nếu bỏ items: đọc giỏ hiện tại. items=[] hoặc items=null là 400, không được hiểu ngầm là dùng giỏ. coupon_code bỏ qua/null/blank được chuẩn hóa null.

Preview không tạo đơn/giữ hàng/trừ coupon. Hết tồn, pet không sẵn sàng hoặc coupon không hợp lệ là kết quả nghiệp vụ: 200 với can_place_order=false và issues. JSON/quantity/địa chỉ sai vẫn trả 400. Không tìm thấy hoặc không được xem product: chỉ trả issue PRODUCT_UNAVAILABLE, không tiết lộ hàng ẩn; name/price/line_total của dòng đó null. Khi không tính được toàn bộ giá hoặc coupon không hợp lệ, subtotal/shipping_fee/discount_amount/total_amount đều null. Khi chỉ thiếu tồn nhưng giá vẫn hợp lệ, vẫn trả đủ giá và can_place_order=false. Giỏ trống khi dùng giỏ trả 400 EMPTY_CART. Vượt giới hạn giá trị lưu trữ trả 400 VALIDATION_ERROR.

Đặt đơn luôn kiểm tra lại các điều kiện, không tin preview trước đó. Với request đặt đơn có lỗi nghiệp vụ trả 409, không tạo một đơn chứa items lỗi.

Ví dụ `POST /checkout/preview`:

```json
{
  "items": [ { "product_id": 101, "quantity": 2 } ],
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

Response 200, giả sử sản phẩm có giá 120.000 và đủ tồn:

```json
{
  "can_place_order": true,
  "items": [
    {
      "product_id": 101,
      "name": "Dây dắt chó màu xanh",
      "product_type": "accessory",
      "quantity": 2,
      "unit_price": 120000.00,
      "line_total": 240000.00,
      "available": true
    }
  ],
  "subtotal": 240000.00,
  "shipping_fee": 0.00,
  "discount_amount": 0.00,
  "total_amount": 240000.00,
  "coupon_code": null,
  "currency": "VND",
  "issues": []
}
```

Để tạo đơn: gửi lại body trên, thêm `"payment_method":"cash"`, tới POST /orders; header `Idempotency-Key: order-demo-0001` cùng cookie/CSRF. Không thêm total_amount do client tính.

## 7. DTO đơn hàng và lịch sử

| DTO | Trường |
|---|---|
| OrderSummaryResponse | id, user_id, order_date, status, total_amount, payment_method, payment_status, reserved_until nullable |
| OrderItemResponse | id, product_id, product_name, quantity, unit_price, line_total |
| OrderDetailResponse | OrderSummaryResponse + subtotal, shipping_fee, discount_amount, coupon_code nullable, currency, items OrderItemResponse[], shipping ShippingResponse, cancellation CancellationResponse nullable |
| OrderStatusUpdateRequest | Bắt buộc: status; chỉ confirmed/shipping/completed. pending là trạng thái server tạo; cancelled dùng cancellation |
| PaymentStatusUpdateRequest | Bắt buộc: payment_status (paid/refunded); note string(255) tùy chọn, chỉ ghi khi cần giải thích thêm |
| OrderCancellationRequest | Bắt buộc: reason string(255) |
| CancellationResponse | order_id, cancelled_at, cancelled_by (user ID hoặc null nếu hệ thống), reason |
| OrderEventResponse | id, order_id, event_type (status/payment/shipping), old_value nullable, new_value, note nullable, created_at |

OrderEventResponse chỉ trả nội dung lịch sử được phép cho người mua xem; không chứa tài khoản nhận tiền, thông tin riêng của nhân viên hoặc ghi chú quản trị nhạy cảm. Note ở endpoint ghi nhận thanh toán được xem là nội dung có thể xuất hiện cho chủ đơn.

Response tạo đơn ví dụ, Location: `/api/v1/orders/501`:

```json
{
  "id": 501,
  "user_id": 12,
  "order_date": "2026-09-28T12:05:00Z",
  "status": "pending",
  "payment_method": "cash",
  "payment_status": "unpaid",
  "reserved_until": null,
  "subtotal": 240000.00,
  "shipping_fee": 0.00,
  "discount_amount": 0.00,
  "total_amount": 240000.00,
  "coupon_code": null,
  "currency": "VND",
  "items": [
    { "id": 801, "product_id": 101, "product_name": "Dây dắt chó màu xanh", "quantity": 2, "unit_price": 120000.00, "line_total": 240000.00 }
  ],
  "shipping": {
    "order_id": 501,
    "recipient_name": "Nguyễn Minh An",
    "recipient_phone": "0901234567",
    "delivery_method": "pickup",
    "address": null,
    "note": "Nhận tại cửa hàng"
  },
  "cancellation": null
}
```

Ví dụ các thao tác riêng biệt, **không phải chuỗi có thể thực hiện tất cả lên cùng một đơn**:

```http
PATCH /api/v1/orders/501/status
Content-Type: application/json
X-CSRF-TOKEN: <token>
Cookie: JSESSIONID=<session>

{"status":"confirmed"}
```

```http
PATCH /api/v1/orders/501/payment-status
Content-Type: application/json
X-CSRF-TOKEN: <token>
Cookie: JSESSIONID=<session>

{"payment_status":"paid","note":"Đã đối chiếu khoản thanh toán"}
```

```http
PUT /api/v1/orders/501/cancellation
Content-Type: application/json
X-CSRF-TOKEN: <token>
Cookie: JSESSIONID=<session>

{"reason":"Tôi muốn thay đổi sản phẩm"}
```

Ví dụ hủy chỉ hợp lệ nếu đơn vẫn pending. Khi hủy lần đầu trả 201 cùng Location tới `/orders/501/cancellation`; lần sau trả 200 thông tin hủy ban đầu, không ghi đè reason/người hủy và không hoàn tồn lại, kể cả khi reason gửi lại khác.

## 8. DTO tồn kho và review

| DTO | Trường |
|---|---|
| InventoryResponse | product_id, name, product_type, stock_quantity, is_active |
| InventoryAdjustmentRequest | Bắt buộc: change số nguyên từ -1000000 đến 1000000, khác 0; reason string(255). Tùy chọn: reference string(100) nullable |
| InventoryMovementResponse | id, product_id, order_id nullable, actor_id nullable, quantity_change, quantity_before, quantity_after, reason, reference nullable, created_at |
| ReviewCreateRequest | Bắt buộc: rating số nguyên 1–5; tùy chọn comment string(10000) nullable. productId lấy từ path, user lấy từ principal |
| ReviewUpdateRequest | rating, comment; ít nhất một trường |
| ReviewResponse | id, product_id, rating, comment, author {display_name}, created_at; không trả email/phone/address |

review_summary.average null khi chưa có review; khi có thì làm tròn 2 chữ số thập phân. display_name lấy từ tên hiển thị người dùng; frontend phải escape mọi text khi hiển thị.

Ví dụ `POST /inventory/101/adjustments`:

```json
{ "change": 10, "reason": "Nhập thêm hàng", "reference": "PN-20260928-01" }
```

reference là ghi chú tra cứu, **không tự có tính duy nhất hoặc chống lặp** trong v1.

Ví dụ `POST /products/101/reviews`:

```json
{ "rating": 5, "comment": "Sản phẩm phù hợp, đóng gói cẩn thận." }
```

Chưa đăng nhập: 401; tài khoản không phải customer: 403; sản phẩm ngừng bán: 404; đã có review: 409 REVIEW_ALREADY_EXISTS; sai chủ khi sửa: 404.

## 9. Query ví dụ

```http
GET /api/v1/products?product_type=food&min_price=100000&max_price=500000&sort=price_asc&page=0&size=20
GET /api/v1/pets?status=available&breed=Poodle&page=0&size=12
GET /api/v1/orders?status=pending&date_from=2026-09-01&date_to=2026-09-30&page=0&size=20
GET /api/v1/inventory?low_stock=5&page=0&size=20
```

Query keyword/breed có ký tự đặc biệt phải URL-encode. Không cho client gửi trực tiếp tên cột SQL hoặc biểu thức sort tùy ý.


