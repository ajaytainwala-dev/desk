use crate::app::{App, MenuTab};
use crate::config::{LineNumberMode, Theme};
use crate::highlighter::{TokenKind, highlight_lines, style_for_kind};
use ratatui::{
    Frame,
    layout::{Constraint, Direction, Layout, Rect},
    style::{Modifier, Style},
    text::{Line, Span},
    widgets::{Block, BorderType, Borders, Clear, List, ListItem, Paragraph},
};
use unicode_width::UnicodeWidthChar;

#[derive(Clone)]
struct Cell {
    ch: char,
    style: Style,
}

pub fn render_ui(f: &mut Frame, app: &mut App) {
    let area = f.area();
    f.render_widget(Clear, area);
    let theme = app.theme();

    let bottom_height = if app.show_terminal || app.show_output {
        app.bottom_panel_height
            .clamp(3, area.height.saturating_sub(8).max(3))
    } else {
        0
    };

    let mut constraints = vec![
        Constraint::Length(1),
        Constraint::Length(1),
        Constraint::Min(4),
    ];
    if app.show_output && !app.show_terminal {
        constraints.push(Constraint::Length(bottom_height));
    } else {
        constraints.push(Constraint::Length(0));
    }
    if app.show_terminal {
        constraints.push(Constraint::Length(bottom_height));
    } else {
        constraints.push(Constraint::Length(0));
    }
    if app.search.active {
        constraints.push(Constraint::Length(2));
    } else {
        constraints.push(Constraint::Length(0));
    }
    if app.show_command_line {
        constraints.push(Constraint::Length(1));
    } else {
        constraints.push(Constraint::Length(0));
    }
    constraints.push(Constraint::Length(1));

    let chunks = Layout::default()
        .direction(Direction::Vertical)
        .constraints(constraints)
        .split(area);

    let output_idx = if app.show_output && !app.show_terminal {
        Some(3)
    } else {
        None
    };
    let terminal_idx = if app.show_terminal { Some(4) } else { None };
    let search_idx = 5;
    let command_idx = 6;
    let status_idx = 7;

    render_menu_bar(f, app, chunks[0], &theme);
    render_tab_bar(f, app, chunks[1], &theme);
    render_workspace(f, app, chunks[2], &theme);

    if let Some(i) = output_idx {
        render_output_panel(f, app, chunks[i], &theme);
    }
    if let Some(i) = terminal_idx {
        app.bottom_panel_rect = Some(chunks[i]);
        render_terminal_panel(f, app, chunks[i], &theme);
    } else if let Some(i) = output_idx {
        app.bottom_panel_rect = Some(chunks[i]);
    } else {
        app.bottom_panel_rect = None;
    }
    if app.search.active {
        render_search_bar(f, app, chunks[search_idx], &theme);
    }
    if app.show_command_line {
        render_command_line(f, app, chunks[command_idx], &theme);
    }
    render_status_bar(f, app, chunks[status_idx], &theme);

    if app.active_menu != MenuTab::None {
        render_menu_dropdown(f, app, chunks[0], &theme);
    } else {
        app.menu_dropdown_rect = None;
    }

    render_toasts(f, app, area, &theme);
    if app.show_help {
        render_help(f, area, &theme);
    }
}

