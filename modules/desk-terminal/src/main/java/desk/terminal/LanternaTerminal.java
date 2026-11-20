package desk.terminal;

import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import desk.terminal.colors.TerminalColor;
import desk.terminal.input.Key;
import desk.terminal.input.KeyEvent;
import desk.core.logging.Logger;
import desk.core.logging.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * {@link Terminal} implementation backed by Lanterna.
 *
 * <p>Lanterna handles terminal I/O, raw mode, screen buffering, and input
 * parsing. This class adapts Lanterna's API to the internal Terminal
 * interface so the rest of the application remains independent of Lanterna.</p>
 *
 * <p>Uses {@link AnsiConsoleTerminal} which writes ANSI escape codes directly
 * to the console, working with {@code java.exe} on all platforms without
 * requiring {@code javaw.exe} or {@code stty.exe}.</p>
 */
public class LanternaTerminal implements Terminal {

  private static final Logger LOG = LoggerFactory.getLogger(LanternaTerminal.class);

  private final Screen screen;
  private volatile boolean closed = false;

  // Current rendering color state, applied to each draw call.
  private TerminalColor currentForeground = TerminalColor.DEFAULT_FOREGROUND;
  private TerminalColor currentBackground = TerminalColor.DEFAULT_BACKGROUND;

  public LanternaTerminal() throws IOException {
    // Use AnsiConsoleTerminal which writes ANSI escape codes directly to the
    // console. This works with java.exe on all platforms and avoids the
    // Lanterna DefaultTerminalFactory issues on Windows (javaw requirement
    // and stty.exe dependency).
    com.googlecode.lanterna.terminal.Terminal lanternaTerminal =
        new AnsiConsoleTerminal(System.in, System.out);
    this.screen = new TerminalScreen(lanternaTerminal);
    this.screen.startScreen();
    LOG.debug("Lanterna terminal started: {}x{}",
        screen.getTerminalSize().getColumns(), screen.getTerminalSize().getRows());
  }

  @Override
  public void enterRawMode() {
    // Lanterna enters raw mode on startScreen(); nothing additional needed.
    LOG.debug("Terminal raw mode confirmed");
  }

  @Override
  public void exitRawMode() {
    if (!closed) {
      try {
        screen.close();
      } catch (IOException e) {
        LOG.warn("Error closing terminal screen", e);
      }
      closed = true;
      LOG.debug("Terminal raw mode exited");
    }
  }

  @Override
  public boolean isRawMode() {
    return !closed;
  }

  @Override
  public TerminalDimensions getDimensions() {
    var size = screen.getTerminalSize();
    return new TerminalDimensions(size.getColumns(), size.getRows());
  }

  @Override
  public void clearScreen() {
    screen.clear();
  }

  @Override
  public void setCursorPosition(int column, int row) {
    var position = new com.googlecode.lanterna.TerminalPosition(column, row);
    screen.setCursorPosition(position);
  }

  @Override
  public void hideCursor() {
    // Lanterna 3.x does not expose hideCursor on Screen; move cursor off-screen.
    var dims = getDimensions();
    setCursorPosition(dims.columns(), dims.rows());
  }

  @Override
  public void showCursor() {
    // No-op: cursor visibility is managed by the terminal.
  }

  @Override
  public void setColor(TerminalColor foreground, TerminalColor background) {
    this.currentForeground = foreground;
    this.currentBackground = background;
  }

  @Override
  public void putCharacter(int column, int row, char ch) {
    var textGraphics = screen.newTextGraphics();
    applyColors(textGraphics);
    textGraphics.putString(column, row, String.valueOf(ch));
  }

  @Override
  public void putString(int column, int row, String text) {
    var textGraphics = screen.newTextGraphics();
    applyColors(textGraphics);
    textGraphics.putString(column, row, text);
  }

  @Override
  public void refresh() {
    try {
      screen.refresh();
    } catch (IOException e) {
      LOG.warn("Error refreshing terminal screen", e);
    }
  }

  @Override
  public KeyEvent readKey() {
    if (closed) {
      return null;
    }
    try {
      KeyStroke stroke = screen.readInput();
      if (stroke == null) {
        return null;
      }
      return convertKeyStroke(stroke);
    } catch (IOException e) {
      LOG.warn("Error reading key input", e);
      return null;
    }
  }

  @Override
  public void close() {
    exitRawMode();
  }

  /* ---------- Lanterna adaptation ---------- */

  private void applyColors(com.googlecode.lanterna.graphics.TextGraphics textGraphics) {
    textGraphics.setForegroundColor(toLanternaColor(currentForeground));
    textGraphics.setBackgroundColor(toLanternaColor(currentBackground));
  }

