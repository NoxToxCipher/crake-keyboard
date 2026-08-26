#!/usr/bin/env python3
"""Train the neural suggestion rescorer.

A 6-feature, one-hidden-layer MLP (6 -> 8 tanh -> 1 sigmoid) that scores how
likely a candidate is the intended word. Training data is SYNTHETIC by
design — user typing is never collected. The corruption model mirrors the
error classes measured from real captured typos (tests/slip_corpus.rs):
adjacent-key substitutions on real QWERTY geometry, transpositions, doubled
letters, dropped letters, plus untouched prefix-typing cases. Candidate
distractors mirror the engine's actual pool composition (prefix completions
and corruption-neighbours). Context positives are drawn from the shipped
CRKB bigram table so the network learns how much context evidence is worth
against frequency and distance.

Feature spec — MUST stay byte-identical with rescorer.rs:
    f0 = min(weighted_edit_units(typed, cand), 6) / 6
         (plain weighted Levenshtein: sub 0 same / 1 near / 2 else, indel 2)
    f1 = ln(1 + freq) / ln(256)
    f2 = bigram_score / 255
    f3 = 1.0 if first letters match else 0.0
    f4 = min(abs(len(cand) - len(typed)), 4) / 4
    f5 = 1.0 if cand starts_with typed (proper prefix completion) else 0.0

Outputs libnative/crates/floris-core/src/rescorer_weights.rs: weights,
provenance, held-out metrics, and parity fixtures the Rust tests verify.

Usage: python utils/train_rescorer.py [seed]
"""

import json
import math
import random
import struct
import sys
from pathlib import Path

import numpy as np

REPO = Path(__file__).resolve().parent.parent
DICT = REPO / "app/src/main/assets/ime/dict/data.json"
BIGRAMS = REPO / "app/src/main/assets/ime/dict/bigrams.crkb"
OUT = REPO / "libnative/crates/floris-core/src/rescorer_weights.rs"

SEED = int(sys.argv[1]) if len(sys.argv) > 1 else 42

QWERTY_ROWS = ["qwertyuiop", "asdfghjkl", "zxcvbnm"]
QWERTY_OFF = [0.0, 0.5, 1.5]
KEY_W, KEY_H = 100.0, 140.0
NEAR_FACTOR = 1.25  # keep in sync with touch_model.rs


def key_centers():
    pts = {}
    for r, row in enumerate(QWERTY_ROWS):
        for i, ch in enumerate(row):
            pts[ch] = ((i + QWERTY_OFF[r] + 0.5) * KEY_W, (r + 0.5) * KEY_H)
    return pts


CENTERS = key_centers()
NEAR_SQ = (KEY_W * NEAR_FACTOR) ** 2  # pitch == KEY_W on this grid


def near(a: str, b: str) -> bool:
    if a == b:
        return True
    pa, pb = CENTERS.get(a), CENTERS.get(b)
    if not pa or not pb:
        return False
    return (pa[0] - pb[0]) ** 2 + (pa[1] - pb[1]) ** 2 <= NEAR_SQ


def neighbours(ch: str):
    return [c for c in CENTERS if c != ch and near(c, ch)]


def edit_units(a: str, b: str) -> int:
    """Weighted Damerau-Levenshtein — MUST match rescorer.rs exactly."""
    la, lb = len(a), len(b)
    if la == 0 or lb == 0:
        return 2 * max(la, lb)
    prev_prev = [0] * (lb + 1)
    prev = [j * 2 for j in range(lb + 1)]
    for i in range(1, la + 1):
        cur = [i * 2] + [0] * lb
        for j in range(1, lb + 1):
            sub = 0 if a[i - 1] == b[j - 1] else (1 if near(a[i - 1], b[j - 1]) else 2)
            v = min(prev[j] + 2, cur[j - 1] + 2, prev[j - 1] + sub)
            if i >= 2 and j >= 2 and a[i - 1] == b[j - 2] and a[i - 2] == b[j - 1]:
                v = min(v, prev_prev[j - 2] + 2)
            cur[j] = v
        prev_prev, prev = prev, cur
    return prev[lb]