fn render_menu_bar(f: &mut Frame, app: &mut App, area: Rect, theme: &Theme) {
    let mut spans = Vec::new();
    for (label, tab) in MenuTab::all().iter() {
        let style = if app.active_menu == *tab {
            Style::default()
                .fg(theme.bg)
                .bg(theme.line_number_current)
                .add_modifier(Modifier::BOLD)
        } else {
            Style::default().fg(theme.fg).bg(theme.title_bg)
        };
        spans.push(Span::styled(format!(" {} ", label), style));
        spans.push(Span::styled(" ", Style::default().bg(theme.title_bg)));
    }
    spans.push(Span::styled(
        " F1:Help  Ctrl+F:Find  ",
        Style::default().fg(theme.line_number).bg(theme.title_bg),
    ));

    let run_text = " [▶ Run F5] ";
    let run_len = run_text.chars().count() as u16;
    let format_text = " [Format F9] ";
    let format_len = format_text.chars().count() as u16;
    app.run_button_x = area.width.saturating_sub(run_len + 1);
    app.run_button_w = run_len;
    app.format_button_x = app.run_button_x.saturating_sub(format_len + 1);
    app.format_button_w = format_len;

    f.render_widget(
        Paragraph::new(Line::from(spans)).style(Style::default().bg(theme.title_bg)),
        area,
    );
    f.render_widget(
        Paragraph::new(Span::styled(
            format_text,
            Style::default()
                .fg(theme.bg)
                .bg(theme.warning)
                .add_modifier(Modifier::BOLD),
        )),
        Rect::new(area.x + app.format_button_x, area.y, format_len, 1),
    );
    f.render_widget(
        Paragraph::new(Span::styled(
            run_text,
            Style::default()
                .fg(theme.bg)
                .bg(theme.success)
                .add_modifier(Modifier::BOLD),
        )),
        Rect::new(area.x + app.run_button_x, area.y, run_len, 1),
    );
}

fn render_menu_dropdown(f: &mut Frame, app: &mut App, menu_area: Rect, theme: &Theme) {
    let tabs = MenuTab::all();
    let idx = tabs
        .iter()
        .position(|(_, t)| *t == app.active_menu)
        .unwrap_or(0);
    let (tab_start, _) = MenuTab::tab_rect(idx);
    let items = app.active_menu.items();
    if items.is_empty() {
        app.active_menu = MenuTab::None;
        return;
    }
    let height = (items.len() as u16 + 2).min(18);
    let width = (items
        .iter()
        .map(|s| s.chars().count())
        .max()
        .unwrap_or(12)
        .max(12)
        + 4) as u16;
    let rect = Rect::new(menu_area.x + tab_start, menu_area.y + 1, width, height);
    app.menu_dropdown_rect = Some((rect.x, rect.y, rect.width, rect.height));
    f.render_widget(Clear, rect);

    let list: Vec<ListItem> = items
        .iter()
        .enumerate()
        .map(|(i, label)| {
            let style = if i == app.menu_selected {
                Style::default()
                    .fg(theme.bg)
                    .bg(theme.line_number_current)
                    .add_modifier(Modifier::BOLD)
            } else {
                Style::default().fg(theme.fg)
            };
            ListItem::new(format!(" {} ", label)).style(style)
        })
        .collect();
    f.render_widget(
        List::new(list).block(
            Block::default()
                .title(format!(" {} ", tabs[idx].0))
                .borders(Borders::ALL)
                .border_type(BorderType::Rounded)
                .style(Style::default().bg(theme.title_bg).fg(theme.fg)),
        ),
        rect,
    );
}

fn render_tab_bar(f: &mut Frame, app: &mut App, area: Rect, theme: &Theme) {
    app.tab_close_positions.clear();
    if app.current_file.is_none() {
        f.render_widget(
            Paragraph::new(Line::from(Span::styled(
                " ",
                Style::default().bg(theme.title_bg),
            )))
            .style(Style::default().bg(theme.title_bg)),
            area,
        );
        return;
    }
    let mut spans = Vec::new();
    let name = app.file_name_or_no_name();
    let modified = if app.is_modified() { " ●" } else { "" };
    let tab = format!(" {}{} ", name, modified);
    let tab_len = tab.chars().count() as u16;
    let close = " ✕";
    let close_len = close.chars().count() as u16;

    spans.push(Span::styled(
        tab,
        Style::default()
            .fg(theme.fg)
            .bg(theme.title_bg)
            .add_modifier(Modifier::BOLD),
    ));
    spans.push(Span::styled(
        close,
        Style::default().fg(theme.error).bg(theme.title_bg),
    ));
    app.tab_close_positions
        .push((area.x, area.x + tab_len, area.x + tab_len + close_len, 0));
    spans.push(Span::styled(
        " │ ",
        Style::default().fg(theme.border).bg(theme.title_bg),
    ));
    spans.push(Span::styled(
        app.cwd.display().to_string(),
        Style::default().fg(theme.line_number).bg(theme.title_bg),
    ));
    f.render_widget(
        Paragraph::new(Line::from(spans)).style(Style::default().bg(theme.title_bg)),
        area,
    );
}

