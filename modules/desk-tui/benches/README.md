# Desk TUI benchmarks

## Methodology

```bash
cd modules/desk-tui
cargo test --release -- --nocapture bench_
```

Timed unit tests (no Criterion) keep CI light. Record wall time from `Instant`.

## Environment to report

- OS / arch
- Rust version (`rustc -V`)
- Desk commit SHA
- Whether release or debug profile

## Workloads

| Name | Measures |
|------|----------|
| `bench_fuzzy_rank_2000` | Ranking 2000 paths |
| `bench_find_in_files` | Search across ~50 files |
| `bench_apply_edits` | 100 LSP text edits |
| `bench_large_buffer_set_text_*` | Buffer load ~64KiB / 256KiB / 1MiB |

## Agent Linux results (debug profile, illustrative)

Captured during Phase 5 on the cloud agent (not SLAs):

Run `cargo test -- --nocapture bench_` and paste timings into `docs/PHASE_5_RESULTS.md`.

## Not measured in CI

- 5–10 MiB buffers (optional local)
- LSP completion latency (needs jdtls)
- Full workspace discovery on huge monorepos
