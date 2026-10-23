# Checklist test Postman — toàn bộ 65 API Pet Shop

Đối chiếu controller và service ngày 30/09/2026. Tài liệu này liệt kê các chức năng đang có, request mẫu và kết quả cần kiểm tra. Các hàng là danh mục test, không phải một chuỗi phải chạy từ trên xuống dưới; xem phần A và L để chuẩn bị và chọn đúng thứ tự.

## A. Chuẩn bị Postman

### A1. Chạy backend và chọn cách test

Khởi động `DemoApplication` trong IDE hoặc chạy `./mvnw.cmd spring-boot:run` từ thư mục `demo`. Đợi ứng dụng khởi động thành công.

Base URL mặc định:

```text
http://localhost:8080/api/v1
```

Có hai cách:

1. **Chạy collection có sẵn:** import [phần 1–3, 44 request](../postman/Pet-Shop.postman_collection.json), [phần 4–10, 62 request](../postman/Commerce.postman_collection.json) và [environment Local](../postman/Local.postman_environment.json). Điền email/mật khẩu admin thật, chọn environment rồi Run từng collection theo thứ tự request bên trong. Mỗi collection tự chuẩn bị dữ liệu cần thiết; collection phần 4–10 không đòi chạy phần 1–3 trước. Cookie jar phải bật. Collection đã có script lấy CSRF.
2. **Test thủ công từng endpoint:** tạo environment với các biến ở A2 và làm A3. Các body trong tài liệu này dùng biến riêng cho test thủ công, không phải tên biến tự tạo trong collection có sẵn.

Collection tạo dữ liệu mẫu và giữ lại đơn/lịch sử tồn theo nghiệp vụ. Chạy trên database phát triển. 65 là số cặp method–path, còn số request trong collection lớn hơn vì có đăng nhập lại, thử lỗi, replay và chuyển trạng thái.

### A2. Biến dùng khi test thủ công

| Biến | Giá trị / cách lấy |
|---|---|
| base_url | `http://localhost:8080/api/v1` |
| admin_email, admin_password | Tài khoản admin đã có trong database; không dùng một mật khẩu tự đoán |
| customer_email | Ví dụ `postman.20260930.01@example.test`; đổi email nếu chạy đăng ký lần nữa |
| customer_password | Ví dụ `CustomerTest123!` |
| customer_id | `id` từ response đăng ký hoặc `/users/me` khi đăng nhập customer |
| staff_id | ID một tài khoản thử khác, do admin đổi role thành staff |
| csrf_token | `csrf_token` từ GET `/auth/csrf-token` |
| category_id | `id` từ POST `/categories` |
| product_id | `id` của hàng food/accessory từ POST `/products` |
| pet_product_id | `id` của product loại pet từ POST `/products` |
| supplier_id | `id` từ POST `/suppliers` |
| pet_id | `id` từ POST `/pets`, khác với `pet_product_id` |
| record_id | `id` từ POST `/pets/{petId}/health-records` |
| order_id | ID đơn dùng để test giao hàng/hoàn tất/review |
| cancel_order_id | ID một đơn pending khác dùng để test hủy |
| refund_order_id | ID một đơn pending đã paid dùng để test hủy và hoàn tiền |
| movement_id | `id` từ POST `/inventory/{productId}/adjustments` |
| review_id | `id` từ POST `/products/{productId}/reviews` |
| order_key | Ví dụ `postman-order-20260930-0001`; đổi khi muốn tạo đơn mới, giữ khi thử replay |

ID trong body là số, vì vậy viết `"product_id": {{product_id}}`, không đặt dấu nháy quanh biến. Postman thay biến trước khi gửi. Trong bảng endpoint, `{productId}` là chỗ thay ID, không gửi nguyên dấu ngoặc nhọn. Ví dụ URL thực tế trong Postman là `{{base_url}}/products/{{product_id}}`.

### A3. Session, CSRF và header

Backend dùng **session cookie**, không dùng Bearer token. Chọn **No Auth** trong tab Authorization cho các request tự tạo; quyền được xác định bằng phiên đăng nhập trong cookie jar.

1. Gọi `GET {{base_url}}/auth/csrf-token`.
2. Response `200` có dạng:

```json
{"csrf_token":"gia-tri-token","header_name":"X-CSRF-TOKEN"}
```

3. Lưu `csrf_token` vào environment. Có thể đặt script này trong phần Tests/Post-response của request lấy CSRF:

```javascript
pm.test("Lấy CSRF thành công", () => pm.response.to.have.status(200));
if (pm.response.code === 200) {
  pm.environment.set("csrf_token", pm.response.json().csrf_token);
}
```

4. Với POST, PUT, PATCH, DELETE, gửi header:

