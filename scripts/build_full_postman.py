"""Build one request per implemented endpoint. Contains no passwords."""
import json
from pathlib import Path
import re
import uuid

ROOT = Path(__file__).resolve().parents[1]
guide = (ROOT / "docs/POSTMAN-TEST-TOAN-BO.md").read_text(encoding="utf-8")
spec = (ROOT / "docs/api/02-DANH-SACH-ENDPOINT.md").read_text(encoding="utf-8")
rows = re.findall(r"^\| (\d{2}) \| (GET|POST|PUT|PATCH|DELETE) `([^`]+)` \| ([^|]+) \| ([^|]+) \| ([^|]+) \|", guide, re.M)
contracts = {}
for line in spec.splitlines():
    if re.match(r"\| (GET|POST|PUT|PATCH|DELETE) \|", line):
        cells = [x.strip() for x in line.strip("|").split("|")]
        contracts[(cells[0], cells[1].strip("`"))] = cells

shipping = {"recipient_name":"Khách test Postman", "recipient_phone":"0901234567", "delivery_method":"pickup", "address":None, "note":"Nhận tại cửa hàng"}
bodies = {
    2: {"full_name":"Khách mới Postman", "email":"{{register_email}}", "password":"{{register_password}}", "password_confirmation":"{{register_password}}", "phone":"0901234567", "address":"12 Đường Hoa"},
    3: {"email":"{{login_email}}", "password":"{{login_password}}"},
    6: {"full_name":"Khách đã sửa tên", "phone":"0901234567", "address":"20 Đường Hoa"},
    7: {"old_password":"{{customer_password}}", "new_password":"{{new_password}}", "password_confirmation":"{{new_password}}"},
    10: {"role":"staff"}, 11: {"is_active":False},
    14: {"name":"Danh mục Postman mới"}, 15: {"name":"Danh mục đã sửa"},
    19: {"category_id":"#category_id", "product_type":"food", "name":"Thức ăn test Postman", "price":120000, "stock_quantity":20, "description":"Sản phẩm tạo thử", "image_url":None},
    20: {"name":"Thức ăn đã sửa", "price":125000, "description":"Mô tả cập nhật"}, 21: {"is_active":False},
    25: {"product_id":"#draft_pet_product_id", "supplier_id":"#supplier_id", "breed":"Poodle", "age":"3 tháng", "gender":"female", "color":"Nâu", "weight":2.5, "is_neutered":False, "vaccination_status":"Đã tiêm mũi đầu"},
    26: {"weight":3.2, "color":"Nâu đậm", "is_neutered":True},
    30: {"record_date":"2026-09-01", "record_type":"vaccination", "description":"Bản ghi tiêm phòng thử", "vet_name":"Bác sĩ An"},
    31: {"description":"Đã tái khám", "vet_name":"Bác sĩ An"},
    36: {"name":"Nhà cung cấp Postman mới", "contact_info":"0901234567", "address":"30 Đường Hoa"},
    37: {"contact_info":"0907654321", "address":None},
    40: {"product_id":"#product_id", "quantity":2}, 41: {"quantity":3},
    44: {"items":[{"product_id":"#product_id", "quantity":2}], "shipping":shipping, "coupon_code":"{{coupon_code}}"},
    45: {"items":[{"product_id":"#product_id", "quantity":1}], "shipping":shipping, "coupon_code":None, "payment_method":"cash"},
    50: {"status":"completed"}, 51: {"payment_status":"refunded", "note":"Đã hoàn tiền mẫu"},
    52: {"reason":"Tôi đổi ý"},
    55: {"recipient_name":"Người nhận mới", "recipient_phone":"0907654321", "address":"30 Đường Hoa", "note":"Gọi trước khi tới"},
    60: {"change":10, "reason":"Nhập hàng test", "reference":"PN-POSTMAN-01"},
    63: {"rating":5, "comment":"Sản phẩm tốt"}, 64: {"rating":4, "comment":None},
}
variables = {"userId":"customer_id", "categoryId":"category_id", "productId":"product_id", "petId":"pet_id", "supplierId":"supplier_id", "recordId":"record_id", "orderId":"order_id", "movementId":"movement_id", "reviewId":"review_id"}
special = {
    10:{"userId":"staff_id"}, 11:{"userId":"staff_id"},
    16:{"categoryId":"empty_category_id"}, 22:{"productId":"deletable_product_id"},
    27:{"petId":"deletable_pet_id"}, 38:{"supplierId":"empty_supplier_id"},
    50:{"orderId":"shipping_order_id"}, 51:{"orderId":"refund_order_id"},
    52:{"orderId":"cancel_order_id"}, 53:{"orderId":"cancelled_order_id"},
    55:{"orderId":"cancel_order_id"}, 63:{"productId":"review_product_id"},
}
query = {
    8:{"keyword":"{{seed_batch}}", "role":"customer", "is_active":"true"},
    12:{"keyword":""},
    17:{"category_id":"{{category_id}}", "product_type":"food", "keyword":"", "min_price":"100000", "max_price":"500000", "sort":"newest", "is_active":"true"},
    23:{"status":"available", "breed":"Poodle", "supplier_id":"{{supplier_id}}"},
    28:{}, 33:{"pet_id":"{{pet_id}}"}, 34:{"keyword":""},
    46:{"status":"pending", "payment_status":"unpaid", "date_from":"2026-09-01", "date_to":"2026-09-30", "user_id":"{{customer_id}}"},
    49:{}, 56:{"low_stock":"5"}, 58:{}, 61:{"sort":"newest"},
}
notes = {
    2:"Email được sinh mới khi gửi request. Đăng ký không tự đăng nhập; không cấp role trong body.",
    3:"Biến login_role trong Variables của collection chọn admin/customer/staff/customer2. Mặc định admin. Request script lấy email/password của role đó từ environment Demo-faker-v1. Sau khi gửi, GET /users/me để xác nhận phiên.",
    4:"Đăng xuất phiên hiện tại; sau đó phải login lại trước request cần quyền.",
    7:"Dùng phiên customer; thao tác đổi mật khẩu sẽ thu hồi các phiên. Sau 204 cập nhật customer_password trong environment bằng new_password.",
    10:"Mặc định dùng staff_id để không thay role customer chính. User bị đổi quyền phải login lại.",
    11:"Mặc định khóa staff_id. Đổi body thành true để mở khóa; phải login lại.",
    16:"Đã chọn empty_category_id. Category còn product, kể cả ngừng bán, sẽ trả 409.",
    19:"Food/accessory có thể mua ngay. Để tạo product pet, đổi product_type=pet, stock_quantity=1; tạo hồ sơ rồi mở bán. ID mới lưu trong created_product_id, không ghi đè ID fixture.",
    20:"Không nhận stock_quantity, product_type, is_active. Giá mới không thay snapshot trong đơn cũ.",
    21:"false để ngừng bán, true để mở lại. Pet phải có hồ sơ trước khi mở bán.",
    22:"Đã chọn deletable_product_id tồn 0, chưa có lịch sử. Product có movement/đơn sẽ trả 409.",
    25:"Dùng draft_pet_product_id đã có product pet nháp, chưa có hồ sơ. Một product chỉ có một pet; gửi lại sẽ 409.",
    27:"Đã chọn deletable_pet_id chưa có đơn. Xóa pet xóa health liên quan và ngừng bán product.",
    30:"record_date phải <= ngày hiện tại. Không gửi pet_id trong body.",
    38:"Đã chọn empty_supplier_id. Nếu chọn supplier đang liên kết, xóa vẫn giữ pet với supplier_id=null.",
    40:"POST cộng quantity vào dòng hiện tại; không giữ/trừ hàng. Không cộng pet thành quantity=2.",
    41:"PUT đặt số lượng tuyệt đối; gửi lặp vẫn giữ quantity=3.",
    44:"Mẫu dùng items trực tiếp. Bỏ hẳn items để lấy giỏ, không gửi items=[]/null. 200 có thể can_place_order=false; xem issues. Không gửi payment_method.",
    45:"Bắt buộc Idempotency-Key. Giữ order_key khi thử replay đúng body; đổi khóa cho ý định mua mới. ID mới lưu created_order_id. Bỏ items để mua giỏ hiện tại.",
    50:"Mẫu chọn shipping_order_id đã shipping/paid và chuyển completed. Nếu dùng đơn mới, đi pending → confirmed → shipping → completed; completed cần paid. Không nhận cancelled.",
    51:"Mẫu chọn refund_order_id đã cancelled/paid để ghi refunded. Ghi thu tiền: dùng đơn pending chưa hết hạn và body payment_status=paid. Đây là ghi nhận thủ công, không gọi cổng thanh toán.",
    52:"Chọn cancel_order_id đang pending. Lần đầu 201, lặp 200; không cộng tồn hai lần. Đơn confirmed trở đi không hủy được.",
    53:"Chọn cancelled_order_id đã có dữ liệu hủy. Đơn chưa hủy trả 404.",
    55:"Chọn cancel_order_id lúc còn pending. Chỉ sửa khi pending/confirmed; không nhận delivery_method.",
    60:"Chỉ food/accessory. change âm để giảm tồn, khác 0. POST lặp có thể chỉnh tồn nhiều lần dù cùng reference.",
    63:"Login customer chính: đã mua review_product_id trong đơn completed và chưa review. Sau khi tạo, dùng created_review_id cùng review_product_id để sửa/xóa review mới.",
    64:"Mẫu dùng product_id + review_id là review sẵn của customer chính. Admin không sửa được review khách.",
    65:"Mẫu dùng product_id + review_id. Chủ hoặc admin được xóa. Sau khi xóa, GET review đó trả 404.",
}
group_names = ["01 - Tài khoản và xác thực (11 API)", "02 - Danh mục (5 API)", "03 - Sản phẩm (6 API)",
               "04 - Thú cưng (5 API)", "05 - Hồ sơ sức khỏe (6 API)", "06 - Nhà cung cấp (5 API)",
               "07 - Giỏ hàng và tính thử đơn (6 API)", "08 - Đơn hàng và giao hàng (11 API)",
               "09 - Tồn kho (5 API)", "10 - Đánh giá (5 API)"]
