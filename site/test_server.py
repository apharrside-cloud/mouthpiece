import hashlib
import http.client
import json
import os
import tempfile
import threading
import unittest
from http.server import ThreadingHTTPServer

HERE = os.path.dirname(os.path.abspath(__file__))
DATA = tempfile.mkdtemp()
os.environ["DATA_DIR"] = DATA
import server  # noqa: E402

APK = b"PK\x03\x04fake-apk-bytes" * 5000


class SiteTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        os.makedirs(os.path.join(DATA, "public"), exist_ok=True)
        with open(os.path.join(DATA, "public", "pharr-voice-9.9.9.apk"), "wb") as f:
            f.write(APK)
        with open(os.path.join(DATA, "public", "version.json"), "w") as f:
            json.dump({"version": "9.9.9", "file": "pharr-voice-9.9.9.apk", "size_mb": "0.1",
                       "sha256": hashlib.sha256(APK).hexdigest(), "date": "2026-11-16"}, f)
        server.PUBLIC = os.path.join(DATA, "public")
        server.COUNTER = os.path.join(DATA, "downloads.json")
        server.FEEDBACK = os.path.join(DATA, "feedback.jsonl")
        cls.httpd = ThreadingHTTPServer(("127.0.0.1", 0), server.Handler)
        cls.port = cls.httpd.server_address[1]
        threading.Thread(target=cls.httpd.serve_forever, daemon=True).start()

    @classmethod
    def tearDownClass(cls):
        cls.httpd.shutdown()

    def get(self, path, method="GET"):
        c = http.client.HTTPConnection("127.0.0.1", self.port, timeout=10)
        c.request(method, path)
        r = c.getresponse()
        body = r.read()
        c.close()
        return r.status, dict((k.lower(), v) for k, v in r.getheaders()), body

    def test_index_is_filled_from_version_json(self):
        s, h, b = self.get("/")
        html = b.decode()
        self.assertEqual(s, 200)
        self.assertIn("9.9.9", html)
        self.assertIn(hashlib.sha256(APK).hexdigest(), html)
        self.assertNotIn("__VERSION__", html)
        self.assertNotIn("__SHA256__", html)
        self.assertEqual(h["cache-control"], "no-store")

    def test_download_serves_the_apk_as_an_attachment(self):
        s, h, b = self.get("/download")
        self.assertEqual(s, 200)
        self.assertEqual(h["content-type"], "application/vnd.android.package-archive")
        self.assertIn("mouthpiece-9.9.9.apk", h["content-disposition"])
        self.assertEqual(b, APK)

    def test_downloads_are_counted_but_head_is_not(self):
        before = server.bump_counter("probe")
        self.get("/download", "HEAD")
        s, h, b = self.get("/files/pharr-voice-9.9.9.apk")
        self.assertEqual(s, 200)
        after = json.load(open(server.COUNTER))
        self.assertEqual(after["pharr-voice-9.9.9.apk"] >= 1, True)
        self.assertGreaterEqual(after["total"], before + 1)

    def test_unknown_and_traversal_paths_are_404(self):
        for p in ("/nope", "/files/../version.json", "/files/%2e%2e%2fversion.json", "/files/..%2f..%2fetc%2fpasswd",
                  "/files/other.txt", "/files/missing.apk"):
            s, _, _ = self.get(p)
            self.assertEqual(s, 404, p)

    def test_icon_assets_are_served(self):
        for p, ct in (("/icon.svg", "image/svg+xml"), ("/icon-512.png", "image/png"), ("/og.png", "image/png"),
                      ("/apple-touch-icon.png", "image/png"), ("/favicon-48.png", "image/png")):
            s, h, b = self.get(p)
            self.assertEqual(s, 200, p)
            self.assertEqual(h["content-type"], ct)
            self.assertGreater(len(b), 200)

    def test_post_is_rejected(self):
        c = http.client.HTTPConnection("127.0.0.1", self.port, timeout=10)
        c.request("POST", "/", body=b"x")
        self.assertEqual(c.getresponse().status, 405)

    def post(self, body, path="/feedback"):
        c = http.client.HTTPConnection("127.0.0.1", self.port, timeout=10)
        c.request("POST", path, body=body if isinstance(body, bytes) else json.dumps(body).encode(), headers={"CF-Connecting-IP": self.ip})
        r = c.getresponse()
        r.read()
        return r.status

    ip = "203.0.113.1"

    def test_feedback_is_stored_and_validated(self):
        self.ip = "203.0.113.10"
        ok = {"type": "idea", "message": "Add a dark bubble", "contact": "a@b.c", "version": "0.4.9", "android": "17", "device": "Pixel 9"}
        self.assertEqual(self.post(ok), 200)
        line = open(server.FEEDBACK, encoding="utf-8").read().strip().splitlines()[-1]
        rec = json.loads(line)
        self.assertEqual((rec["type"], rec["message"], rec["version"]), ("idea", "Add a dark bubble", "0.4.9"))
        self.assertNotIn("203.0.113.10", line)  # no IPs on disk
        self.assertEqual(self.post({"type": "idea", "message": "   "}), 400)
        self.assertEqual(self.post({"type": "spam", "message": "x"}), 400)
        self.assertEqual(self.post(b"not json"), 400)
        self.assertEqual(self.post(b"x" * 9000), 413)

    def test_feedback_is_rate_limited_per_client(self):
        self.ip = "203.0.113.20"
        codes = [self.post({"type": "problem", "message": "m%d" % i}) for i in range(7)]
        self.assertEqual(codes, [200] * 5 + [429] * 2)
        self.ip = "203.0.113.21"
        self.assertEqual(self.post({"type": "problem", "message": "other client"}), 200)

    def test_other_post_paths_stay_rejected(self):
        self.assertEqual(self.post(b"x", "/download"), 405)

    def test_health_and_version(self):
        self.assertEqual(self.get("/healthz")[2], b"ok")
        s, h, b = self.get("/version.json")
        self.assertEqual(json.loads(b)["version"], "9.9.9")


if __name__ == "__main__":
    unittest.main()
