use crossterm::event::{
    Event, KeyCode, KeyEvent, KeyEventKind, KeyModifiers, MouseButton, MouseEvent, MouseEventKind,
};

use crate::app::{App, JobStatus, MenuTab};
use crate::buffer::Selection;
use crate::mode::Mode;
use crate::overlay::default_palette_items;
use crate::overlay::{Overlay, find_in_files, rank_paths};

pub fn handle_event(app: &mut App, event: Event) {
    match event {
        Event::Key(key) if key.kind == KeyEventKind::Press => handle_key_event(app, key),
        Event::Mouse(mouse) => handle_mouse_event(app, mouse),
        Event::Resize(width, height) => app.set_terminal_size(width, height),
        _ => {}
    }
}

pub fn handle_key_event(app: &mut App, key: KeyEvent) {
    if app.overlay.is_active() {
        handle_overlay_key(app, key);
        return;
    }

    if app.show_help {
        if matches!(
            key.code,
            KeyCode::F(1) | KeyCode::Esc | KeyCode::Enter | KeyCode::Char('q')
        ) {
            app.show_help = false;
        }
        return;
    }

    if app.search.active {
        handle_search_input(app, key);
        return;
    }

    if app.show_command_line {
        handle_command_line_input(app, key);
        return;
    }

    if app.terminal_focused {
        handle_terminal_input(app, key);
        return;
    }

    if app.active_menu != MenuTab::None {
        handle_menu_key(app, key);
        return;
    }

    if key.code == KeyCode::Char('F')
        && key.modifiers.contains(KeyModifiers::ALT)
        && key.modifiers.contains(KeyModifiers::SHIFT)
        && !key.modifiers.contains(KeyModifiers::CONTROL)
    {
        app.format_current();
        return;
    }

    if key.modifiers.contains(KeyModifiers::ALT)
        && !key.modifiers.contains(KeyModifiers::CONTROL)
        && !key.modifiers.contains(KeyModifiers::SHIFT)
    {
        match key.code {
            KeyCode::Char('f') | KeyCode::Char('F') => {
                app.active_menu = MenuTab::File;
                app.menu_selected = 0;
                return;
            }
            KeyCode::Char('e') | KeyCode::Char('E') => {
                app.active_menu = MenuTab::Edit;
                app.menu_selected = 0;
                return;
            }
            KeyCode::Char('v') | KeyCode::Char('V') => {
                app.active_menu = MenuTab::View;
                app.menu_selected = 0;
                return;
            }
            KeyCode::Char('r') | KeyCode::Char('R') => {
                app.active_menu = MenuTab::Run;
                app.menu_selected = 0;
                return;
            }
            _ => {}
        }
    }

    if key.modifiers.contains(KeyModifiers::CONTROL) {
        match key.code {
            KeyCode::Char('q') | KeyCode::Char('Q') => {
                app.request_quit();
                return;
            }
            KeyCode::Char('s') | KeyCode::Char('S') => {
                if key.modifiers.contains(KeyModifiers::SHIFT) {
                    if let Err(e) = app.save_all_tabs() {
                        app.toast(&format!("Save all failed: {}", e), "ERROR");
                    }
                } else if let Err(e) = app.save_current() {
                    app.toast(&format!("Save failed: {}", e), "ERROR");
                }
                return;
            }
            KeyCode::Char('p') | KeyCode::Char('P') => {
                if key.modifiers.contains(KeyModifiers::SHIFT) {
                    app.open_palette();
                } else {
                    app.open_fuzzy_files();
                }
                return;
            }
            KeyCode::Char(' ') => {
                app.trigger_completion();
                return;
            }
            KeyCode::Char('m') | KeyCode::Char('M')
                if key.modifiers.contains(KeyModifiers::SHIFT) =>
            {
                app.show_problems = !app.show_problems;
                app.show_output = app.show_problems;
                return;
            }
            KeyCode::Tab | KeyCode::PageDown => {
                app.next_tab();
                return;
            }
            KeyCode::BackTab | KeyCode::PageUp => {
                app.prev_tab();
                return;
            }
            KeyCode::Char('n') | KeyCode::Char('N') => {
                app.new_file(None);
                return;
            }
            KeyCode::Char('o') | KeyCode::Char('O') => {
                if key.modifiers.contains(KeyModifiers::SHIFT) {
                    app.request_outline();
                } else {
                    app.explorer_focused = true;
                    app.terminal_focused = false;
                    app.toast("Choose a file in the explorer (Enter)", "INFO");
                }
                return;
            }
            KeyCode::Char('f') | KeyCode::Char('F') => {
                if key.modifiers.contains(KeyModifiers::SHIFT) {
                    app.open_find_in_files();
                } else {
                    app.open_search(false);
                }
                return;
            }
            KeyCode::Char('h') | KeyCode::Char('H') => {
                app.open_search(true);
                return;
            }
            KeyCode::Char('g') | KeyCode::Char('G') => {
                app.enter_command_mode();
                app.command_line = format!(":{}", app.current_line_index() + 1);
                return;
            }
            KeyCode::Char('c') | KeyCode::Char('C') => {
                app.copy_selection();
                return;
            }
            KeyCode::Char('x') | KeyCode::Char('X') => {
                app.cut_selection();
                return;
            }
            KeyCode::Char('v') | KeyCode::Char('V') => {
                if let Err(e) = app.paste_from_clipboard() {
                    app.toast(&format!("Paste failed: {}", e), "ERROR");
                }
                return;
            }
            KeyCode::Char('a') | KeyCode::Char('A') => {
                app.select_all();
                return;
            }
            KeyCode::Char('z') | KeyCode::Char('Z') => {
                app.undo();
                return;
            }
            KeyCode::Char('y') | KeyCode::Char('Y') => {
                app.redo();
                return;
            }
            KeyCode::Char('d') | KeyCode::Char('D') => {
                app.buffer.delete_line();
                app.mark_edited();
                return;
            }
            KeyCode::Char('/') => {
                app.buffer.toggle_comment();
                app.mark_edited();
                return;
            }
            KeyCode::Char(']') => {
                app.buffer.indent_selection_or_line();
                app.mark_edited();
                return;
            }
            KeyCode::Char('[') => {
                app.buffer.outdent_selection_or_line();
                app.mark_edited();
                return;
            }
            KeyCode::Char('j') | KeyCode::Char('J') => {
                app.toggle_terminal();
                return;
            }
            KeyCode::Char('b') | KeyCode::Char('B') => {
                app.toggle_explorer();
                return;
            }
            KeyCode::Char('e') | KeyCode::Char('E') => {
                if key.modifiers.contains(KeyModifiers::SHIFT) {
                    app.toggle_explorer();
                } else {
                    app.show_explorer = true;
                    app.explorer_focused = true;
                    app.terminal_focused = false;
                }
                return;
            }
            KeyCode::Char('`') => {
                app.toggle_output();
                return;
            }
            KeyCode::Up => {
                app.buffer.move_line(-1);
                app.mark_edited();
                return;
            }
            KeyCode::Down => {
                app.buffer.move_line(1);
                app.mark_edited();
                return;
            }
            _ => {}
        }
    }

    match key.code {
        KeyCode::F(1) => {
            app.show_help = true;
            return;
        }
        KeyCode::F(2) => {
            app.show_command_line = true;
            let path = app
                .buffer
                .path
                .as_ref()
                .map(|p| p.display().to_string())
                .unwrap_or_default();
            app.command_line = format!(":w {}", path);
            return;
        }
        KeyCode::F(3) => {
            app.toggle_explorer();
            return;
        }
        KeyCode::F(4) => {
            app.cycle_line_numbers();
            return;
        }
        KeyCode::F(5) => {
            app.run_current_file();
            return;
        }
        KeyCode::F(6) => {
            app.compile_current_file();
            return;
        }
        KeyCode::F(7) => {
            app.stop_job();
            return;
        }
        KeyCode::F(8) => {
            app.toggle_output();
            return;
        }
        KeyCode::F(9) => {
            app.format_current();
            return;
        }
        KeyCode::F(10) => {
            app.check_current_java();
            return;
        }
        KeyCode::F(11) => {
            if key.modifiers.contains(KeyModifiers::SHIFT) {
                app.prev_problem();
            } else {
                app.next_problem();
            }
            return;
        }
        KeyCode::F(12) => {
            if key.modifiers.contains(KeyModifiers::SHIFT) {
                app.find_references();
            } else {
                app.goto_definition();
            }
            return;
        }
        _ => {}
    }

    if app.explorer_focused && app.show_explorer {
        match key.code {
            KeyCode::Up => {
                app.navigate_explorer_up();
                return;
            }
            KeyCode::Down => {
                app.navigate_explorer_down();
                return;
            }
            KeyCode::Enter => {
                if let Err(e) = app.open_explorer_file() {
                    app.toast(&format!("Open failed: {}", e), "ERROR");
                }
                app.explorer_focused = false;
                return;
            }
            KeyCode::Char(' ') | KeyCode::Right | KeyCode::Left => {
                app.toggle_explorer_expand();
                return;
            }
            KeyCode::Esc | KeyCode::Tab => {
                app.explorer_focused = false;
                return;
            }
            KeyCode::Char('/') => {
                app.explorer_focused = false;
                app.open_search(false);
                return;
            }
            _ => return,
        }
    }

    match app.mode {
        Mode::Normal => handle_normal_mode(app, key),
        Mode::Insert => handle_insert_mode(app, key),
        Mode::Visual => handle_visual_mode(app, key),
        Mode::Command => handle_command_mode(app, key),
    }
}

