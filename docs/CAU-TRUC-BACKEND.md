> Cây mục tiêu cho toàn bộ back-end. Hiện đã viết 22 API; cấu trúc thực tế và cách chạy được ghi trong [CHAY-VA-TEST-POSTMAN.md](CHAY-VA-TEST-POSTMAN.md). Các mô tả hiện trạng ban đầu dưới đây là mốc trước triển khai.

# Cấu trúc tổng quát khi hoàn thành back-end

**Toàn bộ lớp và file bên dưới là dự kiến**, trừ `DemoApplication.java`, Maven wrapper, `pom.xml`, `application.properties` và test khởi tạo đã có. Tài liệu API đã nằm trong `docs`. Giữ package `com.example.demo` và sáu thư mục chính đúng như ảnh; bổ sung các thư mục phục vụ cấu hình, bảo mật, ánh xạ DTO và tác vụ hết hạn.

## 1. Cây thư mục mục tiêu

```text
demo/
├── pom.xml
├── mvnw / mvnw.cmd
├── README.md
├── docs/
│   ├── CAU-TRUC-BACKEND.md
│   ├── KE-HOACH-BACKEND.md
│   ├── MO-TA-API.md
│   └── api/
│       ├── 01-QUY-UOC.md
│       ├── 02-DANH-SACH-ENDPOINT.md
│       ├── 03-DU-LIEU-VA-VI-DU.md
│       └── 04-QUY-TAC-NGHIEP-VU.md
├── src/main/java/com/example/demo/
│   ├── DemoApplication.java
│   ├── config/
│   │   ├── SecurityConfig.java
│   │   ├── JsonConfig.java
│   │   ├── WebConfig.java
│   │   ├── OpenApiConfig.java
│   │   ├── ClockConfig.java
│   │   └── ShopProperties.java
│   ├── controller/
│   │   ├── AuthController.java
│   │   ├── UserController.java
│   │   ├── CategoryController.java
│   │   ├── ProductController.java
│   │   ├── PetController.java
│   │   ├── HealthRecordController.java
│   │   ├── SupplierController.java
│   │   ├── CartController.java
│   │   ├── CheckoutController.java
│   │   ├── OrderController.java
│   │   ├── ShippingController.java
│   │   ├── InventoryController.java
│   │   └── ReviewController.java
│   ├── dto/
│   │   ├── request/
│   │   │   ├── auth/          # RegisterRequest, LoginRequest
│   │   │   ├── user/          # ProfileUpdateRequest, ChangePasswordRequest, RoleUpdateRequest
│   │   │   ├── category/      # CategoryCreateRequest, CategoryUpdateRequest
│   │   │   ├── product/       # ProductCreateRequest, ProductUpdateRequest
│   │   │   ├── pet/           # PetCreateRequest, PetUpdateRequest
│   │   │   ├── health/        # HealthRecordCreateRequest, HealthRecordUpdateRequest
│   │   │   ├── supplier/      # SupplierCreateRequest, SupplierUpdateRequest
│   │   │   ├── cart/          # CartItemCreateRequest, CartItemReplaceRequest
│   │   │   ├── checkout/      # CheckoutPreviewRequest, OrderLineRequest
│   │   │   ├── order/         # OrderCreateRequest, OrderStatusUpdateRequest,
│   │   │   │                 # PaymentStatusUpdateRequest, OrderCancellationRequest
│   │   │   ├── shipping/      # ShippingCreateRequest, ShippingUpdateRequest
│   │   │   ├── inventory/     # InventoryAdjustmentRequest
│   │   │   ├── review/        # ReviewCreateRequest, ReviewUpdateRequest
│   │   │   └── common/        # ActiveStatusRequest
│   │   └── response/
│   │       ├── common/        # PageResponse<T>, PaginationResponse, ApiErrorResponse, FieldErrorResponse
│   │       ├── auth/          # CsrfResponse
│   │       ├── user/          # UserResponse
│   │       ├── category/      # CategoryResponse
│   │       ├── product/       # ProductSummaryResponse, ProductDetailResponse, ReviewSummaryResponse
│   │       ├── pet/           # PetProfileResponse, PetSummaryResponse, PetDetailResponse
│   │       ├── health/        # HealthRecordResponse
│   │       ├── supplier/      # SupplierResponse
│   │       ├── cart/          # CartResponse, CartItemResponse
│   │       ├── checkout/      # CheckoutPreviewResponse, CheckoutLineResponse, CheckoutIssueResponse
│   │       ├── order/         # OrderSummaryResponse, OrderDetailResponse, OrderItemResponse,
│   │       │                 # OrderEventResponse, CancellationResponse
│   │       ├── shipping/      # ShippingResponse
│   │       ├── inventory/     # InventoryResponse, InventoryMovementResponse
│   │       └── review/        # ReviewResponse, ReviewAuthorResponse
│   ├── entity/
│   │   ├── User.java
│   │   ├── Category.java
│   │   ├── Product.java
│   │   ├── Pet.java
│   │   ├── HealthRecord.java
│   │   ├── Supplier.java
│   │   ├── Cart.java
│   │   ├── CartItem.java
│   │   ├── CartItemId.java
│   │   ├── Order.java
│   │   ├── OrderItem.java
│   │   ├── ShippingInfo.java
│   │   ├── OrderEvent.java
│   │   ├── InventoryMovement.java
│   │   ├── Coupon.java
│   │   ├── Review.java
│   │   └── enums/            # Role, ProductType, PetGender, PetStatus, OrderStatus,
│   │                         # PaymentMethod, PaymentStatus, DeliveryMethod, DiscountType
│   ├── repository/
│   │   ├── UserRepository.java
│   │   ├── CategoryRepository.java
│   │   ├── ProductRepository.java
│   │   ├── PetRepository.java
│   │   ├── HealthRecordRepository.java
│   │   ├── SupplierRepository.java
│   │   ├── CartRepository.java
│   │   ├── CartItemRepository.java
│   │   ├── OrderRepository.java
│   │   ├── OrderItemRepository.java
│   │   ├── ShippingInfoRepository.java
│   │   ├── OrderEventRepository.java
│   │   ├── InventoryMovementRepository.java
│   │   ├── CouponRepository.java
│   │   ├── ReviewRepository.java
│   │   └── specification/    # Bộ lọc Product, Order, Pet, User theo query được hỗ trợ
│   ├── service/
│   │   ├── AuthService.java
│   │   ├── UserService.java
│   │   ├── CategoryService.java
│   │   ├── ProductService.java
│   │   ├── PetService.java
│   │   ├── HealthRecordService.java
│   │   ├── SupplierService.java
│   │   ├── CartService.java
│   │   ├── CheckoutService.java
│   │   ├── PricingService.java
│   │   ├── OrderService.java
│   │   ├── OrderExpiryService.java
│   │   ├── ShippingService.java
│   │   ├── InventoryService.java
│   │   ├── CouponService.java
│   │   ├── ReviewService.java
│   │   └── IdempotencyService.java
│   ├── exception/
│   │   ├── ApiException.java
│   │   ├── ErrorCode.java
│   │   ├── NotFoundException.java
│   │   ├── ConflictException.java
│   │   ├── BusinessValidationException.java
│   │   └── GlobalExceptionHandler.java
│   ├── security/
│   │   ├── ShopUserPrincipal.java
│   │   ├── ShopUserDetailsService.java
│   │   ├── CurrentUser.java
│   │   ├── SessionVersionFilter.java
│   │   ├── JsonAuthenticationEntryPoint.java
│   │   └── JsonAccessDeniedHandler.java
│   ├── mapper/               # UserMapper, ProductMapper, PetMapper, OrderMapper...
│   ├── validation/           # Kiểm tra password, số điện thoại, PATCH thiếu/null, query
│   ├── filter/               # TraceIdFilter, RequestLimitFilter, RateLimitFilter
│   ├── scheduler/
│   │   └── ExpiredOrderScheduler.java
│   └── bootstrap/
│       └── DevelopmentDataInitializer.java
├── src/main/resources/
│   ├── application.properties
│   ├── application-dev.properties
│   ├── application-test.properties
│   └── db/migration/
│       ├── V1__create_core_schema.sql
│       └── V2__create_order_inventory_indexes.sql
└── src/test/java/com/example/demo/
    ├── DemoApplicationTests.java
    ├── controller/           # Hợp đồng HTTP, DTO, JSON, quyền
    ├── service/              # Tính tiền và chuyển trạng thái
    ├── integration/          # MySQL: transaction, khóa, unique, migration
    └── support/              # Dữ liệu thử và client session/CSRF dùng chung
```

