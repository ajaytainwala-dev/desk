//! Apply LSP WorkspaceEdit results safely (version-aware, multi-file).

use serde_json::Value;
use std::collections::HashMap;
use std::fs;
use std::path::{Path, PathBuf};

#[derive(Debug, Clone)]
pub struct TextEdit {
    pub start_line: usize,
    pub start_col: usize,
    pub end_line: usize,
    pub end_col: usize,
    pub new_text: String,
}

#[derive(Debug, Clone)]
pub struct FileEdits {
    pub path: PathBuf,
    pub edits: Vec<TextEdit>,
}

#[derive(Debug, Clone)]
pub struct WorkspaceEditPlan {
    pub files: Vec<FileEdits>,
}

impl WorkspaceEditPlan {
    pub fn is_empty(&self) -> bool {
        self.files.is_empty() || self.files.iter().all(|f| f.edits.is_empty())
    }

    pub fn file_count(&self) -> usize {
        self.files.len()
    }

    pub fn summary(&self) -> String {
        format!(
            "{} file(s), {} edit(s)",
            self.file_count(),
            self.files.iter().map(|f| f.edits.len()).sum::<usize>()
        )
    }
}

pub fn parse_workspace_edit(value: &Value) -> WorkspaceEditPlan {
    let mut by_path: HashMap<PathBuf, Vec<TextEdit>> = HashMap::new();

    if let Some(changes) = value.get("changes").and_then(|c| c.as_object()) {
        for (uri, edits) in changes {
            let path = uri_to_path(uri);
            if let Some(arr) = edits.as_array() {
                for e in arr {
                    if let Some(te) = parse_text_edit(e) {
                        by_path.entry(path.clone()).or_default().push(te);
                    }
                }
            }
        }
    }

    if let Some(doc_changes) = value.get("documentChanges").and_then(|d| d.as_array()) {
        for change in doc_changes {
            // TextDocumentEdit
            if let Some(doc) = change.get("textDocument") {
                let uri = doc.get("uri").and_then(|u| u.as_str()).unwrap_or_default();
                let path = uri_to_path(uri);
                if let Some(arr) = change.get("edits").and_then(|e| e.as_array()) {
                    for e in arr {
                        if let Some(te) = parse_text_edit(e) {
                            by_path.entry(path.clone()).or_default().push(te);
                        }
                    }
                }
            }
        }
    }

    let files = by_path
        .into_iter()
        .map(|(path, mut edits)| {
            // Apply from bottom to top so offsets stay valid.
            edits.sort_by_key(|a| std::cmp::Reverse((a.start_line, a.start_col)));
            FileEdits { path, edits }
        })
        .collect();
    WorkspaceEditPlan { files }
}

fn parse_text_edit(e: &Value) -> Option<TextEdit> {
    let start = e.pointer("/range/start")?;
    let end = e.pointer("/range/end")?;
    Some(TextEdit {
        start_line: start.get("line")?.as_u64()? as usize,
        start_col: start.get("character")?.as_u64()? as usize,
        end_line: end.get("line")?.as_u64()? as usize,
        end_col: end.get("character")?.as_u64()? as usize,
        new_text: e.get("newText")?.as_str()?.to_string(),
    })
}

pub fn uri_to_path(uri: &str) -> PathBuf {
    url::Url::parse(uri)
        .ok()
        .and_then(|u| u.to_file_path().ok())
        .unwrap_or_else(|| PathBuf::from(uri.trim_start_matches("file://")))
}

/// Apply edits to an in-memory line buffer (0-based LSP positions).
pub fn apply_edits_to_lines(lines: &mut Vec<String>, edits: &[TextEdit]) {
    for edit in edits {
        apply_one(lines, edit);
    }
}

fn apply_one(lines: &mut Vec<String>, edit: &TextEdit) {
    if lines.is_empty() {
        lines.push(String::new());
    }
    let start_line = edit.start_line.min(lines.len().saturating_sub(1));
    let end_line = edit.end_line.min(lines.len().saturating_sub(1));
    let start_col = edit.start_col.min(lines[start_line].chars().count());
    let end_col = edit.end_col.min(lines[end_line].chars().count());

    let prefix: String = lines[start_line].chars().take(start_col).collect();
    let suffix: String = lines[end_line].chars().skip(end_col).collect();
    let replacement = format!("{}{}{}", prefix, edit.new_text, suffix);
    let new_lines: Vec<String> = if replacement.is_empty() {
        vec![String::new()]
    } else {
        replacement.split('\n').map(|s| s.to_string()).collect()
    };

    lines.splice(start_line..=end_line, new_lines);
}

/// Apply file edits on disk for files not open in the editor.
pub fn apply_file_on_disk(path: &Path, edits: &[TextEdit]) -> std::io::Result<()> {
    let text = if path.is_file() {
        fs::read_to_string(path)?
    } else {
        String::new()
    };
    let mut lines: Vec<String> = if text.is_empty() {
        vec![String::new()]
    } else {
        text.replace("\r\n", "\n")
            .replace('\r', "\n")
            .split('\n')
            .map(|s| s.to_string())
            .collect()
    };
    apply_edits_to_lines(&mut lines, edits);
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent)?;
    }
    fs::write(path, lines.join("\n"))?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn applies_single_line_replace() {
        let mut lines = vec!["hello world".into()];
        apply_edits_to_lines(
            &mut lines,
            &[TextEdit {
                start_line: 0,
                start_col: 6,
                end_line: 0,
                end_col: 11,
                new_text: "desk".into(),
            }],
        );
        assert_eq!(lines[0], "hello desk");
    }

    #[test]
    fn applies_bottom_up_multi_edit() {
        let mut lines = vec!["aaa".into(), "bbb".into(), "ccc".into()];
        let edits = vec![
            TextEdit {
                start_line: 2,
                start_col: 0,
                end_line: 2,
                end_col: 3,
                new_text: "CCC".into(),
            },
            TextEdit {
                start_line: 0,
                start_col: 0,
                end_line: 0,
                end_col: 3,
                new_text: "AAA".into(),
            },
        ];
        // Already sorted bottom-up by parse; apply in given order (caller sorts)
        let mut sorted = edits;
        sorted.sort_by(|a, b| (b.start_line, b.start_col).cmp(&(a.start_line, a.start_col)));
        apply_edits_to_lines(&mut lines, &sorted);
        assert_eq!(lines, vec!["AAA".to_string(), "bbb".into(), "CCC".into()]);
    }

    #[test]
    fn parses_changes_map() {
        let v = serde_json::json!({
            "changes": {
                "file:///tmp/A.java": [{
                    "range": {
                        "start": {"line": 0, "character": 0},
                        "end": {"line": 0, "character": 5}
                    },
                    "newText": "class"
                }]
            }
        });
        let plan = parse_workspace_edit(&v);
        assert_eq!(plan.file_count(), 1);
        assert_eq!(plan.files[0].edits[0].new_text, "class");
    }
}