def features(typed, cand, freq, bigram):
    return [
        min(edit_units(typed, cand), 6) / 6.0,
        math.log1p(freq) / math.log(256.0),
        bigram / 255.0,
        1.0 if typed[:1] == cand[:1] else 0.0,
        min(abs(len(cand) - len(typed)), 4) / 4.0,
        1.0 if cand.startswith(typed) and cand != typed else 0.0,
    ]


def corrupt(word, rng):
    kind = rng.randrange(5)
    chars = list(word)
    if kind == 0:  # adjacent substitution (1-2 slips)
        for _ in range(1 if len(word) < 6 else rng.choice([1, 1, 2])):
            i = rng.randrange(len(chars))
            ns = neighbours(chars[i])
            if ns:
                chars[i] = rng.choice(ns)
    elif kind == 1 and len(chars) >= 4:  # transposition
        i = rng.randrange(len(chars) - 1)
        chars[i], chars[i + 1] = chars[i + 1], chars[i]
    elif kind == 2:  # double letter
        i = rng.randrange(len(chars))
        chars.insert(i, chars[i])
    elif kind == 3 and len(chars) >= 4:  # dropped letter
        del chars[rng.randrange(len(chars))]
    else:  # prefix typing (mid-word suggestion moment)
        cut = rng.randrange(2, max(3, len(chars)))
        chars = chars[:cut]
    return "".join(chars)


