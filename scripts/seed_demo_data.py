"""Opt-in, additive MySQL fixtures matching the Spring Pet Shop schema.

Uses Faker vi_VN for synthetic names/addresses, BCrypt for Spring-compatible
passwords, and Decimal for money. Does not replace/reset any existing shop row.
One batch is committed atomically with its manifest; reruns return the manifest.
"""
from __future__ import annotations

import argparse
from collections import Counter
from datetime import datetime, timedelta, timezone
from decimal import Decimal, ROUND_HALF_UP
import json
import os
from pathlib import Path
import re
import secrets
from urllib.parse import urlparse
import uuid

import bcrypt
from faker import Faker
import pymysql

ROOT = Path(__file__).resolve().parents[1]
TABLES = {
    "users", "categories", "products", "suppliers", "pets", "pet_health_records",
    "carts", "cart_items", "coupons", "orders", "order_items", "shipping_info",
    "inventory_movements", "order_events", "reviews",
}
REQUIRED = {
    "users": {"id", "password_hash", "session_version", "is_active"},
    "products": {"id", "category_id", "product_type", "stock_quantity", "is_active"},
    "pets": {"id", "product_id", "reserved_order_id", "reserved_until"},
    "orders": {"id", "idempotency_key", "request_hash", "cancelled_at", "cancelled_by", "cancel_reason"},
    "order_items": {"order_id", "product_name", "unit_price"},
    "shipping_info": {"order_id", "recipient_name", "recipient_phone"},
}


def settings() -> dict:
    text = (ROOT / "src/main/resources/application.properties").read_text(encoding="utf-8")
    def read(key):
        value = re.search(r"^" + re.escape(key) + r"=(.*)$", text, re.M).group(1).strip()
        match = re.fullmatch(r"\$\{([^:]+):(.*)\}", value)
        return os.environ.get(match[1], match[2]) if match else value
    url = urlparse(read("spring.datasource.url").removeprefix("jdbc:"))
    if url.scheme != "mysql" or not url.hostname or not url.path.strip("/"):
        raise ValueError("Expected a MySQL JDBC URL with an explicit database")
    return dict(host=url.hostname, port=url.port or 3306, database=url.path.strip("/"),
                user=read("spring.datasource.username"), password=read("spring.datasource.password"))


def connect(config):
    connection = pymysql.connect(**config, charset="utf8mb4", autocommit=False,
                                 cursorclass=pymysql.cursors.DictCursor,
                                 connect_timeout=10, read_timeout=30, write_timeout=30)
    with connection.cursor() as cursor:
        cursor.execute("SET time_zone='+00:00'")
        cursor.execute("SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED")
    return connection


def schema_check(connection):
    with connection.cursor() as cursor:
        cursor.execute("SELECT table_name,column_name FROM information_schema.columns WHERE table_schema=DATABASE()")
        columns = {}
        for row in cursor.fetchall():
            row = {k.lower(): v for k, v in row.items()}
            columns.setdefault(row["table_name"], set()).add(row["column_name"])
        if not TABLES <= columns.keys():
            raise ValueError("Database does not match the application. Run Flyway first; missing: " + str(TABLES - columns.keys()))
        for table, required in REQUIRED.items():
            if not required <= columns[table]:
                raise ValueError("Incompatible schema: " + table + " missing " + str(required - columns[table]))
        cursor.execute("SELECT table_name,engine FROM information_schema.tables WHERE table_schema=DATABASE()")
        for row in cursor.fetchall():
            row = {k.lower(): v for k, v in row.items()}
            if row["table_name"] in TABLES and row["engine"] != "InnoDB":
                raise ValueError("Fixtures require transactional InnoDB tables")