fn render_workspace(f: &mut Frame, app: &mut App, area: Rect, theme: &Theme) {
    let editor_focused = !app.explorer_focused
        && !app.terminal_focused
        && !app.show_help
        && !app.search.active
        && !app.show_command_line
        && app.active_menu == MenuTab::None;
    if app.show_explorer && area.width > 35 {
        let explorer_width = app
            .explorer_width
            .min(area.width.saturating_sub(20))
            .max(20);
        let chunks = Layout::default()
            .direction(Direction::Horizontal)
            .constraints([Constraint::Length(explorer_width), Constraint::Min(10)])
            .split(area);
        render_explorer(f, app, chunks[0], theme);
        render_editor_area(f, app, chunks[1], theme, editor_focused);
    } else {
        app.explorer_inner_rect = None;
        render_editor_area(f, app, area, theme, editor_focused);
    }
}

fn render_explorer(f: &mut Frame, app: &mut App, area: Rect, theme: &Theme) {
    let dir = app
        .explorer
        .path
        .file_name()
        .map(|n| n.to_string_lossy().to_string())
        .unwrap_or_else(|| app.explorer.path.display().to_string());
    let title = if app.explorer_focused {
        format!(" {} [FOCUSED] ", dir)
    } else {
        format!(" {} ", dir)
    };
    let block = Block::default()
        .borders(Borders::ALL)
        .border_style(Style::default().fg(if app.explorer_focused {
            theme.function
        } else {
            theme.border
        }))
        .title(Span::styled(
            title,
            Style::default()
                .fg(theme.function)
                .add_modifier(Modifier::BOLD),
        ));
    f.render_widget(block, area);

    let inner = inset(area, 1, 1);
    app.explorer_inner_rect = Some(inner);
    if inner.height == 0 || inner.width == 0 {
        return;
    }
    if app.explorer_selected < app.explorer_scroll {
        app.explorer_scroll = app.explorer_selected;
    }
    let start = app.explorer_scroll;
    let end = (start + inner.height as usize).min(app.explorer_flat.len());
    let mut items = Vec::new();
    for idx in start..end {
        let (path, depth) = &app.explorer_flat[idx];
        let node = app.explorer.find(path);
        let icon = node.map(|n| n.icon()).unwrap_or("·");
        let name = path.file_name().and_then(|n| n.to_str()).unwrap_or("?");
        let display = format!("{}{} {}", "  ".repeat(*depth), icon, name);
        let style = if idx == app.explorer_selected {
            if app.explorer_focused {
                Style::default()
                    .fg(theme.bg)
                    .bg(theme.line_number_current)
                    .add_modifier(Modifier::BOLD)
            } else {
                Style::default().fg(theme.fg).bg(theme.selection)
            }
        } else {
            Style::default().fg(if path.is_dir() {
                theme.function
            } else {
                theme.fg
            })
        };
        items.push(ListItem::new(Line::from(Span::styled(display, style))));
    }
    for _ in end - start..inner.height as usize {
        items.push(ListItem::new(Line::from("")));
    }
    f.render_widget(List::new(items), inner);
}

fn inset(area: Rect, horizontal: u16, vertical: u16) -> Rect {
    Rect {
        x: area.x + horizontal,
        y: area.y + vertical,
        width: area.width.saturating_sub(horizontal * 2),
        height: area.height.saturating_sub(vertical * 2),
    }
}

