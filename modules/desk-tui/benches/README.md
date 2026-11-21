# Desk TUI benchmarks (Phase 4)

## Methodology

Run from `modules/desk-tui`:

```bash
cargo test --release -- --nocapture bench_
```

Micro-benchmarks live as timed unit tests (no Criterion dependency yet) so CI stays light.

## Workloads

| Name | What it measures |
|------|------------------|
| `bench_fuzzy_rank_2000` | Ranking 2000 paths |
| `bench_find_in_files` | Search across fixture-sized tree |
| `bench_apply_edits` | Applying 100 LSP text edits |

## Reporting rules

- Record wall time from `Instant`.
- Note CPU arch / OS.
- Never invent numbers — print measured values only.
- Large-file highlight / LSP latency require local jdtls and are marked **unverified in CI**.
