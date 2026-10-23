> Hợp đồng toàn bộ back-end: hiện đã triển khai nhóm 1–3 (22 API tài khoản, danh mục, sản phẩm); các nhóm còn lại là kế hoạch. Xem [trạng thái và hướng dẫn chạy](../CHAY-VA-TEST-POSTMAN.md). Kế thừa từ [NEW-API/04-QUY-TAC-NGHIEP-VU.md](../../../NEW-API/04-QUY-TAC-NGHIEP-VU.md) ngày 29/09/2026. Khi triển khai `demo`, cập nhật đồng bộ bộ tài liệu trong `demo/docs/api`; bản NEW-API được giữ làm tham chiếu thiết kế ban đầu.

# 04. Quy tắc nghiệp vụ

Đây là yêu cầu cho backend Spring Boot mới. Không suy ra tính đúng của nghiệp vụ chỉ từ việc controller trả được HTTP 200. Các thay đổi đơn, tồn, pet, coupon và nhật ký cần có tính nhất quán ở database.

## 1. Tài khoản và quyền

- Tài khoản đăng ký mới luôn có role customer và is_active=true. Client không tự cấp quyền.
- Chỉ admin đổi role/khóa/mở khóa người khác; không tự khóa hoặc đổi role của chính mình. Hệ thống chỉ có một tài khoản admin: không thể nâng tài khoản khác lên admin, không thể tự khóa hay tự hạ quyền. Các tài khoản khác chỉ được phân quyền customer hoặc staff.
- Đổi password phải xác minh mật khẩu cũ và thu hồi tất cả phiên của user. Khóa, mở khóa và đổi role cũng thu hồi phiên cũ; mở khóa không khôi phục session trước đó.
- Khóa tài khoản không xóa đơn/review/giỏ. Nhân viên vẫn có thể xử lý đơn đã tồn tại; không tự hủy đơn hoặc tự hoàn tiền khi khóa user.
- Giới hạn đăng nhập theo IP và email chuẩn hóa; giới hạn đăng ký/tạo đơn theo cấu hình. 429 kèm Retry-After. Không dùng CSRF như một cơ chế chống brute force.

## 2. Sản phẩm và cá thể thú cưng

- product_type=pet nghĩa là một cá thể. Không bán quantity=2 cho cùng pet.
- Pet được tạo theo ba bước: tạo product pet với stock_quantity=1 và inactive → tạo hồ sơ pets → mở bán. Lỗi ở bước sau để lại bản nháp inactive, không tự hiển thị như hàng có thể mua.
- Mở bán product pet phải có hồ sơ pet. available phải có tồn 1; reserved/sold phải có tồn 0. Việc mở bán lại không tự chuyển reserved/sold thành available; trang public có thể hiển thị pet đã giữ/bán với trạng thái tương ứng nhưng checkout từ chối mua.
- Trường product_type và pets.product_id không đổi sau khi tạo. Sửa giá không thay giá đã lưu trong order_items.
- Xóa product chỉ khi chưa có order_items và inventory_movements; nếu có lịch sử trả 409. Có pet phụ thuộc nhưng chưa có lịch sử đơn thì phải xóa hồ sơ pet theo quy tắc bên dưới trước khi xóa product; không tự xóa sức khỏe bằng DELETE product.
- Xóa hồ sơ pet chỉ khi available, chưa từng có đơn tham chiếu product. Cùng giao dịch: ngừng bán product, xóa health records phụ thuộc rồi xóa pet. Product giữ nguyên tồn 1 và lịch sử nhập ban đầu; có thể tạo lại hồ sơ đúng cho product đó nếu cần. Pet từng có đơn, kể cả đơn hủy, không được xóa.
- Xóa supplier giữ pet và đặt supplier_id=null. Xóa category khi còn product bị chặn.
- API public dùng DTO có danh sách trường rõ ràng; không serialize trực tiếp entity và toàn bộ quan hệ.

## 3. Giỏ hàng

- User có tối đa một carts; mỗi product có tối đa một dòng trong cart_items.
- Thêm vào giỏ không giữ pet và không trừ tồn. Nhiều người có thể thêm cùng một pet vào giỏ.
- Thêm/sửa phải kiểm tra active, tồn và trạng thái pet. Dòng pet luôn quantity=1; cộng thêm thành 2 bị từ chối.
- Giỏ giữ sau logout, session hết hạn, đổi thiết bị. GET giỏ trả giá hiện tại của catalog, khác giá snapshot trong đơn.
- Sản phẩm hết hàng/ngừng bán vẫn có thể hiện trong giỏ cũ với available=false để người dùng xóa. Không cho checkout thành công với dòng không khả dụng.
- Giới hạn 100 product/giỏ phải được kiểm tra cả khi thêm dòng mới; PUT và POST thực hiện trong transaction để không vượt giới hạn bởi nhiều request đồng thời.