ends = [11,16,22,27,33,38,44,55,60,65]
groups = [{"name":name, "item":[]} for name in group_names]
pre = '''const number = Number(pm.info.requestName.slice(0, 2));
if (number === 2) {
  pm.variables.set("register_email", "postman." + Date.now() + "." + Math.floor(Math.random()*100000) + "@example.test");
}
if (number === 3) {
  const role = pm.variables.get("login_role") || "admin";
  if (!["admin","staff","customer","customer2"].includes(role)) throw new Error("login_role phải là admin/staff/customer/customer2");
  const email = pm.variables.get(role + "_email"), password = pm.variables.get(role + "_password");
  if (!email || !password) throw new Error("Chọn environment Demo-faker-v1 chứa tài khoản và mật khẩu mẫu");
  pm.variables.set("login_email", email);
  pm.variables.set("login_password", password);
}
if (["POST","PUT","PATCH","DELETE"].includes(pm.request.method)) {
  pm.sendRequest({url: pm.variables.get("base_url") + "/auth/csrf-token", method:"GET"}, (err, response) => {
    if (err || !response || response.code !== 200) throw new Error("Không lấy được CSRF; hãy chạy backend và kiểm tra base_url");
    const csrf = response.json();
    pm.collectionVariables.set("csrf_token", csrf.csrf_token);
    pm.request.headers.upsert({key:csrf.header_name, value:csrf.csrf_token});
  });
}'''
save = {2:"created_user_id",14:"created_category_id",19:"created_product_id",25:"created_pet_id",30:"created_record_id",
        36:"created_supplier_id",45:"created_order_id",60:"created_movement_id",63:"created_review_id"}