```text
X-CSRF-TOKEN: {{csrf_token}}
```

5. Request có JSON body: chọn **Body → raw → JSON**, gửi `Content-Type: application/json`.
6. Sau **mỗi lần đăng nhập thành công**, gọi GET CSRF lại vì phiên/token đã thay đổi. Sau logout hoặc đổi mật khẩu, lấy CSRF mới trước khi login lại. Postman tự giữ cookie; không gõ tay JSESSIONID.
7. POST `/orders` cần thêm:

```text
Idempotency-Key: {{order_key}}
```

Khi đổi từ customer sang admin/staff hoặc ngược lại, logout, lấy CSRF, login đúng tài khoản rồi lấy lại CSRF. Hai environment trên cùng host không tự tạo hai cookie jar độc lập; luôn gọi `/users/me` để xác nhận đang test với ai. Để test public sau đăng nhập, logout hoặc xóa cookie của host trước.

### A4. Quy ước kết quả

| Quyền | Ý nghĩa |
|---|---|
| Public | Không cần đăng nhập; dữ liệu ẩn/ngừng bán vẫn có thể trả 404 |
| Login | Mọi tài khoản đã đăng nhập và đang hoạt động |
| Owner | Đúng chủ tài nguyên |
| Staff | Role staff **hoặc admin** |
| Admin | Chỉ role admin |

GET/DELETE không có body. POST logout cũng không có body. PATCH phải có ít nhất một trường; chỉ những trường nullable mới được gửi null. Body không được thêm trường ngoài hợp đồng. Số và boolean phải đúng kiểu JSON, ví dụ `2`, `true`, không phải `"2"`, `"true"`.

Danh sách phân trang trả `data` và `pagination`; `page` mặc định 0, tối đa 100000, `size` mặc định 20, từ 1 đến 100. Chi tiết trả object trực tiếp. GET order items trả mảng trực tiếp. `204` có body rỗng.

## B. Tài khoản và xác thực — 11 API

| # | Method và path | Quyền | Body / query để test | Kết quả mong đợi |
|---|---|---|---|---|
| 01 | GET `/auth/csrf-token` | Public | Không body | 200; có csrf_token/header_name; Cache-Control: no-store |
| 02 | POST `/auth/register` | Public + CSRF | Body B1 | 201; role=customer, is_active=true; chưa tự login; lưu customer_id |
| 03 | POST `/auth/login` | Public + CSRF | Body B2 | 200; thông tin user, session cookie; lấy lại CSRF |
| 04 | POST `/auth/logout` | Login | Không body | 204; GET /users/me tiếp theo trả 401 |
| 05 | GET `/users/me` | Login | Không body | 200; đúng user đang đăng nhập; không có password/hash |
| 06 | PATCH `/users/me` | Login | `{"full_name":"Tên mới","phone":"0901234567","address":"12 Đường Hoa"}` | 200; chỉ cập nhật trường đã gửi |
| 07 | PUT `/users/me/password` | Login | Body B3 | 204; tất cả phiên cũ hết hiệu lực; cần login lại |
| 08 | GET `/users` | Admin | `?keyword=postman&role=customer&is_active=true&page=0&size=20` | 200; danh sách khớp bộ lọc |
| 09 | GET `/users/{userId}` | Owner/Admin | Không body | 200; customer xem người khác trả 404 |
| 10 | PATCH `/users/{userId}/role` | Admin | `{"role":"staff"}` | 200; phiên cũ của user bị thu hồi |
| 11 | PATCH `/users/{userId}/status` | Admin | `{"is_active":false}`; dùng true để mở khóa | 200; khóa/mở đều thu hồi phiên cũ |

**B1 — Đăng ký customer:**

```json
{
  "full_name": "Khách test Postman",
  "email": "{{customer_email}}",
  "password": "{{customer_password}}",
  "password_confirmation": "{{customer_password}}",
  "phone": "0901234567",
  "address": "12 Đường Hoa, TP.HCM"
}
```

**B2 — Đăng nhập customer:**

```json
{"email":"{{customer_email}}","password":"{{customer_password}}"}
```

Đăng nhập admin dùng cùng endpoint với `admin_email` và `admin_password`. Admin phải tồn tại trước; đăng ký luôn chỉ tạo customer. Role hợp lệ: `customer`, `staff`, `admin`.

**B3 — Đổi mật khẩu:**

```json
{
  "old_password": "{{customer_password}}",
  "new_password": "CustomerNew123!",
  "password_confirmation": "CustomerNew123!"
}
```

Sau thành công, cập nhật biến customer_password thành mật khẩu mới. Để test này cuối luồng mua hàng hoặc dùng tài khoản riêng.

