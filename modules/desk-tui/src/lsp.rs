//! Async Language Server Protocol client for the TUI.
//!
//! Speaks Content-Length JSON-RPC with an external server (typically JDT LS).
//! Never blocks the UI thread: requests go out on a worker and results return
//! via a channel polled in {@code App::tick}.

use crossbeam_channel::{Receiver, Sender, unbounded};
use serde_json::{Value, json};
use std::collections::HashMap;
use std::io::{BufRead, BufReader, Write};
use std::path::{Path, PathBuf};
use std::process::{Child, Command, Stdio};
use std::sync::{Arc, Mutex};
use std::thread;
use std::time::Duration;

use crate::overlay::{CompletionEntry, OutlineSymbol};
use crate::workspace_edit::{WorkspaceEditPlan, parse_workspace_edit};

#[derive(Debug, Clone)]
pub struct LspDiagnostic {
    pub path: PathBuf,
    pub line: usize,
    pub column: usize,
    pub severity: String,
    pub message: String,
    pub source: String,
}

#[derive(Debug, Clone)]
pub struct LspLocation {
    pub path: PathBuf,
    pub line: usize,
    pub column: usize,
}

#[derive(Debug, Clone)]
pub enum LspEvent {
    Ready {
        server: String,
    },
    Unavailable {
        reason: String,
    },
    Diagnostics {
        path: PathBuf,
        items: Vec<LspDiagnostic>,
    },
    Completions {
        request_id: u64,
        items: Vec<CompletionEntry>,
    },
    Hover {
        request_id: u64,
        contents: String,
    },
    Locations {
        request_id: u64,
        kind: String,
        items: Vec<LspLocation>,
    },
    Symbols {
        request_id: u64,
        items: Vec<OutlineSymbol>,
    },
    WorkspaceEdit {
        request_id: u64,
        kind: String,
        plan: WorkspaceEditPlan,
    },
    Formatted {
        request_id: u64,
        edits: Vec<crate::workspace_edit::TextEdit>,
    },
    CodeActions {
        request_id: u64,
        actions: Vec<CodeActionEntry>,
    },
    SignatureHelp {
        request_id: u64,
        label: String,
    },
    Error {
        message: String,
    },
    Crashed,
}

#[derive(Debug, Clone)]
pub struct CodeActionEntry {
    pub title: String,
    pub kind: String,
    pub plan: Option<WorkspaceEditPlan>,
}

#[derive(Debug)]
#[allow(dead_code)]
enum LspCommand {
    DidOpen {
        path: PathBuf,
        language: String,
        text: String,
    },
    DidChange {
        path: PathBuf,
        text: String,
    },
    DidSave {
        path: PathBuf,
        text: String,
    },
    DidClose {
        path: PathBuf,
    },
    Complete {
        request_id: u64,
        path: PathBuf,
        line: usize,
        character: usize,
    },
    Hover {
        request_id: u64,
        path: PathBuf,
        line: usize,
        character: usize,
    },
    Definition {
        request_id: u64,
        path: PathBuf,
        line: usize,
        character: usize,
    },
    References {
        request_id: u64,
        path: PathBuf,
        line: usize,
        character: usize,
    },
    DocumentSymbol {
        request_id: u64,
        path: PathBuf,
    },
    Format {
        request_id: u64,
        path: PathBuf,
    },
    Rename {
        request_id: u64,
        path: PathBuf,
        line: usize,
        character: usize,
        new_name: String,
    },
    CodeAction {
        request_id: u64,
        path: PathBuf,
        line: usize,
        character: usize,
    },
    SignatureHelp {
        request_id: u64,
        path: PathBuf,
        line: usize,
        character: usize,
    },
    Cancel {
        id: u64,
    },
    Shutdown,
}

pub struct LspBridge {
    cmd_tx: Option<Sender<LspCommand>>,
    pub events: Receiver<LspEvent>,
    next_request: u64,
    pub available: bool,
    pub status: String,
}