fn render_editor_area(f: &mut Frame, app: &mut App, area: Rect, theme: &Theme, focused: bool) {
    let block = Block::default()
        .borders(Borders::ALL)
        .border_style(
            Style::default().fg(if app.terminal_focused || app.explorer_focused {
                theme.border
            } else {
                theme.function
            }),
        )
        .title(Span::styled(
            format!(" {} ", app.language.display_name()),
            Style::default()
                .fg(theme.function)
                .add_modifier(Modifier::BOLD),
        ));
    f.render_widget(block, area);

    let inner = inset(area, 1, 1);
    app.editor_inner_rect = Some(inner);
    if inner.height == 0 || inner.width == 0 {
        return;
    }

    let buffer = &mut app.buffer;
    let cursor_line = buffer.cursor.line;
    let cursor_col = buffer.cursor.col;
    let line_count = buffer.line_count();

    let ln_width = match app.config.line_numbers {
        LineNumberMode::None => 0usize,
        _ => line_count.to_string().len().max(1) + 1,
    };
    let content_width = inner.width.saturating_sub(ln_width as u16) as usize;
    if content_width == 0 {
        return;
    }
    buffer.ensure_cursor_visible(inner.height as usize, content_width.max(1));
    let scroll_top = buffer.scroll_top;
    let scroll_left = buffer.scroll_left;

    let tokens_all = highlight_lines(&buffer.lines, app.language);
    let selection = buffer.selection;
    let bracket = app.bracket_match;
    let wrap = app.config.word_wrap;
    let highlight_current = app.config.show_line_highlight;
    let number_mode = app.config.line_numbers;
    let mut visual_rows: Vec<(Option<usize>, Line<'static>, Style)> = Vec::new();
    let mut cursor_visual_row = 0usize;

    for line_idx in scroll_top..line_count.min(scroll_top + inner.height as usize + 1) {
        let line_text = &buffer.lines[line_idx];
        let cells = build_cells(
            line_text,
            tokens_all
                .get(line_idx)
                .map(|v| v.as_slice())
                .unwrap_or(&[]),
            theme,
            selection,
            line_idx,
            bracket,
        );
        let chunks: Vec<&[Cell]> = if wrap {
            cells.chunks(content_width.max(1)).collect()
        } else {
            vec![cells.as_slice()]
        };
        if line_idx == cursor_line {
            cursor_visual_row = visual_rows.len()
                + if wrap {
                    cursor_col / content_width.max(1)
                } else {
                    0
                };
        }
        for (chunk_idx, chunk) in chunks.iter().enumerate() {
            if visual_rows.len() >= inner.height as usize {
                break;
            }
            let visible = if wrap {
                *chunk
            } else {
                let skip = scroll_left.min(chunk.len());
                &chunk[skip..]
            };
            let visible = &visible[..visible.len().min(content_width)];
            let number = if chunk_idx == 0 { Some(line_idx) } else { None };
            let mut spans = Vec::new();
            if ln_width > 0 {
                let label = match number {
                    Some(i) => match number_mode {
                        LineNumberMode::Absolute => {
                            format!("{:>width$} ", i + 1, width = ln_width - 1)
                        }
                        LineNumberMode::Relative => {
                            if i == cursor_line {
                                format!("{:>width$} ", i + 1, width = ln_width - 1)
                            } else {
                                format!(
                                    "{:>width$} ",
                                    i.abs_diff(cursor_line),
                                    width = ln_width - 1
                                )
                            }
                        }
                        LineNumberMode::None => String::new(),
                    },
                    None => " ".repeat(ln_width),
                };
                spans.push(Span::styled(
                    label,
                    Style::default().fg(if number == Some(cursor_line) {
                        theme.line_number_current
                    } else {
                        theme.line_number
                    }),
                ));
            }
            spans.extend(cells_to_spans(visible));
            let row_style = if highlight_current && number == Some(cursor_line) {
                Style::default().bg(theme.current_line)
            } else {
                Style::default().bg(theme.bg)
            };
            visual_rows.push((number, Line::from(spans).style(row_style), row_style));
        }
    }

    while visual_rows.len() < inner.height as usize {
        visual_rows.push((None, Line::from(""), Style::default().bg(theme.bg)));
    }
    // If cursor line is beyond the viewport (possible with wrapping), pin to last row.
    if cursor_visual_row >= visual_rows.len() {
        cursor_visual_row = visual_rows.len().saturating_sub(1);
    }

    for (row, (_, line, style)) in visual_rows.iter().enumerate() {
        let rect = Rect {
            x: inner.x,
            y: inner.y + row as u16,
            width: inner.width,
            height: 1,
        };
        let p = Paragraph::new(line.clone()).style(*style);
        f.render_widget(p, rect);
    }

    let cursor_col_in_row = if wrap {
        cursor_col % content_width.max(1)
    } else {
        cursor_col.saturating_sub(scroll_left)
    };
    let row_text = visual_rows
        .get(cursor_visual_row)
        .map(|(_, line, _)| line.clone())
        .unwrap_or_default();
    let prefix_width = if ln_width > 0 { ln_width } else { 0 };
    let display_col: u16 = row_text
        .spans
        .iter()
        .flat_map(|span| span.content.chars())
        .take(prefix_width + cursor_col_in_row)
        .map(|c| c.width().unwrap_or(0) as u16)
        .sum();
    if focused {
        f.set_cursor_position((
            inner.x + display_col.min(inner.width.saturating_sub(1)),
            inner.y + cursor_visual_row as u16,
        ));
    }
}

fn build_cells(
    line: &str,
    tokens: &[crate::highlighter::HighlightedToken],
    theme: &Theme,
    selection: Option<crate::buffer::Selection>,
    line_idx: usize,
    bracket: Option<(usize, usize)>,
) -> Vec<Cell> {
    let mut cells: Vec<Cell> = line
        .chars()
        .map(|ch| Cell {
            ch,
            style: Style::default().fg(theme.fg),
        })
        .collect();
    for token in tokens {
        let style = style_for_kind(
            match token.kind {
                TokenKind::Whitespace => TokenKind::Whitespace,
                other => other,
            },
            theme,
        );
        for col in token.start..token.end.min(cells.len()) {
            cells[col].style = style;
        }
    }
    if let Some(sel) = selection {
        let (sl, sc, el, ec) = sel.normalized();
        if line_idx >= sl && line_idx <= el {
            let from = if line_idx == sl { sc } else { 0 };
            let to = if line_idx == el { ec } else { cells.len() };
            for col in from..to.min(cells.len()) {
                cells[col].style = cells[col].style.bg(theme.selection);
            }
        }
    }
    if let Some((bline, bcol)) = bracket
        && bline == line_idx
        && bcol < cells.len()
    {
        cells[bcol].style = cells[bcol]
            .style
            .add_modifier(Modifier::UNDERLINED | Modifier::BOLD)
            .fg(theme.warning);
    }
    cells
}

fn cells_to_spans(cells: &[Cell]) -> Vec<Span<'static>> {
    let mut spans = Vec::new();
    let mut current = String::new();
    let mut current_style = cells.first().map(|c| c.style).unwrap_or_default();
    for cell in cells {
        if cell.style != current_style && !current.is_empty() {
            spans.push(Span::styled(std::mem::take(&mut current), current_style));
            current_style = cell.style;
        }
        current.push(cell.ch);
    }
    if !current.is_empty() {
        spans.push(Span::styled(current, current_style));
    }
    spans
}

