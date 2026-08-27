#!/usr/bin/env python3
"""Harvest CrakeGlideTrace lines from a device into replayable specimens.

The debug keyboard logs, for every committed glide:
    layout q:59:80:95:131,w:165:80:95:131,...      (on keyboard layout)
    pts 0 x:y;x:y;...                              (chunked, 150/line)
    pts 1 x:y;...
    commit prev="i" top=would,world,word n=213     (ends one trace)

This script pairs each commit with the pts chunks before it and the most
recent layout, writing one pipe-delimited line per trace to the output file, which
tests/glide_replay.rs replays against the real engine (no serde needed):

    prev|top1,top2,top3|ch:x:y:w:h,ch:x:y:w:h,...|x:y;x:y;...

Usage:
    python utils/harvest_glide_traces.py [serial] [outfile]
    adb -s SERIAL logcat -d -s CrakeGlideTrace | python utils/harvest_glide_traces.py - [outfile]

Default outfile: libnative/crates/floris-core/tests/data/glide_traces.txt
(appends; dedupes exact repeats).
"""
import subprocess
import sys
from pathlib import Path

DEFAULT_OUT = Path(__file__).resolve().parent.parent / (
    "libnative/crates/floris-core/tests/data/glide_traces.txt"
)


def read_lines(argv):
    if len(argv) > 1 and argv[1] == "-":
        return sys.stdin.read().splitlines()
    serial = argv[1] if len(argv) > 1 else None
    cmd = ["adb"] + (["-s", serial] if serial else []) + [
        "logcat", "-d", "-s", "CrakeGlideTrace",
    ]
    return subprocess.run(cmd, capture_output=True, text=True, check=True).stdout.splitlines()


def payload(line):
    # "08-27 12:50:49.857  1307  1307 I CrakeGlideTrace: layout q:..."
    marker = "CrakeGlideTrace: "
    i = line.find(marker)
    return line[i + len(marker):] if i >= 0 else None


def parse(lines):
    layout = None
    chunks = {}
    for raw in lines:
        p = payload(raw)
        if p is None:
            continue
        if p.startswith("layout "):
            keys = []
            for item in p[len("layout "):].split(","):
                parts = item.split(":")
                if len(parts) != 5 or len(parts[0]) != 1:
                    continue
                ch = parts[0]
                if not ("a" <= ch <= "z" or "A" <= ch <= "Z"):
                    continue  # pre-ASCII-filter builds logged easter-egg glyphs
                x, y, w, h = (float(v) for v in parts[1:])
                keys.append({"ch": ch.lower(), "x": x, "y": y, "w": w, "h": h})
            if keys:
                layout = keys
            chunks = {}
        elif p.startswith("pts "):
            _, idx, data = p.split(" ", 2)
            pts = []
            for pair in data.split(";"):
                xy = pair.split(":")
                # Format v1: x:y. Format v2: x:y:t where t is milliseconds
                # since the stroke's first point (u32). Both accepted; v2
                # is what unblocks dwell/velocity work downstream.
                if len(xy) == 2:
                    pts.append([float(xy[0]), float(xy[1])])
                elif len(xy) == 3:
                    pts.append([float(xy[0]), float(xy[1]), float(xy[2])])
            chunks[int(idx)] = pts
        elif p.startswith("commit "):
            if layout is None or not chunks:
                chunks = {}
                continue
            meta = p[len("commit "):]
            prev = meta.split('prev="', 1)[1].split('"', 1)[0] if 'prev="' in meta else ""
            top = meta.split("top=", 1)[1].split(" ", 1)[0].split(",") if "top=" in meta else []
            points = [pt for i in sorted(chunks) for pt in chunks[i]]
            chunks = {}
            if len(points) >= 2 and top:
                lay = ",".join(
                    f"{k['ch']}:{k['x']:g}:{k['y']:g}:{k['w']:g}:{k['h']:g}" for k in layout
                )
                pts = ";".join(
                    ":".join(f"{v:g}" for v in pt) for pt in points
                )
                yield f"{prev}|{','.join(top)}|{lay}|{pts}"


def main():
    out = Path(sys.argv[2]) if len(sys.argv) > 2 else DEFAULT_OUT
    out.parent.mkdir(parents=True, exist_ok=True)
    seen = set()
    if out.exists():
        for line in out.read_text(encoding="utf-8").splitlines():
            seen.add(line)
    added = 0
    with out.open("a", encoding="utf-8") as f:
        for line in parse(read_lines(sys.argv)):
            if line not in seen:
                f.write(line + "\n")
                seen.add(line)
                added += 1
    print(f"harvested {added} new traces -> {out}")


if __name__ == "__main__":
    main()