class Seeder:
    def __init__(self, connection, batch, password):
        self.connection = connection
        self.batch = batch
        self.password = password
        self.fake = Faker("vi_VN")
        self.fake.seed_instance(20260930)
        self.now = datetime.now(timezone.utc).replace(tzinfo=None, microsecond=0)
        self.prefix = f"[DEMO {batch}] "
        self.ids = {table: [] for table in TABLES}
        self.counts = Counter()
        self.users = {}
        self.products = {}
        self.pet_ids = {}
        self.orders = {}
        self.coupons = {}

    def insert(self, table, **fields):
        if table not in TABLES:
            raise ValueError("Invalid table")
        with self.connection.cursor() as cursor:
            cursor.execute(f"INSERT INTO `{table}` (" + ",".join(f"`{key}`" for key in fields)
                           + ") VALUES (" + ",".join(["%s"] * len(fields)) + ")", tuple(fields.values()))
            identity = fields["code"] if table == "coupons" else cursor.lastrowid
        self.counts[table] += 1
        if table != "cart_items":
            self.ids[table].append(identity)
        return identity

    def execute(self, sql, *params):
        with self.connection.cursor() as cursor:
            cursor.execute(sql, params)
            return cursor.rowcount

    def event(self, order, actor, kind, old, new, when, note=None):
        self.insert("order_events", order_id=order, actor_id=actor, event_type=kind,
                    old_value=old, new_value=new, note=note, created_at=when)

    def movement(self, product, change, when, reason, order=None, actor=None):
        data = self.products[product]
        before = data["stock_quantity"]
        after = before + change
        if not 0 <= after <= 1000000:
            raise ValueError("Invalid fixture stock")
        self.execute("UPDATE products SET stock_quantity=%s WHERE id=%s", after, product)
        data["stock_quantity"] = after
        return self.insert("inventory_movements", product_id=product, order_id=order,
                           actor_id=actor, quantity_change=change, quantity_before=before,
                           quantity_after=after, reason=reason, reference=self.batch, created_at=when)

    def account(self, alias, role="customer", active=True):
        email = f"{alias}.{self.batch}@example.test"
        # Separate salts; hashes use the $2a$ prefix accepted by BCryptPasswordEncoder.
        password_hash = bcrypt.hashpw(self.password.encode(), bcrypt.gensalt(rounds=10, prefix=b"2a")).decode()
        data = dict(full_name=self.fake.name()[:100], email=email, password_hash=password_hash,
                    phone="090" + self.fake.numerify("#######"), address=self.fake.address().replace("\n", ", ")[:255],
                    role=role, is_active=active, session_version=0, created_at=self.now - timedelta(days=60))
        identity = self.insert("users", **data)
        self.users[alias] = {"id": identity, "email": email, "full_name": data["full_name"],
                             "phone": data["phone"], "address": data["address"], "role": role, "is_active": active}
        return identity

    def product(self, category, name, kind, price, opening, active=True):
        data = dict(category_id=category, product_type=kind, name=self.prefix + name,
                    description="Dữ liệu giả lập để test Postman; " + self.fake.sentence(),
                    price=Decimal(str(price)), stock_quantity=0,
                    image_url=None, is_active=active, created_at=self.now - timedelta(days=45))
        identity = self.insert("products", **data)
        data["stock_quantity"] = 0
        self.products[identity] = data
        if opening:
            self.movement(identity, opening, data["created_at"], "opening_stock", actor=self.users["admin"]["id"])
        return identity

    def coupon(self, alias, kind, value, minimum=0, active=True, limit=100, expired=False):
        code = f"{self.batch}-{alias}".upper()
        self.insert("coupons", code=code, discount_type=kind, discount_value=Decimal(str(value)),
                    min_subtotal=Decimal(str(minimum)), starts_at=self.now - timedelta(days=60),
                    ends_at=self.now + timedelta(days=-1 if expired else 365),
                    usage_limit=limit, used_count=0, is_active=active)
        self.coupons[alias] = {"code": code, "kind": kind, "value": Decimal(str(value))}
        return code

    def order(self, alias, owner, lines, state, payment="unpaid", method="cash", coupon=None,
              days_ago=5, expired=False, delivery="pickup"):
        customer = self.users[owner]
        staff = self.users["staff"]["id"]
        created = self.now - timedelta(days=days_ago) if days_ago else self.now - timedelta(minutes=1)
        expiry = created + timedelta(minutes=15) if method != "cash" and payment == "unpaid" and state == "pending" else None
        subtotal = sum((self.products[p]["price"] * quantity for p, quantity in lines), Decimal("0.00"))
        discount = Decimal("0.00")
        if coupon:
            c = self.coupons[coupon]
            discount = ((subtotal * c["value"] / 100).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
                        if c["kind"] == "percent" else c["value"])
            discount = min(subtotal, discount)
        cancelled = state == "cancelled"
        cancelled_at = created + timedelta(minutes=16 if expired else 10) if cancelled else None
        actor = None if expired else customer["id"]
        identity = self.insert("orders", user_id=customer["id"], order_date=created,
                               status=state, payment_method=method, payment_status=payment,
                               subtotal=subtotal, shipping_fee=Decimal("0.00"), discount_amount=discount,
                               total_amount=subtotal-discount, coupon_code=self.coupons[coupon]["code"] if coupon else None,
                               reserved_until=expiry, idempotency_key=None, request_hash=None,
                               cancelled_at=cancelled_at, cancelled_by=actor if cancelled else None,
                               cancel_reason=("Hết hạn chờ thanh toán" if expired else "Khách hủy đơn mẫu") if cancelled else None)
        self.insert("shipping_info", order_id=identity, recipient_name=customer["full_name"],
                    recipient_phone=customer["phone"], delivery_method=delivery,
                    address=None if delivery == "pickup" else customer["address"], note="Đơn giả lập " + alias)
        self.event(identity, customer["id"], "status", None, "pending", created)
        if payment in ("paid", "refunded"):
            self.event(identity, staff, "payment", "unpaid", "paid", created+timedelta(minutes=2), "Đã ghi nhận thanh toán mẫu")
        if state in ("confirmed", "shipping", "completed"):
            self.event(identity, staff, "status", "pending", "confirmed", created+timedelta(minutes=3))
        if state in ("shipping", "completed"):
            self.event(identity, staff, "status", "confirmed", "shipping", created+timedelta(minutes=4))
        if state == "completed":
            self.event(identity, staff, "status", "shipping", "completed", created+timedelta(minutes=5))
        if cancelled:
            self.event(identity, actor, "status", "pending", "cancelled", cancelled_at, "Hủy đơn mẫu")
        if payment == "refunded":
            self.event(identity, staff, "payment", "paid", "refunded", created+timedelta(minutes=12), "Đã hoàn tiền mẫu")
        for product, quantity in sorted(lines):
            data = self.products[product]
            self.insert("order_items", order_id=identity, product_id=product,
                        product_name=data["name"], quantity=quantity, unit_price=data["price"])
            self.movement(product, -quantity, created, "order_created", identity, customer["id"])
            if cancelled:
                self.movement(product, quantity, cancelled_at, "order_cancelled", identity, actor)
            if data["product_type"] == "pet" and not cancelled:
                if state == "completed":
                    self.execute("UPDATE pets SET status='sold',reserved_order_id=NULL,reserved_until=NULL WHERE product_id=%s", product)
                else:
                    self.execute("UPDATE pets SET status='reserved',reserved_order_id=%s,reserved_until=%s WHERE product_id=%s", identity, expiry, product)
        if coupon:
            self.execute("UPDATE coupons SET used_count=used_count+1 WHERE code=%s", self.coupons[coupon]["code"])
        self.orders[alias] = identity
        return identity

    def run(self):
        # Demo data uses the already configured administrator; it never creates another one.
        with self.connection.cursor() as cursor:
            cursor.execute("SELECT id,email,full_name,phone,address,is_active FROM users WHERE role='admin' ORDER BY id")
            administrators = cursor.fetchall()
        if len(administrators) != 1 or not administrators[0]["is_active"]:
            raise ValueError("Seed requires exactly one active administrator in users")
        administrator = administrators[0]
        self.users["admin"] = {**administrator, "role": "admin"}
        self.account("staff", "staff")
        self.account("staff2", "staff")
        for alias in ("customer", "customer2", "customer3", "customer4", "customer5", "customer6"):
            self.account(alias)
        self.account("locked", active=False)
        categories = [self.insert("categories", name=self.prefix+name, created_at=self.now-timedelta(days=50))
                      for name in ("Thức ăn chó", "Thức ăn mèo", "Phụ kiện", "Chó cảnh", "Mèo cảnh", "Danh mục rỗng để xóa")]
        suppliers = [self.insert("suppliers", name=self.prefix+"Nhà cung cấp "+str(i+1),
                                contact_info=self.fake.name()+" - 090"+self.fake.numerify("#######"),
                                address=self.fake.address().replace("\n", ", ")[:255], created_at=self.now-timedelta(days=50))
                     for i in range(5)]
        goods = []
        food_names = ["Hạt chó vị gà", "Pate mèo", "Hạt chó con", "Thức ăn mèo trưởng thành", "Snack thưởng",
                      "Pate chó", "Hạt mèo con", "Thức ăn vị cá", "Bánh thưởng", "Sữa cho thú cưng"]
        accessories = ["Dây dắt", "Vòng cổ", "Bát ăn", "Nhà vải", "Lược chải", "Đệm nằm", "Đồ chơi bóng", "Túi vận chuyển", "Khay vệ sinh", "Sản phẩm rỗng để xóa"]
        for i, name in enumerate(food_names+accessories):
            # Last fixtures cover out of stock, low stock, inactive, and deletable.
            opening = {16: 0, 17: 3, 18: 8, 19: 0}.get(i, 60+i)
            goods.append(self.product(categories[i % 2] if i < 10 else categories[2], name,
                                      "food" if i < 10 else "accessory", 120000+i*15000,
                                      opening, active=i != 18))
        # goods[16] is purchased historically below; start stocked, then sell out.
        self.movement(goods[16], 1, self.now-timedelta(days=40), "demo_restock", actor=self.users["staff"]["id"])
        pets = []
        breeds = ["Poodle", "Corgi", "Golden Retriever", "Mèo Anh lông ngắn", "Mèo Ba Tư", "Poodle",
                  "Corgi", "Mèo Anh lông dài", "Shiba", "Poodle", "Mèo Xiêm", "Poodle"]
        for i, breed in enumerate(breeds):
            product = self.product(categories[4] if "Mèo" in breed else categories[3],
                                   f"{breed} số {i+1}", "pet", 3000000+i*250000, 1, active=i < 10)
            pets.append(product)
            if i == 11:  # Draft product with no profile: test POST /pets.
                continue
            identity = self.insert("pets", product_id=product, supplier_id=suppliers[i % 4],
                                   breed=breed, age=f"{3+i%6} tháng", gender="female" if i%2 else "male",
                                   color=["Nâu", "Trắng", "Vàng", "Xám"][i%4], weight=Decimal("2.50")+i,
                                   is_neutered=i%3 == 0, vaccination_status="Hồ sơ tiêm mẫu",
                                   status="available", created_at=self.now-timedelta(days=40))
            self.pet_ids[product] = identity
            for offset, kind in enumerate(("checkup", "vaccination", "deworming")):
                self.insert("pet_health_records", pet_id=identity, record_date=(self.now-timedelta(days=30-offset*7)).date(),
                            record_type=kind, description="Bản ghi giả lập dùng kiểm thử: "+kind,
                            vet_name="Bác sĩ "+self.fake.name(), created_at=self.now-timedelta(days=30-offset*7))
        for args in (("PERCENT10", "percent", 10, 100000), ("FIXED50K", "fixed", 50000, 100000)):
            self.coupon(*args)
        self.coupon("EXPIRED", "percent", 5, expired=True)
        self.coupon("INACTIVE", "fixed", 20000, active=False)
        self.coupon("EXHAUSTED", "percent", 5, limit=1)
        self.order("completed", "customer", [(goods[0],2),(goods[1],1),(pets[9],1)], "completed", "paid", coupon="PERCENT10", days_ago=10)
        self.order("pending_cash", "customer", [(goods[2],2),(pets[6],1)], "pending", days_ago=0)
        self.order("pending_paid", "customer", [(goods[3],1),(pets[5],1)], "pending", "paid", "bank_transfer", coupon="FIXED50K", days_ago=1)
        self.order("confirmed", "customer", [(goods[4],2),(pets[8],1)], "confirmed", days_ago=1, delivery="pet_taxi")
        self.order("shipping", "customer", [(goods[5],1),(pets[7],1)], "shipping", "paid", days_ago=1, delivery="shipping")
        self.order("cancelled_unpaid", "customer", [(goods[6],2)], "cancelled", coupon="EXHAUSTED")
        self.order("cancelled_paid", "customer", [(goods[7],1)], "cancelled", "paid", "card")
        self.order("cancelled_refunded", "customer", [(goods[8],2)], "cancelled", "refunded", "e_wallet")
        self.order("pending_prepaid", "customer2", [(goods[9],1)], "pending", method="bank_transfer", days_ago=0)
        self.order("other_customer", "customer2", [(goods[10],2)], "completed", "paid")
        self.order("completed_customer3", "customer3", [(goods[11],1),(goods[12],1)], "completed", "paid")
        self.order("expired_cancelled", "customer2", [(goods[13],1)], "cancelled", method="card", coupon="EXPIRED", expired=True)
        self.order("cancelled_pet", "customer", [(pets[4],1),(goods[14],1)], "cancelled")
        self.order("completed_customer4", "customer4", [(goods[15],1),(goods[16],1)], "completed", "paid")
        for owner, product, rating in (("customer", goods[0], 5), ("customer", pets[9], 4), ("customer2", goods[10], 4),
                                       ("customer3", goods[11], 5), ("customer3", goods[12], 3), ("customer4", goods[15], 4)):
            self.insert("reviews", product_id=product, user_id=self.users[owner]["id"], rating=rating,
                        comment=self.fake.sentence(), created_at=self.now-timedelta(days=1))
        for owner, lines in (("customer", [(goods[0],2),(goods[1],1),(pets[0],1)]),
                              ("customer2", [(goods[16],1),(goods[18],1),(goods[9],2)]),
                              ("customer3", [(goods[12],1)])):
            cart = self.insert("carts", user_id=self.users[owner]["id"], created_at=self.now)
            for product, quantity in lines:
                self.insert("cart_items", cart_id=cart, product_id=product, quantity=quantity)
        return dict(batch=self.batch, created_at=self.now.isoformat()+"Z", counts=dict(sorted(self.counts.items())),
                    ids=self.ids, accounts=self.users, orders=self.orders,
                    coupon_codes={alias: data["code"] for alias, data in self.coupons.items()},
                    fixtures=dict(category_id=categories[0], empty_category_id=categories[5], supplier_id=suppliers[0],
                                  empty_supplier_id=suppliers[4], product_id=goods[0], review_product_id=goods[1],
                                  out_of_stock_product_id=goods[16], low_stock_product_id=goods[17],
                                  inactive_product_id=goods[18], deletable_product_id=goods[19],
                                  pet_product_id=pets[0], pet_id=self.pet_ids[pets[0]],
                                  reserved_pet_id=self.pet_ids[pets[6]], sold_pet_id=self.pet_ids[pets[9]],
                                  deletable_pet_id=self.pet_ids[pets[10]], draft_pet_product_id=pets[11],
                                  historical_pet_id=self.pet_ids[pets[4]], record_id=self.ids["pet_health_records"][0],
                                  movement_id=self.ids["inventory_movements"][0], review_id=self.ids["reviews"][0]))