fn handle_search_input(app: &mut App, key: KeyEvent) {
    match key.code {
        KeyCode::Esc => {
            app.close_search();
        }
        KeyCode::Tab => {
            app.search.field = match app.search.field {
                crate::app::SearchField::Find => crate::app::SearchField::Replace,
                crate::app::SearchField::Replace => crate::app::SearchField::Find,
            };
        }
        KeyCode::Enter => {
            if key.modifiers.contains(KeyModifiers::ALT) {
                app.replace_all_matches();
            } else if key.modifiers.contains(KeyModifiers::CONTROL) {
                app.replace_current();
            } else if key.modifiers.contains(KeyModifiers::SHIFT) {
                app.search_next(true);
            } else {
                app.search_next(false);
            }
        }
        KeyCode::Backspace => {
            match app.search.field {
                crate::app::SearchField::Find => app.search.query.pop(),
                crate::app::SearchField::Replace => app.search.replacement.pop(),
            };
            app.update_search_matches();
        }
        KeyCode::Up => app.search_next(true),
        KeyCode::Down => app.search_next(false),
        KeyCode::Char('c') if key.modifiers.contains(KeyModifiers::CONTROL) => {
            app.search.case_sensitive = !app.search.case_sensitive;
            app.update_search_matches();
        }
        KeyCode::Char(c) => {
            if !key.modifiers.contains(KeyModifiers::CONTROL) {
                match app.search.field {
                    crate::app::SearchField::Find => app.search.query.push(c),
                    crate::app::SearchField::Replace => app.search.replacement.push(c),
                }
                app.update_search_matches();
            }
        }
        _ => {}
    }
}

