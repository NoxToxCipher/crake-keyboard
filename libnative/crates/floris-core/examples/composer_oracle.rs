//! Differential-oracle runner for the composer migration. Reads the case
//! file produced by the Kotlin oracle harness (tag \t hex16(preceding) \t
//! hex16(toInsert) \t n \t hex16(out)), recomputes the last two columns
//! through the migrated pipeline — including the FlorisNative wrapper's
//! string-conversion semantics — and writes an identically formatted file.
//! A byte-identical diff against the Kotlin output proves the port.

use floris_core::{hangul_unicode_actions, kana_unicode_actions, RuleComposer};
use std::io::{BufRead, BufWriter, Write};

fn decode(field: &str) -> Vec<u16> {
    if field == "-" {
        Vec::new()
    } else {
        field
            .split(',')
            .map(|h| u16::from_str_radix(h, 16).expect("bad hex"))
            .collect()
    }
}

fn encode(s: &str) -> String {
    if s.is_empty() {
        "-".to_string()
    } else {
        s.encode_utf16()
            .map(|u| format!("{u:x}"))
            .collect::<Vec<_>>()
            .join(",")
    }
}

fn encode_units(units: &[u16]) -> String {
    if units.is_empty() {
        "-".to_string()
    } else {
        units.iter().map(|u| format!("{u:x}")).collect::<Vec<_>>().join(",")
    }
}

fn rulesets() -> Vec<RuleComposer> {
    // Must match RULESETS in OracleMain.kt exactly, in order.
    let mk = |v: &[(&str, &str)]| {
        RuleComposer::new(v.iter().map(|(k, val)| (k.to_string(), val.to_string())).collect())
    };
    vec![
        mk(&[
            ("aa", "â"), ("ee", "ê"), ("oo", "ô"), ("dd", "đ"),
            ("aw", "ă"), ("ow", "ơ"), ("uw", "ư"), ("w", "ư"),
        ]),
        mk(&[("sch", "š"), ("ch", "č"), ("h", "ħ"), ("c", "ç")]),
        mk(&[("ss", "ß"), ("SS", "X"), ("i", "ı"), ("a", "á")]),
        mk(&[("", "E"), ("q", "Q")]),
        mk(&[("𝕒", "A"), ("aa", "B")]),
    ]
}

fn main() {
    let args: Vec<String> = std::env::args().collect();
    let cases = std::fs::File::open(&args[1]).expect("open cases");
    let out_file = std::fs::File::create(&args[2]).expect("create out");
    let mut out = BufWriter::new(out_file);
    let rules = rulesets();
    let mut n_cases = 0u64;

    for line in std::io::BufReader::new(cases).lines() {
        let line = line.expect("read line");
        let mut cols = line.split('\t');
        let tag = cols.next().expect("tag");
        let p_units = decode(cols.next().expect("p"));
        let t_units = decode(cols.next().expect("t"));

        // FlorisNative wrapper semantics: unpaired surrogate in toInsert ->
        // no native call -> Kotlin fallback (0, toInsert); preceding is
        // sanitized per-unit (from_utf16_lossy).
        let (n, out_field) = match String::from_utf16(&t_units) {
            Err(_) => (0, encode_units(&t_units)),
            Ok(t) => {
                let p = String::from_utf16_lossy(&p_units);
                let (n, res) = match tag {
                    "h" => hangul_unicode_actions(&p, &t),
                    "k" => kana_unicode_actions(&p, &t),
                    _ => {
                        let idx: usize = tag
                            .strip_prefix('r')
                            .and_then(|i| i.parse().ok())
                            .expect("rule tag");
                        rules[idx].get_actions(&p, &t)
                    }
                };
                (n, encode(&res))
            }
        };
        writeln!(
            out,
            "{}\t{}\t{}\t{}\t{}",
            tag,
            encode_units(&p_units),
            encode_units(&t_units),
            n,
            out_field
        )
        .expect("write");
        n_cases += 1;
    }
    out.flush().expect("flush");
    eprintln!("RUST CASES: {n_cases}");
}
