#!/usr/bin/env python3
"""Verify a dedicated landscape for every encounter and preserve generation provenance."""
import hashlib
import json
import re
import struct
from pathlib import Path

root = Path(__file__).resolve().parent.parent
assets = root / "app/src/main/assets/backgrounds"
content = (root / "app/src/main/java/io/github/hatake716/bossrush/Content.kt").read_text()
bosses = re.findall(r'Boss\("([a-z]+)"', content)
expected = {f"battle_{name}" for name in bosses} | {"worldtree"}
assert len(bosses) == 32 and len(expected) == 33
actual = {path.stem for path in assets.glob("*.png")}
assert actual == expected, f"Missing: {expected - actual}; unexpected: {actual - expected}"
records = json.loads((root / "docs/background-prompts.json").read_text())["backgrounds"]
assert len(records) == 33 and {r["id"] for r in records} == expected
hashes = set()
for record in records:
    path = assets / f'{record["id"]}.png'
    assert record["file"] == str(path.relative_to(root))
    data = path.read_bytes()
    assert data[:8] == b"\x89PNG\r\n\x1a\n", path
    width, height, depth, color_type = struct.unpack(">IIBB", data[16:26])
    assert 960 <= width <= 4096 and 540 <= height <= 4096, path
    assert 1.6 <= width / height <= 1.9, f"Landscape composition required: {path}"
    assert depth == 8 and color_type in (2, 6), f"RGB or RGBA PNG required: {path}"
    digest = hashlib.sha256(data).hexdigest()
    assert record["sha256"] == digest, f"Record does not match asset: {path}"
    assert digest not in hashes, f"Shared background: {path}"
    hashes.add(digest)
    assert record["prompt"] and record["source"].endswith(".png"), path
print("33 distinct landscape PNGs match all 32 boss IDs, the world tree and generation records.")