**Test lỗi:** đăng ký trùng email → 409 EMAIL_ALREADY_EXISTS; sai password khi login → 401 INVALID_CREDENTIALS; thêm role vào body đăng ký → 400; customer GET /users → 403; admin tự đổi role/tự khóa → 403. Mật khẩu mới tối thiểu 8 ký tự, tối đa 72 byte UTF-8. Đổi role và khóa/mở khóa tài khoản test staff trước khi dùng staff, sau đó staff login lại.

## C. Danh mục — 5 API

| # | Method và path | Quyền | Body / query để test | Kết quả mong đợi |
|---|---|---|---|---|
| 12 | GET `/categories` | Public | `?keyword=Thức&page=0&size=20` | 200; danh sách phân trang |
| 13 | GET `/categories/{categoryId}` | Public | Không body | 200; đúng danh mục |
| 14 | POST `/categories` | Admin | `{"name":"Thức ăn và phụ kiện"}` | 201; lưu category_id |
| 15 | PATCH `/categories/{categoryId}` | Admin | `{"name":"Danh mục đã sửa"}` | 200; tên mới |
| 16 | DELETE `/categories/{categoryId}` | Admin | Không body | 204 chỉ khi không có product nào |

**Test lỗi:** danh mục còn product, kể cả inactive → 409 RESOURCE_IN_USE; customer tạo/sửa/xóa → 403; name trống → 400. Để test xóa thành công, tạo một danh mục rỗng riêng.

## D. Sản phẩm — 6 API

| # | Method và path | Quyền | Body / query để test | Kết quả mong đợi |
|---|---|---|---|---|
| 17 | GET `/products` | Public | `?category_id={{category_id}}&product_type=food&min_price=100000&max_price=200000&sort=price_asc&page=0&size=20` | 200; lọc đúng; public chỉ thấy active |
| 18 | GET `/products/{productId}` | Public/Staff | Không body | 200; có pet hoặc null, review_summary; hàng inactive chỉ Staff xem được |
| 19 | POST `/products` | Admin | Body D1 hoặc D2 | 201; lưu product_id hoặc pet_product_id |
| 20 | PATCH `/products/{productId}` | Admin | `{"name":"Thức ăn mới","price":125000,"description":"Mô tả mới"}` | 200; không thay giá snapshot của đơn cũ |
| 21 | PATCH `/products/{productId}/status` | Admin | `{"is_active":false}` hoặc true | 200; ngừng/mở bán |
| 22 | DELETE `/products/{productId}` | Admin | Không body | 204 chỉ khi chưa có lịch sử và không còn hồ sơ pet phụ thuộc |

**D1 — Hàng thông thường:**

```json
{
  "category_id": {{category_id}},
  "product_type": "food",
  "name": "Thức ăn cho chó 1 kg",
  "description": "Sản phẩm thử nghiệm",
  "price": 120000,
  "stock_quantity": 20,
  "image_url": "https://example.test/food.jpg"
}
```

food/accessory mới mặc định active. Đổi product_type thành accessory để test phụ kiện. image_url chỉ là đường dẫn http/https; không có upload ảnh và backend không tải ảnh từ URL.

**D2 — Product cho một cá thể pet:**

```json
{
  "category_id": {{category_id}},
  "product_type": "pet",
  "name": "Poodle nâu",
  "price": 3000000,
  "stock_quantity": 1
}
```

Response có is_active=false. Lưu id vào pet_product_id, tạo hồ sơ ở E, rồi mới PATCH `/products/{{pet_product_id}}/status` với is_active=true.

PATCH thông tin chỉ nhận category_id, name, description, price, image_url. Giá phải > 0, tối đa 9999999999.99 và 2 chữ số thập phân. Sort: newest, price_asc, price_desc. Staff có thể dùng `?is_active=false`; customer gửi bộ lọc này → 403.

**Test lỗi:** pet chưa có hồ sơ mà mở bán → 409 PRODUCT_NOT_READY; product pet thiếu stock_quantity=1 → 400; gửi stock_quantity/product_type/is_active vào PATCH thông tin → 400; public xem hàng inactive → 404. Product tạo với tồn > 0 đã có opening_stock movement, nên DELETE trả 409 dù chưa có đơn. Muốn test DELETE 204, tạo food/accessory tồn 0, chưa điều chỉnh kho/chưa có đơn.

## E. Thú cưng — 5 API

