#!/usr/bin/env python3
"""Minimal stdio MCP-like echo server for Phase 2 qasync smoke tests."""

from __future__ import annotations

import json
import sys


def main() -> None:
    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue
        try:
            payload = json.loads(line)
        except json.JSONDecodeError:
            print(json.dumps({"error": "invalid_json"}), flush=True)
            continue
        method = payload.get("method") or payload.get("tool") or "echo"
        response = {
            "id": payload.get("id"),
            "result": {"ok": True, "method": method, "echo": payload},
        }
        print(json.dumps(response), flush=True)


if __name__ == "__main__":
    main()
