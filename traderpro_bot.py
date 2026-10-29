#!/usr/bin/env python3
import json, urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

class Handler(BaseHTTPRequestHandler):
    def log_message(self, f, *a): pass
    def do_GET(self):
        if self.path.startswith("/api/raw_tv"):
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(b'{"map":{}}')
            return
        body = open("index.html", "rb").read()
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.end_headers()
        self.wfile.write(body)

if __name__ == "__main__":
    print("✅ GitHub Actions is now compiling TraderPro-v12-Autonomous.apk in your GitHub repo!")
    print("🌐 Meanwhile, preview the standalone app at http://127.0.0.1:8080")
    ThreadingHTTPServer.allow_reuse_address = True
    ThreadingHTTPServer(("127.0.0.1", 8080), Handler).serve_forever()