fn render_terminal_panel(f: &mut Frame, app: &mut App, area: Rect, theme: &Theme) {
    let title = if app.terminal_focused {
        " Terminal [FOCUSED] "
    } else {
        " Terminal "
    };
    let block = Block::default()
        .borders(Borders::ALL)
        .border_style(Style::default().fg(if app.terminal_focused {
            theme.function
        } else {
            theme.border
        }))
        .title(Span::styled(
            title,
            Style::default()
                .fg(theme.function)
                .add_modifier(Modifier::BOLD),
        ));
    f.render_widget(block, area);
    let inner = inset(area, 1, 1);
    if inner.height == 0 || inner.width == 0 {
        return;
    }

    let lines = &app.terminal_output;
    let usable = inner.height.saturating_sub(1) as usize;
    let end = lines.len().saturating_sub(app.terminal_scroll);
    let start = end.saturating_sub(usable);
    let mut y = inner.y;
    for line in lines.iter().take(end).skip(start) {
        let color = if line.starts_with("[err]") {
            theme.error
        } else if line.starts_with("[ok]") {
            theme.success
        } else if line.starts_with("[out]") {
            theme.string
        } else if line.starts_with("$ ") {
            theme.function
        } else {
            theme.fg
        };
        f.render_widget(
            Paragraph::new(Line::from(Span::styled(
                line.clone(),
                Style::default().fg(color).bg(theme.bg),
            ))),
            Rect {
                x: inner.x,
                y,
                width: inner.width,
                height: 1,
            },
        );
        y += 1;
    }

    let input_y = inner.y + inner.height - 1;
    f.render_widget(
        Paragraph::new(Span::styled(
            " ".repeat(inner.width as usize),
            Style::default().bg(theme.status_bg),
        )),
        Rect {
            x: inner.x,
            y: input_y,
            width: inner.width,
            height: 1,
        },
    );
    let prompt = format!(
        "{} {}{}",
        app.cwd.display(),
        if app.terminal_focused { "$ " } else { "> " },
        app.terminal_input
    );
    f.render_widget(
        Paragraph::new(Span::styled(
            prompt,
            Style::default()
                .fg(theme.fg)
                .bg(theme.status_bg)
                .add_modifier(Modifier::BOLD),
        )),
        Rect {
            x: inner.x,
            y: input_y,
            width: inner.width,
            height: 1,
        },
    );
}