| # | Method và path | Quyền | Body / query để test | Kết quả mong đợi |
|---|---|---|---|---|
| 23 | GET `/pets` | Public/Staff | `?status=available&breed=Poodle&supplier_id={{supplier_id}}&page=0&size=20` | 200; public chỉ thấy pet có product active; Staff có thể thấy mọi pet |
| 24 | GET `/pets/{petId}` | Public/Staff | Không body | 200; public không có supplier_id/reserved_order_id |
| 25 | POST `/pets` | Admin | Body E1 | 201; lưu pet_id; status=available |
| 26 | PATCH `/pets/{petId}` | Admin | `{"weight":3.2,"color":"Nâu đậm","is_neutered":true}` | 200; cập nhật đặc điểm |
| 27 | DELETE `/pets/{petId}` | Admin | Không body | 204 nếu available và chưa từng có đơn |

**E1 — Hồ sơ pet:**

```json
{
  "product_id": {{pet_product_id}},
  "supplier_id": {{supplier_id}},
  "breed": "Poodle",
  "age": "3 tháng",
  "gender": "female",
  "color": "Nâu",
  "weight": 2.5,
  "is_neutered": false,
  "vaccination_status": "Đã tiêm mũi đầu"
}
```

Chỉ product_id bắt buộc. Chưa có supplier thì bỏ supplier_id hoặc gửi null. Supplier đã gửi phải tồn tại. gender: male/female/null; weight dương, tối đa 9999.99, tối đa 2 số lẻ; is_neutered mặc định false, không nhận null.

PATCH nhận supplier_id, breed, age, gender, color, weight, is_neutered, vaccination_status. Không nhận product_id, status hoặc trường reservation.

**Test lỗi/nghiệp vụ:** tạo hồ sơ lần hai cùng product → 409; product không phải pet hoặc không còn đúng điều kiện nháp → 409; đổi product_id/status bằng PATCH → 400; pet có đơn kể cả đã hủy → DELETE 409. Xóa hồ sơ hợp lệ sẽ xóa health records, ngừng bán product, giữ product và tồn 1. Dùng pet riêng để test xóa, tránh làm mất dữ liệu của luồng mua hàng tiếp theo.

## F. Hồ sơ sức khỏe — 6 API

| # | Method và path | Quyền | Body / query để test | Kết quả mong đợi |
|---|---|---|---|---|
| 28 | GET `/pets/{petId}/health-records` | Public/Staff | `?page=0&size=20` | 200; records của đúng pet |
| 29 | GET `/pets/{petId}/health-records/{recordId}` | Public/Staff | Không body | 200; đúng record thuộc pet |
| 30 | POST `/pets/{petId}/health-records` | Admin | Body F1 | 201; lưu record_id |
| 31 | PATCH `/pets/{petId}/health-records/{recordId}` | Admin | `{"description":"Đã tái khám","vet_name":"Bác sĩ An"}` | 200 |
| 32 | DELETE `/pets/{petId}/health-records/{recordId}` | Admin | Không body | 204; GET lại trả 404 |
| 33 | GET `/health-records` | Staff | `?pet_id={{pet_id}}&page=0&size=20` | 200; bỏ pet_id để xem toàn bộ |

**F1 — Tạo record:**

```json
{
  "record_date": "2026-09-01",
  "record_type": "vaccination",
  "description": "Ghi nhận tiêm phòng",
  "vet_name": "Bác sĩ An"
}
```

record_date và record_type bắt buộc; ngày không ở tương lai. record_type là chuỗi tự do tối đa 100 ký tự. PATCH có thể sửa bốn trường trên. Không gửi pet_id trong body.

**Test lỗi:** recordId của pet khác → 404 cho GET/PATCH/DELETE; record_date ở tương lai → 400; public xem health của product inactive → 404, Staff vẫn đọc được; customer GET /health-records → 403. Nội dung sức khỏe là dữ liệu có thể công khai, không điền thông tin cá nhân của khách mua.

## G. Nhà cung cấp — 5 API

| # | Method và path | Quyền | Body / query để test | Kết quả mong đợi |
|---|---|---|---|---|
| 34 | GET `/suppliers` | Staff | `?keyword=Trại&page=0&size=20` | 200; danh sách lọc theo tên |
| 35 | GET `/suppliers/{supplierId}` | Staff | Không body | 200; có name/contact_info/address |
| 36 | POST `/suppliers` | Admin | Body G1 | 201; lưu supplier_id |
| 37 | PATCH `/suppliers/{supplierId}` | Admin | `{"contact_info":"0907654321","address":null}` | 200; địa chỉ được xóa |
| 38 | DELETE `/suppliers/{supplierId}` | Admin | Không body | 204; pet giữ nguyên và supplier_id=null |

**G1 — Tạo supplier:**

```json
{"name":"Trại thú cưng A","contact_info":"0901234567","address":"20 Đường Hoa"}
```