  private KeyEvent convertKeyStroke(KeyStroke stroke) {
    KeyType type = stroke.getKeyType();
    boolean ctrl = stroke.isCtrlDown();
    boolean alt = stroke.isAltDown();
    boolean shift = stroke.isShiftDown();

    if (type == null) {
      // Character key
      char ch = stroke.getCharacter();
      if (ch == '\n' || ch == '\r') {
        return KeyEvent.key(Key.ENTER);
      }
      if (ch == '\b' || ch == 127) {
        return KeyEvent.key(Key.BACKSPACE);
      }
      if (ch == '\t') {
        return KeyEvent.key(Key.TAB);
      }
      if (ch == 27) {
        return KeyEvent.key(Key.ESCAPE);
      }
      return KeyEvent.character(ch);
    }

    return switch (type) {
      case Enter -> KeyEvent.key(Key.ENTER);
      case Backspace -> KeyEvent.key(Key.BACKSPACE);
      case Tab -> KeyEvent.key(Key.TAB);
      case Escape -> KeyEvent.key(Key.ESCAPE);
      case Delete -> KeyEvent.key(Key.DELETE);
      case Insert -> KeyEvent.key(Key.INSERT);
      case Home -> KeyEvent.key(Key.HOME);
      case End -> KeyEvent.key(Key.END);
      case PageUp -> KeyEvent.key(Key.PAGE_UP);
      case PageDown -> KeyEvent.key(Key.PAGE_DOWN);
      case ArrowUp -> KeyEvent.key(Key.ARROW_UP);
      case ArrowDown -> KeyEvent.key(Key.ARROW_DOWN);
      case ArrowLeft -> KeyEvent.key(Key.ARROW_LEFT);
      case ArrowRight -> KeyEvent.key(Key.ARROW_RIGHT);
      case F1 -> KeyEvent.key(Key.F1);
      case F2 -> KeyEvent.key(Key.F2);
      case F3 -> KeyEvent.key(Key.F3);
      case F4 -> KeyEvent.key(Key.F4);
      case F5 -> KeyEvent.key(Key.F5);
      case F6 -> KeyEvent.key(Key.F6);
      case F7 -> KeyEvent.key(Key.F7);
      case F8 -> KeyEvent.key(Key.F8);
      case F9 -> KeyEvent.key(Key.F9);
      case F10 -> KeyEvent.key(Key.F10);
      case F11 -> KeyEvent.key(Key.F11);
      case F12 -> KeyEvent.key(Key.F12);
      default -> {
        char ch = stroke.getCharacter();
        if (ch != '\0') {
          yield KeyEvent.character(ch);
        }
        yield KeyEvent.key(Key.UNKNOWN);
      }
    };
  }

  private com.googlecode.lanterna.TextColor toLanternaColor(TerminalColor color) {
    if (color == null) {
      return com.googlecode.lanterna.TextColor.ANSI.DEFAULT;
    }
    // Map TerminalColor to Lanterna TextColor.ANSI directly, avoiding
    // TextColor.Factory.fromString() which can trigger java.awt.Color
    // class loading (not available in jlink runtimes without java.desktop).
    if (color == TerminalColor.BLACK) {
      return com.googlecode.lanterna.TextColor.ANSI.BLACK;
    } else if (color == TerminalColor.RED) {
      return com.googlecode.lanterna.TextColor.ANSI.RED;
    } else if (color == TerminalColor.GREEN) {
      return com.googlecode.lanterna.TextColor.ANSI.GREEN;
    } else if (color == TerminalColor.YELLOW) {
      return com.googlecode.lanterna.TextColor.ANSI.YELLOW;
    } else if (color == TerminalColor.BLUE) {
      return com.googlecode.lanterna.TextColor.ANSI.BLUE;
    } else if (color == TerminalColor.MAGENTA) {
      return com.googlecode.lanterna.TextColor.ANSI.MAGENTA;
    } else if (color == TerminalColor.CYAN) {
      return com.googlecode.lanterna.TextColor.ANSI.CYAN;
    } else if (color == TerminalColor.WHITE) {
      return com.googlecode.lanterna.TextColor.ANSI.WHITE;
    } else if (color == TerminalColor.DEFAULT_FOREGROUND
        || color == TerminalColor.DEFAULT_BACKGROUND) {
      return com.googlecode.lanterna.TextColor.ANSI.DEFAULT;
    } else if (color.isRgb()) {
      int rgb = color.rgb();
      int r = (rgb >> 16) & 0xFF;
      int g = (rgb >> 8) & 0xFF;
      int b = rgb & 0xFF;
      return com.googlecode.lanterna.TextColor.Factory.fromString(
          String.format("#%02x%02x%02x", r, g, b));
    } else {
      return com.googlecode.lanterna.TextColor.ANSI.DEFAULT;
    }
  }
}