fn render_output_panel(f: &mut Frame, app: &App, area: Rect, theme: &Theme) {
    let block = Block::default()
        .borders(Borders::ALL)
        .border_style(Style::default().fg(theme.border))
        .title(Span::styled(
            " Output ",
            Style::default()
                .fg(theme.function)
                .add_modifier(Modifier::BOLD),
        ));
    f.render_widget(block, area);
    let inner = inset(area, 1, 1);
    if inner.height == 0 || inner.width == 0 {
        return;
    }
    let lines = &app.output_lines;
    let end = lines.len().saturating_sub(app.output_scroll);
    let start = end.saturating_sub(inner.height as usize);
    let mut y = inner.y;
    for line in lines.iter().take(end).skip(start) {
        let color = if line.starts_with("[err]") {
            theme.error
        } else if line.starts_with("[ok]") {
            theme.success
        } else {
            theme.fg
        };
        f.render_widget(
            Paragraph::new(Line::from(Span::styled(
                line.clone(),
                Style::default().fg(color).bg(theme.bg),
            ))),
            Rect {
                x: inner.x,
                y,
                width: inner.width,
                height: 1,
            },
        );
        y += 1;
    }
}

fn render_search_bar(f: &mut Frame, app: &mut App, area: Rect, theme: &Theme) {
    let find_focused = app.search.field == crate::app::SearchField::Find;
    let find_style = if find_focused {
        Style::default().fg(theme.bg).bg(theme.line_number_current)
    } else {
        Style::default().fg(theme.fg).bg(theme.title_bg)
    };
    let replace_style = if !find_focused {
        Style::default().fg(theme.bg).bg(theme.line_number_current)
    } else {
        Style::default().fg(theme.fg).bg(theme.title_bg)
    };
    let matches = format!(" {}/{} ", app.search.current + 1, app.search.matches.len());
    let case = if app.search.case_sensitive {
        "Aa"
    } else {
        "aa"
    };

    let line = Line::from(vec![
        Span::styled(
            " Find: ",
            Style::default().fg(theme.function).bg(theme.title_bg),
        ),
        Span::styled(app.search.query.clone(), find_style),
        Span::styled(
            "  Replace: ",
            Style::default().fg(theme.function).bg(theme.title_bg),
        ),
        Span::styled(app.search.replacement.clone(), replace_style),
        Span::styled(
            matches,
            Style::default().fg(theme.number).bg(theme.title_bg),
        ),
        Span::styled(
            format!(" [{}] ", case),
            Style::default().fg(theme.line_number).bg(theme.title_bg),
        ),
        Span::styled(
            " Enter:next  Shift+Enter:prev  Ctrl+Enter:replace  Alt+Enter:all  Esc:close",
            Style::default().fg(theme.line_number).bg(theme.title_bg),
        ),
    ]);
    f.render_widget(
        Paragraph::new(line).style(Style::default().bg(theme.title_bg)),
        Rect {
            x: area.x,
            y: area.y,
            width: area.width,
            height: 1,
        },
    );
    f.render_widget(
        Paragraph::new(Span::styled(
            " ".repeat(area.width as usize),
            Style::default().bg(theme.title_bg),
        )),
        Rect {
            x: area.x,
            y: area.y + 1,
            width: area.width,
            height: 1,
        },
    );
    let _ = app;
}