fn handle_command_line_input(app: &mut App, key: KeyEvent) {
    match key.code {
        KeyCode::Enter => app.execute_command_line(),
        KeyCode::Esc => {
            app.show_command_line = false;
            app.command_line.clear();
            app.mode = Mode::Normal;
        }
        KeyCode::Backspace => {
            app.command_line.pop();
        }
        KeyCode::Up => app.command_history_prev(),
        KeyCode::Down => app.command_history_next(),
        KeyCode::Char(c) if !key.modifiers.contains(KeyModifiers::CONTROL) => {
            app.command_line.push(c);
        }
        _ => {}
    }
}

fn handle_terminal_input(app: &mut App, key: KeyEvent) {
    match key.code {
        KeyCode::Enter => app.terminal_input_enter(),
        KeyCode::Backspace => app.terminal_input_backspace(),
        KeyCode::Up => app.terminal_history_prev(),
        KeyCode::Down => app.terminal_history_next(),
        KeyCode::PageUp => app.terminal_scroll = app.terminal_scroll.saturating_add(5),
        KeyCode::PageDown => app.terminal_scroll = app.terminal_scroll.saturating_sub(5),
        KeyCode::Char('c') if key.modifiers.contains(KeyModifiers::CONTROL) => app.stop_job(),
        KeyCode::Char('l') if key.modifiers.contains(KeyModifiers::CONTROL) => app.clear_output(),
        KeyCode::Esc => {
            app.terminal_focused = false;
            app.terminal_scroll = 0;
        }
        KeyCode::Char(c) if !key.modifiers.contains(KeyModifiers::CONTROL) => {
            app.terminal_input_char(c);
            app.terminal_scroll = 0;
        }
        _ => {}
    }
}

fn handle_menu_key(app: &mut App, key: KeyEvent) {
    let items = app.active_menu.items();
    if items.is_empty() {
        app.active_menu = MenuTab::None;
        return;
    }
    match key.code {
        KeyCode::Esc => app.active_menu = MenuTab::None,
        KeyCode::Up => {
            app.menu_selected = if app.menu_selected == 0 {
                items.len() - 1
            } else {
                app.menu_selected - 1
            };
        }
        KeyCode::Down => app.menu_selected = (app.menu_selected + 1) % items.len(),
        KeyCode::Enter => {
            let tab = app.active_menu;
            let idx = app.menu_selected;
            app.active_menu = MenuTab::None;
            execute_menu_item(app, tab, idx);
        }
        _ => {}
    }
}