impl LspBridge {
    pub fn start(workspace: &Path) -> Self {
        let (event_tx, event_rx) = unbounded();
        let server = discover_server();
        let Some(server) = server else {
            let _ = event_tx.send(LspEvent::Unavailable {
                reason: "No JDT LS found. Set DESK_JAVA_LS or install jdtls on PATH.".into(),
            });
            return Self {
                cmd_tx: None,
                events: event_rx,
                next_request: 1,
                available: false,
                status: "LSP: unavailable".into(),
            };
        };

        let (cmd_tx, cmd_rx) = unbounded();
        let workspace = workspace.to_path_buf();
        let server_clone = server.clone();
        thread::spawn(move || {
            if let Err(e) = run_session(workspace, server_clone, cmd_rx, event_tx.clone()) {
                let _ = event_tx.send(LspEvent::Error {
                    message: e.to_string(),
                });
                let _ = event_tx.send(LspEvent::Crashed);
            }
        });

        Self {
            cmd_tx: Some(cmd_tx),
            events: event_rx,
            next_request: 1,
            available: true,
            status: format!("LSP: starting ({})", server.display()),
        }
    }

    pub fn poll_events(&mut self) -> Vec<LspEvent> {
        let mut out = Vec::new();
        while let Ok(ev) = self.events.try_recv() {
            match &ev {
                LspEvent::Ready { server } => {
                    self.available = true;
                    self.status = format!("LSP: {}", server);
                }
                LspEvent::Unavailable { reason } => {
                    self.available = false;
                    self.status = format!("LSP: {}", reason);
                }
                LspEvent::Crashed => {
                    self.available = false;
                    self.status = "LSP: crashed".into();
                }
                _ => {}
            }
            out.push(ev);
        }
        out
    }

    pub fn did_open(&self, path: &Path, language: &str, text: &str) {
        self.send(LspCommand::DidOpen {
            path: path.to_path_buf(),
            language: language.to_string(),
            text: text.to_string(),
        });
    }

    pub fn did_change(&self, path: &Path, text: &str) {
        self.send(LspCommand::DidChange {
            path: path.to_path_buf(),
            text: text.to_string(),
        });
    }

    pub fn request_complete(&mut self, path: &Path, line: usize, character: usize) -> Option<u64> {
        self.cmd_tx.as_ref()?;
        let id = self.next_request;
        self.next_request += 1;
        self.send(LspCommand::Complete {
            request_id: id,
            path: path.to_path_buf(),
            line,
            character,
        });
        Some(id)
    }

    pub fn request_hover(&mut self, path: &Path, line: usize, character: usize) -> Option<u64> {
        self.cmd_tx.as_ref()?;
        let id = self.next_request;
        self.next_request += 1;
        self.send(LspCommand::Hover {
            request_id: id,
            path: path.to_path_buf(),
            line,
            character,
        });
        Some(id)
    }

    pub fn request_definition(
        &mut self,
        path: &Path,
        line: usize,
        character: usize,
    ) -> Option<u64> {
        self.cmd_tx.as_ref()?;
        let id = self.next_request;
        self.next_request += 1;
        self.send(LspCommand::Definition {
            request_id: id,
            path: path.to_path_buf(),
            line,
            character,
        });
        Some(id)
    }

    pub fn request_references(
        &mut self,
        path: &Path,
        line: usize,
        character: usize,
    ) -> Option<u64> {
        self.cmd_tx.as_ref()?;
        let id = self.next_request;
        self.next_request += 1;
        self.send(LspCommand::References {
            request_id: id,
            path: path.to_path_buf(),
            line,
            character,
        });
        Some(id)
    }

    pub fn request_symbols(&mut self, path: &Path) -> Option<u64> {
        self.cmd_tx.as_ref()?;
        let id = self.next_request;
        self.next_request += 1;
        self.send(LspCommand::DocumentSymbol {
            request_id: id,
            path: path.to_path_buf(),
        });
        Some(id)
    }

    pub fn request_format(&mut self, path: &Path) -> Option<u64> {
        self.cmd_tx.as_ref()?;
        let id = self.next_request;
        self.next_request += 1;
        self.send(LspCommand::Format {
            request_id: id,
            path: path.to_path_buf(),
        });
        Some(id)
    }

    pub fn request_rename(
        &mut self,
        path: &Path,
        line: usize,
        character: usize,
        new_name: &str,
    ) -> Option<u64> {
        self.cmd_tx.as_ref()?;
        let id = self.next_request;
        self.next_request += 1;
        self.send(LspCommand::Rename {
            request_id: id,
            path: path.to_path_buf(),
            line,
            character,
            new_name: new_name.to_string(),
        });
        Some(id)
    }

    pub fn request_code_action(
        &mut self,
        path: &Path,
        line: usize,
        character: usize,
    ) -> Option<u64> {
        self.cmd_tx.as_ref()?;
        let id = self.next_request;
        self.next_request += 1;
        self.send(LspCommand::CodeAction {
            request_id: id,
            path: path.to_path_buf(),
            line,
            character,
        });
        Some(id)
    }

