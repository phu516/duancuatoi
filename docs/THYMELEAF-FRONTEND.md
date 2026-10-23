# Giao diện PetVibe bằng Thymeleaf

Chạy `DemoApplication` bằng Java 21 trong module `demo`, rồi mở http://localhost:8080/.
Ứng dụng dùng cùng MySQL và các Service với 65 API hiện có. Cấu hình `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` như backend; mặc định database `pet_shop_api`.

## Các trang

| URL | Chức năng |
|---|---|
| `/` | Trang chủ: danh mục, thú cưng, thức ăn, đánh giá từ database |
| `/products` | Tìm kiếm, lọc loại/danh mục/khoảng giá, sắp xếp, phân trang |
| `/products/{id}` | Giá, tồn, thông tin thú cưng, hồ sơ sức khỏe, đánh giá |
| `/pets` | Lọc hồ sơ thú cưng theo tình trạng, giống và phân trang |
| `/register` | Đăng ký tài khoản customer |
| `/login` | Đăng nhập bằng email và mật khẩu |
| `/account`, `/account/password` | Sửa hồ sơ, đổi mật khẩu và đăng nhập lại |
| `/cart` | Giỏ riêng theo tài khoản, sửa số lượng và xóa sản phẩm |
| `/checkout` | Thông tin nhận hàng, vận chuyển, voucher và báo giá |
| `/orders` | Các đơn của chính tài khoản đang đăng nhập |
| `/orders/{id}` | Sản phẩm và giá lúc đặt, trạng thái, lịch sử, hủy đơn pending |
| `/admin` | Trang tổng quan cho staff/admin; liên kết xuất hiện trong menu tài khoản |
| `/admin/orders`, `/admin/orders/{id}` | Staff/admin lọc và xử lý tất cả đơn, thanh toán, giao nhận |
| `/admin/inventory`, `/admin/inventory/{id}` | Staff/admin xem tồn, nhập/điều chỉnh và xem biến động |
| `/admin/health`, `/admin/pets/{id}` | Staff/admin xem hồ sơ sức khỏe và pet |
| `/admin/users`, `/admin/categories`, `/admin/products`, `/admin/pets`, `/admin/suppliers` | Admin quản lý người dùng và danh mục hàng hóa |

Cả admin/staff vẫn thấy đơn của chính mình ở `/orders`. Customer không được truy cập `/admin`. Các form quản trị dùng cùng Service và quy tắc nghiệp vụ với API.

## Luồng sử dụng

1. Đăng ký hoặc dùng tài khoản đã có trong `users`. Tài khoản Faker nằm trong environment `postman/Demo-faker-v1.postman_environment.json`; có thể xem biến customer_email và customer_password để đăng nhập.
2. Xem sản phẩm và thêm vào giỏ; thú cưng có số lượng cố định 1.
3. Mở Thanh toán, kiểm tra người nhận, số điện thoại, chọn hình thức giao nhận; nhập voucher nếu có.
4. Bấm **Kiểm tra đơn & tính tổng tiền**. Tổng tiền, phí và ưu đãi do CheckoutService tính.
5. Bấm **Xác nhận đặt hàng**. Server kiểm tra lại hàng tồn và giá rồi lưu đơn. Bấm lại cùng lần xác nhận sẽ trả về cùng đơn.
6. Mở **Đơn hàng của tôi** để xem trạng thái hoặc hủy đơn đang chờ xác nhận.
7. Sau khi đơn hoàn tất, mở trang sản phẩm để viết, sửa hoặc xóa đánh giá. Có thể sắp xếp đánh giá theo thời gian hoặc số sao.

Quản trị: admin tạo sản phẩm pet với tồn 1 và trạng thái nháp → tạo hồ sơ tại `/admin/pets` → mở bán trong chi tiết sản phẩm. Staff xử lý đơn, tồn kho và xem sức khỏe; admin còn quản lý danh mục, nhà cung cấp, sản phẩm, pet và tài khoản. Lỗi xóa do dữ liệu đã được dùng sẽ hiển thị lý do từ Service.