## 4. Preview

- Đọc items client gửi hoặc giỏ hiện tại. Không gửi cả hai nguồn riêng; việc có/bỏ items quyết định nguồn.
- Tính giá từ database, phí từ cấu hình, coupon từ bảng coupons.
- Có vấn đề tồn/coupon thì tổng hợp issues theo hợp đồng file 03, không tạo đơn dở dang.
- Preview chỉ phản ánh tại thời điểm đọc, không hứa giữ giá hoặc tồn. Client phải chấp nhận rằng POST /orders có thể trả 409 sau một preview thành công.
- GET và preview không cần quét/hủy các đơn khác; việc hết hạn do scheduler/luồng xử lý hết hạn chịu trách nhiệm. Nếu gặp pet đang giữ bởi đơn hết hạn nhưng chưa được giải phóng, vẫn coi chưa mua được tới khi giao dịch giải phóng hoàn tất.

## 5. Tạo đơn trong một transaction

Các bước logic cần đảm bảo:

1. Xác định user từ principal; kiểm tra user/phiên vẫn hợp lệ trước thao tác ghi.
2. Kiểm tra Idempotency-Key và tìm đơn cùng user/khóa. Nếu có, so khớp request và replay trước khi đọc lại giỏ.
3. Đọc/khóa giỏ nếu dùng giỏ; validate 1–100 dòng, không trùng product ID.
4. Khóa product theo thứ tự ID tăng dần; khóa pet liên quan; kiểm tra active, quantity và tồn khả dụng. Mọi luồng cùng sửa tài nguyên phải thống nhất thứ tự khóa để giảm deadlock.
5. Khóa coupon nếu có và kiểm tra hiệu lực/số lượt trong chính giao dịch.
6. Tính tiền bằng BigDecimal; kiểm tra giới hạn cho từng thành phần; không lấy price, total, user_id hay status từ client.
7. Tạo orders pending/unpaid; lưu snapshot giá và tên vào order_items, người nhận vào shipping_info.
8. Trừ tồn, đặt pet reserved và gắn reserved_order_id; ghi inventory_movements và order_events.
9. Tăng used_count coupon một lần. Xóa/giảm đúng số lượng những dòng đã mua khỏi giỏ, giữ nguyên sản phẩm khác. Việc này cũng áp dụng khi items được gửi trực tiếp mà giỏ có các sản phẩm tương ứng.
10. Commit toàn bộ rồi trả 201. Nếu bất kỳ bước nào lỗi, rollback toàn bộ; không để đơn không có item hoặc tồn đã trừ nhưng đơn chưa tạo.

Hai request mua cùng một pet: tối đa một request thành công; request còn lại nhận 409 PET_UNAVAILABLE/OUT_OF_STOCK. Hai request cùng user/khóa: chỉ có một đơn; request còn lại replay hoặc nhận lỗi retry tạm thời theo xử lý lock, tuyệt đối không tạo hai đơn.

Unique(user_id,idempotency_key) là bảo vệ cuối cùng. Không chỉ làm `exists` rồi `insert` ngoài transaction. Nếu gặp unique conflict do cạnh tranh, đọc lại bản ghi đã commit và đối chiếu request. Nếu phải retry do deadlock, lặp giao dịch bằng cùng khóa, không lặp riêng lẻ một lệnh trừ tồn.

Không giữ transaction/row lock trong lúc gọi dịch vụ thanh toán hoặc mạng bên ngoài. V1 chỉ ghi nhận thanh toán thủ công.

## 6. Công thức tiền và coupon

```text
line_total      = unit_price × quantity
subtotal        = tổng line_total
shipping_fee    = 0 nếu pickup, ngược lại theo cấu hình phương thức
discount_amount = min(giảm giá hợp lệ, subtotal)
total_amount    = subtotal + shipping_fee - discount_amount
```

