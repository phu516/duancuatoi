> Hợp đồng toàn bộ back-end: hiện đã triển khai nhóm 1–3 (22 API tài khoản, danh mục, sản phẩm); các nhóm còn lại là kế hoạch. Xem [trạng thái và hướng dẫn chạy](../CHAY-VA-TEST-POSTMAN.md). Kế thừa từ [NEW-API/01-QUY-UOC.md](../../../NEW-API/01-QUY-UOC.md) ngày 29/09/2026. Khi triển khai `demo`, cập nhật đồng bộ bộ tài liệu trong `demo/docs/api`; bản NEW-API được giữ làm tham chiếu thiết kế ban đầu.

# 01. Quy ước REST API

## 1. URL và HTTP

- Prefix cho mọi endpoint: `/api/v1`.
- Tài nguyên dùng danh từ số nhiều: `/products`, `/orders`, `/users`. `/cart` là giỏ duy nhất của người đang đăng nhập.
- Path ID là số nguyên dương; JSON gửi số ID dưới dạng number, không phải string. ID sai định dạng: 400; đúng định dạng nhưng không tồn tại: 404.
- Request có JSON body dùng `Content-Type: application/json`; response có body cũng dùng JSON. DELETE không có body. 204 không trả JSON.
- GET không làm thay đổi nghiệp vụ, không gửi body. Endpoint lấy CSRF có thể tạo phiên bảo mật nhưng không tạo giỏ, đơn hoặc thay tồn.
- Query không hỗ trợ hoặc trường body không hỗ trợ: 400. Trường chỉ đọc như `role` khi đăng ký, `user_id`/`total_amount` khi đặt đơn phải bị từ chối, không được gán vào entity.
- HEAD theo cùng quyền đọc của GET. OPTIONS/preflight do cấu hình CORS xử lý; không coi chúng là thao tác nghiệp vụ.

| Method | Ý nghĩa | Ví dụ |
|---|---|---|
| GET | Đọc chi tiết/danh sách | `GET /products/101` |
| POST | Tạo tài nguyên hoặc tính toán trên dữ liệu phức tạp | `POST /orders`, `POST /checkout/preview` |
| PATCH | Sửa các trường được gửi; trường không gửi giữ nguyên | `PATCH /products/101` |
| PUT | Đặt đầy đủ tài nguyên con theo URL, gọi lặp không cộng dồn | `PUT /cart/items/101` |
| DELETE | Xóa tài nguyên được phép xóa | `DELETE /categories/5` |

PATCH trong tài liệu dùng JSON object thông thường, **không** dùng JSON Patch. Gửi null chỉ hợp lệ với trường được ghi nullable. Body PATCH rỗng: 400. Mọi cập nhật liên quan đến nghiệp vụ chạy nguyên khối: hoặc toàn bộ thành công, hoặc không thay đổi gì.

## 2. Mã trạng thái

| HTTP | Khi sử dụng |
|---|---|
| 200 | Đọc, tính thử, cập nhật có response; replay tạo đơn |
| 201 | Tạo mới tài nguyên; gửi header Location tới URL tài nguyên tương ứng |
| 204 | Xóa thành công, đăng xuất, đổi mật khẩu; không có response body |
| 400 | JSON/path/query sai, thiếu trường, sai kiểu, vi phạm validation |
| 401 | Chưa đăng nhập, phiên hết hạn/bị thu hồi, thông tin đăng nhập sai |
| 403 | Không đủ vai trò hoặc CSRF thiếu/sai |
| 404 | Không tồn tại; hoặc không được nhìn thấy dữ liệu thuộc người khác |
| 405 | Method không hỗ trợ tại URL; có Allow header |
| 409 | Thiếu tồn, pet đã giữ/bán, trùng email/review, chuyển trạng thái sai, dữ liệu còn liên kết |
| 413 | Request vượt 64 KiB |
| 415 | Content-Type không hỗ trợ |
| 429 | Quá giới hạn tần suất; có Retry-After |
| 500 | Lỗi bất ngờ; không trả SQL/stack trace/thông tin kết nối |