fn execute_menu_item(app: &mut App, tab: MenuTab, idx: usize) {
    match tab {
        MenuTab::File => match idx {
            0 => app.new_file(None),
            1 => {
                app.explorer_focused = true;
                app.terminal_focused = false;
            }
            2 => {
                if let Err(e) = app.save_current() {
                    app.toast(&format!("Save failed: {}", e), "ERROR");
                }
            }
            3 => {
                app.show_command_line = true;
                let path = app
                    .buffer
                    .path
                    .as_ref()
                    .map(|p| p.display().to_string())
                    .unwrap_or_default();
                app.command_line = format!(":w {}", path);
            }
            4 => app.reload_current_file(),
            5 => app.running = false,
            _ => {}
        },
        MenuTab::Edit => match idx {
            0 => app.undo(),
            1 => app.redo(),
            2 => app.cut_selection(),
            3 => app.copy_selection(),
            4 => {
                if let Err(e) = app.paste_from_clipboard() {
                    app.toast(&format!("Paste failed: {}", e), "ERROR");
                }
            }
            5 => app.select_all(),
            6 => app.open_search(false),
            7 => app.open_search(true),
            8 => app.format_current(),
            _ => {}
        },
        MenuTab::View => match idx {
            0 => app.toggle_explorer(),
            1 => app.toggle_terminal(),
            2 => app.toggle_output(),
            3 => app.cycle_line_numbers(),
            4 => app.cycle_theme(),
            5 => app.toggle_word_wrap(),
            _ => {}
        },
        MenuTab::Run => match idx {
            0 => app.run_current_file(),
            1 => app.compile_current_file(),
            2 => app.check_current_java(),
            3 => app.stop_job(),
            4 => app.clear_output(),
            _ => {}
        },
        MenuTab::None => {}
    }
}

fn move_with_selection(app: &mut App, f: impl FnOnce(&mut App)) {
    f(app);
    app.extend_visual_selection();
    app.update_bracket_match();
}

