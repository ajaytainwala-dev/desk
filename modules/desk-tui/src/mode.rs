use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Default, Serialize, Deserialize)]
pub enum Mode {
    #[default]
    Normal,
    Insert,
    Visual,
    Command,
}

impl Mode {
    pub fn name(&self) -> &'static str {
        match self {
            Mode::Normal => "NORMAL",
            Mode::Insert => "INSERT",
            Mode::Visual => "VISUAL",
            Mode::Command => "COMMAND",
        }
    }

    pub fn color(&self) -> ratatui::style::Color {
        match self {
            Mode::Normal => ratatui::style::Color::Cyan,
            Mode::Insert => ratatui::style::Color::Green,
            Mode::Visual => ratatui::style::Color::Magenta,
            Mode::Command => ratatui::style::Color::Yellow,
        }
    }
}