def audit(connection, manifest):
    """Check relationships, snapshots, pet state and the full stock movement chain."""
    def rows(sql, *params):
        with connection.cursor() as c:
            c.execute(sql, params)
            return c.fetchall()
    for order in manifest["ids"]["orders"]:
        data = rows("SELECT * FROM orders WHERE id=%s", order)[0]
        lines = rows("SELECT quantity,unit_price FROM order_items WHERE order_id=%s", order)
        assert lines and data["subtotal"] == sum((x["quantity"]*x["unit_price"] for x in lines), Decimal(0))
        assert data["total_amount"] == data["subtotal"]+data["shipping_fee"]-data["discount_amount"]
        assert len(rows("SELECT id FROM shipping_info WHERE order_id=%s", order)) == 1
        assert rows("SELECT id FROM order_events WHERE order_id=%s", order)
    for product in manifest["ids"]["products"]:
        p = rows("SELECT stock_quantity FROM products WHERE id=%s", product)[0]
        movements = rows("SELECT * FROM inventory_movements WHERE product_id=%s ORDER BY id", product)
        previous = 0
        for movement in movements:
            assert movement["quantity_before"] == previous
            assert movement["quantity_after"] == previous+movement["quantity_change"]
            previous = movement["quantity_after"]
        assert p["stock_quantity"] == previous
    for identity in manifest["ids"]["pets"]:
        pet = rows("SELECT t.*,p.stock_quantity FROM pets t JOIN products p ON p.id=t.product_id WHERE t.id=%s", identity)[0]
        assert pet["stock_quantity"] == (1 if pet["status"] == "available" else 0)
        if pet["status"] == "reserved":
            order = rows("SELECT status FROM orders WHERE id=%s", pet["reserved_order_id"])[0]
            assert order["status"] in ("pending", "confirmed", "shipping")
            assert rows("SELECT id FROM order_items WHERE order_id=%s AND product_id=%s", pet["reserved_order_id"],pet["product_id"])
        else:
            assert pet["reserved_order_id"] is None
    for identity in manifest["ids"]["reviews"]:
        review = rows("SELECT * FROM reviews WHERE id=%s", identity)[0]
        assert rows("SELECT o.id FROM orders o JOIN order_items i ON i.order_id=o.id WHERE o.user_id=%s AND i.product_id=%s AND o.status='completed'",review["user_id"],review["product_id"])
    for code in manifest["ids"]["coupons"]:
        coupon = rows("SELECT used_count FROM coupons WHERE code=%s", code)[0]
        assert coupon["used_count"] == rows("SELECT COUNT(*) AS n FROM orders WHERE coupon_code=%s", code)[0]["n"]