    pub fn request_signature_help(
        &mut self,
        path: &Path,
        line: usize,
        character: usize,
    ) -> Option<u64> {
        self.cmd_tx.as_ref()?;
        let id = self.next_request;
        self.next_request += 1;
        self.send(LspCommand::SignatureHelp {
            request_id: id,
            path: path.to_path_buf(),
            line,
            character,
        });
        Some(id)
    }

    pub fn did_close(&self, path: &Path) {
        self.send(LspCommand::DidClose {
            path: path.to_path_buf(),
        });
    }

    pub fn did_save(&self, path: &Path, text: &str) {
        self.send(LspCommand::DidSave {
            path: path.to_path_buf(),
            text: text.to_string(),
        });
    }

    pub fn cancel(&self, id: u64) {
        self.send(LspCommand::Cancel { id });
    }

    pub fn shutdown(&self) {
        self.send(LspCommand::Shutdown);
    }

    fn send(&self, cmd: LspCommand) {
        if let Some(tx) = &self.cmd_tx {
            let _ = tx.send(cmd);
        }
    }
}

/// Pinned JDT LS version, kept in sync with `JdtLsProvisioner.VERSION`.
const JDT_LS_VERSION: &str = "1.57.0";

/// Executable names for the JDT LS launcher on the current OS.
fn jdtls_exe_names() -> Vec<&'static str> {
    if cfg!(windows) {
        vec!["jdtls.exe", "jdtls.bat"]
    } else {
        vec!["jdtls"]
    }
}

/// PATH list separators for the current OS.
fn path_separators() -> &'static [char] {
    if cfg!(windows) { &[';'] } else { &[':'] }
}

fn discover_server() -> Option<PathBuf> {
    // 1. Explicit override via env var.
    if let Ok(env) = std::env::var("DESK_JAVA_LS") {
        let p = PathBuf::from(env.trim());
        if p.is_file() {
            return Some(p);
        }
    }

    let home = dirs_next_home();

    // 2. Locations provisioned by desk itself (Java-side JdtLsProvisioner) and
    //    common manual install locations.
    let mut candidate_dirs: Vec<PathBuf> = Vec::new();
    if let Some(h) = &home {
        // Java-side provisioner default: ~/.desk/jdtls/<VERSION>/bin/{jdtls,jdtls.exe,jdtls.bat}
        candidate_dirs.push(h.join(format!(".desk/jdtls/{}/bin", JDT_LS_VERSION)));
        candidate_dirs.push(h.join(".desk/jdt-language-server/bin"));
        candidate_dirs.push(h.join(".local/share/jdtls/bin"));
    }
    // System-wide locations (Unix only).
    if !cfg!(windows) {
        candidate_dirs.push(PathBuf::from("/usr/bin"));
        candidate_dirs.push(PathBuf::from("/usr/local/bin"));
    }

    for dir in &candidate_dirs {
        for name in jdtls_exe_names() {
            let p = dir.join(name);
            if p.is_file() {
                return Some(p);
            }
        }
    }

    // 3. Search PATH using the OS-specific separator.
    if let Ok(path) = std::env::var("PATH") {
        for entry in path.split(path_separators()) {
            if entry.is_empty() {
                continue;
            }
            let dir = PathBuf::from(entry);
            for name in jdtls_exe_names() {
                let p = dir.join(name);
                if p.is_file() {
                    return Some(p);
                }
            }
        }
    }

    None
}

fn dirs_next_home() -> Option<PathBuf> {
    dirs::home_dir()
}

