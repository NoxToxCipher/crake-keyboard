#!/usr/bin/env python3
"""Generate the CRKD binary dictionary blob from the JSON dictionary.

The keyboard loads app/src/main/assets/ime/dict/data.crkd at startup via a
single JNI call (see floris-core/src/blob.rs for the format and the on-device
timings that motivated it). Rerun this whenever data.json changes:

    python utils/gen_dict_blob.py

Format (all little-endian):
    magic   b"CRKD"
    version u8 = 1
    count   u32
    entry*  { len: u16, word: UTF-8, freq: u32 }

The script fails loudly instead of skipping entries: a silently thinner
dictionary is exactly the kind of divergence the blob path must not introduce.
"""

import json
import struct
import sys
from pathlib import Path

MAGIC = b"CRKD"
VERSION = 1
MAX_WORD_LEN = 64  # keep in sync with floris-core/src/blob.rs

REPO = Path(__file__).resolve().parent.parent
SRC = REPO / "app/src/main/assets/ime/dict/data.json"
DST = REPO / "app/src/main/assets/ime/dict/data.crkd"


def main() -> None:
    data = json.loads(SRC.read_text(encoding="utf-8"))
    out = bytearray()
    out += MAGIC
    out += struct.pack("<BI", VERSION, len(data))
    for word, freq in data.items():
        encoded = word.encode("utf-8")
        if not 1 <= len(encoded) <= MAX_WORD_LEN:
            sys.exit(f"word length {len(encoded)} outside 1..{MAX_WORD_LEN}: {word!r}")
        if not 0 <= int(freq) <= 0xFFFFFFFF:
            sys.exit(f"frequency {freq!r} does not fit u32: {word!r}")
        out += struct.pack("<H", len(encoded))
        out += encoded
        out += struct.pack("<I", int(freq))
    DST.write_bytes(out)
    json_kb = SRC.stat().st_size // 1024
    blob_kb = len(out) // 1024
    print(f"wrote {DST.name}: {len(data)} words, {blob_kb} KiB (json: {json_kb} KiB)")


if __name__ == "__main__":
    main()