for number, method, template, role, sample, expected in rows:
    n = int(number)
    variables_for_request = {**variables, **special.get(n,{})}
    path = re.sub(r"\{(\w+)\}", lambda m: "{{"+variables_for_request[m[1]]+"}}", template)
    contract = contracts[(method, template)]
    description = f"Quyền: {role.strip()}.\n\nEndpoint: {method} {template}\n\nKết quả: {expected.strip()}.\n\n" + notes.get(n, "Dùng ID từ environment. Đọc checklist POSTMAN-TEST-TOAN-BO.md để xem ca lỗi và quy tắc chi tiết.")
    description += "\n\nCác request là danh mục test thủ công, không phải một luồng Run all. Request ghi cần CSRF; collection tự lấy token bằng cookie của phiên hiện tại."
    request = {"method":method, "header":[{"key":"Accept","value":"application/json"}],
               "auth":{"type":"noauth"}, "url":"{{base_url}}"+path, "description":description}
    if n in query:
        params = [{"key":k,"value":v,"disabled":True,"description":"Bật khi muốn lọc; kiểm tra quyền trước khi dùng"} for k,v in query[n].items()]
        params += [{"key":"page","value":"0"},{"key":"size","value":"20"}]
        request["url"] = {"raw":"{{base_url}}"+path+"?page=0&size=20", "host":["{{base_url}}"], "path":path.strip("/").split("/"), "query":params}
    if method in ("POST","PUT","PATCH","DELETE"):
        request["header"].append({"key":"X-CSRF-TOKEN","value":"{{csrf_token}}","description":"Tự cập nhật bằng pre-request script"})
    if n in bodies:
        raw = json.dumps(bodies[n], ensure_ascii=False, indent=2)
        raw = re.sub(r'"#([a-z_]+)"', lambda m:"{{"+m[1]+"}}", raw)
        request["header"].append({"key":"Content-Type","value":"application/json"})
        request["body"] = {"mode":"raw","raw":raw,"options":{"raw":{"language":"json"}}}
    if n == 45:
        request["header"].append({"key":"Idempotency-Key","value":"{{order_key}}","description":"Giữ nguyên khi replay, đổi khóa khi mua mới"})
    statuses = [201,200] if n in (45,52) else [204] if method == "DELETE" or n in (4,7) else [201] if method == "POST" and n not in (3,40,44) else [200]
    tests = [f'pm.test("HTTP thành công: {statuses}", () => pm.expect(pm.response.code).to.be.oneOf({statuses}));']
    if n in save:
        tests.append(f'if ([200,201].includes(pm.response.code)) pm.collectionVariables.set("{save[n]}", pm.response.json().id);')
    if n == 1:
        tests.append('if (pm.response.code === 200) pm.collectionVariables.set("csrf_token", pm.response.json().csrf_token);')
    if n == 3:
        tests.append('if (pm.response.code === 200) console.log("Đã đăng nhập:", pm.response.json().email, "role:", pm.response.json().role);')
    if n == 44:
        tests.append('if (pm.response.code === 200) console.log("can_place_order:", pm.response.json().can_place_order, "issues:", pm.response.json().issues);')
    name = number + " " + contract[2] + " [" + role.strip() + "]"
    group = next(i for i,end in enumerate(ends) if n <= end)
    groups[group]["item"].append({"name":name,"request":request,"event":[{"listen":"test","script":{"type":"text/javascript","exec":tests}}]})