fn render_command_line(f: &mut Frame, app: &App, area: Rect, theme: &Theme) {
    let text = format!(":{}█", app.command_line);
    f.render_widget(
        Paragraph::new(Line::from(vec![Span::styled(
            text,
            Style::default()
                .fg(theme.fg)
                .bg(theme.title_bg)
                .add_modifier(Modifier::BOLD),
        )]))
        .style(Style::default().bg(theme.title_bg)),
        area,
    );
}

fn render_status_bar(f: &mut Frame, app: &App, area: Rect, theme: &Theme) {
    f.render_widget(
        Paragraph::new(Span::styled(
            " ".repeat(area.width as usize),
            Style::default().bg(theme.status_bg),
        )),
        area,
    );
    let status = app.status_line();
    f.render_widget(
        Paragraph::new(Line::from(Span::styled(
            status,
            Style::default().fg(theme.status_fg).bg(theme.status_bg),
        ))),
        area,
    );
    if let Some(toast) = app.toasts.last() {
        let color = match toast.kind.as_str() {
            "ERROR" => theme.error,
            "SUCCESS" => theme.success,
            "WARN" => theme.warning,
            _ => theme.fg,
        };
        let msg = format!(" {} ", toast.message);
        let width = msg.chars().count() as u16;
        if width < area.width / 2 {
            f.render_widget(
                Paragraph::new(Line::from(Span::styled(
                    msg,
                    Style::default()
                        .fg(color)
                        .bg(theme.status_bg)
                        .add_modifier(Modifier::BOLD),
                ))),
                Rect {
                    x: area.x + area.width - width,
                    y: area.y,
                    width,
                    height: 1,
                },
            );
        }
    }
}

fn render_toasts(f: &mut Frame, app: &App, area: Rect, theme: &Theme) {
    if app.toasts.is_empty() {
        return;
    }
    let mut y = area.y + area.height.saturating_sub(3);
    for toast in app.toasts.iter().rev() {
        if y <= area.y {
            break;
        }
        let color = match toast.kind.as_str() {
            "ERROR" => theme.error,
            "SUCCESS" => theme.success,
            "WARN" => theme.warning,
            _ => theme.fg,
        };
        let text = format!(" [{}] {}", toast.kind, toast.message);
        let width = (text.chars().count() as u16).min(area.width);
        f.render_widget(
            Paragraph::new(Line::from(Span::styled(
                text,
                Style::default().fg(color).bg(theme.status_bg),
            ))),
            Rect {
                x: area.x,
                y,
                width,
                height: 1,
            },
        );
        y = y.saturating_sub(1);
    }
}

