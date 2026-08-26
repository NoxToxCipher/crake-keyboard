#!/usr/bin/env python3
"""Generate the CRKB bigram language-model blob.

Source data: Peter Norvig's `count_2w.txt` (https://norvig.com/ngrams/),
286k English bigrams with counts derived from the Google Web Trillion Word
Corpus; published as free-to-use data. Pairs are kept only when BOTH words
exist in our dictionary (data.json), so IDs can index straight into the CRKD
blob order and no vocabulary table needs shipping.

Scores are quantized log2(count) * 8, clamped to u8 — ranking resolution of
an eighth of a bit, far finer than re-ranking needs.

Format (all little-endian), entries sorted by (id1, id2) so the reader can
binary-search and can VERIFY sortedness on load:
    magic   b"CRKB"
    version u8 = 1
    count   u32
    entry*  { id1: u32, id2: u32, score: u8 }

Rerun when data.json changes (IDs are positional!):
    python utils/gen_bigram_blob.py path/to/count_2w.txt
"""

import json
import math
import re
import struct
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
DICT = REPO / "app/src/main/assets/ime/dict/data.json"
DST = REPO / "app/src/main/assets/ime/dict/bigrams.crkb"
TOKEN = re.compile(r"^[a-z']+$")


def main() -> None:
    src = Path(sys.argv[1])
    vocab = {w: i for i, w in enumerate(json.load(DICT.open(encoding="utf-8")))}
    pairs = {}
    total = kept = 0
    for line in src.open(encoding="utf-8"):
        total += 1
        try:
            words, count = line.rsplit("\t", 1)
            w1, w2 = words.lower().split()
        except ValueError:
            continue
        if not (TOKEN.match(w1) and TOKEN.match(w2)):
            continue
        i1, i2 = vocab.get(w1), vocab.get(w2)
        if i1 is None or i2 is None:
            continue
        score = min(255, round(math.log2(max(2, int(count))) * 8))
        key = (i1, i2)
        if score > pairs.get(key, -1):
            pairs[key] = score
        kept += 1
    out = bytearray(b"CRKB")
    out += struct.pack("<BI", 1, len(pairs))
    for (i1, i2), score in sorted(pairs.items()):
        out += struct.pack("<IIB", i1, i2, score)
    DST.write_bytes(out)
    print(
        f"read {total} lines, kept {kept} in-vocab pairs, {len(pairs)} unique -> "
        f"{DST.name} {len(out) // 1024} KiB"
    )


if __name__ == "__main__":
    main()