# IDs and synthetic emails are safe in the collection; passwords stay in the local environment file.
seed_env = json.loads((ROOT / "postman/Demo-faker-v1.postman_environment.json").read_text(encoding="utf-8"))
values = {v["key"]:v["value"] for v in seed_env["values"] if not v["key"].endswith("_password")}
values.update(login_role="admin",register_password="CustomerPostman123!",new_password="CustomerNew123!")
result = {"info":{"_postman_id":str(uuid.uuid5(uuid.NAMESPACE_URL,"pet-shop-full-65-v1")),
          "name":"Pet Shop - Toàn bộ 65 API", "schema":"https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
          "description":"65 API thật của dự án, chia 10 thư mục. Dùng để gửi từng request, không Run all vì có đổi mật khẩu, khóa tài khoản, xóa và chuyển trạng thái loại trừ nhau. Chọn environment Pet Shop Demo - faker-v1. Chạy backend localhost:8080. Mở Variables của collection: login_role=admin/customer/staff/customer2, rồi gửi request 03 Đăng nhập. Request ghi tự lấy CSRF. ID mới lưu created_* để không ghi đè ID fixture. Tham khảo demo/docs/POSTMAN-TEST-TOAN-BO.md và DU-LIEU-MAU-POSTMAN.md."},
          "auth":{"type":"noauth"},"variable":[{"key":k,"value":v,"type":"string"} for k,v in values.items()],
          "event":[{"listen":"prerequest","script":{"type":"text/javascript","exec":pre.splitlines()}}],"item":groups}
assert len(rows) == 65 and sum(len(g["item"]) for g in groups) == 65
path = ROOT / "postman/Pet-Shop-Full-65.postman_collection.json"
path.write_text(json.dumps(result,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
print(f"Created {path}; 65 requests in 10 folders")