fn run_session(
    workspace: PathBuf,
    server: PathBuf,
    cmd_rx: Receiver<LspCommand>,
    event_tx: Sender<LspEvent>,
) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
    let data = workspace.join(".desk").join("jdtls-workspace");
    let _ = std::fs::create_dir_all(&data);
    let mut child = Command::new(&server)
        .arg("-data")
        .arg(&data)
        .current_dir(&workspace)
        .stdin(Stdio::piped())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .spawn()?;

    // Drain stderr so it cannot block or corrupt stdout framing.
    if let Some(stderr) = child.stderr.take() {
        thread::spawn(move || {
            let mut reader = BufReader::new(stderr);
            let mut line = String::new();
            while reader.read_line(&mut line).unwrap_or(0) > 0 {
                if std::env::var("DESK_LSP_LOG").ok().as_deref() == Some("1") {
                    eprintln!("[jdtls] {}", line.trim_end());
                }
                line.clear();
            }
        });
    }

    let stdin = Arc::new(Mutex::new(child.stdin.take().ok_or("no stdin")?));
    let stdout = child.stdout.take().ok_or("no stdout")?;
    let reader = Arc::new(Mutex::new(BufReader::new(stdout)));
    let pending: Arc<Mutex<HashMap<u64, Pending>>> = Arc::new(Mutex::new(HashMap::new()));
    let versions: Arc<Mutex<HashMap<PathBuf, i64>>> = Arc::new(Mutex::new(HashMap::new()));

    // Reader thread for responses/notifications
    {
        let reader = Arc::clone(&reader);
        let event_tx = event_tx.clone();
        let pending = Arc::clone(&pending);
        let stdin_r = Arc::clone(&stdin);
        thread::spawn(move || {
            loop {
                let msg = match read_message(&mut *reader.lock().unwrap()) {
                    Ok(Some(v)) => v,
                    Ok(None) => {
                        let _ = event_tx.send(LspEvent::Crashed);
                        break;
                    }
                    Err(_) => {
                        let _ = event_tx.send(LspEvent::Crashed);
                        break;
                    }
                };
                dispatch_message(msg, &pending, &event_tx, &stdin_r);
            }
        });
    }

    // initialize
    let init_id = 1u64;
    {
        let mut stdin_g = stdin.lock().unwrap();
        write_message(
            &mut *stdin_g,
            &json!({
                "jsonrpc": "2.0",
                "id": init_id,
                "method": "initialize",
                "params": {
                    "processId": std::process::id(),
                    "rootUri": path_to_uri(&workspace),
                    "capabilities": {
                        "textDocument": {
                            "synchronization": { "didSave": true },
                            "completion": { "completionItem": { "snippetSupport": false } },
                            "hover": { "contentFormat": ["plaintext", "markdown"] },
                            "definition": {},
                            "references": {},
                            "documentSymbol": {},
                            "formatting": {},
                            "rename": { "prepareSupport": false },
                            "codeAction": {},
                            "signatureHelp": {},
                            "publishDiagnostics": {}
                        },
                        "workspace": {
                            "workspaceFolders": true,
                            "applyEdit": true
                        }
                    },
                    "workspaceFolders": [{
                        "uri": path_to_uri(&workspace),
                        "name": workspace.file_name().and_then(|n| n.to_str()).unwrap_or("workspace")
                    }]
                }
            }),
        )?;
    }
    // Wait briefly for initialize result
    thread::sleep(Duration::from_millis(500));
    {
        let mut stdin_g = stdin.lock().unwrap();
        write_message(
            &mut *stdin_g,
            &json!({
                "jsonrpc": "2.0",
                "method": "initialized",
                "params": {}
            }),
        )?;
    }
    let _ = event_tx.send(LspEvent::Ready {
        server: server.display().to_string(),
    });

    let mut rpc_id: u64 = 10;
    while let Ok(cmd) = cmd_rx.recv() {
        let mut stdin_g = stdin.lock().unwrap();
        let stdin = &mut *stdin_g;
        match cmd {
            LspCommand::Shutdown => {
                let _ = write_message(
                    stdin,
                    &json!({"jsonrpc":"2.0","id": rpc_id, "method":"shutdown","params": null}),
                );
                let _ = write_message(
                    stdin,
                    &json!({"jsonrpc":"2.0","method":"exit","params": null}),
                );
                break;
            }
            LspCommand::DidOpen {
                path,
                language,
                text,
            } => {
                let mut vers = versions.lock().unwrap();
                let v = vers.entry(path.clone()).or_insert(0);
                *v += 1;
                let version = *v;
                drop(vers);
                write_message(
                    stdin,
                    &json!({
                        "jsonrpc": "2.0",
                        "method": "textDocument/didOpen",
                        "params": {
                            "textDocument": {
                                "uri": path_to_uri(&path),
                                "languageId": language,
                                "version": version,
                                "text": text
                            }
                        }
                    }),
                )?;
            }
            LspCommand::DidChange { path, text } => {
                let mut vers = versions.lock().unwrap();
                let v = vers.entry(path.clone()).or_insert(0);
                *v += 1;
                let version = *v;
                drop(vers);
                write_message(
                    stdin,
                    &json!({
                        "jsonrpc": "2.0",
                        "method": "textDocument/didChange",
                        "params": {
                            "textDocument": { "uri": path_to_uri(&path), "version": version },
                            "contentChanges": [{ "text": text }]
                        }
                    }),
                )?;
            }
            LspCommand::DidSave { path, text } => {
                write_message(
                    stdin,
                    &json!({
                        "jsonrpc": "2.0",
                        "method": "textDocument/didSave",
                        "params": {
                            "textDocument": { "uri": path_to_uri(&path) },
                            "text": text
                        }
                    }),
                )?;
            }
            LspCommand::DidClose { path } => {
                write_message(
                    stdin,
                    &json!({
                        "jsonrpc": "2.0",
                        "method": "textDocument/didClose",
                        "params": { "textDocument": { "uri": path_to_uri(&path) } }
                    }),
                )?;
            }
            LspCommand::Complete {
                request_id,
                path,
                line,
                character,
            } => {
                rpc_id += 1;
                pending
                    .lock()
                    .unwrap()
                    .insert(rpc_id, Pending::Complete { request_id });
                write_message(
                    stdin,
                    &json!({
                        "jsonrpc": "2.0",
                        "id": rpc_id,
                        "method": "textDocument/completion",
                        "params": position_params(&path, line, character)
                    }),
                )?;
            }
            LspCommand::Hover {
                request_id,
                path,
                line,
                character,
            } => {
                rpc_id += 1;
                pending
                    .lock()
                    .unwrap()
                    .insert(rpc_id, Pending::Hover { request_id });
                write_message(
                    stdin,
                    &json!({
                        "jsonrpc": "2.0",
                        "id": rpc_id,
                        "method": "textDocument/hover",
                        "params": position_params(&path, line, character)
                    }),
                )?;
            }
            LspCommand::Definition {
                request_id,
                path,
                line,
                character,
            } => {
                rpc_id += 1;
                pending.lock().unwrap().insert(
                    rpc_id,
                    Pending::Locations {
                        request_id,
                        kind: "definition".into(),
                    },
                );
                write_message(
                    stdin,
                    &json!({
                        "jsonrpc": "2.0",
                        "id": rpc_id,
                        "method": "textDocument/definition",
                        "params": position_params(&path, line, character)
                    }),
                )?;
            }
            LspCommand::References {
                request_id,
                path,
                line,
                character,
            } => {
                rpc_id += 1;
                pending.lock().unwrap().insert(
                    rpc_id,
                    Pending::Locations {
                        request_id,
                        kind: "references".into(),
                    },
                );
                let mut params = position_params(&path, line, character);
                params["context"] = json!({ "includeDeclaration": true });
                write_message(
                    stdin,
                    &json!({
                        "jsonrpc": "2.0",
                        "id": rpc_id,
                        "method": "textDocument/references",
                        "params": params
                    }),
                )?;
            }
            LspCommand::DocumentSymbol { request_id, path } => {
                rpc_id += 1;
                pending
                    .lock()
                    .unwrap()
                    .insert(rpc_id, Pending::Symbols { request_id });
                write_message(
                    stdin,
                    &json!({
                        "jsonrpc": "2.0",
                        "id": rpc_id,
                        "method": "textDocument/documentSymbol",
                        "params": { "textDocument": { "uri": path_to_uri(&path) } }
                    }),
                )?;
            }
            LspCommand::Format { request_id, path } => {
                rpc_id += 1;
                pending
                    .lock()
                    .unwrap()
                    .insert(rpc_id, Pending::Format { request_id });
                write_message(
                    stdin,
                    &json!({
                        "jsonrpc": "2.0",
                        "id": rpc_id,
                        "method": "textDocument/formatting",
                        "params": {
                            "textDocument": { "uri": path_to_uri(&path) },
                            "options": { "tabSize": 4, "insertSpaces": true }
                        }
                    }),
                )?;
            }
            LspCommand::Rename {
                request_id,
                path,
                line,
                character,
                new_name,
            } => {
                rpc_id += 1;
                pending.lock().unwrap().insert(
                    rpc_id,
                    Pending::WorkspaceEdit {
                        request_id,
                        kind: "rename".into(),
                    },
                );
                write_message(
                    stdin,
                    &json!({
                        "jsonrpc": "2.0",
                        "id": rpc_id,
                        "method": "textDocument/rename",
                        "params": {
                            "textDocument": { "uri": path_to_uri(&path) },
                            "position": { "line": line, "character": character },
                            "newName": new_name
                        }
                    }),
                )?;
            }
            LspCommand::CodeAction {
                request_id,
                path,
                line,
                character,
            } => {
                rpc_id += 1;
                pending
                    .lock()
                    .unwrap()
                    .insert(rpc_id, Pending::CodeAction { request_id });
                write_message(
                    stdin,
                    &json!({
                        "jsonrpc": "2.0",
                        "id": rpc_id,
                        "method": "textDocument/codeAction",
                        "params": {
                            "textDocument": { "uri": path_to_uri(&path) },
                            "range": {
                                "start": { "line": line, "character": character },
                                "end": { "line": line, "character": character }
                            },
                            "context": { "diagnostics": [] }
                        }
                    }),
                )?;
            }
            LspCommand::SignatureHelp {
                request_id,
                path,
                line,
                character,
            } => {
                rpc_id += 1;
                pending
                    .lock()
                    .unwrap()
                    .insert(rpc_id, Pending::SignatureHelp { request_id });
                write_message(
                    stdin,
                    &json!({
                        "jsonrpc": "2.0",
                        "id": rpc_id,
                        "method": "textDocument/signatureHelp",
                        "params": position_params(&path, line, character)
                    }),
                )?;
            }
            LspCommand::Cancel { id } => {
                write_message(
                    stdin,
                    &json!({
                        "jsonrpc": "2.0",
                        "method": "$/cancelRequest",
                        "params": { "id": id }
                    }),
                )?;
            }
        }
        if !child_alive(&mut child) {
            let _ = event_tx.send(LspEvent::Crashed);
            break;
        }
    }

    let _ = child.kill();
    Ok(())
}