- Cấu hình shipping_fee mặc định 0 VND cho bản phát triển, cần đặt giá thực trước vận hành. Không coi mặc định này là chương trình miễn phí vận chuyển.
- Coupon đang active, đã bắt đầu, chưa hết hạn, đủ min_subtotal và còn lượt mới hợp lệ. Thời hạn sử dụng: starts_at <= now < ends_at theo UTC.
- Giảm phần trăm làm tròn HALF_UP tới 2 chữ số thập phân; không giảm vào shipping_fee.
- Không có coupon mặc định được tự kích hoạt. Chưa có API quản trị coupon trong v1; dữ liệu thử nghiệm chỉ được nạp có chủ ý vào môi trường phát triển.
- Theo yêu cầu gốc: đơn hủy/hết hạn vẫn tính lượt coupon đã dùng. Đây là chính sách cần xem xét lại nếu shop muốn tính lượt theo đơn mua thành công.

## 7. Trạng thái đơn

```text
pending → confirmed → shipping → completed
    └──→ cancelled
```

| Chuyển trạng thái | Ai được làm | Điều kiện |
|---|---|---|
| Tạo pending | Authenticated | Checkout thành công |
| pending → confirmed | Staff | Chưa hết hạn; nếu trả trước phải paid |
| confirmed → shipping | Staff | Đúng trạng thái trước; pet vẫn thuộc lượt giữ của đơn |
| shipping → completed | Staff | Phải paid; pet của đơn chuyển sold |
| pending → cancelled | Owner/Staff hoặc scheduler | Thực hiện bằng luồng hủy có lý do và hoàn tồn |

PATCH status gửi lại đúng trạng thái hiện tại trả 200 không ghi thêm event hoặc thay tồn. Không cho nhảy cóc, lùi trạng thái hoặc mở lại đơn hủy. Khi đơn đã confirmed, cash vẫn có thể unpaid cho tới trước completed.

Giữ luồng nguồn cả cho pickup trong v1; shipping khi pickup được hiểu là chuẩn bị bàn giao. Nếu muốn confirmed → completed trực tiếp cho pickup, phải bổ sung quy tắc và test riêng, không tự bỏ qua kiểm tra thanh toán.

## 8. Trạng thái thanh toán

```text
unpaid → paid → refunded
```

- unpaid → paid: chỉ Staff, đơn không cancelled và chưa hết hạn giữ thanh toán; ghi note và actor. Bỏ hạn chờ thanh toán của đơn/pet nhưng pet vẫn reserved.
- paid → refunded: chỉ Staff và đơn đã cancelled; chỉ ghi nhận sau khi hoàn tiền ngoài hệ thống thật sự hoàn tất.
- Không chuyển ngược về unpaid, không refund một đơn chưa paid. Lặp lại trạng thái hiện tại trả 200, giữ event cũ; không tạo thêm lần thu/hoàn tiền.
- cash/bank_transfer/card/e_wallet trong v1 là thông tin phương thức, không chứng minh đã tích hợp cổng thu tiền.
- Hủy đơn paid giữ payment_status=paid cho tới khi có thao tác refunded. Không tự đổi thành refunded vì người mua bấm hủy.

## 9. Hủy đơn và giữ thú cưng

- PUT cancellation lần đầu khóa đơn, kiểm tra quyền và status pending, trả tồn từng dòng đúng một lần, giải phóng pet đúng reserved_order_id, lưu lý do/người/thời điểm và event trong cùng transaction.
- Đơn đã cancelled: trả tài nguyên hủy ban đầu với 200, không thay lý do, không cộng tồn lần nữa. Quyền sở hữu vẫn phải kiểm tra trước khi trả dữ liệu.
- Đơn confirmed/shipping/completed: 409 INVALID_ORDER_TRANSITION, cả staff cũng không dùng endpoint này để hủy.
- Trạng thái pet: available → reserved khi đặt đơn; reserved → sold khi completed; reserved → available khi đúng đơn giữ bị hủy.
- Đơn thanh toán trước unpaid/pending hết 15 phút: scheduler mỗi phút tìm và hủy nguyên đơn, trả tồn và pet. Kiểm tra lại sau khi khóa; đơn vừa được paid thì không hủy.
- Cash không có hạn 15 phút theo yêu cầu gốc. Đây là rủi ro giữ pet lâu nếu người mua bỏ đơn; nên bổ sung thời hạn xác nhận riêng/đặt cọc ở giai đoạn tiếp theo.
- Hủy hoàn tồn và adjustment đều phải đảm bảo tồn sau cùng trong giới hạn lưu trữ/nghiệp vụ. Khi chỉnh tăng hàng, không cho tổng tồn khả dụng cộng lượng đang giữ trong các đơn chưa completed/cancelled vượt trần 1000000, để việc hủy sau này không bị kẹt vì vượt trần.
- Lỗi dữ liệu ở một đơn hết hạn phải được log và cô lập, không ngăn xử lý các đơn còn lại. Nhiều instance chạy scheduler cần khóa/claim/recheck để không hoàn tồn hai lần.