fn render_help(f: &mut Frame, area: Rect, theme: &Theme) {
    let lines = [
        "╔══════════════════════════════════════════════════════════════════════╗",
        "║                 DEVKit EDITOR — SHORTCUTS                            ║",
        "╠══════════════════════════════════════════════════════════════════════╣",
        "║ FILE          Ctrl+N new  Ctrl+O tree  Ctrl+S save  F2 save as      ║",
        "║               Ctrl+Q quit  :w / :wq / :q / :e file / :new file      ║",
        "║ EDIT          i insert  Esc normal  v visual  y copy  d delete      ║",
        "║               dd delete line  o/O new line  u undo  U redo          ║",
        "║               Ctrl+C copy  Ctrl+X cut  Ctrl+V paste  Ctrl+A all     ║",
        "║               Ctrl+/ comment  Ctrl+] indent  Ctrl+[ outdent         ║",
        "║ SEARCH        Ctrl+F find  Ctrl+H replace  Tab: find/replace field  ║",
        "║               Enter next  Shift+Enter prev  Ctrl+Enter replace one  ║",
        "║               Alt+Enter replace all  Ctrl+C toggle case             ║",
        "║ NAVIGATE      h/j/k/l  w/b words  0/^/$ line  gg/G top/bottom       ║",
        "║               Ctrl+G goto line  Ctrl+Up/Down move line              ║",
        "║ PANELS        F3 explorer  Ctrl+J terminal  F8 output  F4 numbers   ║",
        "║               Mouse wheel scroll, drag panel border to resize       ║",
        "║ JAVA   F5 run  F6 compile  F10 check  F9 format  F7 stop  :check   ║",
        "║               F9 / Shift+Alt+F format  :fmt  :format                 ║",
        "║               Program input: type in terminal, Enter sends, Esc exits ║",
        "║ TERMINAL      Up/Down history  Ctrl+C stop job  Ctrl+L clear        ║",
        "║               Built-ins: cd, pwd, clear, javac, java; shell fallback║",
        "║ COMMANDS      :theme dark|light|monokai  :set number=relative       ║",
        "║               :set wrap=on  :set tabsize=4  :set autosave=on        ║",
        "║               :find text  :s/from/to  :clear  :reload  :help        ║",
        "║ MENUS         Alt+F/E/V/R  or click menu bar                        ║",
        "╚══════════════════════════════════════════════════════════════════════╝",
    ];
    let width = area.width.min(75);
    let height = (lines.len() as u16 + 2).min(area.height);
    let rect = Rect {
        x: area.x + (area.width.saturating_sub(width)) / 2,
        y: area.y + (area.height.saturating_sub(height)) / 2,
        width,
        height,
    };
    f.render_widget(Clear, rect);
    f.render_widget(
        Block::default()
            .borders(Borders::ALL)
            .border_type(BorderType::Rounded)
            .border_style(Style::default().fg(theme.function))
            .title(Span::styled(
                " Help (F1/Esc to close) ",
                Style::default()
                    .fg(theme.function)
                    .add_modifier(Modifier::BOLD),
            )),
        rect,
    );
    let inner = inset(rect, 1, 1);
    for (i, line) in lines.iter().enumerate() {
        if i >= inner.height as usize {
            break;
        }
        let clipped: String = line.chars().take(inner.width as usize).collect();
        f.render_widget(
            Paragraph::new(Line::from(Span::styled(
                clipped,
                Style::default().fg(theme.fg).bg(theme.bg),
            ))),
            Rect {
                x: inner.x,
                y: inner.y + i as u16,
                width: inner.width,
                height: 1,
            },
        );
    }
}