enum Pending {
    Complete { request_id: u64 },
    Hover { request_id: u64 },
    Locations { request_id: u64, kind: String },
    Symbols { request_id: u64 },
    Format { request_id: u64 },
    WorkspaceEdit { request_id: u64, kind: String },
    CodeAction { request_id: u64 },
    SignatureHelp { request_id: u64 },
}

fn child_alive(child: &mut Child) -> bool {
    matches!(child.try_wait(), Ok(None))
}

fn position_params(path: &Path, line: usize, character: usize) -> Value {
    json!({
        "textDocument": { "uri": path_to_uri(path) },
        "position": { "line": line, "character": character }
    })
}

fn path_to_uri(path: &Path) -> String {
    let abs = path.canonicalize().unwrap_or_else(|_| path.to_path_buf());
    url::Url::from_file_path(abs)
        .map(|u| u.to_string())
        .unwrap_or_else(|_| format!("file://{}", path.display()))
}

fn uri_to_path(uri: &str) -> PathBuf {
    url::Url::parse(uri)
        .ok()
        .and_then(|u| u.to_file_path().ok())
        .unwrap_or_else(|| PathBuf::from(uri.trim_start_matches("file://")))
}

fn write_message(stdin: &mut impl Write, value: &Value) -> std::io::Result<()> {
    let body = serde_json::to_vec(value)?;
    write!(stdin, "Content-Length: {}\r\n\r\n", body.len())?;
    stdin.write_all(&body)?;
    stdin.flush()?;
    Ok(())
}

