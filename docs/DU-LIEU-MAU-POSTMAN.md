# Dữ liệu mẫu Faker để test Postman

Bộ `faker-v1` đã được nạp vào **MySQL `pet_shop_api` trên localhost**. Đây là database đang được cấu hình cho backend Spring Boot. Script không nạp vào database cũ `pet_shop`, không xóa dữ liệu có sẵn và không đổi mật khẩu của tài khoản cũ.

## 1. Dữ liệu đã thêm

| Bảng | Số dòng mới | Nội dung |
|---|---:|---|
| users | 10 | 1 admin, 2 staff, 6 customer hoạt động và 1 customer bị khóa |
| categories | 6 | Thức ăn, phụ kiện, chó/mèo và danh mục rỗng để test xóa |
| suppliers | 5 | Thông tin giả lập, có nhà cung cấp chưa gắn pet |
| products | 32 | 20 food/accessory và 12 product pet |
| pets | 11 | Available, reserved, sold; có hồ sơ inactive để test xóa |
| pet_health_records | 33 | Mỗi pet có 3 bản ghi khám/tiêm/tẩy giun giả lập |
| carts / cart_items | 3 / 7 | Có giỏ mua được và giỏ chứa hàng hết/ngừng bán |
| coupons | 5 | Giảm 10%, giảm 50.000, hết hạn, tắt, hết lượt |
| orders / order_items | 14 / 23 | Pending, confirmed, shipping, completed, cancelled; nhiều trạng thái thanh toán |
| shipping_info | 14 | Mỗi đơn có thông tin người nhận/giao hàng |
| inventory_movements | 60 | Tồn ban đầu, trừ khi đặt, trả khi hủy; before/change/after khớp |
| order_events | 43 | Lịch sử trạng thái và thu/hoàn tiền |
| reviews | 6 | Đều có đơn completed của đúng chủ review |

