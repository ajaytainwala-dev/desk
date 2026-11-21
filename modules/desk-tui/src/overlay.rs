//! Keyboard-first overlays: confirm, command palette, fuzzy file finder,
//! find-in-files, completion popup, document outline.

use std::path::{Path, PathBuf};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ConfirmAction {
    CloseTab,
    Quit,
    ReloadConflict,
    SaveOverwriteConflict,
    DeletePath,
    ApplyWorkspaceEdit,
}

#[derive(Debug, Clone)]
pub struct PaletteItem {
    pub id: String,
    pub label: String,
    pub hint: String,
}

#[derive(Debug, Clone)]
pub struct FuzzyMatch {
    pub path: PathBuf,
    pub score: i32,
}

#[derive(Debug, Clone)]
pub struct SearchHit {
    pub path: PathBuf,
    pub line: usize,
    pub column: usize,
    pub preview: String,
}

#[derive(Debug, Clone)]
pub struct CompletionEntry {
    pub label: String,
    pub detail: String,
    pub insert: String,
}

#[derive(Debug, Clone)]
pub struct OutlineSymbol {
    pub name: String,
    pub kind: String,
    pub line: usize,
    pub column: usize,
}

#[derive(Debug, Clone, Default)]
pub enum Overlay {
    #[default]
    None,
    Confirm {
        title: String,
        message: String,
        action: ConfirmAction,
    },
    Palette {
        query: String,
        selected: usize,
        items: Vec<PaletteItem>,
    },
    FuzzyFiles {
        query: String,
        selected: usize,
        matches: Vec<FuzzyMatch>,
    },
    FindInFiles {
        query: String,
        selected: usize,
        matches: Vec<SearchHit>,
    },
    Completions {
        items: Vec<CompletionEntry>,
        selected: usize,
    },
    Outline {
        symbols: Vec<OutlineSymbol>,
        selected: usize,
    },
}

impl Overlay {
    pub fn is_active(&self) -> bool {
        !matches!(self, Overlay::None)
    }

    pub fn close(&mut self) {
        *self = Overlay::None;
    }
}

/// Simple fuzzy score: higher is better. Prefers contiguous and path-end matches.
pub fn fuzzy_score(query: &str, candidate: &str) -> Option<i32> {
    if query.is_empty() {
        return Some(0);
    }
    let q: Vec<char> = query.to_lowercase().chars().collect();
    let c: Vec<char> = candidate.to_lowercase().chars().collect();
    let mut qi = 0usize;
    let mut score = 0i32;
    let mut last = None;
    for (i, ch) in c.iter().enumerate() {
        if qi < q.len() && *ch == q[qi] {
            score += 10;
            if let Some(prev) = last {
                if i == prev + 1 {
                    score += 15;
                }
            }
            if i + 1 == c.len() || candidate.as_bytes().get(i + 1) == Some(&b'/') {
                score += 5;
            }
            last = Some(i);
            qi += 1;
        }
    }
    if qi == q.len() {
        Some(score - (c.len() as i32 / 8))
    } else {
        None
    }
}

pub fn rank_paths(query: &str, paths: &[PathBuf], limit: usize) -> Vec<FuzzyMatch> {
    let mut scored: Vec<FuzzyMatch> = paths
        .iter()
        .filter_map(|p| {
            let s = p.to_string_lossy();
            fuzzy_score(query, &s).map(|score| FuzzyMatch {
                path: p.clone(),
                score,
            })
        })
        .collect();
    scored.sort_by_key(|a| std::cmp::Reverse(a.score));
    scored.truncate(limit);
    scored
}

pub fn find_in_files(root: &Path, query: &str, limit: usize) -> Vec<SearchHit> {
    if query.is_empty() {
        return Vec::new();
    }
    let mut hits = Vec::new();
    let _ = walk_search(root, query, limit, &mut hits, 0);
    hits
}