fn handle_normal_mode(app: &mut App, key: KeyEvent) {
    if let Some(pending) = app.pending_key.take() {
        match (pending, key.code) {
            ('d', KeyCode::Char('d')) => {
                app.buffer.delete_line();
                app.mark_edited();
                return;
            }
            ('d', KeyCode::Char('w')) => {
                app.buffer.delete_word_forward();
                app.mark_edited();
                return;
            }
            ('d', KeyCode::Char('$')) => {
                app.buffer.delete_to_line_end();
                app.mark_edited();
                return;
            }
            ('g', KeyCode::Char('g')) => {
                app.goto_line(1);
                return;
            }
            _ => {}
        }
    }

    match key.code {
        KeyCode::Char('d') => app.pending_key = Some('d'),
        KeyCode::Char('g') => app.pending_key = Some('g'),
        KeyCode::Char('i') => app.toggle_mode(),
        KeyCode::Char('I') => {
            app.move_to_line_start();
            app.toggle_mode();
        }
        KeyCode::Char('a') => {
            app.move_right();
            app.toggle_mode();
        }
        KeyCode::Char('A') => {
            app.move_to_line_end();
            app.toggle_mode();
        }
        KeyCode::Char('o') => {
            app.buffer.insert_line_below();
            app.toggle_mode();
            app.mark_edited();
        }
        KeyCode::Char('O') => {
            app.buffer.insert_line_above();
            app.toggle_mode();
            app.mark_edited();
        }
        KeyCode::Char('v') => app.start_visual(),
        KeyCode::Char(':') => app.enter_command_mode(),
        KeyCode::Char('u') => app.undo(),
        KeyCode::Char('U') => app.redo(),
        KeyCode::Char('s') => {
            if let Err(e) = app.save_current() {
                app.toast(&format!("Save failed: {}", e), "ERROR");
            }
        }
        KeyCode::Char('r') => app.run_current_file(),
        KeyCode::Char('c') => app.compile_current_file(),
        KeyCode::Char('e') => {
            app.show_explorer = true;
            app.explorer_focused = true;
            app.terminal_focused = false;
        }
        KeyCode::Char('t') => {
            app.show_terminal = true;
            app.show_output = false;
            app.terminal_focused = true;
            app.explorer_focused = false;
        }
        KeyCode::Char('f') => app.open_search(false),
        KeyCode::Char('G') => {
            let last = app.buffer.line_count();
            app.goto_line(last);
        }
        KeyCode::Char('x') => {
            app.buffer.delete_forward();
            app.mark_edited();
        }
        KeyCode::Char('X') => {
            app.buffer.delete_backward();
            app.mark_edited();
        }
        KeyCode::Char('D') => {
            app.buffer.delete_to_line_end();
            app.mark_edited();
        }
        KeyCode::Char('J') => {
            app.buffer.move_line(1);
            app.mark_edited();
        }
        KeyCode::Char('K') => {
            app.buffer.move_line(-1);
            app.mark_edited();
        }
        KeyCode::Char('>') => {
            app.buffer.indent_selection_or_line();
            app.mark_edited();
        }
        KeyCode::Char('<') => {
            app.buffer.outdent_selection_or_line();
            app.mark_edited();
        }
        KeyCode::Char('0') | KeyCode::Home => app.move_to_line_start(),
        KeyCode::Char('^') => app.move_to_line_start(),
        KeyCode::Char('$') | KeyCode::End => app.move_to_line_end(),
        KeyCode::Char('w') => {
            app.buffer.move_word_right();
            app.update_bracket_match();
        }
        KeyCode::Char('b') => {
            app.buffer.move_word_left();
            app.update_bracket_match();
        }
        KeyCode::Up => app.move_up(),
        KeyCode::Down => app.move_down(),
        KeyCode::Left | KeyCode::Char('h') => app.move_left(),
        KeyCode::Right | KeyCode::Char('l') => app.move_right(),
        KeyCode::Enter => {
            if app.explorer_focused {
                if let Err(e) = app.open_explorer_file() {
                    app.toast(&format!("Open failed: {}", e), "ERROR");
                }
                app.explorer_focused = false;
            } else {
                app.buffer.insert_newline();
                app.mark_edited();
            }
        }
        KeyCode::PageUp => app.buffer.page_up(10),
        KeyCode::PageDown => app.buffer.page_down(10),
        KeyCode::Backspace => app.delete_backward(),
        KeyCode::Delete => app.delete_forward(),
        KeyCode::Tab => {
            app.buffer.indent_selection_or_line();
            app.mark_edited();
        }
        KeyCode::Esc => {
            if app
                .job
                .as_ref()
                .is_some_and(|j| j.status == JobStatus::Running)
            {
                app.stop_job();
                return;
            }
            app.explorer_focused = false;
            app.terminal_focused = false;
            app.active_menu = MenuTab::None;
            app.buffer.selection = None;
        }
        _ => {}
    }
}

fn handle_insert_mode(app: &mut App, key: KeyEvent) {
    match key.code {
        KeyCode::Esc => {
            if app
                .job
                .as_ref()
                .is_some_and(|j| j.status == JobStatus::Running)
            {
                app.stop_job();
                return;
            }
            app.mode = Mode::Normal;
            app.buffer.selection = None;
            app.buffer.end_edit_group();
        }
        KeyCode::Enter => app.insert_newline(),
        KeyCode::Backspace => app.delete_backward(),
        KeyCode::Delete => app.delete_forward(),
        KeyCode::Up => app.move_up(),
        KeyCode::Down => app.move_down(),
        KeyCode::Left => app.move_left(),
        KeyCode::Right => app.move_right(),
        KeyCode::Home => {
            app.buffer.move_to_line_start();
            app.update_bracket_match();
        }
        KeyCode::End => {
            app.buffer.move_to_line_end();
            app.update_bracket_match();
        }
        KeyCode::Tab => {
            let unit = if app.config.use_spaces {
                " ".repeat(app.config.tab_width)
            } else {
                "\t".to_string()
            };
            app.buffer.insert_text(&unit);
            app.mark_edited();
        }
        KeyCode::Char(c) => {
            if !key.modifiers.contains(KeyModifiers::CONTROL) {
                app.insert_char(c);
            }
        }
        _ => {}
    }
}

