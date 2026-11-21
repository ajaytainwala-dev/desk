#![allow(
    clippy::collapsible_if,
    clippy::collapsible_match,
    clippy::explicit_counter_loop,
    clippy::needless_range_loop
)]

pub mod app;
pub mod buffer;
pub mod config;
pub mod highlighter;
pub mod input;
pub mod lsp;
pub mod mode;
pub mod overlay;
pub mod ui;
pub mod workspace_edit;

#[cfg(test)]
mod bench_tests;