Các giá trị trong HTML biểu mẫu không quyết định giá hoặc chủ đơn. Bản xác nhận lưu ở session kèm các sản phẩm/số lượng, thông tin nhận và tổng tiền đã báo. Nếu giá hoặc tổng tiền thay đổi thì người dùng cần báo giá lại. Session giữ tối đa 5 bản xác nhận để hỗ trợ nhiều tab; bản cũ hết hiệu lực sẽ yêu cầu kiểm tra lại đơn.

## Dữ liệu và giao diện

- Sản phẩm: `products`, hình ảnh từ `image_url`.
- Thông tin từng bé: `pets`; sức khỏe: `pet_health_records`.
- Danh mục: `categories`; giỏ: `carts`, `cart_items`.
- Đơn: `orders`, `order_items`, `shipping_info`, `order_events`; ưu đãi: `coupons`.
- Đánh giá: `reviews`, tên người đánh giá từ `users`.
- Khi chưa có ảnh, website hiển thị ảnh thay thế. Bộ Faker cũ có `image_url = NULL`; cập nhật ảnh qua API quản trị sản phẩm để hiển thị ảnh tương ứng.
- Ảnh banner được lấy từ URL có sẵn trong bản thiết kế và lưu tại `static/images/hero-pets.jpg`.
- Giá trên đơn dùng snapshot `order_items`, không thay theo giá bán hiện tại.
- CSS nằm trong dự án, không cần Tailwind CDN khi chạy. Google Fonts có font dự phòng nếu mất mạng.

## Bảo mật và giới hạn nghiệp vụ

Form dùng CSRF của Spring Security qua Thymeleaf `th:action`; token được gắn tự động, không phải thao tác của khách hàng. Sau đăng nhập Spring đổi session ID và token. Trang HTML chuyển về login khi chưa đăng nhập; lỗi API vẫn là JSON theo contract hiện có.

Website hiện nhận **thanh toán khi nhận hàng**. Các phương thức ví/thẻ/chuyển khoản trong bản thiết kế chưa có cổng thu tiền hoặc webhook nên chưa đưa vào luồng mua trên web. Trạng thái thanh toán được đọc từ database, không tự hiển thị là đã trả tiền sau khi đặt.

OTP, OAuth Google/Apple, PetPoints, wishlist, combo, microchip và hóa đơn điện tử là các phần mở rộng riêng. Không dùng số liệu hay tính năng giả từ bản thiết kế.

Đăng nhập giới hạn mặc định 20 lần/15 phút theo IP và 8 lần/15 phút theo email; đăng ký 5 lần/giờ theo IP; đặt đơn 10 lần/phút theo tài khoản. Có thể chỉnh bằng `RATE_LOGIN_IP`, `RATE_LOGIN_EMAIL`, `RATE_REGISTRATION`, `RATE_ORDERS` và các biến `RATE_*_WINDOW_SECONDS`. Khi vượt giới hạn, API trả 429 kèm `Retry-After`. Bộ đếm nằm trong bộ nhớ mỗi instance; địa chỉ IP lấy từ kết nối HTTP, cần cấu hình proxy tin cậy nếu triển khai sau reverse proxy.

## Cấu trúc mã

- `web/`: controller giao diện, form validation, dữ liệu tổng hợp và format hiển thị.
- `templates/`: trang khách hàng và quản trị; `fragments.html` cho header/footer/thẻ sản phẩm/phân trang.
- `static/css/storefront.css`, `static/js/storefront.js`, `static/images/`: giao diện responsive và tương tác nhỏ.
- `OrderService.create(..., expectedTotal)`: kiểm tra giá khi xác nhận sau khi đã khóa tồn kho.

## Kiểm tra

`ApiIntegrationTests` gồm kiểm thử API hiện có và các ca HTTP/MySQL cho website. Test dùng database tên ngẫu nhiên `petshop_test_*`, không chạy thao tác mua thử trên database cửa hàng. Cấu hình `TEST_DB_PASSWORD` trước khi chạy `mvnw.cmd test`.

Các ca web kiểm tra dữ liệu thật và escape HTML, đăng ký/đăng nhập/CSRF xoay vòng, tạo đơn chống trùng, quyền sở hữu, quản trị đơn/kho/pet, đổi mật khẩu, sửa giá giữa báo giá và đặt hàng, voucher sai, form sai và hồ sơ sức khỏe. `RateLimitServiceTests` kiểm tra ngưỡng 429 và thời gian chờ.