## 10. Giao hàng, tồn kho, review

**Giao hàng:** tạo shipping cùng order. Chỉ Owner/Staff sửa khi pending/confirmed. Merge các trường PATCH rồi validate lại toàn bộ; không bỏ địa chỉ nếu không phải pickup. Không sửa delivery_method; muốn đổi thì hủy/đặt lại khi còn pending. Snapshot không tự đổi theo hồ sơ user. Ghi event shipping khi thông tin giao nhận thực sự thay đổi, nhưng không đưa toàn bộ địa chỉ vào nhật ký công khai không cần thiết.

**Tồn kho:** stock_quantity là tồn khả dụng, không phải toàn bộ số hàng vật lý trong cửa hàng. Nhập/điều chỉnh, tạo/hủy đơn đều có movement gồm before/change/after, actor và reason. Tạo product có tồn ban đầu phải ghi opening_stock. Không sửa tồn thông qua product PATCH; không adjustment pet. Giữ lịch sử movement, không cung cấp API xóa/sửa movement.

**Review:** tài khoản customer đã đăng nhập có thể đánh giá sản phẩm đang bán mà không cần mua trước. Một user/product tối đa một review; cập nhật cần đúng chủ, xóa cho phép chủ/admin. DB unique(user_id,product_id) cùng service validation bảo vệ cạnh tranh. Chủ review vẫn có thể sửa review đã tạo nếu sản phẩm ngừng bán.

## 11. Liên hệ với database

| Nghiệp vụ | Bảng đề xuất sử dụng |
|---|---|
| Tài khoản, quyền, thu hồi phiên | users + session store/registry tùy cách triển khai |
| Catalog | categories, products |
| Thú cưng, sức khỏe, nhà cung cấp | pets, pet_health_records, suppliers |
| Giỏ hàng | carts, cart_items |
| Đặt hàng/giao nhận/hủy | orders, order_items, shipping_info; cancellation có thể ánh xạ các cột cancelled_* của orders |
| Khuyến mại | coupons |
| Nhật ký | inventory_movements, order_events |
| Đánh giá | reviews |

API không bắt buộc một endpoint phải tương ứng một bảng. `/checkout/preview` là tính toán; `/orders/{id}/cancellation` là tài nguyên có thể lưu bằng các cột trên orders, không bắt buộc thêm bảng cancellation.

Ràng buộc nên có: email chuẩn hóa unique; carts.user_id unique; cart_items PK(cart_id,product_id); pets.product_id unique; shipping_info.order_id unique; order_items unique(order_id,product_id); reviews unique(user_id,product_id); orders unique(user_id,idempotency_key). Có FK và CHECK cho giá/tồn/số lượng/rating. Quan hệ status pet–order–stock liên bảng phải được đảm bảo bằng transaction, không chỉ bằng một CHECK đơn giản.

Đặc tả không tự thay schema hiện tại. Trước khi triển khai cần viết migration có phiên bản, đối chiếu dữ liệu cũ, đặc biệt đơn pet đang mở chưa có reserved_order_id và các trường snapshot trước đây chưa lưu.

## 12. Chức năng mở rộng sau v1

- Thời hạn xác nhận cash, đặt cọc pet, xác minh email/điện thoại và quên mật khẩu.
- Hủy sau xác nhận có kiểm soát, giao thất bại, hoàn hàng/hoàn tiền sau hoàn tất.
- Payment transactions, payment attempts, refund records và webhook được xác minh nếu tích hợp cổng thanh toán.
- Coupon CRUD, giới hạn theo user và chính sách trả lại lượt khi hủy.
- Ảnh nhiều góc, biến thể phụ kiện, lô và hạn dùng thức ăn.
- Phân loại cách vận chuyển được phép cho thú cưng; xử lý đơn hỗn hợp pet/hàng hóa.

Không ghi các chức năng này vào frontend như thể backend v1 đã hỗ trợ.