Các tên trong comment là lớp dự kiến trong package đó, không phải gom toàn bộ DTO vào một file. Danh sách trường chi tiết nằm trong [đặc tả DTO](api/03-DU-LIEU-VA-VI-DU.md). Các migration ở cây trên dành cho DB mới; nâng cấp DB cũ có kế hoạch riêng trong [kế hoạch triển khai](KE-HOACH-BACKEND.md).

## 2. Mỗi tầng sẽ làm gì?

| Tầng | Trách nhiệm | Ví dụ khi đặt hàng |
|---|---|---|
| controller | Nhận HTTP/path/query/body/header, validate đầu vào, gọi service, trả HTTP và DTO | OrderController nhận JSON và Idempotency-Key, trả 201 hoặc 200 khi replay |
| dto/request | Chỉ nhận các trường người gọi được phép gửi | OrderCreateRequest không có total_amount, user_id hoặc status |
| service | Quyền trên từng đối tượng, quy tắc nghiệp vụ và ranh giới transaction | OrderService khóa hàng, tính tiền, tạo đơn, trừ tồn, giữ pet |
| repository | Truy vấn DB, phân trang, lọc, khóa hàng dữ liệu | Tìm đơn theo user/khóa; khóa product theo thứ tự ID |
| entity | Ánh xạ bảng, quan hệ và enum | Order, OrderItem, Pet, InventoryMovement |
| mapper + dto/response | Chuyển dữ liệu đã được phép xem thành JSON công khai | OrderDetailResponse có snapshot; UserResponse không có password hash |
| exception | Mã lỗi và cấu trúc lỗi thống nhất | OUT_OF_STOCK → 409; sai dữ liệu → 400 |
| security | Session, CSRF, role, principal và vô hiệu phiên cũ | Lấy người mua từ phiên; thu hồi phiên sau khóa tài khoản |
| scheduler | Tìm đơn hết hạn và gọi service xử lý từng đơn | Hủy pending/unpaid trả trước quá 15 phút |