fn read_message(
    reader: &mut impl BufRead,
) -> Result<Option<Value>, Box<dyn std::error::Error + Send + Sync>> {
    let mut content_length = None;
    loop {
        let mut line = String::new();
        let n = reader.read_line(&mut line)?;
        if n == 0 {
            return Ok(None);
        }
        let trimmed = line.trim_end_matches(['\r', '\n']);
        if trimmed.is_empty() {
            break;
        }
        if let Some(rest) = trimmed.strip_prefix("Content-Length:") {
            content_length = Some(rest.trim().parse::<usize>()?);
        }
    }
    let len = content_length.ok_or("missing Content-Length")?;
    let mut buf = vec![0u8; len];
    reader.read_exact(&mut buf)?;
    let value: Value = serde_json::from_slice(&buf)?;
    Ok(Some(value))
}

fn dispatch_message(
    msg: Value,
    pending: &Arc<Mutex<HashMap<u64, Pending>>>,
    event_tx: &Sender<LspEvent>,
    stdin: &Arc<Mutex<std::process::ChildStdin>>,
) {
    if let Some(method) = msg.get("method").and_then(|m| m.as_str()) {
        if method == "textDocument/publishDiagnostics" {
            if let Some(params) = msg.get("params") {
                let uri = params.get("uri").and_then(|u| u.as_str()).unwrap_or("");
                let path = uri_to_path(uri);
                let mut items = Vec::new();
                if let Some(arr) = params.get("diagnostics").and_then(|d| d.as_array()) {
                    for d in arr {
                        let sev = match d.get("severity").and_then(|s| s.as_u64()).unwrap_or(1) {
                            1 => "ERROR",
                            2 => "WARNING",
                            3 => "INFO",
                            _ => "HINT",
                        };
                        let start = d.pointer("/range/start").cloned().unwrap_or(json!({}));
                        items.push(LspDiagnostic {
                            path: path.clone(),
                            line: start.get("line").and_then(|l| l.as_u64()).unwrap_or(0) as usize
                                + 1,
                            column: start.get("character").and_then(|c| c.as_u64()).unwrap_or(0)
                                as usize
                                + 1,
                            severity: sev.into(),
                            message: d
                                .get("message")
                                .and_then(|m| m.as_str())
                                .unwrap_or("")
                                .into(),
                            source: d
                                .get("source")
                                .and_then(|s| s.as_str())
                                .unwrap_or("lsp")
                                .into(),
                        });
                    }
                }
                let _ = event_tx.send(LspEvent::Diagnostics { path, items });
            }
            return;
        }
        if method == "workspace/applyEdit" {
            if let Some(id) = msg.get("id").cloned() {
                let edit = msg.pointer("/params/edit").cloned().unwrap_or(Value::Null);
                let plan = parse_workspace_edit(&edit);
                let _ = event_tx.send(LspEvent::WorkspaceEdit {
                    request_id: 0,
                    kind: "applyEdit".into(),
                    plan,
                });
                // Acknowledge without applying automatically — UI confirms separately.
                if let Ok(mut s) = stdin.lock() {
                    let _ = write_message(
                        &mut *s,
                        &json!({
                            "jsonrpc": "2.0",
                            "id": id,
                            "result": { "applied": false, "failureReason": "awaiting user confirmation in Desk" }
                        }),
                    );
                }
            }
            return;
        }
        return;
    }

    let id = msg.get("id").and_then(|i| i.as_u64());
    let Some(id) = id else {
        return;
    };
    let pending_kind = pending.lock().unwrap().remove(&id);
    let Some(pending_kind) = pending_kind else {
        return;
    };
    if msg.get("error").is_some() {
        let message = msg
            .pointer("/error/message")
            .and_then(|m| m.as_str())
            .unwrap_or("LSP error")
            .to_string();
        let _ = event_tx.send(LspEvent::Error { message });
        return;
    }
    let result = msg.get("result").cloned().unwrap_or(Value::Null);
    match pending_kind {
        Pending::Complete { request_id } => {
            let items = parse_completions(&result);
            let _ = event_tx.send(LspEvent::Completions { request_id, items });
        }
        Pending::Hover { request_id } => {
            let contents = parse_hover(&result);
            let _ = event_tx.send(LspEvent::Hover {
                request_id,
                contents,
            });
        }
        Pending::Locations { request_id, kind } => {
            let items = parse_locations(&result);
            let _ = event_tx.send(LspEvent::Locations {
                request_id,
                kind,
                items,
            });
        }
        Pending::Symbols { request_id } => {
            let items = parse_symbols(&result);
            let _ = event_tx.send(LspEvent::Symbols { request_id, items });
        }
        Pending::Format { request_id } => {
            let edits = parse_text_edits(&result);
            let _ = event_tx.send(LspEvent::Formatted { request_id, edits });
        }
        Pending::WorkspaceEdit { request_id, kind } => {
            let plan = parse_workspace_edit(&result);
            let _ = event_tx.send(LspEvent::WorkspaceEdit {
                request_id,
                kind,
                plan,
            });
        }
        Pending::CodeAction { request_id } => {
            let actions = parse_code_actions(&result);
            let _ = event_tx.send(LspEvent::CodeActions {
                request_id,
                actions,
            });
        }
        Pending::SignatureHelp { request_id } => {
            let label = parse_signature_help(&result);
            let _ = event_tx.send(LspEvent::SignatureHelp { request_id, label });
        }
    }
}

