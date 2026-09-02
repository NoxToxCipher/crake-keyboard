use crake_core::{damerau_levenshtein, RadixTrie};
use std::time::Instant;

#[test]
fn bench_throughput_and_latencies() {
    let mut trie = RadixTrie::new();
    let word_count = 10_000;

    // 1. Benchmark Insertion
    let start = Instant::now();
    for i in 0..word_count {
        let word = format!("word{:05}", i);
        trie.insert(&word, i as u32);
    }
    let insert_duration = start.elapsed();
    let insert_per_sec = (word_count as f64) / insert_duration.as_secs_f64();

    // 2. Benchmark Prefix Lookup Latency
    let queries = ["word001", "word050", "word099", "word01", "word02"];
    let start = Instant::now();
    let iters = 10_000;
    for _ in 0..iters {
        for q in &queries {
            let _ = trie.prefix_search(q, 5);
        }
    }
    let prefix_duration = start.elapsed();
    let avg_prefix_ns = prefix_duration.as_nanos() / (iters * queries.len()) as u128;

    // 3. Benchmark Fuzzy Typo Search Latency
    let fuzzy_queries = ["wrd00100", "word0050x", "wrod00900"];
    let start = Instant::now();
    let fuzzy_iters = 1_000;
    for _ in 0..fuzzy_iters {
        for q in &fuzzy_queries {
            let _ = trie.fuzzy_search(q, 1, 5);
        }
    }
    let fuzzy_duration = start.elapsed();
    let avg_fuzzy_us = (fuzzy_duration.as_micros() as f64) / (fuzzy_iters * fuzzy_queries.len()) as f64;

    // 4. Benchmark Damerau-Levenshtein Metric Latency
    let start = Instant::now();
    let dl_iters = 50_000;
    for _ in 0..dl_iters {
        let _ = damerau_levenshtein("algorithm", "altruism");
        let _ = damerau_levenshtein("transposition", "trnaspsoition");
    }
    let dl_duration = start.elapsed();
    let avg_dl_ns = dl_duration.as_nanos() / (dl_iters * 2) as u128;

    println!("\n=== NATIVE BENCHMARK REPORT ===");
    println!("Dictionary Size:       {} words", word_count);
    println!("Insert Throughput:     {:.0} words/sec", insert_per_sec);
    println!("Prefix Lookup Latency: {} ns / query", avg_prefix_ns);
    println!("Fuzzy Search Latency:  {:.2} µs / query", avg_fuzzy_us);
    println!("Damerau-Levenshtein:   {} ns / comparison", avg_dl_ns);
    println!("===============================\n");

    #[cfg(not(debug_assertions))]
    {
        assert!(avg_prefix_ns < 50_000, "Prefix lookup should be < 50µs in release");
        assert!(avg_fuzzy_us < 500.0, "Fuzzy search should be < 500µs in release");
    }
    #[cfg(debug_assertions)]
    {
        assert!(avg_prefix_ns < 500_000, "Prefix lookup debug threshold");
        assert!(avg_fuzzy_us < 5_000.0, "Fuzzy search debug threshold");
    }
}