def main():
    rng = random.Random(SEED)
    np.random.seed(SEED)
    vocab = json.load(DICT.open(encoding="utf-8"))
    words = [w for w in vocab if w.isalpha() and 3 <= len(w) <= 12]
    order = {w: i for i, w in enumerate(vocab)}
    rev = list(vocab)

    raw = BIGRAMS.read_bytes()
    assert raw[4] == 2, "CRKB v2 expected"
    count, blob_vocab = struct.unpack_from("<II", raw, 5)
    assert blob_vocab == len(vocab), f"bigram table vocab {blob_vocab} != dict {len(vocab)}"
    pair_score = {}
    by_next = {}
    off = 13
    for _ in range(count):
        i1, i2, s = struct.unpack_from("<IIB", raw, off)
        off += 9
        pair_score[(i1, i2)] = s
        by_next.setdefault(i2, []).append(i1)

    by_first = {}
    for w in words:
        by_first.setdefault(w[0], []).append(w)
    for lst in by_first.values():
        lst.sort(key=lambda w: -vocab[w])

    weights = np.array([vocab[w] for w in words], dtype=np.float64)
    weights /= weights.sum()
    picks = np.random.choice(len(words), size=4000, p=weights)

    rows, labels, case_ids = [], [], []
    case = 0
    for pick in picks:
        intended = words[int(pick)]
        typed = corrupt(intended, rng)
        if typed == intended:
            continue
        # Context: half the cases get a genuine preceding word for the
        # intended target, the rest a random word (no signal).
        iid = order[intended]
        if rng.random() < 0.5 and by_next.get(iid):
            prev = rev[rng.choice(by_next[iid])]
        else:
            prev = rng.choice(words)
        pid = order.get(prev)

        def bg(cand):
            cid = order.get(cand)
            if pid is None or cid is None:
                return 0
            return pair_score.get((pid, cid), 0)

        cands = {intended}
        pool = by_first.get(typed[0], [])
        cands.update(pool[:4])  # prefix-ish frequent distractors
        for _ in range(4):  # corruption-neighbours of the typed string
            alt = corrupt(typed, rng)
            if alt in vocab:
                cands.add(alt)
        same_len = [w for w in pool if abs(len(w) - len(typed)) <= 1]
        if same_len:
            cands.add(rng.choice(same_len))
        if len(cands) < 3:
            continue
        for cand in sorted(cands):
            rows.append(features(typed, cand, vocab.get(cand, 0), bg(cand)))
            labels.append(1.0 if cand == intended else 0.0)
            case_ids.append(case)
        case += 1

    X = np.array(rows, dtype=np.float64)
    y = np.array(labels, dtype=np.float64)
    cid = np.array(case_ids)
    holdout = cid % 7 == 0
    Xtr, ytr = X[~holdout], y[~holdout]

    hidden = 8
    rng2 = np.random.default_rng(SEED)
    w1 = rng2.normal(0, 0.5, (hidden, 6))
    b1 = np.zeros(hidden)
    w2 = rng2.normal(0, 0.5, hidden)
    b2 = 0.0
    lr = 0.05
    n = len(Xtr)
    # Class weighting: ~1 positive to ~8 negatives per case.
    pos_w = (ytr == 0).sum() / max(1.0, (ytr == 1).sum())
    for epoch in range(60):
        perm = np.random.permutation(n)
        for start in range(0, n, 256):
            idx = perm[start : start + 256]
            xb, yb = Xtr[idx], ytr[idx]
            h = np.tanh(xb @ w1.T + b1)
            z = h @ w2 + b2
            p = 1.0 / (1.0 + np.exp(-z))
            sw = np.where(yb == 1.0, pos_w, 1.0)
            g = (p - yb) * sw / len(xb)
            gw2 = g @ h
            gb2 = g.sum()
            gh = np.outer(g, w2) * (1 - h * h)
            gw1 = gh.T @ xb
            gb1 = gh.sum(axis=0)
            w2 -= lr * gw2
            b2 -= lr * gb2
            w1 -= lr * gw1
            b1 -= lr * gb1

    def score_all(Xa):
        return 1.0 / (1.0 + np.exp(-(np.tanh(Xa @ w1.T + b1) @ w2 + b2)))

    # Held-out ranking eval: per case, does the intended word win?
    s = score_all(X)
    neural_top1 = base_top1 = total = 0
    for c in np.unique(cid[holdout]):
        m = cid == c
        if y[m].sum() != 1:
            continue
        total += 1
        truth = np.argmax(y[m])
        if np.argmax(s[m]) == truth:
            neural_top1 += 1
        # Baseline mirror of today's tail order: fewest units, then frequency.
        base_key = X[m][:, 0] * 1000 - X[m][:, 1]
        if np.argmin(base_key) == truth:
            base_top1 += 1

    print(f"cases={case} rows={len(X)} holdout_cases={total}")
    print(f"held-out top-1: neural={neural_top1 / total:.3f} baseline={base_top1 / total:.3f}")

    fixtures = X[:3]
    fix_scores = score_all(fixtures)

    def fmt(a):
        return ", ".join(f"{v:.7}f32" for v in np.asarray(a).flatten())

    body = f"""//! GENERATED by utils/train_rescorer.py — do not edit by hand.
//!
//! Neural suggestion rescorer weights: MLP 6 -> {hidden} (tanh) -> 1 (sigmoid).
//! Trained {case} synthetic fat-finger cases (seed {SEED}) generated from the
//! measured error classes in tests/slip_corpus.rs over real QWERTY geometry,
//! with context positives drawn from the shipped CRKB bigram table.
//! Held-out top-1: neural {neural_top1 / total:.3f} vs baseline {base_top1 / total:.3f}
//! ({total} held-out cases). No user typing was used, ever.

pub const HIDDEN: usize = {hidden};
pub const W1: [[f32; 6]; {hidden}] = [{", ".join("[" + fmt(r) + "]" for r in w1)}];
pub const B1: [f32; {hidden}] = [{fmt(b1)}];
pub const W2: [f32; {hidden}] = [{fmt(w2)}];
pub const B2: f32 = {b2:.7}f32;

/// Cross-language parity fixtures: feature vectors and the exact scores the
/// training-time network produced for them. rescorer.rs asserts its forward
/// pass reproduces these within 1e-4.
pub const FIXTURES: [([f32; 6], f32); 3] = [
{chr(10).join(f"    ([{fmt(f)}], {sc:.7}f32)," for f, sc in zip(fixtures, fix_scores))}
];
"""
    OUT.write_text(body, encoding="utf-8")
    print(f"wrote {OUT.relative_to(REPO)}")


if __name__ == "__main__":
    main()