def seed(connection, batch, password):
    if not re.fullmatch(r"[a-z0-9][a-z0-9-]{0,23}", batch):
        raise ValueError("Batch must be 1-24 lowercase letters/digits/hyphens")
    if len(password) < 8 or len(password.encode()) > 72:
        raise ValueError("Seed password must be 8+ characters, at most 72 UTF-8 bytes")
    schema_check(connection)
    # Metadata DDL is separate from the business transaction; no existing table is altered.
    with connection.cursor() as cursor:
        cursor.execute("""CREATE TABLE IF NOT EXISTS demo_seed_batches (
            batch_key VARCHAR(24) PRIMARY KEY,
            manifest LONGTEXT NOT NULL,
            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""")
        cursor.execute("SELECT DATABASE() AS db")
        database = cursor.fetchone()["db"]
        lock = "demo-seed:" + database
        cursor.execute("SELECT GET_LOCK(%s,10) AS acquired", (lock,))
        if cursor.fetchone()["acquired"] != 1:
            raise RuntimeError("Another seeder is running")
    try:
        connection.begin()
        with connection.cursor() as cursor:
            cursor.execute("SELECT manifest FROM demo_seed_batches WHERE batch_key=%s FOR UPDATE", (batch,))
            previous = cursor.fetchone()
            if previous:
                manifest = json.loads(previous["manifest"])
                cursor.execute("SELECT id,email,full_name,phone,address,is_active FROM users WHERE role='admin' ORDER BY id")
                administrators = cursor.fetchall()
                if len(administrators) != 1 or not administrators[0]["is_active"]:
                    raise ValueError("Seed requires exactly one active administrator in users")
                manifest["accounts"]["admin"] = {**administrators[0], "role": "admin"}
                connection.commit()
                return manifest, False
            # Refuse collisions rather than overwriting an account/password already present.
            cursor.execute("SELECT id FROM users WHERE email LIKE %s", ("%."+batch+"@example.test",))
            if cursor.fetchone():
                raise ValueError("Existing demo emails without a batch manifest; choose a new --batch")
        manifest = Seeder(connection, batch, password).run()
        manifest["database"] = database
        audit(connection, manifest)
        with connection.cursor() as cursor:
            cursor.execute("INSERT INTO demo_seed_batches(batch_key,manifest) VALUES (%s,%s)",
                           (batch, json.dumps(manifest, ensure_ascii=False)))
        connection.commit()
        return manifest, True
    except BaseException:
        connection.rollback()
        raise
    finally:
        with connection.cursor() as cursor:
            cursor.execute("SELECT RELEASE_LOCK(%s)", (lock,))