Các số trên là dữ liệu **thêm mới tại thời điểm tạo batch**, không phải tổng toàn database. Tên danh mục, product, supplier có tiền tố `[DEMO faker-v1]`. Email dùng miền `example.test`. Tên người/địa chỉ/nội dung mẫu sinh bằng [Faker](https://faker.readthedocs.io/en/master/); liên kết ID và trạng thái được thiết kế theo nghiệp vụ, không random tùy ý. Giá và tổng tiền tính bằng Decimal; mật khẩu lưu BCrypt tương thích Spring Security.

## 2. Test ngay bằng Postman

1. Chạy `DemoApplication` bình thường trên `http://localhost:8080`.
2. Import [environment đã có ID và tài khoản mẫu](../postman/Demo-faker-v1.postman_environment.json).
3. Import [collection đọc dữ liệu mẫu — 44 request](../postman/Seeded-Data.postman_collection.json).
4. Chọn environment **Pet Shop Demo - faker-v1**, bật cookie jar, rồi Run collection.

Collection này đọc dữ liệu, kiểm tra quyền và preview coupon; không tạo/hủy đơn, sửa/xóa product hoặc review. Có các kết quả 401/404 chủ ý được kiểm tra. Collection tự lấy CSRF. Chạy trước khi tự sửa/xóa các fixture bên dưới; sau khi bạn thay đổi dữ liệu, kết quả có thể khác trạng thái ban đầu.

Environment dùng các tên biến giống [checklist 65 API](POSTMAN-TEST-TOAN-BO.md), ví dụ product_id, pet_id, order_id. File có mật khẩu test, được loại khỏi Git. Các collection phần 1–3 và 4–10 cũ vẫn chạy được nhưng tự tạo thêm dữ liệu của riêng chúng.

## 3. Tài khoản mẫu

| ID lúc tạo | Email | Quyền / công dụng |
|---|---|---|
| 5 | admin.faker-v1@example.test | Tài khoản mẫu cũ, hiện dùng quyền staff để hệ thống chỉ có một admin |
| 6 | staff.faker-v1@example.test | Staff xử lý đơn/kho |
| 7 | staff2.faker-v1@example.test | Staff thứ hai để thử quyền/phiên |
| 8 | customer.faker-v1@example.test | Khách chính, đã có đơn completed để đánh giá |
| 9 | customer2.faker-v1@example.test | Khách thứ hai để thử quyền sở hữu |
| 10–13 | customer3 đến customer6.faker-v1@example.test | Khách bổ sung |
| 14 | locked.faker-v1@example.test | Bị khóa; login dự kiến 401 |

Các batch mẫu mới dùng tài khoản admin hiện có và không tạo thêm admin. Hãy điền email và mật khẩu của admin hiện có vào các biến `admin_email` và `admin_password` của Postman. Mật khẩu của các tài khoản mẫu còn lại được tạo ngẫu nhiên khi nạp và lưu trong environment; mỗi hash có salt riêng.

Login khách chính:

```http
POST {{base_url}}/auth/login
Content-Type: application/json
X-CSRF-TOKEN: {{csrf_token}}
```

```json
{"email":"{{customer_email}}","password":"{{customer_password}}"}
```

Nếu test thủ công, GET `/auth/csrf-token` trước login và gọi lại sau login; collection đã tự làm bước này.

## 4. Dữ liệu nào dùng để test chức năng nào?

ID dưới đây đúng cho batch `faker-v1` trên database này. Khi tạo batch mới, ID sẽ khác; ưu tiên dùng biến trong environment tương ứng.

| Chức năng cần thử | Biến / ID ban đầu | Cách thử |
|---|---|---|
| Mua hàng, cộng/thay số lượng giỏ | product_id=10 | Customer POST /cart/items; sản phẩm còn tồn |
| Viết **review mới ngay** | review_product_id=11 | Login customer chính; đã mua trong đơn 1 completed, chưa review sản phẩm 11 |
| Sửa/xóa review của mình | product_id=10, review_id=1 | Login customer chính, PATCH/DELETE /products/10/reviews/1 |
| Tồn thấp | low_stock_product_id=27 | Tồn ban đầu của fixture là 3; GET /inventory?low_stock=5 bằng Staff |
| Hết hàng | out_of_stock_product_id=26 | Tồn 0, thêm giỏ/đặt đơn sẽ bị từ chối |
| Hàng ngừng bán | inactive_product_id=28 | Public GET trả 404; Admin/Staff đọc được |
| Xóa product thành công | deletable_product_id=29 | Admin DELETE; tồn 0, chưa có movement/đơn |
| Mua pet còn available | pet_product_id=30, pet_id=5 | Giỏ/đơn gửi product_id=30; đọc hồ sơ qua /pets/5 |
| Pet đang được giữ | reserved_pet_id=11 | Có trong đơn pending cash số 2, không mua thêm được |
| Pet đã bán | sold_pet_id=14 | Có trong đơn completed số 1 |
| Xóa hồ sơ pet thành công | deletable_pet_id=15 | Admin DELETE; pet inactive, chưa có đơn; health bị xóa, product giữ lại |
| Tạo hồ sơ pet mới | draft_pet_product_id=41 | Admin POST /pets với product_id=41; sau đó mở bán product |
| Pet available nhưng có lịch sử | historical_pet_id=9 | Đã có đơn hủy số 13; DELETE /pets/9 trả 409 |
| Đọc/sửa health record | pet_id=5, record_id=1 | /pets/5/health-records/1 |
| Xóa danh mục rỗng | empty_category_id=13 | Admin DELETE /categories/13 |
| Xóa supplier chưa liên kết | empty_supplier_id=5 | Admin DELETE /suppliers/5 |
| Xóa supplier đang liên kết | supplier_id=1 | Admin DELETE; pet giữ nguyên với supplier_id=null |
| Lịch sử tồn | product_id=10, movement_id=3 | Staff GET /inventory/10/movements/3 |
| Giỏ có dòng không khả dụng | customer2 | GET /cart có hàng hết tồn/ngừng bán, available=false |

Ví dụ tạo review mới ngay:

```http
POST {{base_url}}/products/{{review_product_id}}/reviews
```

```json
{"rating":5,"comment":"Đánh giá thử từ khách đã mua hàng"}
```

Kỳ vọng 201 khi login customer chính. Gửi lần hai sẽ trả 409 vì đã có review.

## 5. Đơn mẫu và trạng thái

| ID | Chủ | Trạng thái / thanh toán ban đầu | Biến hoặc cách sử dụng |
|---|---|---|---|
| 1 | Customer chính | completed / paid | order_id; xem snapshot, items, events và tạo review cho product 11 |
| 2 | Customer chính | pending / unpaid, cash | cancel_order_id; hủy lần đầu 201, lặp 200; hoàn tồn và giải phóng pet |
| 3 | Customer chính | pending / paid, bank_transfer | paid_order_id; có thể xác nhận hoặc hủy rồi hoàn tiền |
| 4 | Customer chính | confirmed / unpaid, cash | confirmed_order_id; chuyển shipping, ghi paid, rồi completed |
| 5 | Customer chính | shipping / paid | shipping_order_id; Staff có thể chuyển completed ngay |
| 6 | Customer chính | cancelled / unpaid | cancelled_order_id; xem cancellation, hủy lặp không cộng tồn |
| 7 | Customer chính | cancelled / paid | refund_order_id; Staff PATCH payment-status=refunded |
| 8 | Customer chính | cancelled / refunded | Xem đơn đã hoàn tiền; không mở lại đơn |
| 9 | Customer2 | pending / unpaid, bank_transfer | prepaid_order_id; có hạn 15 phút tính từ thời điểm tạo đơn mẫu |
| 10 | Customer2 | completed / paid | other_order_id; customer chính đọc trả 404, Staff đọc được |
| 11 | Customer3 | completed / paid | Có các review của customer3 |
| 12 | Customer2 | cancelled / unpaid | Đơn mẫu đã hết hạn, cancelled_by=null |
| 13 | Customer chính | cancelled / unpaid | Có pet đã hoàn tồn nhưng không được xóa hồ sơ vì có lịch sử |
| 14 | Customer4 | completed / paid | Có sản phẩm bán hết tồn và review của customer4 |

Đơn 9 sẽ bị scheduler hủy khi backend chạy và nhận thấy đã quá hạn; đó là hành vi đúng, không phải seed lỗi. Không làm mới hạn này khi chạy lại cùng batch. Muốn test khoảng 15 phút từ đầu, tạo đơn mới bằng API hoặc nạp một batch mới.

Các đơn seed là dữ liệu lịch sử chuẩn bị sẵn, idempotency_key/request_hash=null. Để test chống trùng đơn, tạo **đơn mới qua POST /orders** với `Idempotency-Key: {{order_key}}`, rồi gửi lại đúng body/key.

Ví dụ Staff ghi nhận hoàn tiền cho đơn 7:

```http
PATCH {{base_url}}/orders/{{refund_order_id}}/payment-status
```

```json
{"payment_status":"refunded","note":"Ghi nhận hoàn tiền mẫu"}
```

## 6. Coupon mẫu

| Mã | Điều kiện / kỳ vọng |
|---|---|
| FAKER-V1-PERCENT10 | Giảm 10%, subtotal từ 100.000; đang active |
| FAKER-V1-FIXED50K | Giảm 50.000, subtotal từ 100.000; đang active |
| FAKER-V1-EXPIRED | Đã hết hạn → COUPON_INVALID |
| FAKER-V1-INACTIVE | Bị tắt → COUPON_INVALID |
| FAKER-V1-EXHAUSTED | Đã hết lượt → COUPON_INVALID |

Ví dụ customer POST `/checkout/preview`:

```json
{
  "items":[{"product_id":{{product_id}},"quantity":2}],
  "shipping":{"recipient_name":"Khách test","recipient_phone":"0901234567","delivery_method":"pickup"},
  "coupon_code":"{{coupon_code}}"
}
```

Với giá ban đầu 120.000, kỳ vọng subtotal=240000, discount_amount=24000, total_amount=216000, can_place_order=true. Các biến expired_coupon_code, inactive_coupon_code, exhausted_coupon_code giúp test trường hợp lỗi; preview trả HTTP 200 nhưng can_place_order=false.

## 7. Script và cách tạo thêm dữ liệu

- [Script Faker + PyMySQL](../scripts/seed_demo_data.py).
- [Thư viện được khóa phiên bản](../scripts/seed-requirements.txt).
- [Lệnh PowerShell tiện dụng](../scripts/seed-demo.ps1).
- [Kiểm thử seed trên database riêng](../scripts/test_seed_demo_data.py).
- [Kiểm tra dữ liệu qua API](../scripts/check_seeded_api.py).

Trong thư mục `demo`:

```powershell
.\scripts\seed-demo.ps1 -Database pet_shop_api -Batch faker-v1
```

Batch faker-v1 đã tồn tại nên chạy lại **không thêm trùng, không reset dữ liệu bạn đã test, không đổi mật khẩu**. Script xuất lại manifest/environment. Nếu mất environment và cần xuất lại mật khẩu cho batch cũ, truyền SEED_PASSWORD bằng đúng mật khẩu ban đầu; script không thể khôi phục mật khẩu từ BCrypt. Khi file cũ không còn và không có SEED_PASSWORD, ô mật khẩu xuất ra sẽ rỗng.

Muốn một bộ thử mới, giữ nguyên bộ cũ:

```powershell
.\scripts\seed-demo.ps1 -Database pet_shop_api -Batch faker-v2
```

Sau đó import environment `Demo-faker-v2.postman_environment.json` mới được sinh. Có thể đặt SEED_PASSWORD trước lần tạo batch đầu nếu muốn tự chọn mật khẩu test. Script đọc DB_URL/DB_USERNAME/DB_PASSWORD giống ứng dụng, chỉ kết nối MySQL local, yêu cầu --database khớp chính xác database đã cấu hình và kiểm tra schema trước khi ghi.

Metadata chống nạp trùng nằm trong bảng phụ `demo_seed_batches`; manifest không chứa mật khẩu. Bảng này do script tạo, không sửa các bảng nghiệp vụ hiện có. Dữ liệu nghiệp vụ và bản ghi batch được commit cùng một transaction. Có khóa MySQL để hai lần seed đồng thời không tạo hai bộ dữ liệu. Nếu lỗi trước commit, toàn bộ dòng nghiệp vụ mới được rollback; bảng metadata rỗng có thể còn lại.

Không có chức năng tự xóa/reset batch. Không xóa bảng metadata rồi chạy lại để thử reset: dữ liệu liên quan đã được dùng trong đơn/giỏ có thể đang tồn tại.

## 8. Đã xác minh

- 2 kiểm thử MySQL độc lập đạt: kiểm tra quan hệ/tổng tiền/tồn/password, chạy lại không trùng và giữ chỉnh sửa; mô phỏng lỗi trước commit để kiểm tra rollback toàn bộ batch.
- Đã nạp faker-v1 vào pet_shop_api và gọi lại script; lần hai trả created=false, không thêm dòng.
- 45 request HTTP và các assertion đi kèm đạt trên chính dữ liệu đã nạp, gồm login bằng BCrypt, quyền truy cập, preview coupon, trạng thái đơn/pet, dữ liệu review và giỏ có hàng không khả dụng.
- Server kiểm tra tạm ở cổng 18081 đã dừng. Chạy ứng dụng bình thường ở cổng 8080 để test trong Postman.
- Collection Seeded-Data mới có 44 request, chưa chạy bằng Newman; kết quả HTTP ở trên từ script check_seeded_api.py.

Kết quả cục bộ: `target/seed-data/faker-v1-manifest.json` và `target/seed-data/faker-v1-api-check.json`. Các trạng thái/số lượng có thể thay đổi khi bạn tiếp tục test API.
