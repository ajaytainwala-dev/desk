package desk.terminal;

import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.terminal.*;
import desk.core.logging.Logger;
import desk.core.logging.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * A minimal ANSI terminal that writes escape codes directly to the console
 * output and reads key input from the console input.
 *
 * <p>This terminal works with {@code java.exe} (not {@code javaw.exe}) on
 * Windows 10+ and on all Unix-like platforms. It does not require
 * {@code javaw.exe} or {@code stty.exe}, avoiding the Lanterna
 * {@code DefaultTerminalFactory} issues on Windows.</p>
 */
class AnsiConsoleTerminal extends AbstractTerminal {

  private static final Logger LOG = LoggerFactory.getLogger(AnsiConsoleTerminal.class);

  private final PrintStream out;
  private final InputStream in;
  private int cursorRow = 0;
  private int cursorCol = 0;
  private int rows = 24;
  private int cols = 80;
  private boolean cursorVisible = true;
  private int inputTimeout = 0;

  AnsiConsoleTerminal(InputStream in, OutputStream out) {
    this.in = in;
    this.out = new PrintStream(out, true, StandardCharsets.UTF_8);
  }

  @Override
  public TerminalSize getTerminalSize() throws IOException {
    return new TerminalSize(cols, rows);
  }

  @Override
  public void setCursorPosition(int column, int row) throws IOException {
    // ANSI escape code: move cursor to (row+1, column+1) - ANSI is 1-based
    out.print("\033[" + (row + 1) + ";" + (column + 1) + "H");
    cursorRow = row;
    cursorCol = column;
  }

  @Override
  public void setCursorPosition(TerminalPosition position) throws IOException {
    setCursorPosition(position.getColumn(), position.getRow());
  }

  @Override
  public TerminalPosition getCursorPosition() throws IOException {
    return new TerminalPosition(cursorCol, cursorRow);
  }

  @Override
  public void setCursorVisible(boolean visible) throws IOException {
    cursorVisible = visible;
    if (visible) {
      out.print("\033[?25h"); // Show cursor
    } else {
      out.print("\033[?25l"); // Hide cursor
    }
    out.flush();
  }

  @Override
  public TextGraphics newTextGraphics() throws IOException {
    return super.newTextGraphics();
  }

  @Override
  public void putCharacter(char c) throws IOException {
    out.print(c);
    cursorCol++;
    out.flush();
  }

  @Override
  public void putString(String s) throws IOException {
    out.print(s);
    cursorCol += s.length();
    out.flush();
  }

  @Override
  public void clearScreen() throws IOException {
    out.print("\033[2J\033[H"); // Clear screen and move cursor to home
    cursorRow = 0;
    cursorCol = 0;
    out.flush();
  }

  @Override
  public void enableSGR(com.googlecode.lanterna.SGR sgr) throws IOException {
    int code = sgrCode(sgr);
    if (code >= 0) {
      out.print("\033[" + code + "m");
      out.flush();
    }
  }

  @Override
  public void disableSGR(com.googlecode.lanterna.SGR sgr) throws IOException {
    // Reset all attributes as a best-effort approach
    out.print("\033[0m");
    out.flush();
  }

  @Override
  public void resetColorAndSGR() throws IOException {
    out.print("\033[0m");
    out.flush();
  }

  @Override
  public void setForegroundColor(com.googlecode.lanterna.TextColor color) throws IOException {
    if (color == null) {
      out.print("\033[39m"); // Default foreground
    } else {
      byte[] seq = color.getForegroundSGRSequence();
      if (seq != null && seq.length > 0) {
        out.print(new String(seq, StandardCharsets.UTF_8));
      }
    }
    out.flush();
  }

  @Override
  public void setBackgroundColor(com.googlecode.lanterna.TextColor color) throws IOException {
    if (color == null) {
      out.print("\033[49m"); // Default background
    } else {
      byte[] seq = color.getBackgroundSGRSequence();
      if (seq != null && seq.length > 0) {
        out.print(new String(seq, StandardCharsets.UTF_8));
      }
    }
    out.flush();
  }

  @Override
  public void bell() throws IOException {
    out.print("\007");
    out.flush();
  }

  @Override
  public void flush() throws IOException {
    out.flush();
  }

  @Override
  public void close() throws IOException {
    setCursorVisible(true);
    resetColorAndSGR();
    out.flush();
  }

  @Override
  public void enterPrivateMode() throws IOException {
    // No-op for basic ANSI terminal
  }

  @Override
  public void exitPrivateMode() throws IOException {
    // No-op for basic ANSI terminal
  }

  @Override
  public void addResizeListener(TerminalResizeListener listener) {
    // No-op: fixed terminal size
  }

  @Override
  public void removeResizeListener(TerminalResizeListener listener) {
    // No-op: fixed terminal size
  }

  @Override
  public byte[] enquireTerminal(int timeout, TimeUnit unit) throws IOException {
    return new byte[0];
  }

  @Override
  public KeyStroke readInput() throws IOException {
    // Simple key reading: read one byte from System.in
    // This handles basic ASCII keys; arrow keys and special keys are not
    // fully supported in this minimal implementation.
    int b = in.read();
    if (b < 0) {
      return null;
    }
    char ch = (char) b;

    // Handle escape sequences for arrow keys
    if (ch == 27) { // ESC
      // Try to read the next bytes for escape sequence
      int b2 = in.read();
      if (b2 < 0) {
        return new KeyStroke(KeyType.Escape);
      }
      char ch2 = (char) b2;
      if (ch2 == '[') {
        int b3 = in.read();
        if (b3 < 0) {
          return new KeyStroke(KeyType.Escape);
        }
        char ch3 = (char) b3;
        switch (ch3) {
          case 'A': return new KeyStroke(KeyType.ArrowUp);
          case 'B': return new KeyStroke(KeyType.ArrowDown);
          case 'C': return new KeyStroke(KeyType.ArrowRight);
          case 'D': return new KeyStroke(KeyType.ArrowLeft);
          case 'H': return new KeyStroke(KeyType.Home);
          case 'F': return new KeyStroke(KeyType.End);
          default: return new KeyStroke(KeyType.Unknown);
        }
      }
      return new KeyStroke(KeyType.Escape);
    }

    // Handle special keys
    switch (ch) {
      case 127: // Backspace (DEL)
        return new KeyStroke(KeyType.Backspace);
      case 8: // Backspace (BS)
        return new KeyStroke(KeyType.Backspace);
      case 10: // Enter
      case 13: // Carriage return
        return new KeyStroke(KeyType.Enter);
      case 9: // Tab
        return new KeyStroke(KeyType.Tab);
      default:
        if (ch >= 32 && ch < 127) {
          return new KeyStroke(Character.valueOf(ch), false, false);
        }
        return new KeyStroke(KeyType.Unknown);
    }
  }

  public void setInputTimeout(int timeout) {
    this.inputTimeout = timeout;
  }

  @Override
  public KeyStroke pollInput() throws IOException {
    if (in.available() > 0) {
      return readInput();
    }
    return null;
  }

  /**
   * Maps a Lanterna SGR enum to its ANSI escape code.
   */
  private int sgrCode(com.googlecode.lanterna.SGR sgr) {
    switch (sgr) {
      case BOLD: return 1;
      case UNDERLINE: return 4;
      case BLINK: return 5;
      case REVERSE: return 7;
      case CROSSED_OUT: return 9;
      case ITALIC: return 3;
      default: return -1;
    }
  }
}