def export(manifest, password, created):
    batch = manifest["batch"]
    directory = ROOT / "target/seed-data"
    directory.mkdir(parents=True, exist_ok=True)
    (directory / f"{batch}-manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    path = ROOT / "postman" / f"Demo-{batch}.postman_environment.json"
    old = {}
    if path.exists() and not created:
        old = {x["key"]: x["value"] for x in json.loads(path.read_text(encoding="utf-8"))["values"]}
    values = {"base_url": "http://localhost:8080/api/v1", "seed_batch": batch,
              **manifest["fixtures"], "customer_id": manifest["accounts"]["customer"]["id"],
              "staff_id": manifest["accounts"]["staff"]["id"],
              "order_id": manifest["orders"]["completed"], "cancel_order_id": manifest["orders"]["pending_cash"],
              "refund_order_id": manifest["orders"]["cancelled_paid"], "paid_order_id": manifest["orders"]["pending_paid"],
              "confirmed_order_id": manifest["orders"]["confirmed"], "shipping_order_id": manifest["orders"]["shipping"],
              "prepaid_order_id": manifest["orders"]["pending_prepaid"], "other_order_id": manifest["orders"]["other_customer"],
              "cancelled_order_id": manifest["orders"]["cancelled_unpaid"],
              "coupon_code": manifest["coupon_codes"]["PERCENT10"],
              "fixed_coupon_code": manifest["coupon_codes"]["FIXED50K"],
              "expired_coupon_code": manifest["coupon_codes"]["EXPIRED"],
              "inactive_coupon_code": manifest["coupon_codes"]["INACTIVE"],
              "exhausted_coupon_code": manifest["coupon_codes"]["EXHAUSTED"],
              "order_key": old.get("order_key") or "postman-" + uuid.uuid4().hex, "csrf_token": ""}
    for alias in ("admin", "staff", "customer", "customer2", "locked"):
        values[alias+"_email"] = manifest["accounts"][alias]["email"]
        if alias == "admin":
            values["admin_password"] = old.get("admin_password", "") if old.get("admin_email") == values["admin_email"] else ""
        else:
            values[alias+"_password"] = password if created else old.get(alias+"_password", os.environ.get("SEED_PASSWORD", ""))
    environment_id = str(uuid.uuid5(uuid.NAMESPACE_URL, "pet-shop-demo:"+manifest["database"]+":"+batch))
    path.write_text(json.dumps({"id": environment_id, "name": "Pet Shop Demo - "+batch,
                    "values": [{"key": k, "value": str(v), "enabled": True,
                                **({"type": "secret"} if k.endswith("_password") else {})} for k,v in values.items()],
                    "_postman_variable_scope": "environment"}, ensure_ascii=False, indent=2), encoding="utf-8")
    return path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--database", required=True, help="Must match the configured DB_URL database exactly")
    parser.add_argument("--batch", default="faker-v1")
    args = parser.parse_args()
    config = settings()
    if args.database != config["database"]:
        raise ValueError("--database does not match the application's DB_URL; nothing was written")
    if config["host"] not in ("localhost", "127.0.0.1", "::1"):
        raise ValueError("This development data loader only targets local MySQL")
    password = os.environ.get("SEED_PASSWORD") or "Demo-"+secrets.token_urlsafe(16)+"!"
    with connect(config) as connection:
        manifest, created = seed(connection, args.batch, password)
    environment = export(manifest, password, created)
    print(json.dumps({"database": args.database, "batch": args.batch, "created": created,
                      "counts_at_creation": manifest["counts"], "postman_environment": str(environment)}, ensure_ascii=True, indent=2))
    if not created:
        print("Batch already exists. No shop rows changed. Fixtures may have changed during your tests.")


if __name__ == "__main__":
    main()