Controller không gọi thẳng repository. Service dự kiến là lớp cụ thể dùng constructor injection; chưa cần thêm một interface và một lớp Impl cho mọi service. Mapper bắt đầu bằng Java tường minh. Các DTO PATCH phải phân biệt trường không gửi với trường gửi null.

`JsonConfig` chịu trách nhiệm snake_case và kiểu JSON nghiêm ngặt. `ShopProperties` tập trung cấu hình phí giao hàng, thời hạn giữ hàng và giới hạn request. `ClockConfig` cung cấp đồng hồ dùng chung để kiểm thử thời hạn. `DevelopmentDataInitializer` chỉ nạp dữ liệu khi bật chủ ý ở profile dev; không tự chạy với dữ liệu thật.

## 3. Mô hình dữ liệu dự kiến

| Entity | Bảng | Quan hệ và ràng buộc chính |
|---|---|---|
| User | users | Email chuẩn hóa unique; role, is_active, session_version |
| Category | categories | Một danh mục có nhiều product; còn product thì không xóa |
| Product | products | category_id; loại pet/accessory/food; tồn khả dụng và giá |
| Pet | pets | product_id unique; supplier tùy chọn; reserved_order_id tham chiếu đúng đơn giữ |
| HealthRecord | pet_health_records | Nhiều hồ sơ sức khỏe thuộc một pet |
| Supplier | suppliers | Xóa supplier đặt supplier_id của pet thành null |
| Cart | carts | user_id unique |
| CartItem | cart_items | Khóa ghép cart_id/product_id qua CartItemId; quantity dương |
| Order | orders | user_id; unique(user_id, idempotency_key); trạng thái, tiền, request_hash, hạn giữ và cancelled_* |
| OrderItem | order_items | unique(order_id, product_id); lưu tên và giá tại lúc đặt |
| ShippingInfo | shipping_info | order_id unique; lưu snapshot người nhận |
| OrderEvent | order_events | Lịch sử đơn, người thực hiện và thời gian |
| InventoryMovement | inventory_movements | Product, order/actor tùy chọn; tồn trước/sau và lý do |
| Coupon | coupons | code làm khóa; loại giảm, hạn sử dụng, giới hạn và used_count |
| Review | reviews | unique(user_id, product_id); rating 1–5 |

