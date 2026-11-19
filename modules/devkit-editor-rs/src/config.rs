use ratatui::style::Color;
use serde::{Deserialize, Serialize};
use std::fs;
use std::path::PathBuf;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, Default)]
pub enum LineNumberMode {
    #[default]
    Absolute,
    Relative,
    None,
}

impl LineNumberMode {
    pub fn name(&self) -> &'static str {
        match self {
            LineNumberMode::Absolute => "absolute",
            LineNumberMode::Relative => "relative",
            LineNumberMode::None => "none",
        }
    }

    pub fn next(&self) -> Self {
        match self {
            LineNumberMode::Absolute => LineNumberMode::Relative,
            LineNumberMode::Relative => LineNumberMode::None,
            LineNumberMode::None => LineNumberMode::Absolute,
        }
    }

    pub fn from_name(name: &str) -> Option<Self> {
        match name.to_lowercase().as_str() {
            "absolute" | "abs" | "on" | "true" => Some(LineNumberMode::Absolute),
            "relative" | "rel" => Some(LineNumberMode::Relative),
            "none" | "off" | "false" => Some(LineNumberMode::None),
            _ => None,
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct EditorConfig {
    #[serde(default = "default_theme")]
    pub theme: String,
    #[serde(default = "default_tab_width")]
    pub tab_width: usize,
    #[serde(default = "default_use_spaces")]
    pub use_spaces: bool,
    #[serde(default = "default_line_numbers")]
    pub line_numbers: LineNumberMode,
    #[serde(default = "default_true")]
    pub auto_save: bool,
    #[serde(default = "default_false")]
    pub word_wrap: bool,
    #[serde(default = "default_true")]
    pub show_line_highlight: bool,
    #[serde(default = "default_true")]
    pub auto_indent: bool,
    #[serde(default = "default_true")]
    pub auto_close_brackets: bool,
}

fn default_theme() -> String {
    "DevKit Dark".to_string()
}

fn default_tab_width() -> usize {
    4
}

fn default_use_spaces() -> bool {
    true
}

fn default_line_numbers() -> LineNumberMode {
    LineNumberMode::Absolute
}

fn default_true() -> bool {
    true
}

fn default_false() -> bool {
    false
}

impl Default for EditorConfig {
    fn default() -> Self {
        Self {
            theme: default_theme(),
            tab_width: default_tab_width(),
            use_spaces: default_use_spaces(),
            line_numbers: default_line_numbers(),
            auto_save: default_true(),
            word_wrap: default_false(),
            show_line_highlight: default_true(),
            auto_indent: default_true(),
            auto_close_brackets: default_true(),
        }
    }
}

#[derive(Debug, Clone)]
pub struct Theme {
    pub name: String,
    pub bg: Color,
    pub fg: Color,
    pub keyword: Color,
    pub string: Color,
    pub number: Color,
    pub comment: Color,
    pub type_color: Color,
    pub function: Color,
    pub operator: Color,
    pub line_number: Color,
    pub line_number_current: Color,
    pub status_bg: Color,
    pub status_fg: Color,
    pub title_bg: Color,
    pub title_fg: Color,
    pub selection: Color,
    pub current_line: Color,
    pub border: Color,
    pub error: Color,
    pub success: Color,
    pub warning: Color,
}

impl Theme {
    pub fn devkit_dark() -> Self {
        Self {
            name: "DevKit Dark".to_string(),
            bg: Color::Rgb(0x1e, 0x1e, 0x1e),
            fg: Color::Rgb(0xd4, 0xd4, 0xd4),
            keyword: Color::Rgb(0x56, 0x9c, 0xd6),
            string: Color::Rgb(0xce, 0x91, 0x78),
            number: Color::Rgb(0xb5, 0xea, 0x8a),
            comment: Color::Rgb(0x6a, 0x99, 0x55),
            type_color: Color::Rgb(0x4e, 0xc9, 0xae),
            function: Color::Rgb(0xc5, 0x86, 0xc9),
            operator: Color::Rgb(0xd4, 0xd4, 0xd4),
            line_number: Color::Rgb(0x85, 0x85, 0x85),
            line_number_current: Color::Rgb(0x56, 0x9c, 0xd6),
            status_bg: Color::Rgb(0x3c, 0x3c, 0x3c),
            status_fg: Color::Rgb(0xd4, 0xd4, 0xd4),
            title_bg: Color::Rgb(0x2d, 0x2d, 0x2d),
            title_fg: Color::Rgb(0x56, 0x9c, 0xd6),
            selection: Color::Rgb(0x2f, 0x4b, 0x63),
            current_line: Color::Rgb(0x27, 0x27, 0x27),
            border: Color::Rgb(0x55, 0x55, 0x55),
            error: Color::Rgb(0xf1, 0x47, 0x4f),
            success: Color::Rgb(0x5d, 0xd1, 0x75),
            warning: Color::Rgb(0xe3, 0xb3, 0x4b),
        }
    }

    pub fn devkit_light() -> Self {
        Self {
            name: "DevKit Light".to_string(),
            bg: Color::Rgb(0xff, 0xff, 0xff),
            fg: Color::Rgb(0x1a, 0x1a, 0x1a),
            keyword: Color::Rgb(0x00, 0x00, 0xc0),
            string: Color::Rgb(0xa3, 0x15, 0x15),
            number: Color::Rgb(0x09, 0x86, 0x57),
            comment: Color::Rgb(0x00, 0x80, 0x00),
            type_color: Color::Rgb(0x2b, 0x91, 0xaf),
            function: Color::Rgb(0x7f, 0x00, 0x7f),
            operator: Color::Rgb(0x1a, 0x1a, 0x1a),
            line_number: Color::Rgb(0x80, 0x80, 0x80),
            line_number_current: Color::Rgb(0x00, 0x00, 0xc0),
            status_bg: Color::Rgb(0xe0, 0xe0, 0xe0),
            status_fg: Color::Rgb(0x1a, 0x1a, 0x1a),
            title_bg: Color::Rgb(0xd0, 0xd0, 0xd0),
            title_fg: Color::Rgb(0x00, 0x00, 0xc0),
            selection: Color::Rgb(0xc8, 0xdc, 0xf0),
            current_line: Color::Rgb(0xf2, 0xf6, 0xfc),
            border: Color::Rgb(0x99, 0x99, 0x99),
            error: Color::Rgb(0xc0, 0x00, 0x00),
            success: Color::Rgb(0x00, 0x70, 0x20),
            warning: Color::Rgb(0x9a, 0x6a, 0x00),
        }
    }

    pub fn monokai() -> Self {
        Self {
            name: "Monokai".to_string(),
            bg: Color::Rgb(0x27, 0x28, 0x22),
            fg: Color::Rgb(0xf8, 0xf8, 0xf2),
            keyword: Color::Rgb(0xff, 0x62, 0x9a),
            string: Color::Rgb(0xe6, 0xdb, 0x74),
            number: Color::Rgb(0xae, 0x81, 0xff),
            comment: Color::Rgb(0x75, 0x71, 0x5e),
            type_color: Color::Rgb(0x66, 0xd9, 0xc2),
            function: Color::Rgb(0xa6, 0xe2, 0x2e),
            operator: Color::Rgb(0xf8, 0xf8, 0xf2),
            line_number: Color::Rgb(0x66, 0x66, 0x60),
            line_number_current: Color::Rgb(0xa6, 0xe2, 0x2e),
            status_bg: Color::Rgb(0x3e, 0x3f, 0x36),
            status_fg: Color::Rgb(0xf8, 0xf8, 0xf2),
            title_bg: Color::Rgb(0x31, 0x32, 0x2a),
            title_fg: Color::Rgb(0xa6, 0xe2, 0x2e),
            selection: Color::Rgb(0x49, 0x53, 0x40),
            current_line: Color::Rgb(0x30, 0x31, 0x29),
            border: Color::Rgb(0x5a, 0x5b, 0x50),
            error: Color::Rgb(0xff, 0x5f, 0x5f),
            success: Color::Rgb(0xa6, 0xe2, 0x2e),
            warning: Color::Rgb(0xe6, 0xdb, 0x74),
        }
    }

    pub fn all_names() -> &'static [&'static str] {
        &["DevKit Dark", "DevKit Light", "Monokai"]
    }

    pub fn from_name(name: &str) -> Self {
        match name.to_lowercase().as_str() {
            "devkit light" | "light" => Self::devkit_light(),
            "monokai" => Self::monokai(),
            _ => Self::devkit_dark(),
        }
    }
}

impl EditorConfig {
    pub fn load() -> Self {
        if let Some(config_path) = Self::config_file_path()
            && config_path.is_file()
            && let Ok(content) = fs::read_to_string(&config_path)
            && let Ok(cfg) = toml::from_str(&content)
        {
            return cfg;
        }
        Self::default()
    }

    pub fn save(&self) -> std::io::Result<()> {
        if let Some(path) = Self::config_file_path() {
            if let Some(parent) = path.parent() {
                fs::create_dir_all(parent)?;
            }
            let content =
                toml::to_string_pretty(self).map_err(|e| std::io::Error::other(e.to_string()))?;
            fs::write(path, content)?;
        }
        Ok(())
    }

    pub fn config_file_path() -> Option<PathBuf> {
        dirs::config_dir()
            .map(|config_dir| config_dir.join("devkit").join("editor.toml"))
            .or_else(|| {
                dirs::home_dir().map(|home| home.join(".config").join("devkit").join("editor.toml"))
            })
    }

    pub fn theme(&self) -> Theme {
        Theme::from_name(&self.theme)
    }

    pub fn cycle_theme(&mut self) -> String {
        let names = Theme::all_names();
        let idx = names.iter().position(|n| *n == self.theme).unwrap_or(0);
        self.theme = names[(idx + 1) % names.len()].to_string();
        let _ = self.save();
        self.theme.clone()
    }

    pub fn set_theme(&mut self, name: &str) -> bool {
        let found = Theme::all_names()
            .iter()
            .find(|n| n.eq_ignore_ascii_case(name));
        if let Some(found) = found {
            self.theme = (*found).to_string();
            let _ = self.save();
            true
        } else {
            false
        }
    }
}
