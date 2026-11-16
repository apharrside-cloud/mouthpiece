"""Static download site for Pharr Voice (voice.pharrlabs.com). Standard library only.

Layout (volume /data):  /data/public/version.json, /data/public/<apk files>, /data/downloads.json (counter).
The page template is baked into the image; __PLACEHOLDERS__ are filled from version.json at request time,
so shipping a new APK is a file copy, not a rebuild.
"""
import json
import os
import re
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

HERE = os.path.dirname(os.path.abspath(__file__))
DATA = os.environ.get("DATA_DIR", "/data")
PUBLIC = os.path.join(DATA, "public")
COUNTER = os.path.join(DATA, "downloads.json")
LOCK = threading.Lock()
APK_TYPE = "application/vnd.android.package-archive"
ASSETS = {  # baked into the image next to index.html
    "/icon.svg": ("image/svg+xml", "public, max-age=3600"),
    "/icon-192.png": ("image/png", "public, max-age=3600"),
    "/icon-512.png": ("image/png", "public, max-age=3600"),
    "/apple-touch-icon.png": ("image/png", "public, max-age=3600"),
    "/favicon-48.png": ("image/png", "public, max-age=3600"),
    "/og.png": ("image/png", "public, max-age=3600"),
}
SAFE_NAME = re.compile(r"^[A-Za-z0-9._-]+$")


def read_version():
    try:
        with open(os.path.join(PUBLIC, "version.json"), encoding="utf-8") as f:
            return json.load(f)
    except Exception:
        return {"version": "", "file": "", "size_mb": "", "sha256": "", "date": ""}


def bump_counter(name):
    with LOCK:
        try:
            with open(COUNTER, encoding="utf-8") as f:
                counts = json.load(f)
        except Exception:
            counts = {}
        counts[name] = counts.get(name, 0) + 1
        counts["total"] = counts.get("total", 0) + 1
        tmp = COUNTER + ".tmp"
        with open(tmp, "w", encoding="utf-8") as f:
            json.dump(counts, f)
        os.replace(tmp, COUNTER)
        return counts["total"]


def render_index():
    v = read_version()
    with open(os.path.join(HERE, "index.html"), encoding="utf-8") as f:
        html = f.read()
    for key in ("version", "size_mb", "sha256", "date"):
        html = html.replace("__%s__" % key.upper(), str(v.get(key, "")))
    return html.encode("utf-8")


class Handler(BaseHTTPRequestHandler):
    server_version = "voice-site"
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):  # keep container logs quiet and free of visitor details
        pass

    def send_bytes(self, status, body, ctype, extra=None):
        self.send_response(status)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Referrer-Policy", "no-referrer")
        for k, val in (extra or {}).items():
            self.send_header(k, val)
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(body)

    def send_apk(self, name, attachment_name):
        path = os.path.join(PUBLIC, name)
        if not SAFE_NAME.match(name) or not os.path.isfile(path):
            return self.send_bytes(404, b"Not found", "text/plain; charset=utf-8")
        size = os.path.getsize(path)
        self.send_response(200)
        self.send_header("Content-Type", APK_TYPE)
        self.send_header("Content-Length", str(size))
        self.send_header("Content-Disposition", 'attachment; filename="%s"' % attachment_name)
        self.send_header("Cache-Control", "no-cache")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.end_headers()
        if self.command == "HEAD":
            return
        bump_counter(name)
        with open(path, "rb") as f:
            while True:
                chunk = f.read(256 * 1024)
                if not chunk:
                    break
                self.wfile.write(chunk)

    def do_HEAD(self):
        self.do_GET()

    def do_GET(self):
        path = self.path.split("?", 1)[0]
        if path in ("/", "/index.html"):
            return self.send_bytes(200, render_index(), "text/html; charset=utf-8", {"Cache-Control": "no-store"})
        if path == "/healthz":
            return self.send_bytes(200, b"ok", "text/plain; charset=utf-8", {"Cache-Control": "no-store"})
        if path == "/version.json":
            body = json.dumps(read_version()).encode("utf-8")
            return self.send_bytes(200, body, "application/json", {"Cache-Control": "no-store", "Access-Control-Allow-Origin": "*"})
        if path in ("/download", "/pharr-voice.apk"):
            v = read_version()
            if not v.get("file"):
                return self.send_bytes(404, b"No release published yet", "text/plain; charset=utf-8")
            return self.send_apk(v["file"], "pharr-voice-%s.apk" % v.get("version", ""))
        if path.startswith("/files/"):
            name = path[len("/files/"):]
            if name.endswith(".apk"):
                return self.send_apk(name, name)
            if name == "SHA256SUMS.txt" and os.path.isfile(os.path.join(PUBLIC, name)):
                with open(os.path.join(PUBLIC, name), "rb") as f:
                    return self.send_bytes(200, f.read(), "text/plain; charset=utf-8", {"Cache-Control": "no-store"})
        if path in ASSETS:
            ctype, cache = ASSETS[path]
            with open(os.path.join(HERE, path.lstrip("/")), "rb") as f:
                return self.send_bytes(200, f.read(), ctype, {"Cache-Control": cache})
        if path in ("/robots.txt",):
            return self.send_bytes(200, b"User-agent: *\nAllow: /\n", "text/plain; charset=utf-8")
        return self.send_bytes(404, b"Not found", "text/plain; charset=utf-8")

    def do_POST(self):
        self.send_bytes(405, b"Method not allowed", "text/plain; charset=utf-8", {"Allow": "GET, HEAD"})


if __name__ == "__main__":
    os.makedirs(PUBLIC, exist_ok=True)
    ThreadingHTTPServer(("0.0.0.0", int(os.environ.get("PORT", "8080"))), Handler).serve_forever()
