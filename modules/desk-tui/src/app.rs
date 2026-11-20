use crate::buffer::{Buffer, Selection};
use crate::config::{EditorConfig, LineNumberMode, Theme};
use crate::highlighter::Language;
use crate::mode::Mode;
use crossbeam_channel::{unbounded, Receiver, Sender};
use std::fs;
use std::io::{BufRead, BufReader};
use std::path::{Path, PathBuf};
use std::process::{Child, ChildStdin, Command, Stdio};
use std::sync::{Arc, Mutex};
use std::thread;
use std::time::{Duration, Instant};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum MenuTab {
    None,
    File,
    Edit,
    View,
    Run,
}

impl MenuTab {
    pub fn all() -> [(&'static str, MenuTab); 4] {
        [
            ("File", MenuTab::File),
            ("Edit", MenuTab::Edit),
            ("View", MenuTab::View),
            ("Run", MenuTab::Run),
        ]
    }

    pub fn tab_rect(idx: usize) -> (u16, u16) {
        let labels = ["File", "Edit", "View", "Run"];
        let mut start: u16 = 0;
        for label in &labels[..idx] {
            start += label.len() as u16 + 3;
        }
        let width = labels[idx].len() as u16 + 2;
        (start, width)
    }

    pub fn items(&self) -> Vec<&'static str> {
        match self {
            MenuTab::File => vec![
                "New File (Ctrl+N)",
                "Open File (Ctrl+O)",
                "Save (Ctrl+S)",
                "Save As...",
                "Reload File",
                "Quit (Ctrl+Q)",
            ],
            MenuTab::Edit => vec![
                "Undo (Ctrl+Z)",
                "Redo (Ctrl+Y)",
                "Cut (Ctrl+X)",
                "Copy (Ctrl+C)",
                "Paste (Ctrl+V)",
                "Select All (Ctrl+A)",
                "Find (Ctrl+F)",
                "Replace (Ctrl+H)",
                "Format (F9 / Shift+Alt+F)",
            ],
            MenuTab::View => vec![
                "Toggle Explorer (F3)",
                "Toggle Terminal (Ctrl+J)",
                "Toggle Output (Ctrl+`)",
                "Cycle Line Numbers (F4)",
                "Switch Theme",
                "Toggle Word Wrap",
            ],
            MenuTab::Run => vec![
                "Run Program (F5)",
                "Compile (F6)",
                "Check Java (F10)",
                "Stop (F7)",
                "Clear Output",
            ],
            MenuTab::None => vec![],
        }
    }
}

#[derive(Debug, Clone)]
pub struct FileNode {
    pub name: String,
    pub path: PathBuf,
    pub is_dir: bool,
    pub expanded: bool,
    pub children: Vec<FileNode>,
    pub children_loaded: bool,
}

impl FileNode {
    pub fn icon(&self) -> &'static str {
        if self.is_dir {
            if self.expanded {
                "▾"
            } else {
                "▸"
            }
        } else {
            match self
                .path
                .extension()
                .and_then(|e| e.to_str())
                .unwrap_or("")
                .to_lowercase()
                .as_str()
            {
                "java" => "☕",
                "rs" => "ᴿ",
                "py" => "Py",
                "c" | "h" | "cpp" | "hpp" => "C",
                "js" | "jsx" | "ts" | "tsx" => "JS",
                "json" | "toml" | "yaml" | "yml" => "{}",
                "md" | "txt" => "T",
                "html" | "css" => "<>",
                "class" => "CL",
                _ => "·",
            }
        }
    }

    pub fn find_mut(&mut self, path: &Path) -> Option<&mut FileNode> {
        if self.path == path {
            return Some(self);
        }
        for child in &mut self.children {
            if let Some(found) = child.find_mut(path) {
                return Some(found);
            }
        }
        None
    }

    pub fn find(&self, path: &Path) -> Option<&FileNode> {
        if self.path == path {
            return Some(self);
        }
        for child in &self.children {
            if let Some(found) = child.find(path) {
                return Some(found);
            }
        }
        None
    }
}

#[derive(Debug, Clone)]
pub struct ToastNotification {
    pub message: String,
    pub kind: String,
    pub created_at: Instant,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum OutputKind {
    Out,
    Err,
    Info,
}

#[derive(Debug)]
pub enum JobMessage {
    Output(String, OutputKind),
    Finished { success: bool, cancelled: bool },
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum JobStatus {
    Running,
    Success,
    Failed,
    Cancelled,
}

#[derive(Debug)]
pub struct Job {
    pub id: u64,
    pub name: String,
    pub child: Arc<Mutex<Option<Child>>>,
    pub stdin: Option<ChildStdin>,
    pub receiver: Receiver<JobMessage>,
    pub started_at: Instant,
    pub status: JobStatus,
}

impl Job {
    pub fn send_input(&mut self, line: &str) -> std::io::Result<()> {
        if let Some(stdin) = self.stdin.as_mut() {
            std::io::Write::write_all(stdin, format!("{}\n", line).as_bytes())?;
            std::io::Write::flush(stdin)?;
        }
        Ok(())
    }

    pub fn stop(&self) {
        if let Ok(mut guard) = self.child.lock()
            && let Some(child) = guard.as_mut()
        {
            let _ = child.kill();
        }
    }

