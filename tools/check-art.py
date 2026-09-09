#!/usr/bin/env python3
"""Check packaged character IDs, PNG alpha format and generation-record digests."""
import hashlib
import json
import re
import struct
from pathlib import Path

root = Path(__file__).resolve().parent.parent
assets = root / "app/src/main/assets/sprites"
content = (root / "app/src/main/java/io/github/hatake716/bossrush/Content.kt").read_text()
bosses = re.findall(r'Boss\("([a-z]+)"', content)
expected = {f"boss_{name}" for name in bosses} | {
    "hero_warrior", "hero_mage", "hero_summoner", "hero_thief",
    "summon_giant", "summon_rabbit", "summon_haniwa",
}
assert len(bosses) == 32 and len(expected) == 39
actual = {path.stem for path in assets.glob("*.png")}
assert actual == expected, f"Missing: {expected - actual}; unexpected: {actual - expected}"
records = json.loads((root / "docs/art-prompts.json").read_text())["characters"]
assert len(records) == 39 and {r["id"] for r in records} == expected
hashes = set()
for record in records:
    path = assets / f'{record["id"]}.png'
    assert record["file"] == str(path.relative_to(root))
    data = path.read_bytes()
    assert data[:8] == b"\x89PNG\r\n\x1a\n", path
    width, height, depth, color_type = struct.unpack(">IIBB", data[16:26])
    assert 256 <= width <= 4096 and 256 <= height <= 4096, path
    assert depth == 8 and color_type == 6, f"RGBA PNG required: {path}"
    digest = hashlib.sha256(data).hexdigest()
    assert record["sha256"] == digest, f"Record does not match asset: {path}"
    assert digest not in hashes, f"Shared artwork: {path}"
    hashes.add(digest)
    assert record["prompt"] and record["source"].endswith(".png"), path
print("39 distinct RGBA character PNGs match all boss IDs and generation records.")
