#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""终端扫码登录：账号口令换 JWT → 申请一次性票据 → 在终端直接画出二维码。

手机：Spore-Mobile 设置 →「服务器与同步」→ 扫码登录 → 对着这个方块扫。

为什么不是网页：票据要一个「已登录的会话」去申请，而手机要扫的地址必须是
手机能访问的局域网 IP（写 127.0.0.1 的二维码扫了也连不上）。脚本能自己探测
本机网卡地址，网页只能让用户手填，填错就是「连不上服务器」。

依赖：Python 3.8+ 与 `segno`（只有打包二维码矩阵这一步用它；没装会给出 pip 提示）。
其余全是标准库，不连外网。

用法：
    python tools/qr-login.py                       # 自动探测本机局域网 IP + :19080
    python tools/qr-login.py --api http://192.168.1.5:19080
    python tools/qr-login.py -u demo               # 口令仍走隐藏输入
    SPORE_PASSWORD=... python tools/qr-login.py    # 给脚本用（避免口令进 argv）

Ctrl+C 退出。票据默认 60s 过期；到点自动换一张并重画。
"""

import argparse
import getpass
import json
import os
import socket
import sys
import time
import urllib.error
import urllib.request

QUIET = 4                 # 二维码静区（模块数），扫码器靠它定位
REFRESH_MARGIN = 5        # 提前几秒换票，别让手机扫到刚过期的码
BLACK = (0, 0, 0)
WHITE = (255, 255, 255)


def lan_ip() -> str:
    """本机在局域网里的地址：连一个不发包的 UDP 目标，拿到出口网卡地址。"""
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        sock.connect(("223.5.5.5", 80))
        return sock.getsockname()[0]
    except OSError:
        return "127.0.0.1"
    finally:
        sock.close()


def normalize_api(raw: str) -> str:
    """与手机端 ServerConnector.normalizeApi 同规则：补 http://、去尾斜杠、补 /api。"""
    api = raw.strip()
    if not api:
        raise SystemExit("服务器地址为空")
    if not api.startswith(("http://", "https://")):
        api = "http://" + api
    api = api.rstrip("/")
    if not api.endswith("/api"):
        api += "/api"
    return api


def call(api: str, path: str, body: dict, token: str = None) -> dict:
    """一次 R 包装调用：code=0 返回 data，否则把服务端原话抛出来。"""
    req = urllib.request.Request(
        api + path,
        data=json.dumps(body).encode("utf-8"),
        headers={"Content-Type": "application/json; charset=utf-8"},
        method="POST",
    )
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            raw = resp.read()
    except urllib.error.HTTPError as err:
        raw = err.read()          # 4xx 也带 R 包装，要读出来展示 message
    except urllib.error.URLError as err:
        raise SystemExit("连不上 %s：%s（服务起了吗？地址是手机也能访问的吗？）"
                         % (api, err.reason))
    try:
        envelope = json.loads(raw.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError):
        raise SystemExit("响应不是 R 包装：%r" % raw[:200])
    if envelope.get("code") != 0:
        raise SystemExit("服务端返回 %s：%s"
                         % (envelope.get("code"), envelope.get("message")))
    return envelope.get("data") or {}


def ticket_payload(api: str, token: str) -> tuple:
    data = call(api, "/auth/qr/ticket", {}, token)
    payload = json.dumps(
        {"v": 1, "api": api, "ticket": data["ticket"]},
        separators=(",", ":"), ensure_ascii=False,
    )
    return payload, int(data.get("expiresIn") or 60)


def render_ansi(payload: str) -> str:
    """把载荷画成终端二维码：每字符两行（▀ 上半用前景、下半用背景），模块近似正方。

    颜色不用终端主题的默认色，而是显式写死黑模块/白底——浅色终端和深色终端
    画出来都是同一个极性（深色模块 + 白色静区），相机不用赌「反色能不能解」。
    """
    try:
        import segno
    except ImportError:
        raise SystemExit(
            "缺 segno（只用来算二维码矩阵）：pip install segno\n"
            "不想装就把下面这段载荷粘到任意二维码生成器：\n" + payload
        )
    qr = segno.make(payload, error="m")
    matrix = [list(row) for row in qr.matrix]      # 0/1，无静区
    n = len(matrix)
    size = n + QUIET * 2

    def dark(x: int, y: int) -> bool:
        if x < QUIET or y < QUIET or x >= QUIET + n or y >= QUIET + n:
            return False
        return bool(matrix[y - QUIET][x - QUIET])

    lines = []
    for y in range(0, size, 2):
        parts = []
        for x in range(size):
            top = dark(x, y)
            bottom = dark(x, y + 1) if y + 1 < size else False
            fg = BLACK if top else WHITE
            bg = BLACK if bottom else WHITE
            parts.append("\x1b[38;2;%d;%d;%dm\x1b[48;2;%d;%d;%dm▀" % (fg + bg))
        parts.append("\x1b[0m")
        lines.append("".join(parts))
    return "\n".join(lines)


def draw(api: str, user: str, payload: str, seconds: int) -> None:
    qr = render_ansi(payload)
    sys.stdout.write("\x1b[2J\x1b[H")              # 清屏：每轮只留一个二维码
    sys.stdout.write("Spore-Cloud 扫码登录\n")
    sys.stdout.write("  账号   %s\n" % user)
    sys.stdout.write("  入口   %s\n" % api)
    sys.stdout.write("  手机   Spore-Mobile → 设置 →「服务器与同步」→ 扫码登录\n")
    sys.stdout.write("\n%s\n\n" % qr)
    sys.stdout.write("  %ds 后自动换票（Ctrl+C 退出）\n" % seconds)
    sys.stdout.flush()


def main() -> None:
    parser = argparse.ArgumentParser(add_help=True, description="终端显示扫码登录二维码")
    parser.add_argument("--api", default=None,
                        help="网关地址，留空用本机局域网 IP + :19080")
    parser.add_argument("-u", "--user", default=None, help="账号（留空则询问）")
    args = parser.parse_args()

    api = normalize_api(args.api or "http://%s:19080" % lan_ip())
    user = args.user or input("账号：").strip()
    password = os.environ.get("SPORE_PASSWORD") or getpass.getpass("口令：")
    if not user:
        raise SystemExit("账号不能为空")

    # 登录一次，之后每轮只换票：口令不在循环里反复提交，op_log 也不会被刷屏
    token = call(api, "/auth/login", {"username": user, "password": password})["token"]

    while True:
        payload, seconds = ticket_payload(api, token)
        draw(api, user, payload, seconds)
        try:
            time.sleep(max(seconds - REFRESH_MARGIN, 5))
        except KeyboardInterrupt:
            sys.stdout.write("\n已退出。\n")
            return


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        sys.stdout.write("\n已退出。\n")