    pub fn elapsed(&self) -> Duration {
        self.started_at.elapsed()
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
pub enum SearchField {
    #[default]
    Find,
    Replace,
}

#[derive(Debug, Default)]
pub struct SearchState {
    pub active: bool,
    pub query: String,
    pub replacement: String,
    pub field: SearchField,
    pub case_sensitive: bool,
    pub matches: Vec<(usize, usize)>,
    pub current: usize,
}

impl SearchState {
    pub fn open(query: String, field: SearchField) -> Self {
        Self {
            active: true,
            query,
            replacement: String::new(),
            field,
            case_sensitive: false,
            matches: Vec::new(),
            current: 0,
        }
    }
}

pub struct App {
    pub buffer: Buffer,
    pub config: EditorConfig,
    pub mode: Mode,
    pub running: bool,
    pub language: Language,
    pub show_line_numbers: bool,

    pub show_explorer: bool,
    pub show_terminal: bool,
    pub show_output: bool,
    pub explorer_width: u16,

    pub active_menu: MenuTab,
    pub menu_selected: usize,
    pub menu_dropdown_rect: Option<(u16, u16, u16, u16)>,

    pub run_button_x: u16,
    pub run_button_w: u16,
    pub format_button_x: u16,
    pub format_button_w: u16,
    pub tab_close_positions: Vec<(u16, u16, u16, usize)>,

    pub bottom_panel_height: u16,
    pub bottom_panel_rect: Option<ratatui::layout::Rect>,
    pub editor_inner_rect: Option<ratatui::layout::Rect>,
    pub explorer_inner_rect: Option<ratatui::layout::Rect>,
    pub is_dragging_bottom_panel: bool,

    pub terminal_scroll: usize,
    pub output_scroll: usize,

    pub mouse_drag_start: Option<(usize, usize)>,
    pub mouse_moved: bool,
    pub last_mouse_pos: Option<(u16, u16)>,

    pub show_command_line: bool,
    pub command_line: String,
    pub command_history: Vec<String>,
    pub command_history_index: usize,

    pub search: SearchState,

    pub explorer: FileNode,
    pub explorer_flat: Vec<(PathBuf, usize)>,
    pub explorer_selected: usize,
    pub explorer_scroll: usize,
    pub explorer_focused: bool,
    pub explorer_dir: Option<PathBuf>,

    pub terminal_output: Vec<String>,
    pub output_lines: Vec<String>,
    pub terminal_input: String,
    pub terminal_focused: bool,
    pub terminal_history: Vec<String>,
    pub terminal_history_index: usize,
    pub cwd: PathBuf,

    pub current_file: Option<PathBuf>,
    pub clipboard: String,

    pub last_terminal_size: (u16, u16),
    pub status_message: String,
    pub toasts: Vec<ToastNotification>,
    pub show_help: bool,
    pub last_edit_time: Instant,
    pub auto_save_failures: u32,
    pub last_auto_save_attempt: Instant,

    pub job: Option<Job>,
    pub pending_run: Option<(String, PathBuf)>,
    pub next_job_id: u64,
    pub bracket_match: Option<(usize, usize)>,
    pub pending_key: Option<char>,
}

impl App {
    pub fn new(initial_path: Option<&Path>) -> Self {
        let config = EditorConfig::load();
        let initial_line_numbers = config.line_numbers;
        let language = initial_path
            .map(|p| Language::from_path(Some(p)))
            .unwrap_or(Language::Generic);
        let mut buffer = initial_path
            .and_then(|p| Buffer::from_file(p).ok())
            .unwrap_or_else(Buffer::new_empty);
        Self::apply_config(&config, &mut buffer);

        let current_file = initial_path.map(|p| p.to_path_buf());
        let cwd = std::env::current_dir().unwrap_or_else(|_| {
            current_file
                .as_ref()
                .and_then(|p| p.parent().map(|p| p.to_path_buf()))
                .unwrap_or_else(|| PathBuf::from("."))
        });

        let mut explorer = FileNode {
            name: cwd
                .file_name()
                .map(|n| n.to_string_lossy().to_string())
                .unwrap_or_else(|| "PROJECT".to_string()),
            path: cwd.clone(),
            is_dir: true,
            expanded: true,
            children: Vec::new(),
            children_loaded: false,
        };
        Self::load_children(&mut explorer);
        let mut explorer_flat = Vec::new();
        Self::collect_nodes(&explorer, 0, &mut explorer_flat);

        Self {
            buffer,
            config,
            mode: Mode::Normal,
            running: true,
            language,
            show_line_numbers: initial_line_numbers != LineNumberMode::None,
            show_explorer: true,
            show_terminal: false,
            show_output: false,
            explorer_width: 30,
            active_menu: MenuTab::None,
            menu_selected: 0,
            menu_dropdown_rect: None,
            run_button_x: 0,
            run_button_w: 0,
            format_button_x: 0,
            format_button_w: 0,
            tab_close_positions: Vec::new(),
            bottom_panel_height: 12,
            bottom_panel_rect: None,
            editor_inner_rect: None,
            explorer_inner_rect: None,
            is_dragging_bottom_panel: false,
            terminal_scroll: 0,
            output_scroll: 0,
            mouse_drag_start: None,
            mouse_moved: false,
            last_mouse_pos: None,
            show_command_line: false,
            command_line: String::new(),
            command_history: Vec::new(),
            command_history_index: 0,
            search: SearchState::default(),
            explorer,
            explorer_flat,
            explorer_selected: 0,
            explorer_scroll: 0,
            explorer_focused: false,
            explorer_dir: Some(cwd.clone()),
            terminal_output: vec![
                "Desk integrated terminal — type a command or press Ctrl+J to hide".to_string(),
                "Tip: javac/java, ls/dir, git and any shell command are supported".to_string(),
            ],
            output_lines: Vec::new(),
            terminal_input: String::new(),
            terminal_focused: false,
            terminal_history: Vec::new(),
            terminal_history_index: 0,
            cwd,
            current_file,
            clipboard: String::new(),
            last_terminal_size: (80, 24),
            status_message: "Ready — F1 help | Ctrl+F find | F9 format | F5 run | Ctrl+J terminal"
                .to_string(),
            toasts: Vec::new(),
            show_help: false,
            last_edit_time: Instant::now(),
            auto_save_failures: 0,
            last_auto_save_attempt: Instant::now(),
            job: None,
            pending_run: None,
            next_job_id: 1,
            bracket_match: None,
            pending_key: None,
        }
    }

    fn apply_config(config: &EditorConfig, buffer: &mut Buffer) {
        buffer.tab_width = config.tab_width;
        buffer.use_spaces = config.use_spaces;
        buffer.auto_indent = config.auto_indent;
    }

    pub fn reload_config(&mut self) {
        self.config = EditorConfig::load();
        Self::apply_config(&self.config, &mut self.buffer);
        self.show_line_numbers = self.config.line_numbers != LineNumberMode::None;
        self.toast("Configuration reloaded", "SUCCESS");
    }

    pub fn save_config(&mut self) {
        if let Err(e) = self.config.save() {
            self.toast(&format!("Could not save config: {}", e), "ERROR");
        }
    }

    pub fn load_children(node: &mut FileNode) {
        if !node.is_dir {
            return;
        }
        let mut children = Vec::new();
        if let Ok(entries) = fs::read_dir(&node.path) {
            let mut entries_vec: Vec<_> = entries.flatten().collect();
            entries_vec.sort_by_key(|e| {
                (
                    !e.path().is_dir(),
                    e.file_name().to_string_lossy().to_lowercase(),
                )
            });
            for entry in entries_vec {
                let p = entry.path();
                let name = entry.file_name().to_string_lossy().to_string();
                if matches!(name.as_str(), ".git" | "target" | "node_modules") {
                    continue;
                }
                let is_dir = p.is_dir();
                let (expanded, children_loaded, existing_children) =
                    if let Some(existing) = node.children.iter().find(|c| c.path == p) {
                        (
                            existing.expanded,
                            existing.children_loaded,
                            existing.children.clone(),
                        )
                    } else {
                        (false, false, Vec::new())
                    };
                let mut child = FileNode {
                    name,
                    path: p,
                    is_dir,
                    expanded,
                    children: existing_children,
                    children_loaded,
                };
                if is_dir && expanded {
                    Self::load_children(&mut child);
                }
                children.push(child);
            }
        }
        node.children = children;
        node.children_loaded = true;
    }

    pub fn refresh_explorer(&mut self) {
        Self::load_children(&mut self.explorer);
        self.rebuild_explorer_flat();
        if self.explorer_selected >= self.explorer_flat.len() && !self.explorer_flat.is_empty() {
            self.explorer_selected = self.explorer_flat.len() - 1;
        }
    }

    fn rebuild_explorer_flat(&mut self) {
        self.explorer_flat.clear();
        let root = self.explorer.clone();
        Self::collect_nodes(&root, 0, &mut self.explorer_flat);
    }

    fn collect_nodes(node: &FileNode, depth: usize, flat: &mut Vec<(PathBuf, usize)>) {
        flat.push((node.path.clone(), depth));
        if node.is_dir && node.expanded {
            for child in &node.children {
                Self::collect_nodes(child, depth + 1, flat);
            }
        }
    }

    pub fn set_explorer_root(&mut self, path: &Path) {
        let path = if path.is_dir() {
            path.to_path_buf()
        } else {
            path.parent().unwrap_or(Path::new(".")).to_path_buf()
        };
        self.explorer = FileNode {
            name: path
                .file_name()
                .map(|n| n.to_string_lossy().to_string())
                .unwrap_or_else(|| path.display().to_string()),
            path: path.clone(),
            is_dir: true,
            expanded: true,
            children: Vec::new(),
            children_loaded: false,
        };
        Self::load_children(&mut self.explorer);
        self.explorer_dir = Some(path);
        self.rebuild_explorer_flat();
        self.explorer_selected = 0;
        self.explorer_scroll = 0;
    }

    pub fn navigate_explorer_up(&mut self) {
        self.explorer_selected = self.explorer_selected.saturating_sub(1);
        self.adjust_explorer_scroll();
    }

    pub fn navigate_explorer_down(&mut self) {
        if self.explorer_selected < self.explorer_flat.len().saturating_sub(1) {
            self.explorer_selected += 1;
        }
        self.adjust_explorer_scroll();
    }

    pub fn adjust_explorer_scroll(&mut self) {
        let visible = self.last_terminal_size.1.saturating_sub(10).max(5) as usize;
        if self.explorer_selected < self.explorer_scroll {
            self.explorer_scroll = self.explorer_selected;
        } else if self.explorer_selected >= self.explorer_scroll + visible {
            self.explorer_scroll = self.explorer_selected + 1 - visible;
        }
    }

    pub fn toggle_explorer_expand(&mut self) {
        if let Some((path, _)) = self.explorer_flat.get(self.explorer_selected).cloned()
            && let Some(node) = self.explorer.find_mut(&path)
            && node.is_dir
        {
            node.expanded = !node.expanded;
            if node.expanded {
                Self::load_children(node);
            }
            self.refresh_explorer();
        }
    }

    pub fn open_explorer_file(&mut self) -> Result<(), std::io::Error> {
        if let Some((path, _)) = self.explorer_flat.get(self.explorer_selected).cloned() {
            if path.is_file() {
                self.open_file(&path)?;
            } else {
                self.toggle_explorer_expand();
            }
        }
        Ok(())
    }

    pub fn open_file(&mut self, path: &Path) -> Result<(), std::io::Error> {
        let mut buffer = Buffer::from_file(path)?;
        Self::apply_config(&self.config, &mut buffer);
        self.buffer = buffer;
        self.buffer.cursor = Default::default();
        self.buffer.selection = None;
        self.buffer.scroll_top = 0;
        self.buffer.scroll_left = 0;
        self.language = Language::from_path(Some(path));
        self.current_file = Some(path.to_path_buf());
        self.mode = Mode::Normal;
        self.bracket_match = None;
        self.pending_key = None;
        self.explorer_focused = false;
        self.terminal_focused = false;
        self.last_edit_time = Instant::now();
        self.buffer.end_edit_group();
        self.toast(&format!("Opened {}", path.display()), "INFO");
        self.refresh_explorer();
        Ok(())
    }

    pub fn reload_current_file(&mut self) {
        if let Some(path) = self.current_file.clone() {
            if self.buffer.modified {
                self.toast("Unsaved changes — save before reloading", "WARN");
                return;
            }
            if let Err(e) = self.open_file(&path) {
                self.toast(&format!("Reload failed: {}", e), "ERROR");
            }
        } else {
            self.toast("No file open", "WARN");
        }
    }

    pub fn new_file(&mut self, name: Option<&str>) {
        self.buffer = Buffer::new_empty();
        if let Some(name) = name.filter(|n| !n.is_empty()) {
            self.buffer.path = Some(PathBuf::from(name));
        }
        Self::apply_config(&self.config, &mut self.buffer);
        self.current_file = self.buffer.path.clone();
        self.language = Language::from_path(self.current_file.as_deref());
        self.mode = Mode::Normal;
        self.bracket_match = None;
        self.pending_key = None;
        self.last_edit_time = Instant::now();
        self.toast("New file", "INFO");
    }

    pub fn close_current_file(&mut self) {
        self.buffer = Buffer::new_empty();
        Self::apply_config(&self.config, &mut self.buffer);
        self.current_file = None;
        self.language = Language::Generic;
        self.mode = Mode::Normal;
        self.bracket_match = None;
        self.pending_key = None;
        self.show_command_line = false;
        self.auto_save_failures = 0;
        self.last_edit_time = Instant::now();
        self.last_auto_save_attempt = Instant::now();
        self.pending_run = None;
        self.tab_close_positions.clear();
        self.toast("Closed file", "INFO");
    }

    pub fn save_current(&mut self) -> Result<(), std::io::Error> {
        if self.buffer.path.is_none() {
            self.show_command_line = true;
            self.command_line = ":w ".to_string();
            self.toast("Enter a path to save this file (Save As)", "INFO");
            return Ok(());
        }
        let path = self.buffer.path.clone().unwrap();
        self.save_as(&path)
    }

    pub fn save_as(&mut self, path: &Path) -> Result<(), std::io::Error> {
        if let Some(parent) = path.parent()
            && !parent.as_os_str().is_empty()
            && !parent.is_dir()
        {
            fs::create_dir_all(parent)?;
        }
        if let Err(e) = self.buffer.save_as(path) {
            let kind = e.kind();
            let backup_name = path
                .file_name()
                .map(|n| format!("{}.desk-backup", n.to_string_lossy()))
                .unwrap_or_else(|| "desk-backup.txt".to_string());
            let backup = path.with_file_name(backup_name);
            let detail = match fs::write(&backup, self.buffer.text()) {
                Ok(()) => format!("backup written to {}", backup.display()),
                Err(backup_err) => format!("backup failed: {}", backup_err),
            };
            return Err(std::io::Error::new(kind, format!("{}; {}", e, detail)));
        }
        self.auto_save_failures = 0;
        self.last_auto_save_attempt = Instant::now();
        self.current_file = Some(path.to_path_buf());
        self.language = Language::from_path(Some(path));
        self.last_edit_time = Instant::now();
        self.refresh_explorer();
        self.toast(&format!("Saved {}", path.display()), "SUCCESS");
        Ok(())
    }

    pub fn set_file_name(&mut self, path: &Path) {
        self.buffer.path = Some(path.to_path_buf());
        self.language = Language::from_path(Some(path));
    }

    pub fn file_name(&self) -> Option<&Path> {
        self.buffer.path.as_deref()
    }

    pub fn file_name_or_no_name(&self) -> String {
        self.buffer
            .path
            .as_ref()
            .and_then(|p| p.file_name().and_then(|n| n.to_str()))
            .map(|s| s.to_string())
            .unwrap_or_else(|| "[No Name]".to_string())
    }

    pub fn is_modified(&self) -> bool {
        self.buffer.modified
    }

    pub fn current_line_index(&self) -> usize {
        self.buffer.cursor.line
    }

    pub fn current_column(&self) -> usize {
        self.buffer.cursor.col
    }

    pub fn theme(&self) -> Theme {
        self.config.theme()
    }

    pub fn status_line(&self) -> String {
        let modified = if self.buffer.modified { "+" } else { "" };
        let sel = match self.buffer.selection {
            Some(s) if !s.is_empty() => {
                let (sl, sc, el, ec) = s.normalized();
                let offset = |line: usize, col: usize| -> usize {
                    self.buffer
                        .lines
                        .iter()
                        .take(line)
                        .map(|l| l.chars().count() + 1)
                        .sum::<usize>()
                        + col
                };
                format!(" sel:{}", offset(el, ec).saturating_sub(offset(sl, sc)))
            }
            _ => String::new(),
        };
        let job = match &self.job {
            Some(j) if j.status == JobStatus::Running => {
                format!("  ⏵ {} {:.1}s", j.name, j.elapsed().as_secs_f32())
            }
            _ => String::new(),
        };
        format!(
            " {} | {} | {} | Ln {}, Col {}{}{} | {} | {} | utf-8{}",
            self.file_name_or_no_name(),
            self.language.display_name(),
            if self.buffer.modified {
                "UNSAVED"
            } else {
                "SAVED"
            },
            self.current_line_index() + 1,
            self.current_column() + 1,
            modified,
            sel,
            self.mode.name(),
            self.config.theme,
            job
        )
    }

    pub fn toast(&mut self, message: &str, kind: &str) {
        self.toasts.push(ToastNotification {
            message: message.to_string(),
            kind: kind.to_string(),
            created_at: Instant::now(),
        });
        if self.toasts.len() > 5 {
            self.toasts.remove(0);
        }
        self.status_message = message.to_string();
    }

    pub fn cleanup_toasts(&mut self) {
        self.toasts
            .retain(|t| t.created_at.elapsed() < Duration::from_secs(4));
    }

    pub fn tick(&mut self) {
        self.poll_job();
        if !self.config.auto_save || !self.buffer.modified || self.buffer.path.is_none() {
            return;
        }
        if self.last_edit_time.elapsed() < Duration::from_millis(800) {
            return;
        }
        let retry_delay = if self.auto_save_failures == 0 {
            Duration::from_millis(800)
        } else {
            Duration::from_secs(2u64.saturating_pow(self.auto_save_failures.min(5)))
        };
        if self.last_auto_save_attempt.elapsed() < retry_delay {
            return;
        }
        self.last_auto_save_attempt = Instant::now();
        if let Err(e) = self.save_current() {
            self.auto_save_failures += 1;
            if self.auto_save_failures == 1 || self.auto_save_failures.is_multiple_of(5) {
                self.toast(
                    &format!(
                        "Auto-save failed ({} try); keeping changes and retrying: {}",
                        self.auto_save_failures, e
                    ),
                    "WARN",
                );
            }
        }
    }

    pub fn set_terminal_size(&mut self, width: u16, height: u16) {
        self.last_terminal_size = (width, height);
    }

    pub fn mark_edited(&mut self) {
        self.last_edit_time = Instant::now();
        self.buffer.end_edit_group();
        self.update_bracket_match();
    }

    pub fn update_bracket_match(&mut self) {
        let line = self.buffer.cursor.line;
        let col = self.buffer.cursor.col;
        let text = self.buffer.current_line().unwrap_or("").to_string();
        let chars: Vec<char> = text.chars().collect();
        let pairs = [('(', ')'), ('[', ']'), ('{', '}')];
        let mut found = None;
        let before = if col > 0 { Some(chars[col - 1]) } else { None };
        let at = chars.get(col).copied();
        for (open, close) in pairs {
            if before == Some(open) || at == Some(close) {
                let start_col = if before == Some(open) { col - 1 } else { col };
                if let Some(pos) = Self::match_bracket(&text, start_col, open, close) {
                    found = Some((line, pos));
                }
                break;
            }
        }
        self.bracket_match = found;
    }

    fn match_bracket(text: &str, start: usize, open: char, close: char) -> Option<usize> {
        let chars: Vec<char> = text.chars().collect();
        let mut depth = 0i32;
        if chars.get(start) == Some(&open) {
            for (i, ch) in chars.iter().enumerate().skip(start) {
                if *ch == open {
                    depth += 1;
                } else if *ch == close {
                    depth -= 1;
                    if depth == 0 {
                        return Some(i);
                    }
                }
            }
        } else {
            for i in (0..=start.min(chars.len().saturating_sub(1))).rev() {
                if chars[i] == close {
                    depth += 1;
                } else if chars[i] == open {
                    depth -= 1;
                    if depth == 0 {
                        return Some(i);
                    }
                }
            }
        }
        None
    }

    pub fn insert_char(&mut self, ch: char) {
        if self.mode != Mode::Insert {
            return;
        }
        let pairs = [('(', ')'), ('[', ']'), ('{', '}'), ('"', '"'), ('\'', '\'')];
        let next = self
            .buffer
            .current_line()
            .and_then(|l| l.chars().nth(self.buffer.cursor.col));
        if self.config.auto_close_brackets {
            if let Some((_, closer)) = pairs.iter().find(|(o, _)| *o == ch)
                && (next.is_none() || next == Some(' ') || ")]};,.".contains(next.unwrap()))
            {
                self.buffer.insert_char(ch);
                self.buffer.insert_char(*closer);
                self.buffer.move_left();
                self.mark_edited();
                return;
            }
            if let Some((_, closer)) = pairs.iter().find(|(_, c)| *c == ch)
                && next == Some(*closer)
            {
                self.buffer.move_right();
                self.mark_edited();
                return;
            }
        }
        if ch == '\n' {
            self.buffer.insert_newline();
        } else {
            self.buffer.insert_char(ch);
        }
        self.mark_edited();
    }

    pub fn insert_newline(&mut self) {
        if self.mode == Mode::Insert {
            self.buffer.insert_newline();
            self.mark_edited();
        }
    }

    pub fn delete_backward(&mut self) {
        self.buffer.delete_backward();
        self.mark_edited();
    }

    pub fn delete_forward(&mut self) {
        self.buffer.delete_forward();
        self.mark_edited();
    }

    pub fn move_up(&mut self) {
        self.buffer.move_up();
        self.update_bracket_match();
    }

    pub fn move_down(&mut self) {
        self.buffer.move_down();
        self.update_bracket_match();
    }

    pub fn move_left(&mut self) {
        self.buffer.move_left();
        self.update_bracket_match();
    }

    pub fn move_right(&mut self) {
        self.buffer.move_right();
        self.update_bracket_match();
    }

    pub fn move_to_line_start(&mut self) {
        self.buffer.move_to_first_non_whitespace();
        self.update_bracket_match();
    }

    pub fn move_to_line_end(&mut self) {
        self.buffer.move_to_line_end();
        self.update_bracket_match();
    }

    pub fn toggle_mode(&mut self) {
        self.mode = match self.mode {
            Mode::Normal => Mode::Insert,
            Mode::Insert | Mode::Visual | Mode::Command => Mode::Normal,
        };
        if self.mode != Mode::Insert {
            self.buffer.end_edit_group();
        }
    }

    pub fn enter_command_mode(&mut self) {
        self.mode = Mode::Command;
        self.show_command_line = true;
        self.command_line = String::new();
        self.command_history_index = self.command_history.len();
    }

    pub fn undo(&mut self) {
        self.buffer.undo();
        self.mark_edited();
    }

    pub fn redo(&mut self) {
        self.buffer.redo();
        self.mark_edited();
    }

    pub fn copy_selection(&mut self) {
        if let Some(text) = self.buffer.get_selected_text() {
            self.clipboard = text;
            self.toast("Copied selection", "SUCCESS");
        } else {
            let line = self.buffer.current_line().unwrap_or("").to_string();
            self.clipboard = line;
            self.toast("Yanked line", "SUCCESS");
        }
    }

    pub fn cut_selection(&mut self) {
        if self.buffer.selection.is_some() && !self.buffer.selection.unwrap().is_empty() {
            self.copy_selection();
            self.buffer.delete_selection();
            self.mark_edited();
        } else {
            self.copy_selection();
            self.buffer.delete_line();
            self.mark_edited();
        }
    }

    pub fn paste_from_clipboard(&mut self) -> Result<(), std::io::Error> {
        if self.clipboard.is_empty() {
            return Ok(());
        }
        let text = self.clipboard.clone();
        self.buffer.insert_text(&text);
        self.buffer.selection = None;
        self.mark_edited();
        self.toast("Pasted", "SUCCESS");
        Ok(())
    }

    pub fn delete_selection(&mut self) {
        self.buffer.delete_selection();
        self.mark_edited();
    }

    pub fn select_all(&mut self) {
        self.buffer.select_all();
        self.toast("Selected all", "INFO");
    }

    pub fn start_visual(&mut self) {
        self.mode = Mode::Visual;
        let (line, col) = (self.buffer.cursor.line, self.buffer.cursor.col);
        self.buffer.selection = Some(Selection::new(line, col, line, col));
    }

    pub fn extend_visual_selection(&mut self) {
        if self.mode == Mode::Visual
            && let Some(sel) = self.buffer.selection
        {
            self.buffer.selection = Some(Selection::new(
                sel.start_line,
                sel.start_col,
                self.buffer.cursor.line,
                self.buffer.cursor.col,
            ));
        }
    }

    // ── Search ───────────────────────────────────────────────────

    pub fn open_search(&mut self, replacement: bool) {
        let word = self.buffer.word_at_cursor();
        self.search = SearchState::open(
            word,
            if replacement {
                SearchField::Replace
            } else {
                SearchField::Find
            },
        );
        self.update_search_matches();
    }

    pub fn update_search_matches(&mut self) {
        let query = self.search.query.clone();
        let case = self.search.case_sensitive;
        self.search.matches.clear();
        if query.is_empty() {
            return;
        }
        let haystack = self.buffer.text();
        let (needle, text) = if case {
            (query.clone(), haystack)
        } else {
            (query.to_lowercase(), haystack.to_lowercase())
        };
        let mut offset = 0;
        while let Some(pos) = text[offset..].find(&needle) {
            let byte = offset + pos;
            let line = text[..byte].chars().filter(|c| *c == '\n').count();
            let line_start = text[..byte].rfind('\n').map(|p| p + 1).unwrap_or(0);
            let col = text[line_start..byte].chars().count();
            self.search.matches.push((line, col));
            offset = byte + needle.len();
            if offset >= text.len() || self.search.matches.len() > 10_000 {
                break;
            }
        }
        let cursor = self.buffer.cursor_offset();
        self.search.current = self
            .search
            .matches
            .iter()
            .position(|(l, c)| {
                let lines = &self.buffer.lines;
                let off: usize = lines
                    .iter()
                    .take(*l)
                    .map(|x| x.chars().count() + 1)
                    .sum::<usize>()
                    + c;
                off >= cursor
            })
            .unwrap_or(0);
    }

    pub fn search_next(&mut self, backwards: bool) {
        self.update_search_matches();
        if self.search.matches.is_empty() {
            self.toast("No matches", "WARN");
            return;
        }
        let len = self.search.matches.len();
        self.search.current = if backwards {
            (self.search.current + len - 1) % len
        } else {
            (self.search.current + 1) % len
        };
        let (line, col) = self.search.matches[self.search.current];
        self.buffer
            .move_to(line, col + self.search.query.chars().count());
        self.buffer.selection = Some(Selection::new(
            line,
            col,
            line,
            col + self.search.query.chars().count(),
        ));
        self.update_bracket_match();
        self.toast(
            &format!("Match {}/{}", self.search.current + 1, len),
            "INFO",
        );
    }

    pub fn replace_current(&mut self) {
        if self.buffer.selection.is_none() {
            self.search_next(false);
        }
        let replacement = self.search.replacement.clone();
        self.buffer.replace_selection(&replacement);
        self.mark_edited();
        self.update_search_matches();
    }

    pub fn replace_all_matches(&mut self) {
        let from = self.search.query.clone();
        let to = self.search.replacement.clone();
        let case = self.search.case_sensitive;
        let count = self.buffer.replace_all(&from, &to, case);
        self.buffer.selection = None;
        self.mark_edited();
        self.update_search_matches();
        self.toast(&format!("Replaced {} occurrences", count), "SUCCESS");
    }

    pub fn close_search(&mut self) {
        self.search.active = false;
        self.buffer.selection = None;
    }

    pub fn goto_line(&mut self, line: usize) {
        let line = line
            .saturating_sub(1)
            .min(self.buffer.line_count().saturating_sub(1));
        self.buffer.move_to(line, 0);
        self.buffer.selection = None;
        self.update_bracket_match();
        self.toast(&format!("Line {}", line + 1), "INFO");
    }

    // ── Jobs ─────────────────────────────────────────────────────

    fn spawn_job(
        &mut self,
        name: String,
        program: &str,
        args: &[String],
    ) -> Result<(), std::io::Error> {
        if let Some(job) = &self.job
            && job.status == JobStatus::Running
        {
            self.toast("A command is already running — stop it first (F7)", "WARN");
            return Ok(());
        }
        let mut child = Command::new(program)
            .args(args)
            .current_dir(&self.cwd)
            .stdin(Stdio::piped())
            .stdout(Stdio::piped())
            .stderr(Stdio::piped())
            .spawn()
            .map_err(|e| std::io::Error::new(e.kind(), format!("{}: {}", program, e)))?;
        let stdin = child.stdin.take();
        let stdout = child
            .stdout
            .take()
            .map(|s| Box::new(s) as Box<dyn std::io::Read + Send>);
        let stderr = child
            .stderr
            .take()
            .map(|s| Box::new(s) as Box<dyn std::io::Read + Send>);
        let (tx, rx): (Sender<JobMessage>, Receiver<JobMessage>) = unbounded();
        let child_arc = Arc::new(Mutex::new(Some(child)));

        for (stream, kind) in [(stdout, OutputKind::Out), (stderr, OutputKind::Err)] {
            if let Some(stream) = stream {
                let tx = tx.clone();
                thread::spawn(move || {
                    let reader = BufReader::new(stream);
                    for line in reader.lines() {
                        match line {
                            Ok(line) => {
                                if tx.send(JobMessage::Output(line, kind)).is_err() {
                                    break;
                                }
                            }
                            Err(_) => break,
                        }
                    }
                });
            }
        }

        let waiter = Arc::clone(&child_arc);
        let tx_wait = tx.clone();
        thread::spawn(move || loop {
            let status = {
                let mut guard = waiter.lock().unwrap_or_else(|e| e.into_inner());
                match guard.as_mut() {
                    Some(child) => child.try_wait(),
                    None => {
                        let _ = tx_wait.send(JobMessage::Finished {
                            success: false,
                            cancelled: true,
                        });
                        return;
                    }
                }
            };
            match status {
                Ok(Some(status)) => {
                    let _ = tx_wait.send(JobMessage::Finished {
                        success: status.success(),
                        cancelled: false,
                    });
                    let mut guard = waiter.lock().unwrap_or_else(|e| e.into_inner());
                    *guard = None;
                    return;
                }
                Ok(None) => thread::sleep(Duration::from_millis(20)),
                Err(_) => {
                    let _ = tx_wait.send(JobMessage::Finished {
                        success: false,
                        cancelled: false,
                    });
                    return;
                }
            }
        });

        let id = self.next_job_id;
        self.next_job_id += 1;
        self.job = Some(Job {
            id,
            name,
            child: child_arc,
            stdin,
            receiver: rx,
            started_at: Instant::now(),
            status: JobStatus::Running,
        });
        self.show_output = true;
        self.terminal_scroll = 0;
        self.output_scroll = 0;
        Ok(())
    }

    fn push_output(&mut self, line: &str, kind: OutputKind) {
        let prefix = match kind {
            OutputKind::Out => "[out] ",
            OutputKind::Err => "[err] ",
            OutputKind::Info => "[info] ",
        };
        let decorated = format!("{}{}", prefix, line);
        self.terminal_output.push(decorated.clone());
        self.output_lines.push(decorated);
        if self.terminal_output.len() > 5000 {
            self.terminal_output.remove(0);
            self.output_lines.remove(0);
        }
    }

    pub fn poll_job(&mut self) {
        let mut messages = Vec::new();
        if let Some(job) = &mut self.job {
            while let Ok(msg) = job.receiver.try_recv() {
                messages.push(msg);
            }
        }
        let mut finished = None;
        for msg in messages {
            match msg {
                JobMessage::Output(line, kind) => self.push_output(&line, kind),
                JobMessage::Finished { success, cancelled } => {
                    let name = self
                        .job
                        .as_ref()
                        .map(|j| j.name.clone())
                        .unwrap_or_default();
                    finished = Some((name, success, cancelled));
                    break;
                }
            }
        }
        if let Some((name, success, cancelled)) = finished {
            if let Some(job) = &mut self.job {
                job.status = if cancelled {
                    JobStatus::Cancelled
                } else if success {
                    JobStatus::Success
                } else {
                    JobStatus::Failed
                };
            }
            let (text, toast_kind) = if cancelled {
                (format!("[info] Stopped: {}", name), "WARN")
            } else if success {
                (format!("[ok] Finished: {}", name), "SUCCESS")
            } else {
                (format!("[err] Failed: {}", name), "ERROR")
            };
            self.terminal_output.push(text.clone());
            self.output_lines.push(text);
            self.toast(
                &format!(
                    "{} — {}",
                    name,
                    if success {
                        "ok"
                    } else if cancelled {
                        "stopped"
                    } else {
                        "failed"
                    }
                ),
                toast_kind,
            );
            self.terminal_scroll = 0;
            self.output_scroll = 0;

            if success && !cancelled && name.starts_with("javac ") {
                if let Some((class_name, classpath)) = self.pending_run.take() {
                    let java = Self::java_tool("java");
                    let args = vec![
                        "-cp".to_string(),
                        classpath.to_string_lossy().to_string(),
                        class_name.clone(),
                    ];
                    self.push_output(
                        &format!("$ {} -cp {} {}", java, classpath.display(), class_name),
                        OutputKind::Info,
                    );
                    if let Err(e) = self.spawn_job(format!("run {}", class_name), &java, &args) {
                        self.toast(&format!("Cannot start java: {}", e), "ERROR");
                    }
                }
            } else if !success || cancelled {
                self.pending_run = None;
            }
        }
    }

    pub fn stop_job(&mut self) {
        if let Some(job) = &self.job
            && job.status == JobStatus::Running
        {
            let id = job.id;
            job.stop();
            self.toast(&format!("Stopping job #{}", id), "WARN");
            return;
        }
        self.toast("No running command to stop", "WARN");
    }

    fn java_tool(name: &str) -> String {
        if let Some(home) = std::env::var_os("JAVA_HOME") {
            let exe = PathBuf::from(home).join("bin").join(if cfg!(windows) {
                format!("{}.exe", name)
            } else {
                name.to_string()
            });
            if exe.is_file() {
                return exe.to_string_lossy().to_string();
            }
        }
        name.to_string()
    }

    fn java_tool_available(tool: &str) -> bool {
        Command::new(tool)
            .arg("-version")
            .stdin(Stdio::null())
            .stdout(Stdio::null())
            .stderr(Stdio::null())
            .status()
            .map(|status| status.success())
            .unwrap_or(false)
    }

    fn absolute_source(path: &Path, cwd: &Path) -> PathBuf {
        let candidate = if path.is_absolute() {
            path.to_path_buf()
        } else {
            cwd.join(path)
        };
        candidate.canonicalize().unwrap_or(candidate)
    }

    fn find_desk_jar() -> Option<PathBuf> {
        if let Ok(from_env) = std::env::var("DESK_JAR") {
            let path = PathBuf::from(from_env);
            if path.is_file() {
                return Some(path);
            }
        }
        let exe = std::env::current_exe().ok()?;
        let mut dir = exe.parent()?.to_path_buf();
        for _ in 0..10 {
            let candidates = [
                dir.join("modules")
                    .join("desk-cli")
                    .join("target")
                    .join("desk-cli-0.1.0-SNAPSHOT.jar"),
                dir.join("lib").join("desk-cli-0.1.0-SNAPSHOT.jar"),
            ];
            for candidate in candidates {
                if candidate.is_file() {
                    return Some(candidate);
                }
            }
            dir = dir.parent()?.to_path_buf();
        }
        None
    }

    fn invoke_desk_jar(&self, args: &[&str], stdin: Option<&str>) -> Result<String, String> {
        let jar = Self::find_desk_jar()
            .ok_or_else(|| "Desk jar not found; build with ./mvnw package".to_string())?;
        let java = Self::java_tool("java");
        if !Self::java_tool_available(&java) {
            return Err("java not found; set JAVA_HOME or add java to PATH".to_string());
        }
        let mut cmd = Command::new(&java);
        cmd.arg("-jar").arg(&jar);
        for arg in args {
            cmd.arg(arg);
        }
        cmd.stdin(Stdio::piped())
            .stdout(Stdio::piped())
            .stderr(Stdio::piped());
        let mut child = cmd
            .spawn()
            .map_err(|e| format!("cannot start desk: {}", e))?;
        if let Some(body) = stdin {
            if let Some(mut child_stdin) = child.stdin.take() {
                std::io::Write::write_all(&mut child_stdin, body.as_bytes())
                    .map_err(|e| format!("cannot write to desk: {}", e))?;
            }
        }
        let output = child
            .wait_with_output()
            .map_err(|e| format!("desk command failed: {}", e))?;
        if !output.status.success() {
            return Err(String::from_utf8_lossy(&output.stderr).trim().to_string());
        }
        String::from_utf8(output.stdout).map_err(|e| format!("desk emitted invalid UTF-8: {}", e))
    }

    fn format_text_with_desk(&self, source: &str) -> Result<String, String> {
        self.invoke_desk_jar(&["fmt", "--stdin"], Some(source))
    }

    pub fn check_current_java(&mut self) {
        let is_java = self.language == Language::Java
            || self
                .current_file
                .as_deref()
                .and_then(|p| p.extension())
                .and_then(|e| e.to_str())
                .is_some_and(|e| e.eq_ignore_ascii_case("java"));
        if !is_java {
            self.toast("Java check is only for .java files", "WARN");
            return;
        }
        let source = self.buffer.text();
        match self.invoke_desk_jar(&["check", "--stdin"], Some(&source)) {
            Ok(_) => {
                self.toast("No Java errors", "SUCCESS");
                self.push_output("desk check: ok", OutputKind::Info);
            }
            Err(err) => {
                self.toast("Java errors found", "ERROR");
                self.push_output(&err, OutputKind::Err);
            }
        }
    }

    fn format_java_basic(source: &str, tab_width: usize, use_spaces: bool) -> String {
        let unit = if use_spaces {
            " ".repeat(tab_width)
        } else {
            "\t".to_string()
        };
        let normalized = source.replace("\r\n", "\n").replace('\r', "\n");
        let expanded = normalized.replace('{', " {\n").replace('}', "\n}");
        let mut depth = 0usize;
        let mut out = Vec::new();
        for raw in expanded.lines() {
            let line = raw.trim();
            if line.is_empty() {
                out.push(String::new());
                continue;
            }
            let opens = line.chars().filter(|&c| c == '{').count();
            let closes = line.chars().filter(|&c| c == '}').count();
            let leading_closes = line.chars().take_while(|&c| c == '}').count();
            let indent = depth.saturating_sub(leading_closes);
            out.push(format!("{}{}", unit.repeat(indent), line));
            depth = depth.saturating_add(opens).saturating_sub(closes);
        }
        let formatted = out.join("\n");
        if normalized.ends_with('\n') {
            format!("{}\n", formatted)
        } else {
            formatted
        }
    }

    pub fn format_current(&mut self) {
        let is_java = self.language == Language::Java
            || self
                .current_file
                .as_deref()
                .and_then(|p| p.extension())
                .and_then(|e| e.to_str())
                .is_some_and(|e| e.eq_ignore_ascii_case("java"));
        if !is_java {
            self.toast("Formatting is available for Java files", "WARN");
            return;
        }
        let original = self.buffer.text();
        let (formatted, engine) = match Self::format_text_with_desk(self, &original) {
            Ok(text) => (text, "Desk formatter"),
            Err(e) => {
                let fallback = Self::format_java_basic(
                    &original,
                    self.config.tab_width,
                    self.config.use_spaces,
                );
                if fallback == original {
                    self.toast(&format!("Format failed: {}", e), "ERROR");
                    return;
                }
                (fallback, "basic formatter")
            }
        };
        if formatted == original {
            self.toast("Already formatted", "INFO");
            return;
        }
        self.buffer.set_text(&formatted);
        Self::apply_config(&self.config, &mut self.buffer);
        self.mark_edited();
        if self.buffer.path.is_some() {
            match self.save_current() {
                Ok(()) => self.toast(&format!("Formatted and saved ({})", engine), "SUCCESS"),
                Err(e) => self.toast(&format!("Formatted in memory; save failed: {}", e), "WARN"),
            }
        } else {
            self.toast(
                &format!("Formatted in memory ({}, unsaved)", engine),
                "SUCCESS",
            );
        }
    }

    pub fn compile_current_file(&mut self) {
        let path = match self.current_file.clone() {
            Some(p) => p,
            None => {
                self.toast("No file to compile", "WARN");
                return;
            }
        };
        if path.extension().and_then(|e| e.to_str()) != Some("java") {
            self.toast("Not a Java file", "WARN");
            return;
        }
        if self.buffer.modified
            && let Err(e) = self.save_current()
        {
            self.toast(&format!("Save failed: {}", e), "ERROR");
            return;
        }
        let source = Self::absolute_source(&path, &self.cwd);
        let dir = source
            .parent()
            .map(|p| p.to_path_buf())
            .unwrap_or_else(|| self.cwd.clone());
        let javac = Self::java_tool("javac");
        if !Self::java_tool_available(&javac) {
            self.toast(
                "javac not found; set JAVA_HOME or add javac to PATH",
                "ERROR",
            );
            return;
        }
        let source_str = source.to_string_lossy().to_string();
        let dir_str = dir.to_string_lossy().to_string();
        let args = vec!["-d".to_string(), dir_str.clone(), source_str.clone()];
        self.push_output(
            &format!("$ {} -d {} {}", javac, dir.display(), source.display()),
            OutputKind::Info,
        );
        if let Err(e) = self.spawn_job(
            format!(
                "javac {}",
                source.file_name().unwrap_or_default().to_string_lossy()
            ),
            &javac,
            &args,
        ) {
            self.toast(&format!("Cannot start javac: {}", e), "ERROR");
        }
    }

    pub fn run_current_file(&mut self) {
        let path = match self.current_file.clone() {
            Some(p) => p,
            None => {
                self.toast("No file to run", "WARN");
                return;
            }
        };
        if path.extension().and_then(|e| e.to_str()) != Some("java") {
            self.toast("Not a Java file", "WARN");
            return;
        }
        if self.buffer.modified
            && let Err(e) = self.save_current()
        {
            self.toast(&format!("Save failed: {}", e), "ERROR");
            return;
        }
        let source = Self::absolute_source(&path, &self.cwd);
        let dir = source
            .parent()
            .map(|p| p.to_path_buf())
            .unwrap_or_else(|| self.cwd.clone());
        let class_name = source
            .file_stem()
            .and_then(|s| s.to_str())
            .unwrap_or("Main")
            .to_string();
        let javac = Self::java_tool("javac");
        let java = Self::java_tool("java");
        if !Self::java_tool_available(&javac) || !Self::java_tool_available(&java) {
            self.toast(
                "javac/java not found; set JAVA_HOME or add them to PATH",
                "ERROR",
            );
            return;
        }
        let source_str = source.to_string_lossy().to_string();
        let dir_str = dir.to_string_lossy().to_string();
        self.show_terminal = true;
        self.show_output = true;
        self.terminal_focused = true;
        self.explorer_focused = false;
        self.push_output(
            "Program input: type in the terminal line and press Enter (Esc returns to editor)",
            OutputKind::Info,
        );
        self.pending_run = Some((class_name.clone(), dir.clone()));
        self.push_output(
            &format!("$ {} -d {} {}", javac, dir.display(), source.display()),
            OutputKind::Info,
        );
        let args = vec!["-d".to_string(), dir_str, source_str];
        if let Err(e) = self.spawn_job(
            format!(
                "javac {}",
                source.file_name().unwrap_or_default().to_string_lossy()
            ),
            &javac,
            &args,
        ) {
            self.pending_run = None;
            self.toast(&format!("Cannot start javac: {}", e), "ERROR");
        }
    }

    pub fn clear_output(&mut self) {
        self.terminal_output.clear();
        self.output_lines.clear();
        self.terminal_scroll = 0;
        self.output_scroll = 0;
        self.toast("Output cleared", "INFO");
    }

    // ── Terminal ─────────────────────────────────────────────────

    pub fn terminal_input_char(&mut self, ch: char) {
        self.terminal_input.push(ch);
    }

    pub fn terminal_input_backspace(&mut self) {
        self.terminal_input.pop();
    }

    pub fn terminal_history_prev(&mut self) {
        if self.terminal_history.is_empty() {
            return;
        }
        self.terminal_history_index = self.terminal_history_index.saturating_sub(1);
        self.terminal_input = self
            .terminal_history
            .get(self.terminal_history_index)
            .cloned()
            .unwrap_or_default();
    }

    pub fn terminal_history_next(&mut self) {
        if self.terminal_history.is_empty() {
            return;
        }
        self.terminal_history_index =
            (self.terminal_history_index + 1).min(self.terminal_history.len());
        self.terminal_input = self
            .terminal_history
            .get(self.terminal_history_index)
            .cloned()
            .unwrap_or_default();
    }

    pub fn terminal_input_enter(&mut self) {
        let cmd = self.terminal_input.trim().to_string();
        self.terminal_input.clear();
        if cmd.is_empty() {
            return;
        }
        self.terminal_history.push(cmd.clone());
        self.terminal_history_index = self.terminal_history.len();

        let job_accepts_input = self
            .job
            .as_ref()
            .is_some_and(|j| j.status == JobStatus::Running && j.stdin.is_some());
        if job_accepts_input {
            self.push_output(&format!("> {}", cmd), OutputKind::Info);
            if let Some(job) = &mut self.job
                && let Err(e) = job.send_input(&cmd)
            {
                self.push_output(&format!("input failed: {}", e), OutputKind::Err);
            }
            return;
        }

        self.push_output(&format!("$ {}", cmd), OutputKind::Info);

        let parts: Vec<&str> = cmd.split_whitespace().collect();
        match parts[0] {
            "clear" | "cls" => {
                self.clear_output();
                return;
            }
            "exit" => {
                self.terminal_focused = false;
                return;
            }
            "cd" => {
                let target = if parts.len() > 1 {
                    let p = PathBuf::from(parts[1]);
                    if p.is_absolute() {
                        p
                    } else {
                        self.cwd.join(p)
                    }
                } else {
                    dirs::home_dir().unwrap_or_else(|| PathBuf::from("."))
                };
                if target.is_dir() {
                    self.cwd = target.clone();
                    self.set_explorer_root(&target);
                    self.push_output(&format!("cd: {}", target.display()), OutputKind::Info);
                } else {
                    self.push_output(
                        &format!("cd: no such directory: {}", target.display()),
                        OutputKind::Err,
                    );
                }
                return;
            }
            "pwd" => {
                self.push_output(&self.cwd.display().to_string(), OutputKind::Out);
                return;
            }
            "help" => {
                for line in [
                    "Built-in: clear, cd, pwd, help, javac <file>, java <class>",
                    "Any other command is executed through the system shell.",
                    "Ctrl+C stops the running job, Up/Down browse history.",
                ] {
                    self.push_output(line, OutputKind::Info);
                }
                return;
            }
            _ => {}
        }

        let (program, args) = if cfg!(windows) {
            ("cmd".to_string(), vec!["/C".to_string(), cmd.clone()])
        } else {
            ("sh".to_string(), vec!["-c".to_string(), cmd.clone()])
        };
        if let Err(e) = self.spawn_job(cmd.clone(), &program, &args) {
            self.push_output(&format!("cannot start command: {}", e), OutputKind::Err);
            self.toast(&format!("Command failed to start: {}", e), "ERROR");
        }
    }

    // ── Commands ─────────────────────────────────────────────────

    pub fn command_history_prev(&mut self) {
        if self.command_history.is_empty() {
            return;
        }
        self.command_history_index = self.command_history_index.saturating_sub(1);
        self.command_line = self
            .command_history
            .get(self.command_history_index)
            .cloned()
            .unwrap_or_default();
    }

    pub fn command_history_next(&mut self) {
        if self.command_history.is_empty() {
            return;
        }
        self.command_history_index =
            (self.command_history_index + 1).min(self.command_history.len());
        self.command_line = self
            .command_history
            .get(self.command_history_index)
            .cloned()
            .unwrap_or_default();
    }

    pub fn execute_command_line(&mut self) {
        let cmd = self
            .command_line
            .trim()
            .strip_prefix(':')
            .unwrap_or_else(|| self.command_line.trim())
            .trim()
            .to_string();
        self.show_command_line = false;
        self.command_line.clear();
        self.mode = Mode::Normal;
        if cmd.is_empty() {
            return;
        }
        self.command_history.push(format!(":{}", cmd));
        self.command_history_index = self.command_history.len();

        let (head, rest) = match cmd.split_once(' ') {
            Some((h, r)) => (h, r.trim()),
            None => (cmd.as_str(), ""),
        };

        match head {
            "w" | "write" => {
                if rest.is_empty() {
                    if let Err(e) = self.save_current() {
                        self.toast(&format!("Save failed: {}", e), "ERROR");
                    }
                } else if let Err(e) = self.save_as(Path::new(rest)) {
                    self.toast(&format!("Save failed: {}", e), "ERROR");
                }
            }
            "wq" | "x" | "wqall" => {
                if rest.is_empty() {
                    if let Err(e) = self.save_current() {
                        self.toast(&format!("Save failed: {}", e), "ERROR");
                    }
                } else if let Err(e) = self.save_as(Path::new(rest)) {
                    self.toast(&format!("Save failed: {}", e), "ERROR");
                }
                self.running = false;
            }
            "q" | "quit" => self.running = false,
            "q!" => {
                self.buffer.modified = false;
                self.running = false;
            }
            "e" | "edit" => {
                if let Err(e) = self.open_file(Path::new(rest)) {
                    self.toast(&format!("Open failed: {}", e), "ERROR");
                }
            }
            "new" => {
                if rest.is_empty() {
                    self.toast("Usage: :new <filename>", "WARN");
                    return;
                }
                let path = if Path::new(rest).is_absolute() {
                    PathBuf::from(rest)
                } else {
                    self.cwd.join(rest)
                };
                if path.is_file() {
                    self.toast("File already exists", "WARN");
                    return;
                }
                match self.save_as(&path) {
                    Ok(_) => {
                        self.toast(&format!("Created {}", path.display()), "SUCCESS");
                    }
                    Err(e) => self.toast(&format!("Create failed: {}", e), "ERROR"),
                }
            }
            "close" => self.close_current_file(),
            "n" | "next" => {
                let line = self.current_line_index() + 2;
                self.goto_line(line);
            }
            "p" | "prev" => {
                let line = self.current_line_index().saturating_sub(1).max(1);
                self.goto_line(line);
            }
            "f" | "find" => {
                self.search.query = rest.to_string();
                self.open_search(false);
                self.search_next(false);
            }
            "s" | "substitute" => {
                let parts: Vec<&str> = rest.splitn(3, '/').collect();
                if parts.len() >= 2 {
                    self.search.query = parts[0].to_string();
                    self.search.replacement = parts[1].to_string();
                    self.replace_all_matches();
                } else {
                    self.toast("Usage: :s/from/to", "WARN");
                }
            }
            "theme" => {
                if rest.is_empty() {
                    let name = self.config.cycle_theme();
                    self.toast(&format!("Theme: {}", name), "SUCCESS");
                } else if self.config.set_theme(rest) {
                    self.toast(&format!("Theme: {}", rest), "SUCCESS");
                } else {
                    self.toast("Unknown theme. Use: dark, light, monokai", "WARN");
                }
            }
            "set" => {
                let (key, value) = match rest.split_once('=') {
                    Some((k, v)) => (k.trim(), v.trim()),
                    None => (rest, ""),
                };
                match key {
                    "number" | "numbers" => {
                        if let Some(mode) = LineNumberMode::from_name(value) {
                            self.config.line_numbers = mode;
                            self.show_line_numbers = mode != LineNumberMode::None;
                            self.save_config();
                            self.toast(&format!("Line numbers: {}", mode.name()), "SUCCESS");
                        } else {
                            self.cycle_line_numbers();
                        }
                    }
                    "wrap" => {
                        self.config.word_wrap = matches!(value, "on" | "true" | "1")
                            || value.is_empty() && !self.config.word_wrap;
                        self.save_config();
                    }
                    "tabsize" | "tabwidth" => {
                        if let Ok(n) = value.parse::<usize>() {
                            self.config.tab_width = n.clamp(1, 16);
                            self.buffer.tab_width = self.config.tab_width;
                            self.save_config();
                            self.toast(&format!("Tab width: {}", n), "SUCCESS");
                        }
                    }
                    "expandtab" => {
                        self.config.use_spaces = matches!(value, "on" | "true" | "1")
                            || value.is_empty() && !self.config.use_spaces;
                        self.buffer.use_spaces = self.config.use_spaces;
                        self.save_config();
                    }
                    "autosave" => {
                        self.config.auto_save = matches!(value, "on" | "true" | "1")
                            || value.is_empty() && !self.config.auto_save;
                        self.save_config();
                    }
                    _ => self.toast("Unknown option", "WARN"),
                }
            }
            "compile" | "make" => self.compile_current_file(),
            "check" | "lint" => self.check_current_java(),
            "fmt" | "format" => self.format_current(),
            "run" => self.run_current_file(),
            "stop" => self.stop_job(),
            "clear" | "cl" => self.clear_output(),
            "cd" => {
                let target = if Path::new(rest).is_absolute() {
                    PathBuf::from(rest)
                } else {
                    self.cwd.join(rest)
                };
                if target.is_dir() {
                    self.cwd = target.clone();
                    self.set_explorer_root(&target);
                } else {
                    self.toast("No such directory", "ERROR");
                }
            }
            "pwd" => self.toast(&self.cwd.display().to_string(), "INFO"),
            "help" => self.show_help = !self.show_help,
            "reload" => self.reload_config(),
            _ => self.toast(&format!("Unknown command: :{}", cmd), "WARN"),
        }
    }

    pub fn cycle_line_numbers(&mut self) {
        self.config.line_numbers = self.config.line_numbers.next();
        self.show_line_numbers = self.config.line_numbers != LineNumberMode::None;
        self.save_config();
        self.toast(
            &format!("Line numbers: {}", self.config.line_numbers.name()),
            "SUCCESS",
        );
    }

    pub fn cycle_theme(&mut self) {
        let name = self.config.cycle_theme();
        self.toast(&format!("Theme: {}", name), "SUCCESS");
    }

    pub fn toggle_word_wrap(&mut self) {
        self.config.word_wrap = !self.config.word_wrap;
        self.save_config();
        self.toast(
            &format!(
                "Word wrap: {}",
                if self.config.word_wrap { "on" } else { "off" }
            ),
            "SUCCESS",
        );
    }

    pub fn toggle_output(&mut self) {
        self.show_output = !self.show_output;
        if self.show_output {
            self.show_terminal = false;
            self.terminal_focused = false;
        }
    }

    pub fn toggle_terminal(&mut self) {
        self.show_terminal = !self.show_terminal;
        if self.show_terminal {
            self.show_output = false;
            self.terminal_focused = true;
            self.explorer_focused = false;
        } else {
            self.terminal_focused = false;
        }
    }

    pub fn toggle_explorer(&mut self) {
        self.show_explorer = !self.show_explorer;
        self.explorer_focused = self.show_explorer;
        self.terminal_focused = false;
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::input::handle_key_event;
    use crossterm::event::{KeyCode, KeyEvent, KeyModifiers};
    use ratatui::{backend::TestBackend, Terminal};
    use std::io::Write;

    fn test_app() -> App {
        let mut app = App::new(None);
        app.config = EditorConfig::default();
        app.buffer.tab_width = 4;
        app.buffer.use_spaces = true;
        app
    }

    fn key(code: KeyCode) -> KeyEvent {
        KeyEvent::new(code, KeyModifiers::NONE)
    }

    #[test]
    fn command_line_save_as_and_open() {
        let dir = tempfile::tempdir().unwrap();
        let file = dir.path().join("Demo.java");
        let mut app = test_app();
        app.buffer.set_text("class Demo {}");
        app.command_line = format!(":w {}", file.display());
        app.execute_command_line();
        assert_eq!(fs::read_to_string(&file).unwrap(), "class Demo {}");
        assert!(!app.buffer.modified);

        app.buffer.set_text("changed");
        app.command_line = format!(":e {}", file.display());
        app.execute_command_line();
        assert_eq!(app.buffer.text(), "class Demo {}");
    }

    #[test]
    fn new_file_creates_and_opens() {
        let dir = tempfile::tempdir().unwrap();
        let mut app = test_app();
        app.cwd = dir.path().to_path_buf();
        app.command_line = ":new New.java".to_string();
        app.execute_command_line();
        let created = dir.path().join("New.java");
        assert!(created.is_file());
        assert_eq!(app.file_name(), Some(created.as_path()));
    }

    #[test]
    fn search_and_replace_flow() {
        let mut app = test_app();
        app.buffer.set_text("foo foo bar");
        app.search.query = "foo".to_string();
        app.search.replacement = "baz".to_string();
        app.update_search_matches();
        assert_eq!(app.search.matches.len(), 2);
        app.replace_all_matches();
        assert_eq!(app.buffer.text(), "baz baz bar");
    }

    #[test]
    fn theme_and_line_number_commands() {
        let mut app = test_app();
        let first = app.config.theme.clone();
        app.command_line = ":theme monokai".to_string();
        app.execute_command_line();
        assert_eq!(app.config.theme, "Monokai");
        app.command_line = ":set number=relative".to_string();
        app.execute_command_line();
        assert_eq!(app.config.line_numbers, LineNumberMode::Relative);
        assert!(app.show_line_numbers);
        let _ = first;
    }

    #[test]
    fn terminal_builtins_and_history() {
        let dir = tempfile::tempdir().unwrap();
        let mut out = fs::File::create(dir.path().join("out.txt")).unwrap();
        writeln!(out, "x").unwrap();
        let mut app = test_app();
        app.cwd = dir.path().to_path_buf();
        app.terminal_focused = true;

        app.terminal_input = "pwd".to_string();
        app.terminal_input_enter();
        let cwd_hint = dir.path().to_string_lossy();
        assert!(app
            .terminal_output
            .iter()
            .any(|l| l.contains(cwd_hint.as_ref()) || l.contains("out.txt")));

        app.terminal_input = format!("cd {}", dir.path().display());
        app.terminal_input_enter();
        assert_eq!(app.cwd, dir.path());
        assert_eq!(app.terminal_history.len(), 2);
        app.terminal_history_prev();
        assert_eq!(app.terminal_input, format!("cd {}", dir.path().display()));

        app.terminal_input = "not_a_real_command_xyz".to_string();
        app.terminal_input_enter();
        assert!(app.job.is_some());
        app.stop_job();
    }

    #[test]
    fn keyboard_insert_and_visual_copy() {
        let mut app = test_app();
        handle_key_event(&mut app, key(KeyCode::Char('i')));
        for ch in "abc".chars() {
            handle_key_event(&mut app, key(KeyCode::Char(ch)));
        }
        handle_key_event(&mut app, key(KeyCode::Esc));
        assert_eq!(app.buffer.text(), "abc");

        handle_key_event(&mut app, key(KeyCode::Char('v')));
        handle_key_event(&mut app, key(KeyCode::Left));
        handle_key_event(&mut app, key(KeyCode::Left));
        handle_key_event(&mut app, key(KeyCode::Char('y')));
        assert_eq!(app.clipboard, "bc");
    }

    #[test]
    fn basic_java_formatter_indents_blocks() {
        let formatted = App::format_java_basic("class A{void b(){x();}}", 4, true);
        assert!(formatted.contains("class A {"));
        assert!(formatted.contains("    void b() {"));
        assert!(formatted.contains("        x();"));
    }

    #[test]
    fn cursor_rendered_on_actual_edited_line() {
        let mut app = test_app();
        app.show_explorer = false;
        app.config.line_numbers = LineNumberMode::Absolute;
        app.buffer.set_text("one\ntwo\nthree\nfour\nfive");
        app.buffer.move_to(3, 2);

        let backend = TestBackend::new(80, 20);
        let mut terminal = Terminal::new(backend).unwrap();
        terminal
            .draw(|f| crate::ui::render_ui(f, &mut app))
            .unwrap();

        let inner = app.editor_inner_rect.unwrap();
        let cursor = terminal.get_cursor_position().unwrap();
        assert_eq!(cursor.y, inner.y + 3);
    }

    #[test]
    fn closing_file_clears_tab_render() {
        let mut app = test_app();
        app.show_explorer = false;
        app.current_file = Some(PathBuf::from("CLOSE_ME_MARKER.java"));
        app.buffer.path = app.current_file.clone();

        let backend = TestBackend::new(80, 20);
        let mut terminal = Terminal::new(backend).unwrap();
        terminal
            .draw(|f| crate::ui::render_ui(f, &mut app))
            .unwrap();
        let rendered: String = terminal
            .backend()
            .buffer()
            .content
            .iter()
            .map(|c| c.symbol())
            .collect();
        assert!(rendered.contains("CLOSE_ME_MARKER.java"));

        app.close_current_file();
        terminal
            .draw(|f| crate::ui::render_ui(f, &mut app))
            .unwrap();
        let rendered: String = terminal
            .backend()
            .buffer()
            .content
            .iter()
            .map(|c| c.symbol())
            .collect();
        assert!(!rendered.contains("CLOSE_ME_MARKER.java"));
        assert_eq!(app.buffer.text(), "");
    }

    #[test]
    fn render_shows_all_visible_lines_from_top() {
        let mut app = test_app();
        app.show_explorer = false;
        app.config.line_numbers = LineNumberMode::None;
        app.buffer
            .set_text("line one\nline two\nLINE_THREE_MARKER\nline four");
        assert_eq!(app.buffer.cursor.line, 0);

        let backend = TestBackend::new(80, 20);
        let mut terminal = Terminal::new(backend).unwrap();
        terminal
            .draw(|f| crate::ui::render_ui(f, &mut app))
            .unwrap();

        let rendered: String = terminal
            .backend()
            .buffer()
            .content
            .iter()
            .map(|c| c.symbol())
            .collect();
        assert!(rendered.contains("LINE_THREE_MARKER"));
    }

    #[test]
    fn render_all_panels_without_panic() {
        let mut app = test_app();
        app.buffer.set_text("public class Demo {\n    // hi\n    public static void main(String[] args) {\n        System.out.println(\"ok\");\n    }\n}");
        app.show_terminal = true;
        app.show_output = true;
        app.active_menu = MenuTab::File;
        app.search = SearchState::open("Demo".to_string(), crate::app::SearchField::Find);
        app.update_search_matches();
        app.show_help = true;

        let backend = TestBackend::new(100, 30);
        let mut terminal = Terminal::new(backend).unwrap();
        terminal
            .draw(|f| crate::ui::render_ui(f, &mut app))
            .unwrap();
        let buffer = terminal.backend().buffer();
        assert!(buffer.content.iter().any(|c| c.symbol() != " "));
        assert!(app.editor_inner_rect.is_some());
        assert!(app.menu_dropdown_rect.is_some());
    }
}
