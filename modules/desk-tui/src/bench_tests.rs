//! Lightweight timing harness (Phase 4). Prints durations; does not assert SLAs.

#[cfg(test)]
mod benches {
    use crate::overlay::{find_in_files, rank_paths};
    use crate::workspace_edit::{TextEdit, apply_edits_to_lines};
    use std::path::PathBuf;
    use std::time::Instant;

    #[test]
    fn bench_fuzzy_rank_2000() {
        let paths: Vec<PathBuf> = (0..2000)
            .map(|i| PathBuf::from(format!("src/main/java/pkg/Class{i}.java")))
            .collect();
        let start = Instant::now();
        let ranked = rank_paths("Class42", &paths, 50);
        let elapsed = start.elapsed();
        assert!(!ranked.is_empty());
        eprintln!(
            "bench_fuzzy_rank_2000: {:?} (top={})",
            elapsed,
            ranked[0].path.display()
        );
    }

    #[test]
    fn bench_apply_edits() {
        let mut lines: Vec<String> = (0..500).map(|i| format!("line {i} content")).collect();
        let mut edits = Vec::new();
        for i in (0..100).rev() {
            edits.push(TextEdit {
                start_line: i * 5,
                start_col: 0,
                end_line: i * 5,
                end_col: 4,
                new_text: "LINE".into(),
            });
        }
        let start = Instant::now();
        apply_edits_to_lines(&mut lines, &edits);
        eprintln!("bench_apply_edits: {:?}", start.elapsed());
    }

    #[test]
    fn bench_find_in_files_empty_query() {
        let dir = tempfile::tempdir().unwrap();
        for i in 0..50 {
            std::fs::write(dir.path().join(format!("F{i}.java")), "class F {}\n").unwrap();
        }
        let start = Instant::now();
        let hits = find_in_files(dir.path(), "class", 100);
        eprintln!(
            "bench_find_in_files: {:?} hits={}",
            start.elapsed(),
            hits.len()
        );
        assert!(!hits.is_empty());
    }

    #[test]
    fn bench_large_buffer_set_text_sizes() {
        use crate::buffer::Buffer;
        for &kib in &[64usize, 256, 1024] {
            let line = "public class Big { int x; void m() { System.out.println(x); } }\n";
            let mut s = String::with_capacity(kib * 1024);
            while s.len() < kib * 1024 {
                s.push_str(line);
            }
            let start = Instant::now();
            let mut buf = Buffer::new_empty();
            buf.set_text(&s);
            let elapsed = start.elapsed();
            eprintln!(
                "bench_large_buffer_set_text_{kib}KiB: {:?} lines={}",
                elapsed,
                buf.lines.len()
            );
            assert!(buf.lines.len() > 10);
        }
    }
}