fn handle_visual_mode(app: &mut App, key: KeyEvent) {
    match key.code {
        KeyCode::Esc | KeyCode::Char('v') => {
            app.mode = Mode::Normal;
            app.buffer.selection = None;
        }
        KeyCode::Char('y') => {
            app.copy_selection();
            app.mode = Mode::Normal;
            app.buffer.selection = None;
        }
        KeyCode::Char('d') | KeyCode::Char('x') => {
            app.delete_selection();
            app.mode = Mode::Normal;
        }
        KeyCode::Char('c') => {
            app.copy_selection();
            app.delete_selection();
            app.mode = Mode::Insert;
        }
        KeyCode::Up => move_with_selection(app, |a| a.move_up()),
        KeyCode::Down => move_with_selection(app, |a| a.move_down()),
        KeyCode::Left => move_with_selection(app, |a| a.move_left()),
        KeyCode::Right => move_with_selection(app, |a| a.move_right()),
        KeyCode::Home => move_with_selection(app, |a| a.buffer.move_to_line_start()),
        KeyCode::End => move_with_selection(app, |a| a.buffer.move_to_line_end()),
        KeyCode::Char('w') => move_with_selection(app, |a| a.buffer.move_word_right()),
        KeyCode::Char('b') => move_with_selection(app, |a| a.buffer.move_word_left()),
        KeyCode::Char('$') => move_with_selection(app, |a| a.buffer.move_to_line_end()),
        KeyCode::Char('0') => move_with_selection(app, |a| a.buffer.move_to_line_start()),
        _ => {}
    }
}

fn handle_command_mode(app: &mut App, key: KeyEvent) {
    match key.code {
        KeyCode::Esc => {
            app.mode = Mode::Normal;
            app.show_command_line = false;
            app.command_line.clear();
        }
        KeyCode::Enter => app.execute_command_line(),
        KeyCode::Backspace => {
            app.command_line.pop();
        }
        KeyCode::Up => app.command_history_prev(),
        KeyCode::Down => app.command_history_next(),
        KeyCode::Char(c) if !key.modifiers.contains(KeyModifiers::CONTROL) => {
            app.command_line.push(c);
        }
        _ => {}
    }
}