Chỉ name bắt buộc, tối đa 150 ký tự. contact_info/address nullable, tối đa 255. PATCH nhận cùng ba trường. Public chưa login GET suppliers → 401; customer → 403; Staff chỉ đọc, không tạo/sửa/xóa. Sau khi xóa supplier, kiểm tra pet và sức khỏe vẫn còn, chi tiết pet bằng admin có supplier_id=null.

## H. Giỏ hàng và preview — 6 API

| # | Method và path | Quyền | Body / query để test | Kết quả mong đợi |
|---|---|---|---|---|
| 39 | GET `/cart` | Login | Không body | 200; giỏ của đúng user, giá hiện tại; trống: items=[], subtotal=0 |
| 40 | POST `/cart/items` | Login | `{"product_id":{{product_id}},"quantity":2}` | 200; thêm mới hoặc cộng 2 vào dòng cũ |
| 41 | PUT `/cart/items/{productId}` | Login | `{"quantity":3}` | 200; đặt chính xác 3, kể cả chưa có dòng |
| 42 | DELETE `/cart/items/{productId}` | Login | Không body | 204; lặp khi không còn dòng vẫn 204 |
| 43 | DELETE `/cart/items` | Login | Không body | 204; GET giỏ sau đó trống |
| 44 | POST `/checkout/preview` | Login | Body H1 | 200; can_place_order, items, tiền, issues |

**H1 — Preview giỏ hiện tại:**

```json
{
  "shipping": {
    "recipient_name": "Khách test Postman",
    "recipient_phone": "0901234567",
    "delivery_method": "pickup",
    "address": null,
    "note": "Nhận tại cửa hàng"
  },
  "coupon_code": null
}
```

Để preview danh sách chỉ định, thêm vào object trên:

```json
"items": [{"product_id": {{product_id}}, "quantity": 2}]
```

Đây là một trường để chèn vào object, không gửi đoạn trường riêng lẻ thành body. Bỏ items hoàn toàn mới có nghĩa là dùng giỏ. items=[] hoặc null → 400; giỏ trống và không gửi items → 400 EMPTY_CART. Không gửi payment_method cho preview.

delivery_method: pickup, pet_taxi. `pet_taxi` bắt buộc address và cộng phí vận chuyển vào tổng tiền; pickup không thu phí. quantity là số nguyên 1–1000000; tối đa 100 product khác nhau, pet chỉ quantity=1. Giá 120000 × 2, pickup, không coupon → subtotal=240000, shipping_fee=0, total_amount=240000.

**Test nghiệp vụ:** POST quantity=2 hai lần → 4; PUT quantity=2 hai lần → vẫn 2. Thêm giỏ/preview không trừ tồn, không giữ pet. Hàng cũ trong giỏ bị ngừng bán vẫn hiện available=false. Preview thiếu tồn/coupon không hợp lệ trả **200** với can_place_order=false và issues; không coi chỉ HTTP 200 là mua được. Sản phẩm bị ẩn: name/giá null. Coupon sai hoặc không tính được đủ giá: các tổng tiền null. Không có API tạo coupon; chỉ test coupon hợp lệ nếu database đã được chuẩn bị mã thật.

## I. Đơn hàng và giao hàng — 11 API

| # | Method và path | Quyền | Body / query để test | Kết quả mong đợi |
|---|---|---|---|---|
| 45 | POST `/orders` | Login | Body I1 + Idempotency-Key | 201 mới; 200 khi replay; lưu order_id |
| 46 | GET `/orders` | Login | `?status=pending&payment_status=unpaid&date_from=2026-09-01&date_to=2026-09-30&page=0&size=20` | 200; customer chỉ có đơn của mình |
| 47 | GET `/orders/{orderId}` | Owner/Staff | Không body | 200; thông tin đơn, items, shipping, cancellation |
| 48 | GET `/orders/{orderId}/items` | Owner/Staff | Không body | 200; mảng giá/tên tại thời điểm mua |
| 49 | GET `/orders/{orderId}/events` | Owner/Staff | `?page=0&size=20` | 200; lịch sử trạng thái/thanh toán/giao hàng |
| 50 | PATCH `/orders/{orderId}/status` | Staff | `{"status":"confirmed"}`; sau đó shipping, completed | 200 nếu chuyển đúng trình tự/điều kiện |
| 51 | PATCH `/orders/{orderId}/payment-status` | Staff | `{"payment_status":"paid","note":"Đã đối chiếu thanh toán"}` | 200; chỉ ghi nhận thu tiền thủ công |
| 52 | PUT `/orders/{orderId}/cancellation` | Owner/Staff | `{"reason":"Tôi đổi ý"}` | 201 lần đầu; 200 khi lặp; chỉ hủy pending |
| 53 | GET `/orders/{orderId}/cancellation` | Owner/Staff | Không body | 200 nếu đã hủy; chưa có tài nguyên hủy → 404 |
| 54 | GET `/orders/{orderId}/shipping` | Owner/Staff | Không body | 200; snapshot thông tin người nhận |
| 55 | PATCH `/orders/{orderId}/shipping` | Owner/Staff | Body I2 | 200 chỉ khi pending/confirmed |