V1 thống nhất lỗi validation là **400**, thay cho 422 trong một số tài liệu cũ. Lỗi do Spring Security xảy ra trước controller vẫn cần cùng cấu trúc JSON. Nếu một request vi phạm nhiều điều kiện, filter bảo mật có thể từ chối trước validation; không cam kết thứ tự lỗi giữa các lớp.

## 3. Response thành công

Response chi tiết trả trực tiếp DTO; không bọc thêm `data`:

```json
{
  "id": 101,
  "name": "Thức ăn cho mèo 1 kg",
  "price": 250000.00,
  "stock_quantity": 20,
  "is_active": true
}
```

Danh sách phân trang:

```json
{
  "data": [],
  "pagination": {
    "page": 0,
    "size": 20,
    "total_elements": 0,
    "total_pages": 0
  }
}
```

- `page`: bắt đầu từ 0, tối đa 100000. `size`: mặc định 20, từ 1 đến 100.
- Vượt trang cuối: 200 với `data=[]`, vẫn trả tổng số phần tử thật.
- Danh sách sản phẩm: `sort=newest|price_asc|price_desc`.
- Danh sách review: `sort=newest|rating_asc|rating_desc`.
- Danh sách khác sắp xếp ID giảm dần; các sort bằng giá/rating thêm ID làm điều kiện phụ ổn định.
- Danh sách items nằm trong một cart/order là mảng không phân trang. Các endpoint list khác ở file 02 dùng format trên.

