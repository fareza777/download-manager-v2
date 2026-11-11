"""Minimal client for the Blender MCP addon socket bridge (default 127.0.0.1:9876).

The addon speaks newline-free JSON: send one command object, read until the reply parses.

Usage:
    python tools/blender_bridge.py info
    python tools/blender_bridge.py run path/to/script.py [timeout_seconds]
"""

import json
import socket
import sys
import time

HOST = "127.0.0.1"
PORT = 9876


def call(command, timeout=60):
    sock = socket.create_connection((HOST, PORT), timeout=5)
    sock.settimeout(timeout)
    sock.sendall(json.dumps(command).encode("utf-8"))
    buffer = b""
    deadline = time.time() + timeout
    try:
        while time.time() < deadline:
            chunk = sock.recv(65536)
            if not chunk:
                break
            buffer += chunk
            try:
                return json.loads(buffer.decode("utf-8"))
            except json.JSONDecodeError:
                continue
        raise RuntimeError("no complete JSON reply: " + buffer[:400].decode("utf-8", "replace"))
    finally:
        sock.close()


def main():
    mode = sys.argv[1] if len(sys.argv) > 1 else "info"
    if mode == "info":
        print(json.dumps(call({"type": "get_scene_info", "params": {}}))[:1200])
        return
    if mode == "run":
        path = sys.argv[2]
        timeout = float(sys.argv[3]) if len(sys.argv) > 3 else 120.0
        with open(path, "r", encoding="utf-8") as handle:
            code = handle.read()
        reply = call({"type": "execute_code", "params": {"code": code}}, timeout=timeout)
        print(json.dumps(reply)[:2000])
        return
    raise SystemExit("unknown mode: " + mode)


if __name__ == "__main__":
    main()