fn handle_mouse_event(app: &mut App, mouse: MouseEvent) {
    match mouse.kind {
        MouseEventKind::Down(MouseButton::Left) => {
            let (col, row) = (mouse.column, mouse.row);
            app.last_mouse_pos = Some((col, row));
            app.mouse_moved = false;

            if let Some(rect) = app.bottom_panel_rect {
                if row == rect.y && col >= rect.x && col < rect.x + rect.width {
                    app.is_dragging_bottom_panel = true;
                    return;
                }
                if row > rect.y && row < rect.y + rect.height {
                    app.terminal_focused = app.show_terminal;
                    app.explorer_focused = false;
                    return;
                }
            }

            if row == 0 {
                if app.format_button_w > 0
                    && col >= app.format_button_x
                    && col < app.format_button_x + app.format_button_w
                {
                    app.format_current();
                    return;
                }
                if app.run_button_w > 0
                    && col >= app.run_button_x
                    && col < app.run_button_x + app.run_button_w
                {
                    app.run_current_file();
                    return;
                }
                for (idx, (_, tab)) in MenuTab::all().iter().enumerate() {
                    let (start, width) = MenuTab::tab_rect(idx);
                    if col >= start && col < start + width {
                        app.active_menu = if app.active_menu == *tab {
                            MenuTab::None
                        } else {
                            *tab
                        };
                        app.menu_selected = 0;
                        return;
                    }
                }
                return;
            }

            if app.active_menu != MenuTab::None {
                if let Some((dx, dy, dw, dh)) = app.menu_dropdown_rect
                    && col >= dx
                    && col < dx + dw
                    && row > dy
                    && row < dy + dh
                {
                    let idx = (row - dy - 1) as usize;
                    let tab = app.active_menu;
                    app.active_menu = MenuTab::None;
                    execute_menu_item(app, tab, idx);
                    return;
                }
                app.active_menu = MenuTab::None;
                return;
            }

            if row == 1 {
                for (tab_start, tab_end, close_end, idx) in app.tab_close_positions.clone() {
                    if col >= tab_end && col < close_end {
                        app.activate_tab(idx);
                        app.close_current_file();
                        return;
                    }
                    if col >= tab_start && col < tab_end {
                        app.activate_tab(idx);
                        return;
                    }
                }
                return;
            }

            if let Some(inner) = app.explorer_inner_rect
                && col >= inner.x.saturating_sub(1)
                && col < inner.x + inner.width + 1
                && row >= inner.y
                && row < inner.y + inner.height
            {
                let target = app.explorer_scroll + (row - inner.y) as usize;
                if target < app.explorer_flat.len() {
                    app.explorer_selected = target;
                    app.explorer_focused = true;
                    app.adjust_explorer_scroll();
                    let path = app.explorer_flat[target].0.clone();
                    if path.is_dir() {
                        if let Some(node) = app.explorer.find_mut(&path) {
                            node.expanded = !node.expanded;
                            if node.expanded {
                                App::load_children(node);
                            }
                        }
                        app.refresh_explorer();
                    } else if let Err(e) = app.open_file(&path) {
                        app.toast(&format!("Open failed: {}", e), "ERROR");
                    } else {
                        app.explorer_focused = false;
                    }
                }
                return;
            }

            if let Some(inner) = app.editor_inner_rect
                && col >= inner.x
                && col < inner.x + inner.width
                && row >= inner.y
                && row < inner.y + inner.height
            {
                let buffer_row = row - inner.y + app.buffer.scroll_top as u16;
                let buffer_col = col.saturating_sub(inner.x) as usize + app.buffer.scroll_left;
                app.buffer.move_to(buffer_row as usize, buffer_col);
                app.buffer.selection = Some(Selection::new(
                    app.buffer.cursor.line,
                    app.buffer.cursor.col,
                    app.buffer.cursor.line,
                    app.buffer.cursor.col,
                ));
                app.mouse_drag_start = Some((app.buffer.cursor.line, app.buffer.cursor.col));
                app.explorer_focused = false;
                app.terminal_focused = false;
                app.update_bracket_match();
            }
        }
        MouseEventKind::Drag(MouseButton::Left) => {
            if app.is_dragging_bottom_panel {
                let total = app.last_terminal_size.1;
                let new_height = total.saturating_sub(mouse.row);
                app.bottom_panel_height = new_height.clamp(3, total.saturating_sub(8).max(3));
                return;
            }
            if let Some((start_line, start_col)) = app.mouse_drag_start {
                if let Some((last_col, last_row)) = app.last_mouse_pos
                    && (last_col != mouse.column || last_row != mouse.row)
                {
                    app.mouse_moved = true;
                }
                app.last_mouse_pos = Some((mouse.column, mouse.row));
                if let Some(inner) = app.editor_inner_rect {
                    let buffer_row =
                        mouse.row.saturating_sub(inner.y) as usize + app.buffer.scroll_top;
                    let buffer_col =
                        mouse.column.saturating_sub(inner.x) as usize + app.buffer.scroll_left;
                    let max_line = app.buffer.line_count().saturating_sub(1);
                    let row = buffer_row.min(max_line);
                    let max_col = app.buffer.line(row).map(|l| l.chars().count()).unwrap_or(0);
                    app.buffer.move_to(row, buffer_col.min(max_col));
                    app.buffer.selection = Some(Selection::new(
                        start_line,
                        start_col,
                        app.buffer.cursor.line,
                        app.buffer.cursor.col,
                    ));
                }
            }
        }
        MouseEventKind::Up(MouseButton::Left) => {
            app.is_dragging_bottom_panel = false;
            if app.mouse_drag_start.is_some() && !app.mouse_moved {
                app.buffer.selection = None;
            } else if let Some(text) = app.buffer.get_selected_text()
                && !text.is_empty()
            {
                app.clipboard = text;
            }
            app.mouse_drag_start = None;
        }
        MouseEventKind::ScrollUp => {
            if app.terminal_focused {
                app.terminal_scroll = app.terminal_scroll.saturating_add(3);
            } else if app.explorer_focused {
                app.explorer_scroll = app.explorer_scroll.saturating_sub(3);
                app.explorer_selected = app.explorer_selected.saturating_sub(3);
            } else {
                app.buffer.scroll_up(3);
            }
        }
        MouseEventKind::ScrollDown => {
            if app.terminal_focused {
                app.terminal_scroll = app.terminal_scroll.saturating_sub(3);
            } else if app.explorer_focused {
                app.explorer_scroll = app.explorer_scroll.saturating_add(3);
                app.navigate_explorer_down();
                for _ in 0..2 {
                    app.navigate_explorer_down();
                }
            } else {
                app.buffer.scroll_down(3);
            }
        }
        _ => {}
    }
}

