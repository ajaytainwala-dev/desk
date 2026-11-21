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
    Error {
        message: String,
    },
    Crashed,
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

    pub fn shutdown(&self) {
        self.send(LspCommand::Shutdown);
    }

    fn send(&self, cmd: LspCommand) {
        if let Some(tx) = &self.cmd_tx {
            let _ = tx.send(cmd);
        }
    }
}

fn discover_server() -> Option<PathBuf> {
    if let Ok(env) = std::env::var("DESK_JAVA_LS") {
        let p = PathBuf::from(env.trim());
        if p.is_file() {
            return Some(p);
        }
    }
    let home = dirs_next_home();
    let mut candidates = vec![
        PathBuf::from("/usr/bin/jdtls"),
        PathBuf::from("/usr/local/bin/jdtls"),
    ];
    if let Some(h) = home {
        candidates.push(h.join(".desk/jdt-language-server/bin/jdtls"));
        candidates.push(h.join(".local/share/jdtls/bin/jdtls"));
    }
    if let Ok(path) = std::env::var("PATH") {
        for entry in path.split(':') {
            let p = PathBuf::from(entry).join("jdtls");
            if p.is_file() {
                return Some(p);
            }
        }
    }
    candidates.into_iter().find(|p| p.is_file())
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

    let mut stdin = child.stdin.take().ok_or("no stdin")?;
    let stdout = child.stdout.take().ok_or("no stdout")?;
    let reader = Arc::new(Mutex::new(BufReader::new(stdout)));
    let pending: Arc<Mutex<HashMap<u64, Pending>>> = Arc::new(Mutex::new(HashMap::new()));
    let versions: Arc<Mutex<HashMap<PathBuf, i64>>> = Arc::new(Mutex::new(HashMap::new()));

    // Reader thread for responses/notifications
    {
        let reader = Arc::clone(&reader);
        let event_tx = event_tx.clone();
        let pending = Arc::clone(&pending);
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
                dispatch_message(msg, &pending, &event_tx);
            }
        });
    }

    // initialize
    let init_id = 1u64;
    write_message(
        &mut stdin,
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
                        "publishDiagnostics": {}
                    },
                    "workspace": { "workspaceFolders": true }
                },
                "workspaceFolders": [{
                    "uri": path_to_uri(&workspace),
                    "name": workspace.file_name().and_then(|n| n.to_str()).unwrap_or("workspace")
                }]
            }
        }),
    )?;
    // Wait briefly for initialize result (reader will not complete pending for id 1 specially —
    // we just notify initialized after a short delay / or track pending).
    thread::sleep(Duration::from_millis(500));
    write_message(
        &mut stdin,
        &json!({
            "jsonrpc": "2.0",
            "method": "initialized",
            "params": {}
        }),
    )?;
    let _ = event_tx.send(LspEvent::Ready {
        server: server.display().to_string(),
    });

    let mut rpc_id: u64 = 10;
    while let Ok(cmd) = cmd_rx.recv() {
        match cmd {
            LspCommand::Shutdown => {
                let _ = write_message(
                    &mut stdin,
                    &json!({"jsonrpc":"2.0","id": rpc_id, "method":"shutdown","params": null}),
                );
                let _ = write_message(
                    &mut stdin,
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
                    &mut stdin,
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
                    &mut stdin,
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
                    &mut stdin,
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
                    &mut stdin,
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
                    &mut stdin,
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
                    &mut stdin,
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
                    &mut stdin,
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
                    &mut stdin,
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
                    &mut stdin,
                    &json!({
                        "jsonrpc": "2.0",
                        "id": rpc_id,
                        "method": "textDocument/documentSymbol",
                        "params": { "textDocument": { "uri": path_to_uri(&path) } }
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
    }
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