## 4. Response lỗi

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Dữ liệu không hợp lệ",
    "field_errors": [
      { "field": "quantity", "message": "Phải là số nguyên từ 1 đến 1000000" }
    ],
    "trace_id": "req-7c920f"
  }
}
```

`field_errors` là mảng rỗng nếu lỗi không gắn với một trường. `trace_id` do server sinh, dùng tra log; không chứa thông tin nhạy cảm.

Các code chính: `VALIDATION_ERROR`, `INVALID_JSON`, `UNSUPPORTED_FIELD`, `AUTHENTICATION_REQUIRED`, `INVALID_CREDENTIALS`, `SESSION_EXPIRED`, `FORBIDDEN`, `CSRF_INVALID`, `NOT_FOUND`, `EMAIL_ALREADY_EXISTS`, `OUT_OF_STOCK`, `PET_UNAVAILABLE`, `PRODUCT_NOT_READY`, `RESOURCE_IN_USE`, `INVALID_ORDER_TRANSITION`, `ORDER_EXPIRED`, `IDEMPOTENCY_CONFLICT`, `REVIEW_ALREADY_EXISTS`, `COUPON_INVALID`, `RATE_LIMITED`, `INTERNAL_ERROR`.

## 5. Quyền truy cập

| Quyền trong file 02 | Ý nghĩa |
|---|---|
| Public | Không yêu cầu đăng nhập; thao tác ghi vẫn cần CSRF |
| Authenticated | Mọi user đang hoạt động đã đăng nhập |
| Owner | User đang đăng nhập chính là chủ tài nguyên |
| Staff | Role staff hoặc admin |
| Admin | Chỉ role admin |
| Owner/Admin | Chính chủ hoặc admin; staff không được đọc hồ sơ người khác qua quyền này |
| Owner/Staff | Chính chủ, staff hoặc admin |

Không nhận user ID từ client để xác định chủ giỏ/đơn mới/review mới. Lấy danh tính từ principal của Spring Security. Kiểm tra cả role và quyền trên từng tài nguyên; biết một order ID không đồng nghĩa được đọc nó.

Customer truy cập đơn hoặc review riêng của người khác: 404. Customer gọi endpoint quản trị: 403. Sản phẩm ngừng bán và dữ liệu pet/sức khỏe liên quan không xuất hiện qua API public; staff/admin có thể xem theo quyền.

## 6. Xác thực Session/Cookie cho v1

1. `GET /auth/csrf-token`: server tạo phiên nếu cần, trả `csrf_token` và `header_name: X-CSRF-TOKEN`; client lưu cookie do server gửi.
2. `POST /auth/register` hoặc `POST /auth/login`: gửi JSON, cookie và header X-CSRF-TOKEN. Đăng ký không tự đăng nhập.
3. Đăng nhập thành công thay session ID, lưu xác thực bằng Spring Security và trả UserResponse. Client gọi lại GET /auth/csrf-token để lấy token phù hợp phiên mới.
4. Request bảo vệ gửi cookie phiên; các POST/PUT/PATCH/DELETE đều cần CSRF, gồm cả login, register, logout.
5. `POST /auth/logout`: hủy phiên hiện tại, xóa cookie phiên, trả 204. Nếu đã hết phiên hoặc không có CSRF hợp lệ có thể trả 401/403 theo filter; không coi logout là thao tác cần replay thành công bằng mọi giá. Lấy CSRF mới trước lần đăng nhập tiếp theo.
6. Đổi mật khẩu, đổi vai trò, khóa/mở khóa làm mất hiệu lực các phiên cũ của user bị tác động. Các request sau phải kiểm tra được phiên đã bị thu hồi; không chỉ sửa một cột mà vẫn tin authority cũ trong session.

Phiên hết hạn sau 30 phút không hoạt động. Cookie HttpOnly; Secure trên HTTPS; SameSite=Lax cho cách triển khai cùng site. Mặc định frontend và API cùng site. Nếu triển khai khác origin, cấu hình cụ thể origins/methods/headers/credentials, cho preflight chạy và để client gửi credentials; không dùng wildcard origin với credentials. Nếu khác site, cần thiết kế lại cấu hình cookie tương ứng, không chỉ bật CORS.

Spring Security có lưu ý riêng về lấy CSRF token sau login/logout: [tài liệu CSRF chính thức](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html).

## 7. Tiền, thời gian và dữ liệu cá nhân

- Tiền tính bằng VND, JSON number thập phân tối đa 2 chữ số sau dấu chấm; Java dùng BigDecimal.
- Các cột tiền DECIMAL(12,2) tương ứng giới hạn 9.999.999.999,99 cho **từng** thành phần. Kiểm tra subtotal, shipping_fee, discount_amount và total_amount trước khi lưu.
- Số lượng là số nguyên; không nhận `"2"` hoặc 1.5 thay số 2.
- Timestamp trả ISO 8601 UTC, ví dụ `2026-09-28T12:00:00Z`; ngày sức khỏe dạng `YYYY-MM-DD`.
- Query date_from/date_to là ngày UTC; date_to bao gồm cả ngày, triển khai bằng mốc đầu ngày kế tiếp loại trừ. date_from > date_to trả 400.
- Không trả password/hash/session_version/request_hash. Public pet response không trả reserved_order_id, nhật ký nội bộ hoặc thông tin liên hệ nhà cung cấp.
- Bản ghi cũ có trường optional chưa có dữ liệu trả null, không bịa giá trị.

## 8. Chống tạo đơn trùng

`POST /orders` bắt buộc header `Idempotency-Key`: 8–100 ký tự chữ/số, `_` hoặc `-`.

- Khóa duy nhất trong phạm vi user. Client tạo khóa mới cho ý định mua mới, giữ nguyên khi retry.
- Cùng khóa + cùng request hợp lệ: lần đầu 201, lần sau 200 với `Idempotency-Replayed: true`, trả lại đơn đã tạo, không trừ tồn lần hai.
- Cùng khóa + request khác: 409 IDEMPOTENCY_CONFLICT.
- Hash từ DTO đã validate/chuẩn hóa: thứ tự key JSON và thứ tự items không làm thay đổi ý nghĩa; items được sắp theo product_id. Shipping mặc định/null được chuẩn hóa trước hash.
- Nếu bỏ items để lấy giỏ, ghi nhận nguồn `cart` trong dấu vân tay request và lưu snapshot items vào đơn. Khi retry phải kiểm tra khóa cũ trước khi đọc lại giỏ đã thay đổi/rỗng.
- Không đưa giá hiện tại từ DB vào hash request của client. Replay trả đơn đã có, gồm cả trạng thái hiện tại nếu đơn đã bị hủy/hết hạn; không tự tạo một đơn thay thế.
- Request thất bại trước commit không tiêu thụ khóa thành công. Giao dịch đã commit mà response bị mất phải truy xuất được bằng cùng khóa.
- Giữ khóa cùng lịch sử đơn; không xóa tùy tiện khi giỏ thay đổi.