fn parse_text_edits(result: &Value) -> Vec<crate::workspace_edit::TextEdit> {
    let Some(arr) = result.as_array() else {
        return Vec::new();
    };
    arr.iter()
        .filter_map(|e| {
            let start = e.pointer("/range/start")?;
            let end = e.pointer("/range/end")?;
            Some(crate::workspace_edit::TextEdit {
                start_line: start.get("line")?.as_u64()? as usize,
                start_col: start.get("character")?.as_u64()? as usize,
                end_line: end.get("line")?.as_u64()? as usize,
                end_col: end.get("character")?.as_u64()? as usize,
                new_text: e.get("newText")?.as_str()?.to_string(),
            })
        })
        .collect()
}

fn parse_code_actions(result: &Value) -> Vec<CodeActionEntry> {
    let Some(arr) = result.as_array() else {
        return Vec::new();
    };
    arr.iter()
        .filter_map(|item| {
            let title = item.get("title")?.as_str()?.to_string();
            let kind = item
                .get("kind")
                .and_then(|k| k.as_str())
                .unwrap_or("")
                .to_string();
            let plan = item
                .get("edit")
                .map(parse_workspace_edit)
                .filter(|p| !p.is_empty());
            Some(CodeActionEntry { title, kind, plan })
        })
        .collect()
}

