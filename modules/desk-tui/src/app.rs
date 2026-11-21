use crate::buffer::{Buffer, Selection};
use crate::config::{EditorConfig, LineNumberMode, Theme};
use crate::highlighter::Language;
use crate::lsp::{LspBridge, LspDiagnostic, LspEvent};
use crate::mode::Mode;
use crate::overlay::{self, ConfirmAction, Overlay, default_palette_items, rank_paths};
use crossbeam_channel::{Receiver, Sender, unbounded};
use std::fs;
use std::io::{BufRead, BufReader};
use std::path::{Path, PathBuf};
use std::process::{Child, ChildStdin, Command, Stdio};
use std::sync::{Arc, Mutex};
use std::thread;
use std::time::{Duration, Instant};

/// One editor document tab. The active tab is mirrored in {@code App::buffer}
/// for compatibility with existing input/render paths.
#[derive(Debug, Clone)]
pub struct EditorTab {
    pub path: Option<PathBuf>,
    pub buffer: Buffer,
    pub language: Language,
}

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
            if self.expanded { "▾" } else { "▸" }
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
            #[cfg(unix)]
            {
                let pid = child.id() as i32;
                // Negative PID targets the process group started via setpgid.
                unsafe {
                    libc::kill(-pid, libc::SIGTERM);
                }
            }
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
    /// All open tabs. Index {@code active_tab} matches {@code buffer}/{@code current_file}.
    pub tabs: Vec<EditorTab>,
    pub active_tab: usize,
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

    pub overlay: Overlay,
    pub lsp: LspBridge,
    pub problems: Vec<LspDiagnostic>,
    pub show_problems: bool,
    pub problems_selected: usize,
    pub pending_lsp_request: Option<u64>,
    pub git_branch: String,
    pub java_status: String,
    pub project_kind: String,
    pub hover_text: Option<String>,
    pub pending_workspace_edit: Option<crate::workspace_edit::WorkspaceEditPlan>,
    pub pending_code_actions: Vec<crate::lsp::CodeActionEntry>,
    /// Disk mtime the user already acknowledged (keep memory / suppress re-prompt).
    pub disk_change_acked: Option<std::time::SystemTime>,
    pub last_disk_poll: Instant,
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

        let initial_tab = EditorTab {
            path: current_file.clone(),
            buffer: buffer.clone(),
            language,
        };

        let mut app = Self {
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
            cwd: cwd.clone(),
            current_file,
            tabs: vec![initial_tab],
            active_tab: 0,
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
            overlay: Overlay::None,
            lsp: LspBridge::start(&cwd),
            problems: Vec::new(),
            show_problems: false,
            problems_selected: 0,
            pending_lsp_request: None,
            git_branch: detect_git_branch(&cwd),
            java_status: "Java ?".into(),
            project_kind: detect_project_kind(&cwd),
            hover_text: None,
            pending_workspace_edit: None,
            pending_code_actions: Vec::new(),
            disk_change_acked: None,
            last_disk_poll: Instant::now(),
        };
        if initial_path.is_none() {
            app.restore_session_if_present();
        }
        if let Some(path) = app.current_file.clone() {
            let text = app.buffer.text();
            app.lsp.did_open(&path, language_id(app.language), &text);
        }
        app
    }

    /// Restore open tabs from {@code .desk/session.json} when present.
    pub fn restore_session_if_present(&mut self) {
        let session = self.cwd.join(".desk").join("session.json");
        let Ok(text) = fs::read_to_string(&session) else {
            return;
        };
        let mut restored = 0usize;
        // Minimal parse of openDocuments[].path without a JSON crate.
        for segment in text.split("\"path\"") {
            let Some(rest) = segment.strip_prefix(':') else {
                continue;
            };
            let rest = rest.trim_start();
            let Some(rest) = rest.strip_prefix('"') else {
                continue;
            };
            let Some(end) = rest.find('"') else {
                continue;
            };
            let path = PathBuf::from(&rest[..end]);
            if path.is_file() {
                if let Err(e) = self.open_file(&path) {
                    self.toast(
                        &format!("Session restore skipped {}: {}", path.display(), e),
                        "WARN",
                    );
                } else {
                    restored += 1;
                }
            }
        }
        if restored > 0 {
            self.toast(
                &format!("Restored {} tab(s) from session", restored),
                "INFO",
            );
        }
    }

    /// Persist the live buffer into the active tab slot.
    pub fn sync_active_tab(&mut self) {
        if self.tabs.is_empty() {
            self.tabs.push(EditorTab {
                path: self.current_file.clone(),
                buffer: self.buffer.clone(),
                language: self.language,
            });
            self.active_tab = 0;
            return;
        }
        if self.active_tab >= self.tabs.len() {
            self.active_tab = self.tabs.len() - 1;
        }
        self.tabs[self.active_tab] = EditorTab {
            path: self.current_file.clone(),
            buffer: self.buffer.clone(),
            language: self.language,
        };
    }

    pub fn activate_tab(&mut self, index: usize) {
        if index >= self.tabs.len() {
            return;
        }
        self.sync_active_tab();
        self.active_tab = index;
        let tab = &self.tabs[index];
        self.buffer = tab.buffer.clone();
        self.current_file = tab.path.clone();
        self.language = tab.language;
        self.mode = Mode::Normal;
        self.bracket_match = None;
        self.pending_key = None;
        self.explorer_focused = false;
        self.terminal_focused = false;
        self.disk_change_acked = None;
        self.check_external_file_state();
    }

    pub fn next_tab(&mut self) {
        if self.tabs.len() <= 1 {
            return;
        }
        let next = (self.active_tab + 1) % self.tabs.len();
        self.activate_tab(next);
    }

    pub fn prev_tab(&mut self) {
        if self.tabs.len() <= 1 {
            return;
        }
        let prev = if self.active_tab == 0 {
            self.tabs.len() - 1
        } else {
            self.active_tab - 1
        };
        self.activate_tab(prev);
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
        self.open_file_with_options(path, false)
    }

    /// Open a file. When {@code force_reload} is false and the path is already
    /// open, switch to that tab without discarding unsaved edits.
    pub fn open_file_with_options(
        &mut self,
        path: &Path,
        force_reload: bool,
    ) -> Result<(), std::io::Error> {
        let canonical = fs::canonicalize(path).unwrap_or_else(|_| path.to_path_buf());
        let existing = self.tabs.iter().position(|t| {
            t.path.as_ref().is_some_and(|p| {
                fs::canonicalize(p).unwrap_or_else(|_| p.clone()) == canonical || p == &canonical
            })
        });

        if let Some(idx) = existing {
            if !force_reload {
                self.activate_tab(idx);
                self.toast(&format!("Switched to {}", path.display()), "INFO");
                return Ok(());
            }
            if self.tabs[idx].buffer.modified || (idx == self.active_tab && self.buffer.modified) {
                self.toast("Unsaved changes — save before reloading", "WARN");
                return Ok(());
            }
        }

        let mut buffer = Buffer::from_file(path)?;
        Self::apply_config(&self.config, &mut buffer);
        buffer.cursor = Default::default();
        buffer.selection = None;
        buffer.scroll_top = 0;
        buffer.scroll_left = 0;
        buffer.end_edit_group();
        let language = Language::from_path(Some(path));

        if let Some(idx) = existing {
            self.tabs[idx] = EditorTab {
                path: Some(canonical.clone()),
                buffer: buffer.clone(),
                language,
            };
            self.active_tab = idx;
            self.toast(&format!("Reloaded {}", path.display()), "INFO");
        } else {
            self.sync_active_tab();
            self.tabs.push(EditorTab {
                path: Some(canonical.clone()),
                buffer: buffer.clone(),
                language,
            });
            self.active_tab = self.tabs.len() - 1;
            self.toast(&format!("Opened {}", path.display()), "INFO");
        }

        self.buffer = buffer;
        self.language = language;
        self.current_file = Some(canonical.clone());
        self.mode = Mode::Normal;
        self.bracket_match = None;
        self.pending_key = None;
        self.explorer_focused = false;
        self.terminal_focused = false;
        self.last_edit_time = Instant::now();
        self.refresh_explorer();
        self.lsp
            .did_open(&canonical, language_id(language), &self.buffer.text());
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
        self.sync_active_tab();
        let mut buffer = Buffer::new_empty();
        if let Some(name) = name.filter(|n| !n.is_empty()) {
            buffer.path = Some(PathBuf::from(name));
        }
        Self::apply_config(&self.config, &mut buffer);
        let language = Language::from_path(buffer.path.as_deref());
        self.tabs.push(EditorTab {
            path: buffer.path.clone(),
            buffer: buffer.clone(),
            language,
        });
        self.active_tab = self.tabs.len() - 1;
        self.buffer = buffer;
        self.current_file = self.buffer.path.clone();
        self.language = language;
        self.mode = Mode::Normal;
        self.bracket_match = None;
        self.pending_key = None;
        self.last_edit_time = Instant::now();
        self.toast("New file", "INFO");
    }

    pub fn request_close_current_file(&mut self) {
        self.sync_active_tab();
        if self.buffer.modified {
            self.overlay = Overlay::Confirm {
                title: "Unsaved changes".into(),
                message: "Close tab and discard unsaved edits?".into(),
                action: ConfirmAction::CloseTab,
            };
            return;
        }
        self.close_current_file_forced();
    }

    pub fn close_current_file(&mut self) {
        self.request_close_current_file();
    }

    pub fn close_current_file_forced(&mut self) {
        self.sync_active_tab();
        if self.tabs.is_empty() {
            return;
        }
        if let Some(path) = self.current_file.clone() {
            // Notify LSP of close when available.
            let _ = path;
        }
        let closing = self.active_tab.min(self.tabs.len() - 1);
        self.tabs.remove(closing);
        if self.tabs.is_empty() {
            let mut buffer = Buffer::new_empty();
            Self::apply_config(&self.config, &mut buffer);
            self.tabs.push(EditorTab {
                path: None,
                buffer: buffer.clone(),
                language: Language::Generic,
            });
            self.active_tab = 0;
            self.buffer = buffer;
            self.current_file = None;
            self.language = Language::Generic;
        } else {
            let next = closing.min(self.tabs.len() - 1);
            self.active_tab = next;
            let tab = &self.tabs[next];
            self.buffer = tab.buffer.clone();
            self.current_file = tab.path.clone();
            self.language = tab.language;
        }
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

    pub fn close_other_tabs(&mut self) {
        self.sync_active_tab();
        if self.tabs.len() <= 1 {
            return;
        }
        let keep = self.tabs[self.active_tab].clone();
        self.tabs.clear();
        self.tabs.push(keep);
        self.active_tab = 0;
        self.toast("Closed other tabs", "INFO");
    }

    pub fn save_all_tabs(&mut self) -> Result<(), std::io::Error> {
        self.sync_active_tab();
        let mut skipped_untitled = 0usize;
        for tab in &mut self.tabs {
            if let Some(path) = tab.path.clone() {
                if tab.buffer.disk_changed_externally() {
                    return Err(std::io::Error::other(format!(
                        "File changed on disk: {} — save individually after reviewing",
                        path.display()
                    )));
                }
                tab.buffer.save_as(&path)?;
            } else if tab.buffer.modified {
                skipped_untitled += 1;
            }
        }
        if let Some(path) = self.current_file.clone() {
            if self.buffer.disk_changed_externally() {
                self.overlay = Overlay::Confirm {
                    title: "File changed on disk".into(),
                    message: format!("Overwrite {} anyway?", path.display()),
                    action: ConfirmAction::SaveOverwriteConflict,
                };
                return Ok(());
            }
            self.buffer.save_as(&path)?;
        } else if self.buffer.modified {
            skipped_untitled += 1;
        }
        if skipped_untitled > 0 {
            self.toast(
                &format!(
                    "Saved named tabs; {} untitled buffer(s) still need Save As",
                    skipped_untitled
                ),
                "WARN",
            );
        } else {
            self.toast("Saved all", "SUCCESS");
        }
        Ok(())
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
        if self.buffer.path.as_ref().is_some_and(|p| p == path)
            && self.buffer.disk_changed_externally()
        {
            self.overlay = Overlay::Confirm {
                title: "File changed on disk".into(),
                message: format!("Overwrite {} anyway?", path.display()),
                action: ConfirmAction::SaveOverwriteConflict,
            };
            return Ok(());
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
        self.sync_active_tab();
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
        let errs = self
            .problems
            .iter()
            .filter(|d| d.severity == "ERROR")
            .count();
        let warns = self
            .problems
            .iter()
            .filter(|d| d.severity == "WARNING")
            .count();
        format!(
            " {}{} | {} | {} | {} | E:{} W:{} | Ln {}, Col {}{} | {} | {}{}",
            self.file_name_or_no_name(),
            if self.buffer.modified { " ●" } else { "" },
            self.project_kind,
            self.git_branch,
            self.lsp.status,
            errs,
            warns,
            self.current_line_index() + 1,
            self.current_column() + 1,
            sel,
            self.mode.name(),
            self.language.display_name(),
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
        self.poll_lsp();
        if self.last_disk_poll.elapsed() >= Duration::from_secs(2) {
            self.last_disk_poll = Instant::now();
            self.check_external_file_state();
        }
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
        let mut cmd = Command::new(program);
        cmd.args(args)
            .current_dir(&self.cwd)
            .stdin(Stdio::piped())
            .stdout(Stdio::piped())
            .stderr(Stdio::piped());
        #[cfg(unix)]
        {
            use std::os::unix::process::CommandExt;
            // Own process group so stop_job can signal the whole tree (mvn/gradle children).
            cmd.process_group(0);
        }
        let mut child = cmd
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
        thread::spawn(move || {
            loop {
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
                    if p.is_absolute() { p } else { self.cwd.join(p) }
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
            "q" | "quit" => self.request_quit(),
            "q!" => {
                self.buffer.modified = false;
                for tab in &mut self.tabs {
                    tab.buffer.modified = false;
                }
                self.persist_session();
                self.lsp.shutdown();
                self.running = false;
            }
            "e" | "edit" => {
                if let Err(e) = self.open_file_with_options(Path::new(rest), true) {
                    self.toast(&format!("Open failed: {}", e), "ERROR");
                }
            }
            "e!" => {
                // Force reload, discarding unsaved edits for the target path.
                let path = Path::new(rest);
                let canonical = fs::canonicalize(path).unwrap_or_else(|_| path.to_path_buf());
                if let Some(idx) = self.tabs.iter().position(|t| {
                    t.path.as_ref().is_some_and(|p| {
                        fs::canonicalize(p).unwrap_or_else(|_| p.clone()) == canonical
                    })
                }) {
                    self.tabs[idx].buffer.modified = false;
                    if idx == self.active_tab {
                        self.buffer.modified = false;
                    }
                } else if self
                    .current_file
                    .as_ref()
                    .is_some_and(|p| fs::canonicalize(p).unwrap_or_else(|_| p.clone()) == canonical)
                {
                    self.buffer.modified = false;
                }
                if let Err(e) = self.open_file_with_options(path, true) {
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
            "build" => self.build_workspace(),
            "check" | "lint" => self.check_current_java(),
            "fmt" | "format" => self.request_lsp_format(),
            "rename" => self.execute_rename(rest),
            "codeaction" | "fix" => {
                if let Ok(n) = rest.parse::<usize>() {
                    if let Some(action) =
                        self.pending_code_actions.get(n.saturating_sub(1)).cloned()
                    {
                        if let Some(plan) = action.plan {
                            self.prompt_workspace_edit("codeAction", plan);
                        } else {
                            self.toast("Action has no workspace edit", "WARN");
                        }
                    } else {
                        self.toast("Unknown code action index", "WARN");
                    }
                } else {
                    self.request_code_actions();
                }
            }
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
            "mkdir" => {
                if rest.is_empty() {
                    self.toast("Usage: :mkdir <name>", "WARN");
                } else {
                    let path = self.cwd.join(rest);
                    match fs::create_dir_all(&path) {
                        Ok(()) => {
                            self.refresh_explorer();
                            self.toast(&format!("Created {}", path.display()), "SUCCESS");
                        }
                        Err(e) => self.toast(&format!("mkdir failed: {}", e), "ERROR"),
                    }
                }
            }
            "mv" => {
                let parts: Vec<&str> = rest.split_whitespace().collect();
                if parts.len() != 2 {
                    self.toast("Usage: :mv <from> <to>", "WARN");
                } else {
                    let from = PathBuf::from(parts[0]);
                    let to = PathBuf::from(parts[1]);
                    match fs::rename(&from, &to) {
                        Ok(()) => {
                            self.refresh_explorer();
                            self.toast("Renamed", "SUCCESS");
                        }
                        Err(e) => self.toast(&format!("rename failed: {}", e), "ERROR"),
                    }
                }
            }
            "help" => self.show_help = !self.show_help,
            "reload" => self.reload_config(),
            _ => {
                // Go-to-line: bare number after :
                if let Ok(line) = head.parse::<usize>() {
                    if line > 0 {
                        self.buffer.cursor.line = line - 1;
                        self.buffer.cursor.col = 0;
                    }
                } else {
                    self.toast(&format!("Unknown command: :{}", cmd), "WARN");
                }
            }
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

    pub fn request_quit(&mut self) {
        self.sync_active_tab();
        if self.tabs.iter().any(|t| t.buffer.modified) || self.buffer.modified {
            self.overlay = Overlay::Confirm {
                title: "Unsaved changes".into(),
                message: "Quit and discard unsaved edits?".into(),
                action: ConfirmAction::Quit,
            };
            return;
        }
        self.persist_session();
        self.lsp.shutdown();
        self.running = false;
    }

    pub fn confirm_yes(&mut self) {
        let action = match &self.overlay {
            Overlay::Confirm { action, .. } => *action,
            _ => {
                self.overlay.close();
                return;
            }
        };
        self.overlay.close();
        match action {
            ConfirmAction::CloseTab => self.close_current_file_forced(),
            ConfirmAction::Quit => {
                for tab in &mut self.tabs {
                    tab.buffer.modified = false;
                }
                self.buffer.modified = false;
                self.persist_session();
                self.lsp.shutdown();
                self.running = false;
            }
            ConfirmAction::ReloadConflict => {
                if let Some(path) = self.current_file.clone() {
                    let _ = self.open_file_with_options(&path, true);
                    self.disk_change_acked = None;
                }
            }
            ConfirmAction::DiskDeleted => {
                self.close_current_file_forced();
                self.disk_change_acked = None;
            }
            ConfirmAction::SaveOverwriteConflict => {
                if let Some(path) = self.current_file.clone() {
                    let _ = self.buffer.save_as(&path);
                    self.sync_active_tab();
                    self.toast("Saved (overwrote disk changes)", "WARN");
                }
            }
            ConfirmAction::DeletePath => {
                if let Some((path, _)) = self.explorer_flat.get(self.explorer_selected).cloned() {
                    let _ = if path.is_dir() {
                        fs::remove_dir_all(&path)
                    } else {
                        fs::remove_file(&path)
                    };
                    self.refresh_explorer();
                    self.toast(&format!("Deleted {}", path.display()), "INFO");
                }
            }
            ConfirmAction::ApplyWorkspaceEdit => {
                if let Some(plan) = self.pending_workspace_edit.take() {
                    self.apply_workspace_edit_plan(plan);
                }
            }
        }
    }

    pub fn apply_workspace_edit_plan(&mut self, plan: crate::workspace_edit::WorkspaceEditPlan) {
        use crate::workspace_edit::{
            apply_edits_to_lines, apply_file_on_disk, filter_plan_to_workspace,
        };
        self.sync_active_tab();
        let (plan, rejected) = filter_plan_to_workspace(&self.cwd, plan);
        if !rejected.is_empty() {
            self.toast(
                &format!("Rejected {} path(s) outside workspace", rejected.len()),
                "WARN",
            );
        }
        if plan.is_empty() {
            self.toast("No in-workspace edits to apply", "WARN");
            return;
        }
        for file in &plan.files {
            let open_idx = self.tabs.iter().position(|t| {
                t.path.as_ref().is_some_and(|p| {
                    fs::canonicalize(p).unwrap_or_else(|_| p.clone())
                        == fs::canonicalize(&file.path).unwrap_or_else(|_| file.path.clone())
                })
            });
            if let Some(idx) = open_idx {
                apply_edits_to_lines(&mut self.tabs[idx].buffer.lines, &file.edits);
                self.tabs[idx].buffer.modified = true;
                if idx == self.active_tab {
                    self.buffer.lines = self.tabs[idx].buffer.lines.clone();
                    self.buffer.modified = true;
                }
            } else if let Err(e) = apply_file_on_disk(&file.path, &file.edits) {
                self.toast(
                    &format!("Edit failed for {}: {}", file.path.display(), e),
                    "ERROR",
                );
            }
        }
        self.toast(&format!("Applied {}", plan.summary()), "SUCCESS");
        self.refresh_explorer();
    }

    pub fn prompt_workspace_edit(
        &mut self,
        kind: &str,
        plan: crate::workspace_edit::WorkspaceEditPlan,
    ) {
        use crate::workspace_edit::filter_plan_to_workspace;
        let (plan, rejected) = filter_plan_to_workspace(&self.cwd, plan);
        if !rejected.is_empty() {
            self.toast(
                &format!(
                    "Ignoring {} edit target(s) outside workspace",
                    rejected.len()
                ),
                "WARN",
            );
        }
        if plan.is_empty() {
            self.toast(&format!("No in-workspace edits from {}", kind), "INFO");
            return;
        }
        self.pending_workspace_edit = Some(plan.clone());
        let paths: Vec<String> = plan
            .files
            .iter()
            .take(5)
            .map(|f| f.path.display().to_string())
            .collect();
        let more = if plan.files.len() > 5 {
            format!(" (+{} more)", plan.files.len() - 5)
        } else {
            String::new()
        };
        self.overlay = Overlay::Confirm {
            title: format!("Apply {} edits?", kind),
            message: format!("{} — {}\n{}", plan.summary(), paths.join(", "), more),
            action: ConfirmAction::ApplyWorkspaceEdit,
        };
    }

    pub fn request_rename_symbol(&mut self) {
        let Some(path) = self.current_file.clone() else {
            self.toast("No file open", "WARN");
            return;
        };
        if !self.lsp.available {
            let status = self.lsp.status.clone();
            self.toast(&status, "WARN");
            return;
        }
        let word = self.buffer.word_at_cursor();
        if word.is_empty() {
            self.toast("No symbol under cursor", "WARN");
            return;
        }
        self.show_command_line = true;
        self.command_line = format!(":rename {}", word);
        self.toast("Edit new name after :rename and press Enter", "INFO");
        let _ = path;
    }

    pub fn execute_rename(&mut self, new_name: &str) {
        let Some(path) = self.current_file.clone() else {
            return;
        };
        if new_name.is_empty() {
            self.toast("Usage: :rename <newName>", "WARN");
            return;
        }
        if !self.lsp.available {
            let status = self.lsp.status.clone();
            self.toast(&status, "WARN");
            return;
        }
        self.lsp.did_change(&path, &self.buffer.text());
        if let Some(id) = self.lsp.request_rename(
            &path,
            self.buffer.cursor.line,
            self.buffer.cursor.col,
            new_name,
        ) {
            self.pending_lsp_request = Some(id);
            self.toast("Renaming…", "INFO");
        }
    }

    pub fn request_lsp_format(&mut self) {
        let Some(path) = self.current_file.clone() else {
            return;
        };
        if !self.lsp.available {
            self.format_current();
            return;
        }
        self.lsp.did_change(&path, &self.buffer.text());
        if let Some(id) = self.lsp.request_format(&path) {
            self.pending_lsp_request = Some(id);
            self.toast("Formatting via LSP…", "INFO");
        }
    }

    pub fn request_code_actions(&mut self) {
        let Some(path) = self.current_file.clone() else {
            return;
        };
        if !self.lsp.available {
            let status = self.lsp.status.clone();
            self.toast(&status, "WARN");
            return;
        }
        self.lsp.did_change(&path, &self.buffer.text());
        if let Some(id) =
            self.lsp
                .request_code_action(&path, self.buffer.cursor.line, self.buffer.cursor.col)
        {
            self.pending_lsp_request = Some(id);
        }
    }

    pub fn build_workspace(&mut self) {
        self.show_output = true;
        self.show_terminal = false;
        if let Some(jar) = Self::find_desk_jar() {
            let java = Self::java_tool("java");
            let args = vec![
                "-jar".to_string(),
                jar.display().to_string(),
                "build".to_string(),
            ];
            if let Err(e) = self.spawn_job("desk build".into(), &java, &args) {
                self.toast(&format!("Build failed to start: {}", e), "ERROR");
            } else {
                self.push_output("Building… Esc or F7 to cancel", OutputKind::Info);
            }
        } else {
            self.compile_current_file();
        }
    }

    pub fn confirm_no(&mut self) {
        if let Overlay::Confirm { action, .. } = &self.overlay {
            match action {
                ConfirmAction::ReloadConflict | ConfirmAction::DiskDeleted => {
                    // Keep in-memory; suppress re-prompt until disk changes again.
                    // Missing file: UNIX_EPOCH sentinel suppresses re-prompt until path returns.
                    self.disk_change_acked = self
                        .buffer
                        .current_disk_mtime()
                        .or(Some(std::time::SystemTime::UNIX_EPOCH));
                }
                _ => {}
            }
        }
        self.overlay.close();
    }

    /// Detect external disk changes / deletions without busy-polling.
    pub fn check_external_file_state(&mut self) {
        if matches!(
            self.overlay,
            Overlay::Confirm {
                action: ConfirmAction::ReloadConflict | ConfirmAction::DiskDeleted,
                ..
            }
        ) {
            return;
        }
        if !matches!(self.overlay, Overlay::None) {
            return;
        }
        let Some(path) = self.current_file.clone() else {
            return;
        };
        if self.buffer.disk_missing() {
            let ack_missing = self.disk_change_acked == Some(std::time::SystemTime::UNIX_EPOCH);
            if ack_missing {
                return;
            }
            self.overlay = Overlay::Confirm {
                title: "File deleted on disk".into(),
                message: format!(
                    "{} no longer exists. Close tab (Yes) or keep buffer (No)?",
                    path.display()
                ),
                action: ConfirmAction::DiskDeleted,
            };
            return;
        }
        if !self.buffer.disk_changed_externally() {
            return;
        }
        let Some(current) = self.buffer.current_disk_mtime() else {
            return;
        };
        if self.disk_change_acked == Some(current) {
            return;
        }
        let dirty = if self.buffer.modified {
            "unsaved edits"
        } else {
            "clean buffer"
        };
        self.overlay = Overlay::Confirm {
            title: "File changed on disk".into(),
            message: format!(
                "{} changed externally ({}). Reload from disk (Yes) or keep memory (No)?",
                path.display(),
                dirty
            ),
            action: ConfirmAction::ReloadConflict,
        };
    }

    pub fn open_palette(&mut self) {
        self.overlay = Overlay::Palette {
            query: String::new(),
            selected: 0,
            items: default_palette_items(),
        };
    }

    pub fn open_fuzzy_files(&mut self) {
        let paths = collect_workspace_files(&self.cwd, 2000);
        let matches = rank_paths("", &paths, 100);
        self.overlay = Overlay::FuzzyFiles {
            query: String::new(),
            selected: 0,
            matches,
        };
    }

    pub fn open_find_in_files(&mut self) {
        self.overlay = Overlay::FindInFiles {
            query: String::new(),
            selected: 0,
            matches: Vec::new(),
        };
    }

    pub fn run_palette_item(&mut self, id: &str) {
        self.overlay.close();
        match id {
            "save" => {
                let _ = self.save_current();
            }
            "save_all" => {
                let _ = self.save_all_tabs();
            }
            "fuzzy" => self.open_fuzzy_files(),
            "find_in_files" => self.open_find_in_files(),
            "goto_line" => {
                self.show_command_line = true;
                self.command_line = ":".to_string();
                self.toast("Type line number after : and Enter", "INFO");
            }
            "outline" => self.request_outline(),
            "problems" => {
                self.show_problems = !self.show_problems;
                if self.show_problems {
                    self.show_output = true;
                }
            }
            "complete" => self.trigger_completion(),
            "definition" => self.goto_definition(),
            "references" => self.find_references(),
            "hover" => self.show_hover(),
            "format" => self.format_current(),
            "build" | "compile" => self.build_workspace(),
            "run" => self.run_current_file(),
            "rename" => self.request_rename_symbol(),
            "code_action" => self.request_code_actions(),
            "explorer_new_file" => self.explorer_new_file(),
            "explorer_new_dir" => self.explorer_new_dir(),
            "explorer_rename" => self.explorer_rename(),
            "explorer_delete" => self.explorer_delete(),
            "quit" => self.request_quit(),
            _ => self.toast(&format!("Unknown action: {}", id), "WARN"),
        }
    }

    pub fn trigger_completion(&mut self) {
        let Some(path) = self.current_file.clone() else {
            self.toast("No file open", "WARN");
            return;
        };
        if !self.lsp.available {
            let status = self.lsp.status.clone();
            self.toast(&status, "WARN");
            return;
        }
        self.lsp.did_change(&path, &self.buffer.text());
        if let Some(id) =
            self.lsp
                .request_complete(&path, self.buffer.cursor.line, self.buffer.cursor.col)
        {
            self.pending_lsp_request = Some(id);
            self.toast("Requesting completions…", "INFO");
        }
    }

    pub fn goto_definition(&mut self) {
        let Some(path) = self.current_file.clone() else {
            return;
        };
        if !self.lsp.available {
            let status = self.lsp.status.clone();
            self.toast(&status, "WARN");
            return;
        }
        self.lsp.did_change(&path, &self.buffer.text());
        if let Some(id) =
            self.lsp
                .request_definition(&path, self.buffer.cursor.line, self.buffer.cursor.col)
        {
            self.pending_lsp_request = Some(id);
        }
    }

    pub fn find_references(&mut self) {
        let Some(path) = self.current_file.clone() else {
            return;
        };
        if !self.lsp.available {
            let status = self.lsp.status.clone();
            self.toast(&status, "WARN");
            return;
        }
        self.lsp.did_change(&path, &self.buffer.text());
        if let Some(id) =
            self.lsp
                .request_references(&path, self.buffer.cursor.line, self.buffer.cursor.col)
        {
            self.pending_lsp_request = Some(id);
        }
    }

    pub fn show_hover(&mut self) {
        let Some(path) = self.current_file.clone() else {
            return;
        };
        if !self.lsp.available {
            let status = self.lsp.status.clone();
            self.toast(&status, "WARN");
            return;
        }
        self.lsp.did_change(&path, &self.buffer.text());
        if let Some(id) =
            self.lsp
                .request_hover(&path, self.buffer.cursor.line, self.buffer.cursor.col)
        {
            self.pending_lsp_request = Some(id);
        }
    }

    pub fn request_outline(&mut self) {
        let Some(path) = self.current_file.clone() else {
            return;
        };
        if !self.lsp.available {
            // Local outline fallback: scan class/method keywords
            let symbols = local_java_outline(&self.buffer.lines);
            self.overlay = Overlay::Outline {
                symbols,
                selected: 0,
            };
            return;
        }
        if let Some(id) = self.lsp.request_symbols(&path) {
            self.pending_lsp_request = Some(id);
        }
    }

    pub fn poll_lsp(&mut self) {
        for ev in self.lsp.poll_events() {
            match ev {
                LspEvent::Diagnostics { path, items } => {
                    self.problems.retain(|d| d.path != path);
                    self.problems.extend(items);
                    self.problems.sort_by(|a, b| {
                        (&a.path, a.line, a.column).cmp(&(&b.path, b.line, b.column))
                    });
                }
                LspEvent::Completions { request_id, items } => {
                    if self.pending_lsp_request == Some(request_id) {
                        self.pending_lsp_request = None;
                        if items.is_empty() {
                            self.toast("No completions", "INFO");
                        } else {
                            self.overlay = Overlay::Completions { items, selected: 0 };
                        }
                    }
                }
                LspEvent::Hover {
                    request_id,
                    contents,
                } => {
                    if self.pending_lsp_request == Some(request_id) {
                        self.pending_lsp_request = None;
                        if contents.is_empty() {
                            self.toast("No hover info", "INFO");
                        } else {
                            self.hover_text = Some(contents.clone());
                            self.toast(&contents.chars().take(80).collect::<String>(), "INFO");
                        }
                    }
                }
                LspEvent::Locations {
                    request_id,
                    kind,
                    items,
                } => {
                    if self.pending_lsp_request == Some(request_id) {
                        self.pending_lsp_request = None;
                        if items.is_empty() {
                            self.toast(&format!("No {}", kind), "INFO");
                        } else {
                            let loc = &items[0];
                            let _ = self.open_file(&loc.path);
                            self.buffer.cursor.line = loc.line.saturating_sub(1);
                            self.buffer.cursor.col = loc.column.saturating_sub(1);
                            if items.len() > 1 {
                                self.show_output = true;
                                for item in &items {
                                    self.push_output(
                                        &format!(
                                            "{}:{}:{}",
                                            item.path.display(),
                                            item.line,
                                            item.column
                                        ),
                                        OutputKind::Info,
                                    );
                                }
                            }
                        }
                    }
                }
                LspEvent::Symbols { request_id, items } => {
                    if self.pending_lsp_request == Some(request_id) {
                        self.pending_lsp_request = None;
                        self.overlay = Overlay::Outline {
                            symbols: items,
                            selected: 0,
                        };
                    }
                }
                LspEvent::WorkspaceEdit {
                    request_id,
                    kind,
                    plan,
                } => {
                    if request_id == 0 || self.pending_lsp_request == Some(request_id) {
                        self.pending_lsp_request = None;
                        self.prompt_workspace_edit(&kind, plan);
                    }
                }
                LspEvent::Formatted { request_id, edits } => {
                    if self.pending_lsp_request == Some(request_id) {
                        self.pending_lsp_request = None;
                        if edits.is_empty() {
                            self.toast("Already formatted", "INFO");
                        } else {
                            crate::workspace_edit::apply_edits_to_lines(
                                &mut self.buffer.lines,
                                &edits,
                            );
                            self.buffer.modified = true;
                            self.sync_active_tab();
                            self.toast("Formatted", "SUCCESS");
                        }
                    }
                }
                LspEvent::CodeActions {
                    request_id,
                    actions,
                } => {
                    if self.pending_lsp_request == Some(request_id) {
                        self.pending_lsp_request = None;
                        if actions.is_empty() {
                            self.toast("No code actions", "INFO");
                        } else {
                            self.pending_code_actions = actions.clone();
                            // Present as palette-like outline using first action confirm
                            let labels: Vec<_> = actions.iter().map(|a| a.title.clone()).collect();
                            self.toast(
                                &format!(
                                    "{} action(s). :codeaction <n> to apply (1-based)",
                                    labels.len()
                                ),
                                "INFO",
                            );
                            for (i, a) in actions.iter().enumerate() {
                                self.push_output(
                                    &format!("[{}] {} ({})", i + 1, a.title, a.kind),
                                    OutputKind::Info,
                                );
                            }
                            self.show_output = true;
                        }
                    }
                }
                LspEvent::SignatureHelp { request_id, label } => {
                    if self.pending_lsp_request == Some(request_id) {
                        self.pending_lsp_request = None;
                        if label.is_empty() {
                            self.toast("No signature help", "INFO");
                        } else {
                            self.toast(&label, "INFO");
                        }
                    }
                }
                LspEvent::Ready { .. } | LspEvent::Unavailable { .. } | LspEvent::Crashed => {}
                LspEvent::Error { message } => self.toast(&message, "ERROR"),
            }
        }
    }

    pub fn persist_session(&self) {
        let desk = self.cwd.join(".desk");
        let _ = fs::create_dir_all(&desk);
        let mut paths = Vec::new();
        for tab in &self.tabs {
            if let Some(p) = &tab.path {
                paths.push(format!(
                    "{{\"path\":\"{}\"}}",
                    p.display()
                        .to_string()
                        .replace('\\', "\\\\")
                        .replace('"', "\\\"")
                ));
            }
        }
        let body = format!(
            "{{\n  \"openDocuments\": [\n    {}\n  ]\n}}\n",
            paths.join(",\n    ")
        );
        let _ = fs::write(desk.join("session.json"), body);
    }

    pub fn jump_to_problem(&mut self, index: usize) {
        if let Some(diag) = self.problems.get(index).cloned() {
            let _ = self.open_file(&diag.path);
            self.buffer.cursor.line = diag.line.saturating_sub(1);
            self.buffer.cursor.col = diag.column.saturating_sub(1);
            self.problems_selected = index;
        }
    }

    pub fn next_problem(&mut self) {
        if self.problems.is_empty() {
            return;
        }
        self.problems_selected = (self.problems_selected + 1) % self.problems.len();
        self.jump_to_problem(self.problems_selected);
    }

    pub fn prev_problem(&mut self) {
        if self.problems.is_empty() {
            return;
        }
        if self.problems_selected == 0 {
            self.problems_selected = self.problems.len() - 1;
        } else {
            self.problems_selected -= 1;
        }
        self.jump_to_problem(self.problems_selected);
    }

    pub fn explorer_new_file(&mut self) {
        self.show_command_line = true;
        self.command_line = ":new ".to_string();
        self.toast("Enter new file name", "INFO");
    }

    pub fn explorer_new_dir(&mut self) {
        self.show_command_line = true;
        self.command_line = ":mkdir ".to_string();
        self.toast("Enter new folder name", "INFO");
    }

    pub fn explorer_rename(&mut self) {
        if let Some((path, _)) = self.explorer_flat.get(self.explorer_selected) {
            self.show_command_line = true;
            self.command_line = format!(":mv {} ", path.display());
        }
    }

    pub fn explorer_delete(&mut self) {
        if let Some((path, _)) = self.explorer_flat.get(self.explorer_selected) {
            self.overlay = Overlay::Confirm {
                title: "Delete".into(),
                message: format!("Delete {}?", path.display()),
                action: ConfirmAction::DeletePath,
            };
        }
    }

    pub fn apply_completion(&mut self, insert: &str) {
        for ch in insert.chars() {
            if ch == '\n' {
                self.buffer.insert_newline();
            } else {
                self.buffer.insert_char(ch);
            }
        }
        self.overlay.close();
        self.last_edit_time = Instant::now();
    }

    pub fn status_left(&self) -> String {
        let file = self
            .current_file
            .as_ref()
            .and_then(|p| p.file_name())
            .and_then(|n| n.to_str())
            .unwrap_or("Untitled");
        let dirty = if self.buffer.modified { " ●" } else { "" };
        format!("{}{}", file, dirty)
    }

    pub fn status_right(&self) -> String {
        let errs = self
            .problems
            .iter()
            .filter(|d| d.severity == "ERROR")
            .count();
        let warns = self
            .problems
            .iter()
            .filter(|d| d.severity == "WARNING")
            .count();
        format!(
            "{} | {} | {} | E:{} W:{} | {}",
            self.project_kind,
            self.git_branch,
            self.lsp.status,
            errs,
            warns,
            self.mode.name()
        )
    }
}

fn language_id(lang: Language) -> &'static str {
    match lang {
        Language::Java => "java",
        Language::Rust => "rust",
        Language::Python => "python",
        Language::Json => "json",
        _ => "plaintext",
    }
}

fn detect_git_branch(cwd: &Path) -> String {
    let output = Command::new("git")
        .args(["rev-parse", "--abbrev-ref", "HEAD"])
        .current_dir(cwd)
        .output();
    match output {
        Ok(o) if o.status.success() => {
            let s = String::from_utf8_lossy(&o.stdout).trim().to_string();
            if s.is_empty() {
                "git:?".into()
            } else {
                format!("git:{}", s)
            }
        }
        _ => "git:—".into(),
    }
}

fn detect_project_kind(cwd: &Path) -> String {
    if cwd.join("pom.xml").is_file() {
        "Maven".into()
    } else if cwd.join("build.gradle").is_file() || cwd.join("build.gradle.kts").is_file() {
        "Gradle".into()
    } else if cwd.join("src").is_dir() {
        "Java".into()
    } else {
        "Folder".into()
    }
}

pub fn collect_workspace_files_pub(root: &Path, limit: usize) -> Vec<PathBuf> {
    collect_workspace_files(root, limit)
}

fn collect_workspace_files(root: &Path, limit: usize) -> Vec<PathBuf> {
    let mut out = Vec::new();
    fn walk(dir: &Path, out: &mut Vec<PathBuf>, limit: usize, depth: usize) {
        if out.len() >= limit || depth > 10 {
            return;
        }
        let Ok(rd) = fs::read_dir(dir) else {
            return;
        };
        for entry in rd.flatten() {
            if out.len() >= limit {
                return;
            }
            let path = entry.path();
            let name = entry.file_name().to_string_lossy().to_string();
            if name.starts_with('.')
                || name == "target"
                || name == "node_modules"
                || name == "desk-out"
                || name == "build"
            {
                continue;
            }
            if path.is_dir() {
                walk(&path, out, limit, depth + 1);
            } else {
                out.push(path);
            }
        }
    }
    walk(root, &mut out, limit, 0);
    out
}

fn local_java_outline(lines: &[String]) -> Vec<overlay::OutlineSymbol> {
    let mut symbols = Vec::new();
    for (i, line) in lines.iter().enumerate() {
        let t = line.trim();
        if t.starts_with("class ")
            || t.starts_with("interface ")
            || t.starts_with("enum ")
            || t.starts_with("record ")
            || ((t.contains("void ") || t.contains("public ") || t.contains("private "))
                && t.contains('(')
                && t.contains(')'))
        {
            let name = t
                .split_whitespace()
                .nth(1)
                .unwrap_or(t)
                .trim_matches('{')
                .to_string();
            symbols.push(overlay::OutlineSymbol {
                name,
                kind: "local".into(),
                line: i + 1,
                column: 1,
            });
        }
    }
    symbols
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::input::handle_key_event;
    use crossterm::event::{KeyCode, KeyEvent, KeyModifiers};
    use ratatui::{Terminal, backend::TestBackend};
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
        assert!(app.buffer.modified);
        // :e refuses to discard unsaved edits
        app.command_line = format!(":e {}", file.display());
        app.execute_command_line();
        assert_eq!(app.buffer.text(), "changed");
        // :e! reloads from disk
        app.command_line = format!(":e! {}", file.display());
        app.execute_command_line();
        assert_eq!(app.buffer.text(), "class Demo {}");
    }

    #[test]
    fn dirty_close_requests_confirmation() {
        let mut app = test_app();
        app.buffer.set_text("unsaved");
        assert!(app.buffer.modified);
        app.request_close_current_file();
        assert!(matches!(app.overlay, Overlay::Confirm { .. }));
        assert!(app.buffer.modified);
        app.confirm_no();
        assert!(matches!(app.overlay, Overlay::None));
        assert!(app.buffer.modified);
    }

    #[test]
    fn save_all_reports_untitled() {
        let mut app = test_app();
        app.buffer.set_text("untitled content");
        assert!(app.buffer.modified);
        assert!(app.current_file.is_none());
        app.save_all_tabs().unwrap();
        assert!(app.buffer.modified);
    }

    #[test]
    fn disk_mtime_conflict_detected() {
        let dir = tempfile::tempdir().unwrap();
        let file = dir.path().join("Conflict.java");
        fs::write(&file, "class A {}").unwrap();
        let mut app = test_app();
        app.open_file(&file).unwrap();
        assert!(!app.buffer.disk_changed_externally());
        // External change
        std::thread::sleep(std::time::Duration::from_millis(20));
        fs::write(&file, "class B {}").unwrap();
        assert!(app.buffer.disk_changed_externally());
    }

    #[test]
    fn external_change_prompts_reload_conflict() {
        let dir = tempfile::tempdir().unwrap();
        let file = dir.path().join("Ext.java");
        fs::write(&file, "class A {}").unwrap();
        let mut app = test_app();
        app.open_file(&file).unwrap();
        std::thread::sleep(std::time::Duration::from_millis(20));
        fs::write(&file, "class B {}").unwrap();
        app.check_external_file_state();
        assert!(matches!(
            app.overlay,
            Overlay::Confirm {
                action: ConfirmAction::ReloadConflict,
                ..
            }
        ));
        app.confirm_no();
        assert!(matches!(app.overlay, Overlay::None));
        // Same external mtime should not re-prompt
        app.check_external_file_state();
        assert!(matches!(app.overlay, Overlay::None));
    }

    #[test]
    fn deleted_file_prompts_disk_deleted() {
        let dir = tempfile::tempdir().unwrap();
        let file = dir.path().join("Gone.java");
        fs::write(&file, "class G {}").unwrap();
        let mut app = test_app();
        app.open_file(&file).unwrap();
        fs::remove_file(&file).unwrap();
        app.check_external_file_state();
        assert!(matches!(
            app.overlay,
            Overlay::Confirm {
                action: ConfirmAction::DiskDeleted,
                ..
            }
        ));
        app.confirm_no();
        assert!(app.buffer.text().contains("class G"));
        app.check_external_file_state();
        assert!(matches!(app.overlay, Overlay::None));
    }

    #[test]
    fn switching_tabs_preserves_unsaved_edits() {
        let dir = tempfile::tempdir().unwrap();
        let a = dir.path().join("A.java");
        let b = dir.path().join("B.java");
        fs::write(&a, "class A {}").unwrap();
        fs::write(&b, "class B {}").unwrap();
        let mut app = test_app();
        app.open_file(&a).unwrap();
        app.buffer.set_text("class A { int x; }");
        assert!(app.buffer.modified);
        app.open_file(&b).unwrap();
        assert_eq!(app.buffer.text().trim(), "class B {}");
        app.prev_tab();
        assert!(app.buffer.text().contains("int x"));
        assert!(app.buffer.modified);
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
        assert!(
            app.terminal_output
                .iter()
                .any(|l| l.contains(cwd_hint.as_ref()) || l.contains("out.txt"))
        );

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
