use std::fs;
use std::io;
use std::path::{Path, PathBuf};
#[cfg(windows)]
use std::process::Command;
use std::time::{Duration, Instant, SystemTime, UNIX_EPOCH};

#[cfg(windows)]
fn clear_readonly(path: &Path) -> io::Result<()> {
    let output = Command::new("attrib").arg("-R").arg(path).output()?;
    if output.status.success() {
        Ok(())
    } else {
        Err(io::Error::new(
            io::ErrorKind::PermissionDenied,
            String::from_utf8_lossy(&output.stderr).to_string(),
        ))
    }
}

#[cfg(not(windows))]
fn clear_readonly(path: &Path) -> io::Result<()> {
    use std::os::unix::fs::PermissionsExt;
    if let Ok(metadata) = fs::metadata(path) {
        let mut permissions = metadata.permissions();
        permissions.set_mode(0o644);
        fs::set_permissions(path, permissions)?;
    }
    Ok(())
}

fn write_file_atomic(path: &Path, content: &str) -> io::Result<()> {
    let parent = path
        .parent()
        .filter(|p| !p.as_os_str().is_empty())
        .unwrap_or_else(|| Path::new("."));
    let name = path
        .file_name()
        .and_then(|n| n.to_str())
        .unwrap_or("desk-file");
    let stamp = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_nanos();
    let tmp = parent.join(format!(
        ".{}.desk-tmp-{}-{}",
        name,
        std::process::id(),
        stamp
    ));

    let result = (|| -> io::Result<()> {
        fs::write(&tmp, content)?;
        let _ = clear_readonly(path);
        if fs::rename(&tmp, path).is_ok() {
            return Ok(());
        }
        let _ = clear_readonly(path);
        fs::write(path, content)?;
        let _ = fs::remove_file(&tmp);
        Ok(())
    })();

    if result.is_err() {
        let _ = fs::remove_file(&tmp);
    }
    result
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
pub struct Cursor {
    pub line: usize,
    pub col: usize,
    pub desired_col: usize,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct Selection {
    pub start_line: usize,
    pub start_col: usize,
    pub end_line: usize,
    pub end_col: usize,
}

impl Selection {
    pub fn new(start_line: usize, start_col: usize, end_line: usize, end_col: usize) -> Self {
        Self {
            start_line,
            start_col,
            end_line,
            end_col,
        }
    }

    pub fn is_empty(&self) -> bool {
        self.start_line == self.end_line && self.start_col == self.end_col
    }

    pub fn normalized(&self) -> (usize, usize, usize, usize) {
        if (self.start_line, self.start_col) <= (self.end_line, self.end_col) {
            (self.start_line, self.start_col, self.end_line, self.end_col)
        } else {
            (self.end_line, self.end_col, self.start_line, self.start_col)
        }
    }

    pub fn contains(&self, line: usize, col: usize) -> bool {
        let (sl, sc, el, ec) = self.normalized();
        if line < sl || line > el {
            return false;
        }
        if line == sl && line == el {
            col >= sc && col < ec
        } else if line == sl {
            col >= sc
        } else if line == el {
            col < ec
        } else {
            true
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum EditKind {
    Insert(char),
    Backspace,
    Delete,
    Newline,
    Other,
}

#[derive(Debug, Clone)]
struct BufferState {
    lines: Vec<String>,
    cursor: Cursor,
    selection: Option<Selection>,
}

#[derive(Debug, Clone)]
pub struct Buffer {
    pub path: Option<PathBuf>,
    pub lines: Vec<String>,
    pub cursor: Cursor,
    pub selection: Option<Selection>,
    pub scroll_top: usize,
    pub scroll_left: usize,
    pub modified: bool,
    /// Disk mtime observed at last load/save — used for external conflict detection.
    pub disk_mtime: Option<SystemTime>,
    pub tab_width: usize,
    pub use_spaces: bool,
    pub auto_indent: bool,
    undo_stack: Vec<BufferState>,
    redo_stack: Vec<BufferState>,
    max_undo_levels: usize,
    last_edit: Option<(EditKind, Instant)>,
}

impl Default for Buffer {
    fn default() -> Self {
        Self::new_empty()
    }
}

fn byte_index(line: &str, col: usize) -> usize {
    line.char_indices()
        .nth(col)
        .map(|(i, _)| i)
        .unwrap_or(line.len())
}

fn is_word_char(c: char) -> bool {
    c.is_alphanumeric() || c == '_'
}

impl Buffer {
    pub fn new_empty() -> Self {
        Self {
            path: None,
            lines: vec![String::new()],
            cursor: Cursor::default(),
            selection: None,
            scroll_top: 0,
            scroll_left: 0,
            modified: false,
            disk_mtime: None,
            tab_width: 4,
            use_spaces: true,
            auto_indent: true,
            undo_stack: Vec::new(),
            redo_stack: Vec::new(),
            max_undo_levels: 200,
            last_edit: None,
        }
    }

    pub fn from_file<P: AsRef<Path>>(path: P) -> std::io::Result<Self> {
        let p = path.as_ref().to_path_buf();
        let content = fs::read_to_string(&p)?;
        let mut buffer = Self::new_empty();
        buffer.path = Some(p.clone());
        buffer.set_text(&content);
        buffer.modified = false;
        buffer.disk_mtime = fs::metadata(&p).and_then(|m| m.modified()).ok();
        Ok(buffer)
    }

    /// True when the path is set but the file no longer exists on disk.
    pub fn disk_missing(&self) -> bool {
        match self.path.as_ref() {
            Some(path) => !path.exists(),
            None => false,
        }
    }

    /// Current on-disk mtime, if the file exists.
    pub fn current_disk_mtime(&self) -> Option<SystemTime> {
        self.path
            .as_ref()
            .and_then(|p| fs::metadata(p).and_then(|m| m.modified()).ok())
    }

    /// True when the on-disk file changed since we last loaded/saved it.
    pub fn disk_changed_externally(&self) -> bool {
        let Some(path) = self.path.as_ref() else {
            return false;
        };
        let Some(known) = self.disk_mtime else {
            return false;
        };
        match fs::metadata(path).and_then(|m| m.modified()) {
            Ok(current) => current != known,
            Err(_) => false,
        }
    }

    pub fn refresh_disk_mtime(&mut self) {
        if let Some(path) = self.path.as_ref() {
            self.disk_mtime = fs::metadata(path).and_then(|m| m.modified()).ok();
        }
    }

    pub fn set_text(&mut self, text: &str) {
        let normalized = text.replace("\r\n", "\n").replace('\r', "\n");
        self.lines = if normalized.is_empty() {
            vec![String::new()]
        } else {
            normalized.split('\n').map(|s| s.to_string()).collect()
        };
        self.cursor = Cursor::default();
        self.selection = None;
        self.scroll_top = 0;
        self.scroll_left = 0;
        self.undo_stack.clear();
        self.redo_stack.clear();
        self.last_edit = None;
        self.modified = true;
    }

    pub fn text(&self) -> String {
        self.lines.join("\n")
    }

    pub fn save(&mut self) -> std::io::Result<()> {
        if let Some(path) = self.path.clone() {
            self.save_as(&path)?;
        }
        Ok(())
    }

    pub fn save_as(&mut self, path: &Path) -> std::io::Result<()> {
        let content = self.text();
        write_file_atomic(path, &content)?;
        self.path = Some(path.to_path_buf());
        self.modified = false;
        self.disk_mtime = fs::metadata(path).and_then(|m| m.modified()).ok();
        Ok(())
    }

    pub fn line_count(&self) -> usize {
        self.lines.len()
    }

    pub fn line(&self, index: usize) -> Option<&str> {
        self.lines.get(index).map(|s| s.as_str())
    }

    pub fn current_line(&self) -> Option<&str> {
        self.line(self.cursor.line)
    }

    fn line_len(&self, line: usize) -> usize {
        self.lines.get(line).map(|l| l.chars().count()).unwrap_or(0)
    }

    fn indent_unit(&self) -> String {
        if self.use_spaces {
            " ".repeat(self.tab_width)
        } else {
            "\t".to_string()
        }
    }

    fn save_undo_state(&mut self, kind: EditKind) {
        let now = Instant::now();
        let coalesce = match self.last_edit {
            Some((EditKind::Insert(a), t)) => {
                matches!(kind, EditKind::Insert(b) if b == a)
                    && now.duration_since(t) < Duration::from_millis(600)
            }
            Some((EditKind::Backspace, t)) => {
                kind == EditKind::Backspace && now.duration_since(t) < Duration::from_millis(400)
            }
            Some((EditKind::Delete, t)) => {
                kind == EditKind::Delete && now.duration_since(t) < Duration::from_millis(400)
            }
            _ => false,
        };
        if !coalesce {
            if self.undo_stack.len() >= self.max_undo_levels {
                self.undo_stack.remove(0);
            }
            self.undo_stack.push(BufferState {
                lines: self.lines.clone(),
                cursor: self.cursor,
                selection: self.selection,
            });
            self.redo_stack.clear();
        }
        self.last_edit = Some((kind, now));
    }

    pub fn end_edit_group(&mut self) {
        self.last_edit = None;
    }

    fn prepare_edit(&mut self, kind: EditKind) {
        if self.selection.is_some() && !self.selection.unwrap().is_empty() {
            self.save_undo_state(EditKind::Other);
            self.delete_selection_inner();
        }
        self.save_undo_state(kind);
    }

    pub fn insert_char(&mut self, ch: char) {
        self.prepare_edit(EditKind::Insert(ch));
        let line = self.cursor.line.min(self.lines.len().saturating_sub(1));
        let col = self.cursor.col.min(self.line_len(line));
        let idx = byte_index(&self.lines[line], col);
        self.lines[line].insert(idx, ch);
        self.cursor.line = line;
        self.cursor.col = col + 1;
        self.cursor.desired_col = self.cursor.col;
        self.modified = true;
    }

    pub fn insert_text(&mut self, text: &str) {
        let mut first = true;
        for ch in text.chars() {
            if ch == '\n' {
                self.insert_newline();
            } else {
                self.insert_char(ch);
            }
            if first {
                first = false;
                self.end_edit_group();
            }
        }
    }

    pub fn insert_newline(&mut self) {
        self.prepare_edit(EditKind::Newline);
        let line = self.cursor.line.min(self.lines.len().saturating_sub(1));
        let col = self.cursor.col.min(self.line_len(line));
        let current = self.lines.remove(line);
        let idx = byte_index(&current, col);
        let (left, right) = current.split_at(idx);
        let mut indent = String::new();
        if self.auto_indent {
            indent = self.leading_whitespace_at(&current);
            let prev = left.trim_end();
            if prev.ends_with('{') || prev.ends_with('[') || prev.ends_with('(') {
                indent.push_str(&self.indent_unit());
            }
        }
        self.lines.insert(line, left.to_string());
        self.lines.insert(line + 1, format!("{}{}", indent, right));
        self.cursor.line = line + 1;
        self.cursor.col = indent.chars().count();
        self.cursor.desired_col = self.cursor.col;
        self.modified = true;
    }

    fn leading_whitespace_at(&self, line: &str) -> String {
        line.chars().take_while(|c| c.is_whitespace()).collect()
    }

    pub fn delete_backward(&mut self) {
        if self.selection.is_some() && !self.selection.unwrap().is_empty() {
            self.save_undo_state(EditKind::Other);
            self.delete_selection_inner();
            return;
        }
        self.save_undo_state(EditKind::Backspace);
        if self.cursor.col == 0 {
            if self.cursor.line == 0 {
                self.undo_stack.pop();
                self.last_edit = None;
                return;
            }
            let prev_line = self.cursor.line - 1;
            let current = self.lines.remove(self.cursor.line);
            let prev_len = self.line_len(prev_line);
            self.lines[prev_line].push_str(&current);
            self.cursor.line = prev_line;
            self.cursor.col = prev_len;
            self.cursor.desired_col = prev_len;
        } else {
            let line = self.cursor.line;
            let idx = byte_index(&self.lines[line], self.cursor.col - 1);
            self.lines[line].remove(idx);
            self.cursor.col -= 1;
            self.cursor.desired_col = self.cursor.col;
        }
        self.modified = true;
    }

    pub fn delete_forward(&mut self) {
        if self.selection.is_some() && !self.selection.unwrap().is_empty() {
            self.save_undo_state(EditKind::Other);
            self.delete_selection_inner();
            return;
        }
        let len = self.line_len(self.cursor.line);
        if self.cursor.col >= len && self.cursor.line + 1 >= self.lines.len() {
            return;
        }
        self.save_undo_state(EditKind::Delete);
        if self.cursor.col >= len {
            let next = self.lines.remove(self.cursor.line + 1);
            self.lines[self.cursor.line].push_str(&next);
        } else {
            let line = self.cursor.line;
            let idx = byte_index(&self.lines[line], self.cursor.col);
            self.lines[line].remove(idx);
        }
        self.modified = true;
    }

    pub fn delete_word_backward(&mut self) {
        if self.cursor.col == 0 && self.cursor.line == 0 {
            return;
        }
        self.save_undo_state(EditKind::Other);
        let line = self.cursor.line;
        if self.cursor.col == 0 {
            let current = self.lines.remove(line);
            let prev_len = self.line_len(line - 1);
            self.lines[line - 1].push_str(&current);
            self.cursor.line -= 1;
            self.cursor.col = prev_len;
        } else {
            let chars: Vec<char> = self.lines[line].chars().collect();
            let mut col = self.cursor.col;
            while col > 0 && !is_word_char(chars[col - 1]) {
                col -= 1;
            }
            while col > 0 && is_word_char(chars[col - 1]) {
                col -= 1;
            }
            let start = byte_index(&self.lines[line], col);
            let end = byte_index(&self.lines[line], self.cursor.col);
            self.lines[line].replace_range(start..end, "");
            self.cursor.col = col;
        }
        self.cursor.desired_col = self.cursor.col;
        self.modified = true;
    }

    pub fn delete_word_forward(&mut self) {
        self.save_undo_state(EditKind::Other);
        let line = self.cursor.line;
        let chars: Vec<char> = self.lines[line].chars().collect();
        let mut col = self.cursor.col;
        if col >= chars.len() {
            if line + 1 < self.lines.len() {
                let next = self.lines.remove(line + 1);
                self.lines[line].push_str(&next);
            }
        } else {
            while col < chars.len() && !is_word_char(chars[col]) {
                col += 1;
            }
            while col < chars.len() && is_word_char(chars[col]) {
                col += 1;
            }
            let start = byte_index(&self.lines[line], self.cursor.col);
            let end = byte_index(&self.lines[line], col);
            self.lines[line].replace_range(start..end, "");
        }
        self.modified = true;
    }

    pub fn delete_to_line_end(&mut self) {
        let len = self.line_len(self.cursor.line);
        if self.cursor.col >= len {
            return;
        }
        self.save_undo_state(EditKind::Other);
        let line = self.cursor.line;
        let start = byte_index(&self.lines[line], self.cursor.col);
        self.lines[line].truncate(start);
        self.modified = true;
    }

    pub fn delete_line(&mut self) {
        self.save_undo_state(EditKind::Other);
        let line = self.cursor.line;
        if self.lines.len() == 1 {
            self.lines[line].clear();
        } else {
            self.lines.remove(line);
            if self.cursor.line >= self.lines.len() {
                self.cursor.line = self.lines.len() - 1;
            }
        }
        self.cursor.col = 0;
        self.cursor.desired_col = 0;
        self.selection = None;
        self.modified = true;
    }

    pub fn duplicate_line(&mut self) {
        self.save_undo_state(EditKind::Other);
        let line = self.cursor.line;
        let copy = self.lines[line].clone();
        self.lines.insert(line + 1, copy);
        self.cursor.line += 1;
        self.modified = true;
    }

    pub fn move_line(&mut self, delta: isize) {
        let line = self.cursor.line;
        let target = line as isize + delta;
        if target < 0 || target >= self.lines.len() as isize {
            return;
        }
        self.save_undo_state(EditKind::Other);
        let target = target as usize;
        let moved = self.lines.remove(line);
        self.lines.insert(target, moved);
        self.cursor.line = target;
        self.modified = true;
    }

    pub fn insert_line_below(&mut self) {
        self.save_undo_state(EditKind::Other);
        let line = self.cursor.line + 1;
        self.lines.insert(line, String::new());
        self.cursor.line = line;
        self.cursor.col = 0;
        self.cursor.desired_col = 0;
        self.modified = true;
    }

    pub fn insert_line_above(&mut self) {
        self.save_undo_state(EditKind::Other);
        let line = self.cursor.line;
        self.lines.insert(line, String::new());
        self.cursor.col = 0;
        self.cursor.desired_col = 0;
        self.modified = true;
    }

    pub fn indent_selection_or_line(&mut self) {
        self.change_indent(true);
    }

    pub fn outdent_selection_or_line(&mut self) {
        self.change_indent(false);
    }

    fn change_indent(&mut self, indent: bool) {
        let (start, end) = self.selection_line_range();
        self.save_undo_state(EditKind::Other);
        let unit = self.indent_unit();
        for line in start..=end {
            if indent {
                self.lines[line].insert_str(0, &unit);
                self.cursor.col += unit.chars().count();
            } else {
                let remove = self.lines[line]
                    .chars()
                    .take_while(|c| *c == ' ' || *c == '\t')
                    .take(self.tab_width)
                    .count();
                if remove > 0 {
                    let byte_end = byte_index(&self.lines[line], remove);
                    self.lines[line].replace_range(0..byte_end, "");
                    self.cursor.col = self.cursor.col.saturating_sub(remove);
                }
            }
        }
        self.cursor.col = self.cursor.col.min(self.line_len(self.cursor.line));
        self.cursor.desired_col = self.cursor.col;
        self.modified = true;
    }

    pub fn toggle_comment(&mut self) {
        let (start, end) = self.selection_line_range();
        self.save_undo_state(EditKind::Other);
        let all_commented = (start..=end).all(|i| self.lines[i].trim_start().starts_with("//"));
        for line in start..=end {
            if all_commented {
                if let Some(pos) = self.lines[line].find("//") {
                    let after = self.lines[line][pos + 2..].starts_with(' ');
                    let end_pos = pos + 2 + usize::from(after);
                    self.lines[line].replace_range(pos..end_pos, "");
                }
            } else {
                self.lines[line].insert_str(0, "// ");
            }
        }
        self.cursor.col = self.cursor.col.min(self.line_len(self.cursor.line));
        self.modified = true;
    }

    fn selection_line_range(&self) -> (usize, usize) {
        match self.selection {
            Some(sel) => {
                let (sl, _, el, _) = sel.normalized();
                (sl, el)
            }
            None => (self.cursor.line, self.cursor.line),
        }
    }

    pub fn move_up(&mut self) {
        if self.cursor.line > 0 {
            self.cursor.line -= 1;
            self.cursor.col = self.cursor.desired_col.min(self.line_len(self.cursor.line));
        }
    }

    pub fn move_down(&mut self) {
        if self.cursor.line + 1 < self.lines.len() {
            self.cursor.line += 1;
            self.cursor.col = self.cursor.desired_col.min(self.line_len(self.cursor.line));
        }
    }

    pub fn move_left(&mut self) {
        self.cursor.desired_col = self.cursor.col;
        if self.cursor.col > 0 {
            self.cursor.col -= 1;
        } else if self.cursor.line > 0 {
            self.cursor.line -= 1;
            self.cursor.col = self.line_len(self.cursor.line);
            self.cursor.desired_col = self.cursor.col;
        }
    }

    pub fn move_right(&mut self) {
        self.cursor.desired_col = self.cursor.col;
        if self.cursor.col < self.line_len(self.cursor.line) {
            self.cursor.col += 1;
        } else if self.cursor.line + 1 < self.lines.len() {
            self.cursor.line += 1;
            self.cursor.col = 0;
            self.cursor.desired_col = 0;
        }
    }

    pub fn move_word_left(&mut self) {
        let line = self.cursor.line;
        if self.cursor.col == 0 {
            if line > 0 {
                self.cursor.line -= 1;
                self.cursor.col = self.line_len(self.cursor.line);
                self.cursor.desired_col = self.cursor.col;
            }
            return;
        }
        let chars: Vec<char> = self.lines[line].chars().collect();
        let mut col = self.cursor.col;
        while col > 0 && !is_word_char(chars[col - 1]) {
            col -= 1;
        }
        while col > 0 && is_word_char(chars[col - 1]) {
            col -= 1;
        }
        self.cursor.col = col;
        self.cursor.desired_col = col;
    }

    pub fn move_word_right(&mut self) {
        let line = self.cursor.line;
        let chars: Vec<char> = self.lines[line].chars().collect();
        if self.cursor.col >= chars.len() {
            if line + 1 < self.lines.len() {
                self.cursor.line += 1;
                self.cursor.col = 0;
                self.cursor.desired_col = 0;
            }
            return;
        }
        let mut col = self.cursor.col;
        while col < chars.len() && !is_word_char(chars[col]) {
            col += 1;
        }
        while col < chars.len() && is_word_char(chars[col]) {
            col += 1;
        }
        self.cursor.col = col;
        self.cursor.desired_col = col;
    }

    pub fn move_to_line_start(&mut self) {
        self.cursor.col = 0;
        self.cursor.desired_col = 0;
    }

    pub fn move_to_first_non_whitespace(&mut self) {
        let col = self
            .lines
            .get(self.cursor.line)
            .map(|l| l.chars().take_while(|c| c.is_whitespace()).count())
            .unwrap_or(0);
        let col = if col >= self.line_len(self.cursor.line) {
            0
        } else {
            col
        };
        if self.cursor.col == col {
            self.cursor.col = 0;
        } else {
            self.cursor.col = col;
        }
        self.cursor.desired_col = self.cursor.col;
    }

    pub fn move_to_line_end(&mut self) {
        self.cursor.col = self.line_len(self.cursor.line);
        self.cursor.desired_col = self.cursor.col;
    }

    pub fn move_to(&mut self, line: usize, col: usize) {
        self.cursor.line = line.min(self.lines.len().saturating_sub(1));
        self.cursor.col = col.min(self.line_len(self.cursor.line));
        self.cursor.desired_col = self.cursor.col;
    }

    pub fn clamp_col(&mut self) {
        self.cursor.col = self.cursor.col.min(self.line_len(self.cursor.line));
        self.cursor.desired_col = self.cursor.desired_col.min(self.line_len(self.cursor.line));
    }

    pub fn set_cursor_offset(&mut self, offset: usize) {
        let mut remaining = offset;
        for (i, line) in self.lines.iter().enumerate() {
            let line_len = line.chars().count() + 1;
            if remaining < line_len {
                self.move_to(i, remaining);
                return;
            }
            remaining -= line_len;
        }
        let last = self.lines.len().saturating_sub(1);
        self.move_to(last, self.line_len(last));
    }

    pub fn cursor_offset(&self) -> usize {
        self.lines
            .iter()
            .take(self.cursor.line)
            .map(|l| l.chars().count() + 1)
            .sum::<usize>()
            + self.cursor.col
    }

    pub fn ensure_cursor_visible(&mut self, viewport_height: usize, viewport_width: usize) {
        if viewport_height == 0 || viewport_width == 0 {
            return;
        }
        if self.cursor.line < self.scroll_top {
            self.scroll_top = self.cursor.line;
        } else if self.cursor.line >= self.scroll_top + viewport_height {
            self.scroll_top = self.cursor.line + 1 - viewport_height;
        }
        if self.cursor.col < self.scroll_left {
            self.scroll_left = self.cursor.col;
        } else if self.cursor.col >= self.scroll_left + viewport_width {
            self.scroll_left = self.cursor.col + 1 - viewport_width;
        }
    }

    pub fn scroll_up(&mut self, rows: usize) {
        self.scroll_top = self.scroll_top.saturating_sub(rows);
    }

    pub fn scroll_down(&mut self, rows: usize) {
        let max = self.lines.len().saturating_sub(1);
        self.scroll_top = (self.scroll_top + rows).min(max);
    }

    pub fn page_up(&mut self, rows: usize) {
        for _ in 0..rows {
            self.move_up();
        }
    }

    pub fn page_down(&mut self, rows: usize) {
        for _ in 0..rows {
            self.move_down();
        }
    }

    pub fn select_all(&mut self) {
        let last = self.lines.len().saturating_sub(1);
        self.selection = Some(Selection::new(0, 0, last, self.line_len(last)));
    }

    pub fn get_selected_text(&self) -> Option<String> {
        let sel = self.selection.as_ref()?;
        let (sl, sc, el, ec) = sel.normalized();
        let mut result = String::new();
        for i in sl..=el {
            if i > sl {
                result.push('\n');
            }
            let line = self.lines.get(i)?;
            let from = if i == sl { sc } else { 0 };
            let to = if i == el { ec } else { self.line_len(i) };
            result.push_str(&char_slice(line, from, to));
        }
        Some(result)
    }

    fn delete_selection_inner(&mut self) {
        let sel = match self.selection {
            Some(sel) => sel.normalized(),
            None => return,
        };
        let (sl, sc, el, ec) = sel;
        let first = self.lines[sl].clone();
        let last = self.lines[el].clone();
        let before = char_slice(&first, 0, sc);
        let after = char_slice(&last, ec, self.line_len(el));
        let merged = format!("{}{}", before, after);
        self.lines.drain(sl..=el);
        self.lines.insert(sl, merged);
        self.cursor.line = sl;
        self.cursor.col = before.chars().count();
        self.cursor.desired_col = self.cursor.col;
        self.selection = None;
        self.modified = true;
    }

    pub fn delete_selection(&mut self) {
        if self.selection.is_some() && !self.selection.unwrap().is_empty() {
            self.save_undo_state(EditKind::Other);
            self.delete_selection_inner();
        }
    }

    pub fn replace_selection(&mut self, text: &str) {
        self.save_undo_state(EditKind::Other);
        self.delete_selection_inner();
        for ch in text.chars() {
            let line = self.cursor.line;
            let col = self.cursor.col;
            let idx = byte_index(&self.lines[line], col);
            self.lines[line].insert(idx, ch);
            self.cursor.col += 1;
        }
        self.cursor.desired_col = self.cursor.col;
        self.modified = true;
    }

    pub fn word_at_cursor(&self) -> String {
        let line = match self.current_line() {
            Some(l) => l,
            None => return String::new(),
        };
        let chars: Vec<char> = line.chars().collect();
        let mut col = self.cursor.col.min(chars.len());
        if col > 0 && !is_word_char(chars[col.saturating_sub(1)]) {
            return String::new();
        }
        while col > 0 && is_word_char(chars[col - 1]) {
            col -= 1;
        }
        let mut end = col;
        while end < chars.len() && is_word_char(chars[end]) {
            end += 1;
        }
        chars[col..end].iter().collect()
    }

    pub fn find(
        &self,
        query: &str,
        from_line: usize,
        from_col: usize,
        case_sensitive: bool,
    ) -> Option<(usize, usize)> {
        if query.is_empty() {
            return None;
        }
        let needle = if case_sensitive {
            query.to_string()
        } else {
            query.to_lowercase()
        };
        for line in from_line..self.lines.len() {
            let haystack = if case_sensitive {
                self.lines[line].clone()
            } else {
                self.lines[line].to_lowercase()
            };
            let start = if line == from_line {
                byte_index(&haystack, from_col)
            } else {
                0
            };
            if let Some(pos) = haystack[start..].find(&needle) {
                let byte = start + pos;
                let col = haystack[..byte].chars().count();
                return Some((line, col));
            }
        }
        None
    }

    pub fn find_previous(&self, query: &str, case_sensitive: bool) -> Option<(usize, usize)> {
        if query.is_empty() {
            return None;
        }
        let needle = if case_sensitive {
            query.to_string()
        } else {
            query.to_lowercase()
        };
        let (cl, cc) = (self.cursor.line, self.cursor.col);
        for line in (0..=cl).rev() {
            let haystack = if case_sensitive {
                self.lines[line].clone()
            } else {
                self.lines[line].to_lowercase()
            };
            let stop = if line == cl {
                byte_index(&haystack, cc)
            } else {
                haystack.len()
            };
            let mut found = None;
            let mut offset = 0;
            while offset < stop.min(haystack.len()) {
                let Some(pos) = haystack[offset..stop.min(haystack.len())].find(&needle) else {
                    break;
                };
                found = Some(offset + pos);
                offset += pos + 1;
                if offset >= stop {
                    break;
                }
            }
            if let Some(byte) = found {
                return Some((line, haystack[..byte].chars().count()));
            }
        }
        None
    }

    pub fn replace_all(&mut self, from: &str, to: &str, case_sensitive: bool) -> usize {
        if from.is_empty() {
            return 0;
        }
        self.save_undo_state(EditKind::Other);
        let mut count = 0;
        for line in &mut self.lines {
            if case_sensitive {
                if line.contains(from) {
                    count += line.matches(from).count();
                    *line = line.replace(from, to);
                }
            } else {
                let lower = line.to_lowercase();
                if lower.contains(&from.to_lowercase()) {
                    let mut result = String::new();
                    let mut rest = line.as_str();
                    let mut lower_rest = lower.as_str();
                    let needle = from.to_lowercase();
                    while let Some(pos) = lower_rest.find(&needle) {
                        result.push_str(&rest[..pos]);
                        result.push_str(to);
                        rest = &rest[pos + from.len()..];
                        lower_rest = &lower_rest[pos + needle.len()..];
                        count += 1;
                    }
                    result.push_str(rest);
                    *line = result;
                }
            }
        }
        if count > 0 {
            self.modified = true;
        } else {
            self.undo_stack.pop();
            self.last_edit = None;
        }
        count
    }

    pub fn undo(&mut self) {
        self.last_edit = None;
        if let Some(state) = self.undo_stack.pop() {
            self.redo_stack.push(BufferState {
                lines: self.lines.clone(),
                cursor: self.cursor,
                selection: self.selection,
            });
            self.lines = state.lines;
            self.cursor = state.cursor;
            self.selection = state.selection;
            self.modified = !self.redo_stack.is_empty() || !self.undo_stack.is_empty();
        }
    }

    pub fn redo(&mut self) {
        self.last_edit = None;
        if let Some(state) = self.redo_stack.pop() {
            self.undo_stack.push(BufferState {
                lines: self.lines.clone(),
                cursor: self.cursor,
                selection: self.selection,
            });
            self.lines = state.lines;
            self.cursor = state.cursor;
            self.selection = state.selection;
            self.modified = true;
        }
    }

    pub fn set_modified(&mut self, modified: bool) {
        self.modified = modified;
    }

    pub fn is_modified(&self) -> bool {
        self.modified
    }
}

fn char_slice(line: &str, from: usize, to: usize) -> String {
    line.chars()
        .skip(from)
        .take(to.saturating_sub(from))
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn save_as_writes_atomically() {
        let dir = tempfile::tempdir().unwrap();
        let file = dir.path().join("Atomic.java");
        let mut b = Buffer::new_empty();
        b.set_text("class Atomic {}");
        b.save_as(&file).unwrap();
        assert_eq!(fs::read_to_string(&file).unwrap(), "class Atomic {}");

        b.set_text("class Atomic { int x; }");
        b.save_as(&file).unwrap();
        assert_eq!(
            fs::read_to_string(&file).unwrap(),
            "class Atomic { int x; }"
        );
    }

    #[test]
    fn save_as_overwrites_readonly_file() {
        let dir = tempfile::tempdir().unwrap();
        let file = dir.path().join("Locked.java");
        fs::write(&file, "old").unwrap();
        #[cfg(windows)]
        {
            let status = std::process::Command::new("attrib")
                .arg("+R")
                .arg(&file)
                .status()
                .unwrap();
            assert!(status.success());
        }
        let mut b = Buffer::new_empty();
        b.set_text("new");
        b.save_as(&file).unwrap();
        assert_eq!(fs::read_to_string(&file).unwrap(), "new");
    }

    #[test]
    fn utf8_cursor_and_delete() {
        let mut b = Buffer::new_empty();
        b.insert_text("aé日b");
        assert_eq!(b.cursor.col, 4);
        b.delete_backward();
        assert_eq!(b.text(), "aé日");
        b.delete_backward();
        assert_eq!(b.text(), "aé");
        b.move_left();
        b.insert_char('X');
        assert_eq!(b.text(), "aXé");
    }

    #[test]
    fn newline_keeps_indent() {
        let mut b = Buffer::new_empty();
        b.insert_text("    if (x) {");
        b.insert_newline();
        assert_eq!(b.lines[1], "        ");
        assert_eq!(b.cursor.col, 8);
    }

    #[test]
    fn selection_multiline_delete() {
        let mut b = Buffer::new_empty();
        b.set_text("hello\nworld\ntest");
        b.selection = Some(Selection::new(0, 3, 1, 3));
        b.delete_selection();
        assert_eq!(b.text(), "helld\ntest");
    }

    #[test]
    fn undo_redo_restore() {
        let mut b = Buffer::new_empty();
        b.insert_char('a');
        b.end_edit_group();
        b.insert_char('b');
        b.end_edit_group();
        b.undo();
        assert_eq!(b.text(), "a");
        b.undo();
        assert_eq!(b.text(), "");
        b.redo();
        assert_eq!(b.text(), "a");
    }

    #[test]
    fn word_motions() {
        let mut b = Buffer::new_empty();
        b.set_text("one two three");
        b.move_to(0, 5);
        b.move_word_left();
        assert_eq!(b.cursor.col, 4);
        b.move_word_right();
        assert_eq!(b.cursor.col, 7);
    }

    #[test]
    fn find_and_replace_all() {
        let mut b = Buffer::new_empty();
        b.set_text("Foo bar foo");
        assert_eq!(b.find("foo", 0, 0, false), Some((0, 0)));
        assert_eq!(b.replace_all("foo", "baz", false), 2);
        assert_eq!(b.text(), "baz bar baz");
    }
}