**I1 — Tạo đơn từ giỏ hiện tại:**

```json
{
  "shipping": {
    "recipient_name": "Khách test Postman",
    "recipient_phone": "0901234567",
    "delivery_method": "pickup",
    "address": null,
    "note": "Nhận tại cửa hàng"
  },
  "coupon_code": null,
  "payment_method": "cash"
}
```

Đặt trực tiếp không qua giỏ: thêm items giống H1, chẳng hạn `"items":[{"product_id":{{pet_product_id}},"quantity":1}]` để mua pet. **Đơn và giỏ dùng product ID, không dùng pet_id.** Không gửi user_id, name, unit_price, total_amount, status hoặc idempotency_key trong body.

payment_method: cash, bank_transfer, card, e_wallet. Phương thức không đồng nghĩa đã thu tiền. Khóa header dài 8–100 ký tự, chỉ chữ/số/_/-. Lần đầu nhận Location tới đơn. Response mới: status=pending, payment_status=unpaid, tiền do server tính. Stock giảm theo số lượng; pet available → reserved; lượng đã mua được trừ khỏi giỏ; có movement/event.

**I2 — Sửa người nhận/giao hàng:**

```json
{
  "recipient_name": "Người nhận mới",
  "recipient_phone": "0907654321",
  "address": "30 Đường Hoa",
  "note": "Gọi trước khi tới"
}
```

Có thể gửi riêng note hoặc một trường khác. Không nhận delivery_method. Không thể bỏ/null address của đơn giao hàng không phải pickup. Chỉnh hồ sơ user không tự đổi snapshot shipping trong đơn.

### I3. Các luồng trạng thái phải test trên đơn riêng

| Luồng | Thao tác và kết quả |
|---|---|
| Hoàn tất đơn cash | pending → PATCH confirmed → PATCH payment_status=paid → PATCH shipping → PATCH completed; đều 200. Có thể ghi paid ở bước khác trước completed |
| Hoàn tất đơn trả trước | pending/unpaid → PATCH paid trước hạn → confirmed → shipping → completed; trước paid mà confirmed → 409 |
| Hoàn tất nhưng chưa paid | shipping/unpaid → PATCH completed trả 409 |
| Hủy pending | PUT cancellation lần đầu 201; tồn trả lại, pet available; lặp 200, reason/người hủy/thời điểm giữ nguyên, không cộng tồn nữa |
| Hủy sau xác nhận | confirmed/shipping/completed → PUT cancellation trả 409 INVALID_ORDER_TRANSITION |
| Hoàn tiền | Tạo đơn riêng pending → Staff ghi paid → Owner/Staff hủy → Staff PATCH payment_status=refunded với note; bước refund trả 200 |
| Refund chưa hủy/chưa paid | PATCH refunded → 409; hủy đơn paid vẫn giữ paid cho tới khi Staff ghi nhận refund |
| Gửi lại trạng thái hiện tại | 200, không ghi thêm event hoặc thay tồn |
| Nhảy cóc/lùi trạng thái | Ví dụ pending → shipping → 409 |
| PATCH status=cancelled | 400; phải dùng PUT cancellation |

Sau completed, pet reserved → sold. Không có API xóa đơn, sửa item/giá/tổng đơn đã tạo. Staff có thể lọc `/orders?user_id={{customer_id}}`; customer gửi user_id → 403. Customer khác xem đơn/items/events/shipping/cancellation → 404. date_to tính hết ngày UTC; chọn khoảng ngày chứa ngày tạo đơn của bạn.

### I4. Chống trùng đơn

1. Tạo đơn với một key mới → 201; ghi lại id, tồn và tổng tiền.
2. Gửi lại **cùng body, cùng user, cùng key**, kể cả giỏ đã bị xóa sau lần đầu → 200, header Idempotency-Replayed=true, cùng id, tồn không bị trừ thêm.
3. Giữ key nhưng đổi quantity hoặc shipping/payment_method → 409 IDEMPOTENCY_CONFLICT.
4. Đổi key để thể hiện lần mua mới → có thể tạo đơn mới nếu còn đủ tồn.
5. Sửa giá catalog rồi đọc đơn cũ: unit_price/product_name trong items vẫn giữ giá trị snapshot. Replay cũng trả đơn đã có, không tính lại giá.

