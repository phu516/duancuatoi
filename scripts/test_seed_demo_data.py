"""Integration tests create/drop only a new petshop_seed_test_<uuid> database."""
import re
import unittest
from unittest.mock import patch
import uuid

import seed_demo_data as seed


class SeedIntegrationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.database = "petshop_seed_test_" + uuid.uuid4().hex
        config = seed.settings()
        cls.server = seed.connect(config)
        with cls.server.cursor() as cursor:
            cursor.execute(f"CREATE DATABASE `{cls.database}` CHARACTER SET utf8mb4")
        cls.connection = seed.connect({**config, "database": cls.database})
        with cls.connection.cursor() as cursor:
            for path in sorted((seed.ROOT / "src/main/resources/db/migration").glob("V*.sql")):
                sql = re.sub(r"--[^\n]*", "", path.read_text(encoding="utf-8"))
                for statement in sql.split(";"):
                    if statement.strip():
                        cursor.execute(statement)
        cls.connection.commit()

    @classmethod
    def tearDownClass(cls):
        cls.connection.close()
        if not re.fullmatch(r"petshop_seed_test_[a-f0-9]{32}", cls.database):
            raise ValueError("Unsafe test cleanup target")
        with cls.server.cursor() as cursor:
            cursor.execute(f"DROP DATABASE `{cls.database}`")
        cls.server.close()

    def counts(self):
        with self.connection.cursor() as cursor:
            result = {}
            for table in seed.TABLES:
                cursor.execute(f"SELECT COUNT(*) AS n FROM `{table}`")
                result[table] = cursor.fetchone()["n"]
            return result

    def test_01_relationships_passwords_rerun_and_preserve_edits(self):
        manifest, created = seed.seed(self.connection, "test-one", "SeedTest123!")
        self.assertTrue(created)
        self.assertEqual(10, manifest["counts"]["users"])
        self.assertEqual(32, manifest["counts"]["products"])
        self.assertEqual(14, manifest["counts"]["orders"])
        self.assertEqual(11, manifest["counts"]["pets"])
        self.assertEqual(33, manifest["counts"]["pet_health_records"])
        seed.audit(self.connection, manifest)
        with self.connection.cursor() as cursor:
            cursor.execute("SELECT password_hash FROM users WHERE id=%s", (manifest["accounts"]["customer"]["id"],))
            self.assertTrue(seed.bcrypt.checkpw(b"SeedTest123!", cursor.fetchone()["password_hash"].encode()))
            cursor.execute("UPDATE products SET name='Preserve my test edit' WHERE id=%s", (manifest["fixtures"]["product_id"],))
        self.connection.commit()
        counts = self.counts()
        repeated, created = seed.seed(self.connection, "test-one", "Different123!")
        self.assertFalse(created)
        self.assertEqual(manifest, repeated)
        self.assertEqual(counts, self.counts())
        with self.connection.cursor() as cursor:
            cursor.execute("SELECT name FROM products WHERE id=%s", (manifest["fixtures"]["product_id"],))
            self.assertEqual("Preserve my test edit", cursor.fetchone()["name"])

    def test_02_failure_rolls_back_entire_batch(self):
        before = self.counts()
        with patch.object(seed, "audit", side_effect=RuntimeError("Forced failure before commit")):
            with self.assertRaisesRegex(RuntimeError, "Forced failure"):
                seed.seed(self.connection, "test-failure", "SeedTest123!")
        self.assertEqual(before, self.counts())
        with self.connection.cursor() as cursor:
            cursor.execute("SELECT COUNT(*) AS n FROM demo_seed_batches WHERE batch_key='test-failure'")
            self.assertEqual(0, cursor.fetchone()["n"])


if __name__ == "__main__":
    unittest.main(verbosity=2)