fn parse_signature_help(result: &Value) -> String {
    if result.is_null() {
        return String::new();
    }
    if let Some(sigs) = result.get("signatures").and_then(|s| s.as_array()) {
        if let Some(first) = sigs.first() {
            return first
                .get("label")
                .and_then(|l| l.as_str())
                .unwrap_or("")
                .to_string();
        }
    }
    String::new()
}

fn parse_completions(result: &Value) -> Vec<CompletionEntry> {
    let arr = if let Some(a) = result.as_array() {
        a.clone()
    } else if let Some(a) = result.get("items").and_then(|i| i.as_array()) {
        a.clone()
    } else {
        return Vec::new();
    };
    arr.iter()
        .filter_map(|item| {
            let label = item.get("label")?.as_str()?.to_string();
            let detail = item
                .get("detail")
                .and_then(|d| d.as_str())
                .unwrap_or("")
                .to_string();
            let insert = item
                .get("insertText")
                .and_then(|d| d.as_str())
                .unwrap_or(&label)
                .to_string();
            Some(CompletionEntry {
                label,
                detail,
                insert,
            })
        })
        .collect()
}

fn parse_hover(result: &Value) -> String {
    if result.is_null() {
        return String::new();
    }
    if let Some(s) = result.get("contents").and_then(|c| c.as_str()) {
        return s.to_string();
    }
    if let Some(v) = result.pointer("/contents/value").and_then(|v| v.as_str()) {
        return v.to_string();
    }
    if let Some(arr) = result.get("contents").and_then(|c| c.as_array()) {
        if let Some(first) = arr.first() {
            if let Some(v) = first.get("value").and_then(|v| v.as_str()) {
                return v.to_string();
            }
            if let Some(s) = first.as_str() {
                return s.to_string();
            }
        }
    }
    String::new()
}

fn parse_locations(result: &Value) -> Vec<LspLocation> {
    let arr = if let Some(a) = result.as_array() {
        a.clone()
    } else if result.is_object() {
        vec![result.clone()]
    } else {
        return Vec::new();
    };
    arr.iter()
        .filter_map(|item| {
            let uri = item
                .get("uri")
                .or_else(|| item.get("targetUri"))
                .and_then(|u| u.as_str())?;
            let start = item
                .pointer("/range/start")
                .or_else(|| item.pointer("/targetSelectionRange/start"))?;
            Some(LspLocation {
                path: uri_to_path(uri),
                line: start.get("line").and_then(|l| l.as_u64()).unwrap_or(0) as usize + 1,
                column: start.get("character").and_then(|c| c.as_u64()).unwrap_or(0) as usize + 1,
            })
        })
        .collect()
}

fn parse_symbols(result: &Value) -> Vec<OutlineSymbol> {
    let Some(arr) = result.as_array() else {
        return Vec::new();
    };
    arr.iter()
        .filter_map(|item| {
            let name = item.get("name")?.as_str()?.to_string();
            let kind = item
                .get("kind")
                .and_then(|k| k.as_u64())
                .map(|k| format!("{}", k))
                .unwrap_or_else(|| "?".into());
            let start = item
                .pointer("/selectionRange/start")
                .or_else(|| item.pointer("/location/range/start"))
                .or_else(|| item.pointer("/range/start"))?;
            Some(OutlineSymbol {
                name,
                kind,
                line: start.get("line").and_then(|l| l.as_u64()).unwrap_or(0) as usize + 1,
                column: start.get("character").and_then(|c| c.as_u64()).unwrap_or(0) as usize + 1,
            })
        })
        .collect()
}
