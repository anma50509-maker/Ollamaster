#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""毒网关模拟器：复现导致「模型未返回有效内容」的三类毒响应，验证修复后的行解析行为。
   /sse           正常 SSE 分块（尾巴带换行）
   /sse_no_nl     SSE 整包返回、末尾无换行  ← 修复前：整条被丢弃
   /plain_json    忽略 stream，直接回整包 JSON、无换行 ← 修复前：整条被丢弃
   /err200        HTTP 200 但体内是 error 对象（网关限流/模型不存在）
   /empty         HTTP 200 且 choices 为空
"""
import sys, threading
from http.server import BaseHTTPRequestHandler, HTTPServer

def sse(deltas, trailing_nl=True):
    out = []
    for d in deltas:
        out.append('data: {"choices":[{"delta":{"content":"%s"}}]}\n\n' % d)
    out.append('data: {"choices":[{"delta":{},"finish_reason":"stop"}]}\n\n')
    out.append('data: [DONE]\n\n')
    s = "".join(out)
    return s if trailing_nl else s.rstrip("\n")

def plain_json(deltas, trailing_nl=False):
    s = '{"choices":[{"message":{"role":"assistant","content":"%s"},"finish_reason":"stop"}]}' % "".join(deltas)
    return s if trailing_nl else s

ROUTES = {
    "/sse":        lambda: ("text/event-stream", sse(["你", "好", "，", "世界"])),
    "/sse_no_nl":  lambda: ("text/event-stream", sse(["缺", "尾", "换", "行"])),
    "/plain_json": lambda: ("application/json",  plain_json(["整", "包", "JSON"])),
    "/err200":     lambda: ("application/json",  '{"error":{"message":"quota exceeded"}}'),
    "/empty":      lambda: ("application/json",  '{"choices":[{"message":{"content":""},"finish_reason":"stop"}]}'),
}

class H(BaseHTTPRequestHandler):
    def log_message(self, *a): pass
    def do_POST(self):
        n = int(self.headers.get('Content-Length') or 0)
        if n: self.rfile.read(n)
        fn = ROUTES.get(self.path)
        if not fn:
            self.send_response(404); self.end_headers(); return
        ct, body = fn()
        b = body.encode('utf-8')
        self.send_response(200)
        self.send_header('Content-Type', ct)
        self.send_header('Content-Length', str(len(b)))
        self.end_headers()
        self.wfile.write(b)

if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8901
    srv = HTTPServer(("127.0.0.1", port), H)
    print("毒网关监听 127.0.0.1:%d" % port)
    srv.serve_forever()