### I5. Đơn trả trước hết hạn

Tạo một đơn bank_transfer/card/e_wallet, chưa ghi paid, giữ status=pending. reserved_until khoảng 15 phút sau lúc tạo. Khi quá hạn, scheduler mặc định chạy mỗi 60 giây sẽ hủy và hoàn tồn; vì vậy có thể phải chờ thêm khoảng một lượt quét. GET đơn phải thấy cancelled, payment_status=unpaid, cancellation có cancelled_by=null; pet trở lại available.

Ghi paid trước hạn rồi chờ: đơn không bị tự hủy, reserved_until được xóa. Cash không có hạn 15 phút. GET/preview không tự kích hoạt hủy. Scheduler cần ORDER_EXPIRY_ENABLED=true, mặc định đã bật.

## J. Tồn kho — 5 API

| # | Method và path | Quyền | Body / query để test | Kết quả mong đợi |
|---|---|---|---|---|
| 56 | GET `/inventory` | Staff | `?low_stock=5&page=0&size=20` | 200; chỉ product có stock_quantity <= 5 |
| 57 | GET `/inventory/{productId}` | Staff | Không body | 200; tồn khả dụng hiện tại |
| 58 | GET `/inventory/{productId}/movements` | Staff | `?page=0&size=20` | 200; lịch sử nhập/đặt/hủy |
| 59 | GET `/inventory/{productId}/movements/{movementId}` | Staff | Không body | 200; movement thuộc đúng product |
| 60 | POST `/inventory/{productId}/adjustments` | Staff | Body J1 | 201; có Location; lưu movement_id |

**J1 — Nhập thêm hàng:**

```json
{"change":10,"reason":"Nhập hàng test","reference":"PN-20260930-01"}
```

change âm để giảm tồn, ví dụ -2. Kiểm tra movement có quantity_before, quantity_change, quantity_after và công thức after=before+change. Tồn khả dụng và lượng hàng đang giữ cộng lại không được vượt 1000000 để khi hủy vẫn hoàn tồn được.

**Test lỗi:** change=0 hoặc ngoài ±1000000 → 400; giảm làm tồn âm → 409; adjustment product pet → 409; movement thuộc product khác → 404; customer → 403. low_stock là số nguyên 0–1000000, không phải true/false. POST lặp cùng reference vẫn có thể điều chỉnh tiếp; reference không phải khóa chống trùng.

## K. Đánh giá — 5 API

| # | Method và path | Quyền | Body / query để test | Kết quả mong đợi |
|---|---|---|---|---|
| 61 | GET `/products/{productId}/reviews` | Public/Staff | `?sort=rating_desc&page=0&size=20` | 200; danh sách review; public chỉ xem product active |
| 62 | GET `/products/{productId}/reviews/{reviewId}` | Public/Staff | Không body | 200; đúng review thuộc product |
| 63 | POST `/products/{productId}/reviews` | Customer đã đăng nhập | `{"rating":5,"comment":"Sản phẩm tốt"}` | 201; lưu review_id |
| 64 | PATCH `/products/{productId}/reviews/{reviewId}` | Owner | `{"rating":4,"comment":null}` | 200; rating mới, xóa comment |
| 65 | DELETE `/products/{productId}/reviews/{reviewId}` | Owner/Admin | Không body | 204; review_summary được tính lại khi đọc product |

Customer đã đăng nhập có thể đánh giá sản phẩm đang bán ngay trên website, không cần đơn completed. Tạo review lấy user từ session; không gửi user_id/product_id/order_id trong body.

rating là số nguyên 1–5, comment tùy chọn/null, tối đa 10000 ký tự. Sort: newest, rating_asc, rating_desc. Response có author.display_name, không lộ email/phone/address.

**Test lỗi:** chưa đăng nhập → 401; staff/admin tạo review → 403; sản phẩm ngừng bán → 404; tạo lần hai cùng user/product → 409 REVIEW_ALREADY_EXISTS; rating=0/6 → 400; người khác sửa → 404; admin cũng không được sửa review của khách nhưng được xóa; reviewId không thuộc productId → 404. Chủ review vẫn có thể sửa review khi sản phẩm ngừng bán, trong khi public đọc review của hàng đó trả 404.

## L. Thứ tự test được đề xuất

