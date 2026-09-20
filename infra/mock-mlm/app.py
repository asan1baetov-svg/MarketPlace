"""
Заглушка внешнего MLM-бэка для локальной разработки mlm-service.

Эндпоинты:
  GET  /sso/issue-token?mlm_user_id=U1&upline_id=&referral_code=&access_status=access_paid
        -> выдаёт подписанный (HS256, общий секрет) короткоживущий SSO-токен;
           этим токеном фронт/тест «заходит» на маркетплейс.
  POST /api/marketplace/activation   -> приём статуса активации от маркетплейса (проверяет подпись, логирует)
  POST /api/marketplace/purchases    -> приём данных о покупках от маркетплейса (проверяет подпись, логирует)
  POST /sim/account-status?mlm_user_id=U1&status=ACTIVE
        -> имитирует событие на стороне MLM (например, оплачен входной взнос через Finik):
           шлёт подписанный webhook в mlm-service маркетплейса (/webhooks/mlm/account-status)
  GET  /healthz

Подпись обратных вызовов в обе стороны: X-Signature = hex(HMAC-SHA256(secret, X-Timestamp + "." + body)).

Реальный контракт (OIDC / RS256+JWKS, состав claim'ов, аутентификация обратных вызовов)
согласуется с командой MLM-бэка — см. docs/ARCHITECTURE.md, раздел 5.
"""
import base64
import hashlib
import hmac
import json
import time
import os
import urllib.request
import uuid
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import urlparse, parse_qs

SECRET = b"local-dev-mlm-shared-secret-change-me"
ISSUER = "mock-mlm"
AUDIENCE = "green-eco-mall"
TTL_SECONDS = 120
MARKETPLACE_MLM_URL = os.environ.get("MARKETPLACE_MLM_URL", "http://localhost:8086")


def _b64(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def sign_body(timestamp: str, body: str) -> str:
    return hmac.new(SECRET, f"{timestamp}.{body}".encode(), hashlib.sha256).hexdigest()


def issue_jwt(claims: dict) -> str:
    header = {"alg": "HS256", "typ": "JWT", "kid": "mock-mlm-1"}
    now = int(time.time())
    payload = {
        "iss": ISSUER,
        "aud": AUDIENCE,
        "iat": now,
        "exp": now + TTL_SECONDS,
        "jti": str(uuid.uuid4()),
        **claims,
    }
    signing_input = f"{_b64(json.dumps(header).encode())}.{_b64(json.dumps(payload).encode())}"
    sig = hmac.new(SECRET, signing_input.encode(), hashlib.sha256).digest()
    return f"{signing_input}.{_b64(sig)}"


class Handler(BaseHTTPRequestHandler):
    def _send(self, code: int, body: dict):
        raw = json.dumps(body).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def do_GET(self):
        u = urlparse(self.path)
        if u.path == "/healthz":
            return self._send(200, {"status": "UP"})
        if u.path == "/sso/issue-token":
            q = parse_qs(u.query)
            claims = {
                "sub": q.get("mlm_user_id", ["U1"])[0],
                "mlm_user_id": q.get("mlm_user_id", ["U1"])[0],
                "referral_code": q.get("referral_code", [""])[0] or None,
                "upline_id": q.get("upline_id", [""])[0] or None,
                "access_status": q.get("access_status", ["access_paid"])[0],
                "email": q.get("email", [""])[0] or None,
                "locale": q.get("locale", ["ru"])[0],
            }
            return self._send(200, {"token": issue_jwt(claims), "expires_in": TTL_SECONDS})
        return self._send(404, {"error": "not found"})

    def do_POST(self):
        u = urlparse(self.path)
        length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(length).decode() if length else ""
        if u.path in ("/api/marketplace/activation", "/api/marketplace/purchases"):
            ts = self.headers.get("X-Timestamp", "")
            valid = hmac.compare_digest(sign_body(ts, body), self.headers.get("X-Signature", ""))
            key = self.headers.get("X-Idempotency-Key")
            print(f"[mock-mlm] {u.path} key={key} signature_valid={valid} <- {body}", flush=True)
            if not valid:
                return self._send(401, {"error": "invalid signature"})
            return self._send(200, {"received": True})
        if u.path == "/sim/account-status":
            q = parse_qs(u.query)
            payload = json.dumps({
                "mlmUserId": q.get("mlm_user_id", ["U1"])[0],
                "status": q.get("status", ["ACTIVE"])[0],
            })
            ts = str(int(time.time()))
            req = urllib.request.Request(
                f"{MARKETPLACE_MLM_URL}/webhooks/mlm/account-status", data=payload.encode(), method="POST",
                headers={"Content-Type": "application/json", "X-Timestamp": ts, "X-Signature": sign_body(ts, payload)})
            try:
                with urllib.request.urlopen(req, timeout=5) as resp:
                    return self._send(200, {"sent": payload, "marketplaceStatus": resp.status})
            except Exception as e:  # noqa: BLE001 — заглушка, показываем ошибку как есть
                return self._send(502, {"sent": payload, "error": str(e)})
        return self._send(404, {"error": "not found"})

    def log_message(self, fmt, *args):  # тише в консоли
        return


if __name__ == "__main__":
    print("mock-mlm listening on :9100", flush=True)
    HTTPServer(("0.0.0.0", 9100), Handler).serve_forever()
