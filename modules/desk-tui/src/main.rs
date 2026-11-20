use std::env;
use std::io;
use std::panic;
use std::path::PathBuf;

use crossterm::cursor::SetCursorStyle;
use crossterm::event::{DisableMouseCapture, EnableMouseCapture};
use crossterm::{
    event, execute,
    terminal::{disable_raw_mode, enable_raw_mode, EnterAlternateScreen, LeaveAlternateScreen},
};
use ratatui::{backend::CrosstermBackend, Terminal};

use desk_tui::app::App;
use desk_tui::input::handle_event;
use desk_tui::ui::render_ui;

fn main() -> Result<(), Box<dyn std::error::Error>> {
    // Terminal setup
    enable_raw_mode()?;
    let mut stdout = io::stdout();
    execute!(
        stdout,
        EnterAlternateScreen,
        EnableMouseCapture,
        SetCursorStyle::BlinkingBar
    )?;

    let backend = CrosstermBackend::new(stdout);
    let mut terminal = Terminal::new(backend)?;

    // Setup panic hook to restore terminal state if panic occurs
    let original_hook = panic::take_hook();
    panic::set_hook(Box::new(move |panic_info| {
        let _ = disable_raw_mode();
        let _ = execute!(
            io::stdout(),
            LeaveAlternateScreen,
            DisableMouseCapture,
            SetCursorStyle::DefaultUserShape
        );
        original_hook(panic_info);
    }));

    // Parse command line arguments
    let args: Vec<String> = env::args().collect();
    let initial_path = if args.len() > 1 {
        Some(PathBuf::from(&args[1]))
    } else {
        None
    };

    let mut app = App::new(initial_path.as_deref());

    // Event loop
    while app.running {
        app.tick();
        app.cleanup_toasts();

        // Update terminal size for mouse click detection
        if let Ok(size) = terminal.size() {
            app.set_terminal_size(size.width, size.height);
        }

        terminal.draw(|f| render_ui(f, &mut app))?;

        if event::poll(std::time::Duration::from_millis(50))?
            && let Ok(evt) = event::read()
        {
            handle_event(&mut app, evt);
        }
    }

    // Terminal teardown
    disable_raw_mode()?;
    execute!(
        terminal.backend_mut(),
        LeaveAlternateScreen,
        DisableMouseCapture,
        SetCursorStyle::DefaultUserShape
    )?;
    terminal.show_cursor()?;

    Ok(())
}