fn walk_search(
    dir: &Path,
    query: &str,
    limit: usize,
    hits: &mut Vec<SearchHit>,
    depth: usize,
) -> bool {
    if hits.len() >= limit || depth > 12 {
        return hits.len() >= limit;
    }
    let Ok(entries) = std::fs::read_dir(dir) else {
        return false;
    };
    for entry in entries.flatten() {
        if hits.len() >= limit {
            return true;
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
            if walk_search(&path, query, limit, hits, depth + 1) {
                return true;
            }
        } else if path.is_file() {
            let ext = path
                .extension()
                .and_then(|e| e.to_str())
                .unwrap_or("")
                .to_lowercase();
            if !matches!(
                ext.as_str(),
                "java"
                    | "rs"
                    | "md"
                    | "txt"
                    | "xml"
                    | "gradle"
                    | "kts"
                    | "toml"
                    | "yml"
                    | "yaml"
                    | "json"
                    | "properties"
            ) {
                continue;
            }
            if let Ok(text) = std::fs::read_to_string(&path) {
                for (idx, line) in text.lines().enumerate() {
                    if let Some(col) = line.find(query) {
                        hits.push(SearchHit {
                            path: path.clone(),
                            line: idx + 1,
                            column: col + 1,
                            preview: line.chars().take(120).collect(),
                        });
                        if hits.len() >= limit {
                            return true;
                        }
                    }
                }
            }
        }
    }
    false
}

pub fn default_palette_items() -> Vec<PaletteItem> {
    vec![
        PaletteItem {
            id: "save".into(),
            label: "File: Save".into(),
            hint: "Ctrl+S".into(),
        },
        PaletteItem {
            id: "save_all".into(),
            label: "File: Save All".into(),
            hint: "Ctrl+Shift+S".into(),
        },
        PaletteItem {
            id: "fuzzy".into(),
            label: "Go to File…".into(),
            hint: "Ctrl+P".into(),
        },
        PaletteItem {
            id: "find_in_files".into(),
            label: "Find in Files…".into(),
            hint: "Ctrl+Shift+F".into(),
        },
        PaletteItem {
            id: "goto_line".into(),
            label: "Go to Line…".into(),
            hint: "Ctrl+G".into(),
        },
        PaletteItem {
            id: "outline".into(),
            label: "Document Outline".into(),
            hint: "Ctrl+Shift+O".into(),
        },
        PaletteItem {
            id: "problems".into(),
            label: "Toggle Problems Panel".into(),
            hint: "Ctrl+Shift+M".into(),
        },
        PaletteItem {
            id: "complete".into(),
            label: "Trigger Suggest".into(),
            hint: "Ctrl+Space".into(),
        },
        PaletteItem {
            id: "definition".into(),
            label: "Go to Definition".into(),
            hint: "F12".into(),
        },
        PaletteItem {
            id: "references".into(),
            label: "Find References".into(),
            hint: "Shift+F12".into(),
        },
        PaletteItem {
            id: "hover".into(),
            label: "Show Hover".into(),
            hint: "Ctrl+K Ctrl+I".into(),
        },
        PaletteItem {
            id: "format".into(),
            label: "Format Document".into(),
            hint: "F9".into(),
        },
        PaletteItem {
            id: "rename".into(),
            label: "Rename Symbol".into(),
            hint: ":rename".into(),
        },
        PaletteItem {
            id: "code_action".into(),
            label: "Code Actions".into(),
            hint: ":fix".into(),
        },
        PaletteItem {
            id: "build".into(),
            label: "Build Project".into(),
            hint: "F6 / Esc cancel".into(),
        },
        PaletteItem {
            id: "run".into(),
            label: "Run Program".into(),
            hint: "F5".into(),
        },
        PaletteItem {
            id: "explorer_new_file".into(),
            label: "Explorer: New File".into(),
            hint: "".into(),
        },
        PaletteItem {
            id: "explorer_new_dir".into(),
            label: "Explorer: New Folder".into(),
            hint: "".into(),
        },
        PaletteItem {
            id: "explorer_rename".into(),
            label: "Explorer: Rename".into(),
            hint: "".into(),
        },
        PaletteItem {
            id: "explorer_delete".into(),
            label: "Explorer: Delete".into(),
            hint: "".into(),
        },
        PaletteItem {
            id: "quit".into(),
            label: "Quit".into(),
            hint: "Ctrl+Q".into(),
        },
    ]
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs;

    #[test]
    fn fuzzy_ranks_substring() {
        let paths = vec![
            PathBuf::from("src/main/java/Demo.java"),
            PathBuf::from("README.md"),
            PathBuf::from("src/test/java/DemoTest.java"),
        ];
        let ranked = rank_paths("Demo", &paths, 10);
        assert!(!ranked.is_empty());
        assert!(ranked[0].path.to_string_lossy().contains("Demo"));
    }

    #[test]
    fn find_in_files_locates_text() {
        let dir = tempfile::tempdir().unwrap();
        let file = dir.path().join("Hello.java");
        fs::write(&file, "class Hello {\n  void deskMarker() {}\n}\n").unwrap();
        let hits = find_in_files(dir.path(), "deskMarker", 10);
        assert_eq!(hits.len(), 1);
        assert_eq!(hits[0].line, 2);
    }
}