fn handle_overlay_key(app: &mut App, key: KeyEvent) {
    match &mut app.overlay {
        Overlay::Confirm { .. } => match key.code {
            KeyCode::Enter | KeyCode::Char('y') | KeyCode::Char('Y') => app.confirm_yes(),
            KeyCode::Esc | KeyCode::Char('n') | KeyCode::Char('N') => app.confirm_no(),
            _ => {}
        },
        Overlay::Palette {
            query,
            selected,
            items,
        } => match key.code {
            KeyCode::Esc => app.overlay.close(),
            KeyCode::Up => *selected = selected.saturating_sub(1),
            KeyCode::Down => {
                if *selected + 1 < items.len() {
                    *selected += 1;
                }
            }
            KeyCode::Enter => {
                if let Some(item) = items.get(*selected).cloned() {
                    app.run_palette_item(&item.id);
                }
            }
            KeyCode::Backspace => {
                query.pop();
                let q = query.clone();
                let all = default_palette_items();
                let filtered: Vec<_> = all
                    .into_iter()
                    .filter(|i| {
                        q.is_empty()
                            || i.label.to_lowercase().contains(&q.to_lowercase())
                            || i.id.contains(&q.to_lowercase())
                    })
                    .collect();
                *selected = 0;
                if let Overlay::Palette { items, .. } = &mut app.overlay {
                    *items = filtered;
                }
            }
            KeyCode::Char(c) if !key.modifiers.contains(KeyModifiers::CONTROL) => {
                query.push(c);
                let q = query.clone();
                let all = default_palette_items();
                let filtered: Vec<_> = all
                    .into_iter()
                    .filter(|i| {
                        i.label.to_lowercase().contains(&q.to_lowercase())
                            || i.id.contains(&q.to_lowercase())
                    })
                    .collect();
                *selected = 0;
                if let Overlay::Palette { items, .. } = &mut app.overlay {
                    *items = filtered;
                }
            }
            _ => {}
        },
        Overlay::FuzzyFiles {
            query,
            selected,
            matches,
        } => match key.code {
            KeyCode::Esc => app.overlay.close(),
            KeyCode::Up => *selected = selected.saturating_sub(1),
            KeyCode::Down => {
                if *selected + 1 < matches.len() {
                    *selected += 1;
                }
            }
            KeyCode::Enter => {
                if let Some(m) = matches.get(*selected).cloned() {
                    app.overlay.close();
                    let _ = app.open_file(&m.path);
                }
            }
            KeyCode::Backspace => {
                query.pop();
                let paths = crate::app::collect_workspace_files_pub(&app.cwd, 2000);
                *matches = rank_paths(query, &paths, 100);
                *selected = 0;
            }
            KeyCode::Char(c) if !key.modifiers.contains(KeyModifiers::CONTROL) => {
                query.push(c);
                let paths = crate::app::collect_workspace_files_pub(&app.cwd, 2000);
                *matches = rank_paths(query, &paths, 100);
                *selected = 0;
            }
            _ => {}
        },
        Overlay::FindInFiles {
            query,
            selected,
            matches,
        } => match key.code {
            KeyCode::Esc => app.overlay.close(),
            KeyCode::Up => *selected = selected.saturating_sub(1),
            KeyCode::Down => {
                if *selected + 1 < matches.len() {
                    *selected += 1;
                }
            }
            KeyCode::Enter => {
                if let Some(hit) = matches.get(*selected).cloned() {
                    app.overlay.close();
                    let _ = app.open_file(&hit.path);
                    app.buffer.cursor.line = hit.line.saturating_sub(1);
                    app.buffer.cursor.col = hit.column.saturating_sub(1);
                }
            }
            KeyCode::Backspace => {
                query.pop();
                *matches = find_in_files(&app.cwd, query, 200);
                *selected = 0;
            }
            KeyCode::Char(c) if !key.modifiers.contains(KeyModifiers::CONTROL) => {
                query.push(c);
                *matches = find_in_files(&app.cwd, query, 200);
                *selected = 0;
            }
            _ => {}
        },
        Overlay::Completions { items, selected } => match key.code {
            KeyCode::Esc => app.overlay.close(),
            KeyCode::Up => *selected = selected.saturating_sub(1),
            KeyCode::Down => {
                if *selected + 1 < items.len() {
                    *selected += 1;
                }
            }
            KeyCode::Enter | KeyCode::Tab => {
                if let Some(item) = items.get(*selected).cloned() {
                    app.apply_completion(&item.insert);
                }
            }
            _ => app.overlay.close(),
        },
        Overlay::Outline { symbols, selected } => match key.code {
            KeyCode::Esc => app.overlay.close(),
            KeyCode::Up => *selected = selected.saturating_sub(1),
            KeyCode::Down => {
                if *selected + 1 < symbols.len() {
                    *selected += 1;
                }
            }
            KeyCode::Enter => {
                if let Some(sym) = symbols.get(*selected).cloned() {
                    app.overlay.close();
                    app.buffer.cursor.line = sym.line.saturating_sub(1);
                    app.buffer.cursor.col = sym.column.saturating_sub(1);
                }
            }
            _ => {}
        },
        Overlay::None => {}
    }
}
