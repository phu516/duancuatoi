# Chuyển dữ liệu từ `pet_shop` sang `pet_shop_api`

## Trạng thái trên máy phát triển

Đã giữ nguyên database nguồn `pet_shop` và nhập phần dữ liệu hiện có vào `pet_shop_api`. Flyway của dự án đã đưa database đích lên V3; các khóa nguồn được ánh xạ sang khóa đích trong `legacy_import_map` để xử lý ID trùng và cho phép chạy lại an toàn.

Kết quả đã xác minh: 2 users, 5 categories, 6 products, 3 pets, 1 cart và 2 cart_items được ánh xạ. Các bảng suppliers, orders, order_items, inventory_movements, order_events, pet_health_records, reviews, shipping_info và coupons ở nguồn đang rỗng. Kiểm tra không phát hiện product/pet/cart_item mồ côi. Mật khẩu legacy dạng văn bản được băm BCrypt trước khi ghi; mật khẩu đã băm được giữ nguyên. Target hiện có 4 tài khoản sau khi giữ 2 tài khoản ban đầu và nhập 2 tài khoản nguồn.

Hai bản sao lưu trước chuyển đổi được lưu tại `C:\Users\nguye\AppData\Local\Temp\pet-shop-db-backup-20260929-153853\pet_shop.sql` và `C:\Users\nguye\AppData\Local\Temp\pet-shop-db-backup-20260929-153853\pet_shop_api.sql`.

## Cấu trúc liên quan

```text
src/main/resources/db/migration/
├── V1__account_catalog.sql       # schema API ban đầu
├── V2__legacy_pet_shop_tables.sql # carts, cart_items, coupons, pet_health_records,
│                                  # order_events, shipping_info
└── V3__legacy_import_tracking.sql # ánh xạ khóa nguồn/đích

src/main/java/com/example/demo/migration/
└── LegacyPetShopImporter.java    # importer một lần, chỉ bật với profile legacy-import
```

V2 chỉ thêm bảng còn thiếu; các bảng users/categories/products/pets hiện có không bị tạo lại hoặc ghi đè. Importer đọc `pet_shop` dạng read-only, kiểm tra đúng hai schema, yêu cầu V1–V3 thành công và từ chối chạy nếu các bảng nguồn chưa được hỗ trợ có dữ liệu. Toàn bộ thay đổi dữ liệu đích chạy trong một transaction; lỗi sẽ rollback. Các bản ghi đích hiện có được giữ nguyên. ID va chạm được tạo ID mới và ghi vào bảng ánh xạ.

## Chạy lại có chủ ý

Mở PowerShell trong thư mục `demo`, cấu hình kết nối như khi chạy API, rồi bật profile và cờ áp dụng:

```powershell
$env:DB_URL='jdbc:mysql://localhost:3306/pet_shop_api?createDatabaseIfNotExist=true&connectionTimeZone=UTC'
$env:DB_USERNAME='root'
$env:DB_PASSWORD='<mat-khau-MySQL>'
$env:APP_LEGACY_SOURCE_URL='jdbc:mysql://localhost:3306/pet_shop?connectionTimeZone=UTC'
$env:APP_LEGACY_SOURCE_USERNAME='root'
$env:APP_LEGACY_SOURCE_PASSWORD='<mat-khau-MySQL>'
$env:APP_LEGACY_IMPORT_APPLY='true'
$env:SPRING_PROFILES_ACTIVE='legacy-import'
.\mvnw.cmd spring-boot:run
```

Chỉ chạy thao tác này sau khi đã sao lưu cả hai database. `legacy_import_map` khiến các hàng đã nhập được bỏ qua khi chạy lại; các hàng mới có thể được nhập thêm. Sau khi kết thúc, tắt ứng dụng và bỏ `SPRING_PROFILES_ACTIVE` cùng `APP_LEGACY_IMPORT_APPLY` trước khi chạy API bình thường. Không dùng profile này để chạy ứng dụng liên tục.
