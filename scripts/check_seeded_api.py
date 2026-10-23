"""Read/preview-only HTTP smoke check of the seeded fixtures (does not place orders)."""
import argparse
from http.cookiejar import CookieJar
import json
from pathlib import Path
from urllib.error import HTTPError
from urllib.request import HTTPCookieProcessor, Request, build_opener

ROOT = Path(__file__).resolve().parents[1]


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--base-url", default="http://localhost:8080/api/v1")
    p.add_argument("--batch", default="faker-v1")
    args = p.parse_args()
    env = json.loads((ROOT / "postman" / f"Demo-{args.batch}.postman_environment.json").read_text(encoding="utf-8"))
    values = {v["key"]: v["value"] for v in env["values"]}
    passed = []

    class Client:
        def __init__(self):
            self.http = build_opener(HTTPCookieProcessor(CookieJar()))
            self.csrf = None

        def call(self, method, path, body=None, status=200):
            headers = {"Content-Type": "application/json"}
            if self.csrf:
                headers["X-CSRF-TOKEN"] = self.csrf
            request = Request(args.base_url + path, method=method, headers=headers,
                              data=json.dumps(body, ensure_ascii=False).encode("utf-8") if body is not None else None)
            try:
                response = self.http.open(request, timeout=15)
            except HTTPError as e:
                response = e
            raw = response.read().decode("utf-8")
            assert response.code == status, f"{method} {path}: expected {status}, got {response.code}: {raw}"
            passed.append(f"{method} {path} -> {status}")
            return json.loads(raw) if raw else None

        def login(self, role, status=200):
            self.csrf = self.call("GET", "/auth/csrf-token")["csrf_token"]
            result = self.call("POST", "/auth/login", {"email": values[role+"_email"], "password": values[role+"_password"]}, status)
            self.csrf = self.call("GET", "/auth/csrf-token")["csrf_token"]
            return result

    public = Client()
    pet = public.call("GET", "/pets/"+values["pet_id"])
    assert pet["status"] == "available" and "supplier_id" not in pet
    public.call("GET", "/pets/"+values["pet_id"]+"/health-records/"+values["record_id"])
    public.call("GET", "/products/"+values["inactive_product_id"], status=404)
    customer = Client()
    assert customer.login("customer")["role"] == "customer"
    assert len(customer.call("GET", "/cart")["items"]) == 3
    completed = customer.call("GET", "/orders/"+values["order_id"])
    assert completed["status"] == "completed" and completed["payment_status"] == "paid"
    assert customer.call("GET", "/orders/"+values["cancel_order_id"])["status"] == "pending"
    assert customer.call("GET", "/orders/"+values["refund_order_id"])["payment_status"] == "paid"
    assert customer.call("GET", "/orders/"+values["confirmed_order_id"])["status"] == "confirmed"
    assert customer.call("GET", "/orders/"+values["shipping_order_id"])["status"] == "shipping"
    customer.call("GET", "/orders/"+values["other_order_id"], status=404)
    customer.call("GET", "/orders/"+values["cancelled_order_id"]+"/cancellation")
    assert customer.call("GET", "/products/"+values["review_product_id"]+"/reviews")["pagination"]["total_elements"] == 0
    body = {"items": [{"product_id": int(values["product_id"]), "quantity": 2}],
            "shipping": {"recipient_name": "Khách kiểm thử", "recipient_phone": "0901234567", "delivery_method": "pickup"},
            "coupon_code": values["coupon_code"]}
    preview = customer.call("POST", "/checkout/preview", body)
    assert preview["can_place_order"] and preview["subtotal"] == 240000 and preview["discount_amount"] == 24000
    for key in ("expired_coupon_code", "inactive_coupon_code", "exhausted_coupon_code"):
        body["coupon_code"] = values[key]
        bad = customer.call("POST", "/checkout/preview", body)
        assert not bad["can_place_order"] and any(i["code"] == "COUPON_INVALID" for i in bad["issues"])
    customer.call("POST", "/auth/logout", status=204)
    other = Client()
    assert other.login("customer2")["role"] == "customer"
    assert sum(not i["available"] for i in other.call("GET", "/cart")["items"]) == 2
    other.call("POST", "/auth/logout", status=204)
    admin = Client()
    assert admin.login("admin")["role"] == "admin"
    admin.call("GET", "/suppliers/"+values["supplier_id"])
    admin.call("GET", "/inventory/"+values["product_id"]+"/movements/"+values["movement_id"])
    admin.call("GET", "/health-records?pet_id="+values["pet_id"])
    assert admin.call("GET", "/pets/"+values["reserved_pet_id"])["status"] == "reserved"
    assert admin.call("GET", "/pets/"+values["sold_pet_id"])["status"] == "sold"
    assert admin.call("GET", "/products/"+values["draft_pet_product_id"])["pet"] is None
    admin.call("GET", "/orders/"+values["other_order_id"])
    admin.call("POST", "/auth/logout", status=204)
    staff = Client()
    assert staff.login("staff")["role"] == "staff"
    staff.call("GET", "/inventory?low_stock=5")
    staff.call("GET", "/suppliers")
    staff.call("POST", "/auth/logout", status=204)
    Client().login("locked", status=401)
    output = ROOT / "target/seed-data" / f"{args.batch}-api-check.json"
    output.write_text(json.dumps({"requests_passed": len(passed), "checks": passed}, indent=2), encoding="utf-8")
    print(f"Passed {len(passed)} HTTP requests and fixture assertions; report: {output}")


if __name__ == "__main__":
    main()