Không cần bảng `inventory` riêng: số tồn nằm ở `products`, lịch sử ở `inventory_movements`. Không cần entity Checkout vì preview là phép tính. Tài nguyên cancellation dùng các cột `cancelled_*` của orders. Coupon có repository/service, chưa có controller quản trị. Session v1 dự kiến lưu trong một instance và kiểm tra session_version của user trên mỗi request bảo vệ; nếu chạy nhiều instance phải bổ sung session store chung hoặc chiến lược triển khai phù hợp.

Tiền dùng BigDecimal và DECIMAL(12,2). Timestamps API dùng UTC; ngày khám dùng ngày không kèm giờ. Tên enum trong JSON/DB tuân theo đặc tả lowercase; Java có thể dùng enum uppercase với ánh xạ rõ ràng. Không serialize trực tiếp các entity có quan hệ hai chiều.

## 4. Phân chia nghiệp vụ đặt đơn

```text
OrderController
  → OrderService.placeOrder(currentUser, request, idempotencyKey)
      → kiểm tra replay và quyền
      → khóa giỏ/product/pet/coupon theo thứ tự thống nhất
      → PricingService: tính tiền từ giá trong DB
      → CouponService: kiểm tra và tiêu thụ lượt
      → tạo Order + OrderItem + ShippingInfo
      → InventoryService: trừ tồn và ghi movement
      → giữ Pet + ghi OrderEvent + cập nhật Cart
      → commit → OrderDetailResponse
```

Toàn bộ thay đổi trên cùng một transaction do OrderService điều phối; các service cộng tác không tự commit riêng. Preview dùng lại phần tính giá nhưng không khóa giữ hàng hay tạo đơn. IdempotencyService chuẩn hóa/hash request; unique trong DB bảo vệ hai request chạy đồng thời. Khi unique conflict, rollback giao dịch thất bại rồi đọc lại đơn đã commit trong giao dịch mới để replay.

Hủy thủ công và hết hạn dùng chung quy tắc giải phóng hàng. Scheduler gọi OrderExpiryService qua một bean riêng, mỗi đơn một transaction, bắt lỗi theo từng đơn để không chặn cả lượt quét. Trước khi hoàn tồn phải khóa/recheck đơn và pet; không hoàn tồn lần hai khi người dùng và scheduler cùng hủy.

## 5. Kết quả bàn giao sau khi viết code

Ứng dụng chạy được với MySQL thử nghiệm; migration tạo schema mới; API/DTO/quyền khớp tài liệu; có Swagger/OpenAPI phù hợp dependency thực tế; có hướng dẫn chạy bằng Maven wrapper, biến môi trường và quy trình test. Các luồng cạnh tranh mua pet, replay, rollback, hủy và hết hạn phải có kiểm thử tích hợp trước khi nối frontend.