| Bước | Tài khoản | Việc cần làm |
|---|---|---|
| 1 | Chưa login | Lấy CSRF, đăng ký customer A; lưu customer_id |
| 2 | Admin | Login, lấy lại CSRF, tạo category, supplier |
| 3 | Admin | Tạo food/accessory tồn 20; lưu product_id |
| 4 | Admin | Tạo product pet tồn 1; tạo hồ sơ pet, health record; mở bán |
| 5 | Public | Logout admin; xem categories/products/pets/health, kiểm tra dữ liệu công khai |
| 6 | Customer A | Login, lấy CSRF; GET giỏ trống, POST cộng, PUT thay số lượng, preview |
| 7 | Customer A | Tạo đơn A từ giỏ, thử replay cùng key; đọc đơn/items/events/shipping và sửa shipping |
| 8 | Admin/Staff | Ghi paid, confirmed → shipping → completed; đọc kho và movements |
| 9 | Customer A | Login lại, đánh giá product trong đơn A, sửa và xóa review |
| 10 | Customer A | Tạo đơn B mới với key mới; giữ pending; hủy và hủy lặp, kiểm tra hoàn tồn |
| 11 | Customer A + Staff | Tạo đơn C riêng, Staff ghi paid khi pending, hủy, Staff ghi refunded |
| 12 | Customer A | Tạo đơn D trả trước unpaid, đợi quá hạn để kiểm tra scheduler |
| 13 | Customer B/Staff | Test quyền sở hữu và vai trò; không dùng phiên admin cho test customer |
| 14 | Admin + customer thử riêng | Test đổi role, khóa/mở, đổi mật khẩu và thu hồi phiên |
| 15 | Admin | Test xóa trên category/product/pet/health/supplier mẫu chưa bị lịch sử chặn |

Để kiểm tra hai khách cùng mua một pet: dùng hai phiên đăng nhập độc lập, mỗi khách tạo đơn với cùng pet_product_id và key riêng. Nếu cùng pet còn available, chỉ một người được mua; người còn lại nhận 409. Chuyển environment trong cùng cookie jar không đủ để tạo hai khách độc lập.

## M. Checklist lỗi chung và kiểm tra response

| Tình huống | Kết quả mong đợi |
|---|---|
| GET tài nguyên yêu cầu login khi chưa login | 401 AUTHENTICATION_REQUIRED |
| Ghi thiếu/sai CSRF | 403 CSRF_INVALID; filter có thể trả lỗi này trước lỗi quyền/body |
| Đã login nhưng sai role | 403 FORBIDDEN |
| Đúng định dạng ID nhưng không tồn tại/không được xem | 404 NOT_FOUND |
| ID=0, ID âm hoặc chữ | 400 |
| Body không có trường bắt buộc, PATCH {}, sai kiểu/field lạ | 400 |
| Query lạ, size=101, page=-1, query bị lặp | 400 |
| Method không hỗ trợ trên URL đã có | 405 |
| Sai Content-Type với body cần JSON | 415 |
| JSON vượt giới hạn 64 KiB | 413 |
| Thiếu tồn, pet không khả dụng, chuyển trạng thái sai, dữ liệu có lịch sử | 409 với error.code tương ứng |

Lỗi dùng cấu trúc:

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Thông báo lỗi",
    "field_errors": [],
    "trace_id": "..."
  }
}
```

Script mẫu để kiểm tra một request tạo tài nguyên và lưu ID, đặt trong Tests/Post-response của request đó:

```javascript
pm.test("Tạo thành công", () => pm.response.to.have.status(201));
if (pm.response.code === 201) {
  pm.test("Có Location", () => pm.expect(pm.response.headers.has("Location")).to.be.true);
  pm.environment.set("product_id", pm.response.json().id);
}
```

Đổi product_id sang biến tương ứng khi tạo category/pet/record/order. Không gắn script này ở cấp collection cho mọi response vì không phải response nào cũng có id/status 201.

Các request về tiền, giỏ và đơn cần kiểm tra cả body, trạng thái DB qua GET API, và số lần thay đổi tồn; HTTP 200 đơn thuần chưa chứng minh nghiệp vụ đúng.

## N. Phạm vi đã xác minh và chưa có

Đã chạy `mvnw.cmd test` trên Java 21 với **27/27 kiểm thử HTTP/MySQL** và **1/1 kiểm thử giới hạn tần suất** thành công. Bộ test tạo rồi xóa database ngẫu nhiên `petshop_test_*`; không thao tác dữ liệu cửa hàng. Collection phần 4–10 có 62 request; chưa có kết quả chạy Newman cho collection mới.

Chưa có API quản trị coupon, upload ảnh, payment gateway/webhook, quên mật khẩu/xác minh email hoặc Swagger/OpenAPI. Đăng nhập, đăng ký và đặt đơn có thể trả HTTP 429 khi vượt giới hạn tần suất. Không có endpoint xóa đơn/xóa user, sửa tồn qua product PATCH hoặc sửa tự do trạng thái pet. Những thao tác không được hỗ trợ này không nên được đưa vào checklist như tính năng đang hoạt động.
