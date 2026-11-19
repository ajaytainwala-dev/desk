package devkit.terminal;

import com.googlecode.lanterna.terminal.ansi.StreamBasedTerminal;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.terminal.TerminalResizeListener;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;

/**
 * A minimal ANSI terminal for Windows that bypasses Lanterna's
 * {@link com.googlecode.lanterna.terminal.DefaultTerminalFactory} to avoid
 * the javaw/stty issues on Windows.
 *
 * <p>This terminal writes escape codes directly to the console output and
 * reads key input from the console input. It works with {@code java.exe}
 * (not {@code javaw.exe}) and does not require {@code stty.exe}.</p>
 */
class WindowsAnsiTerminal extends StreamBasedTerminal {

  private final OutputStream out;
  private int cursorRow = 0;
  private int cursorColumn = 0;
  private int terminalRows = 24;
  private int terminalColumns = 80;
  private boolean cursorVisible = true;

  public WindowsAnsiTerminal(InputStream in, OutputStream out, Charset charset) {
    super(in, out, charset);
    this.out = out;
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
  public void clearScreen() throws IOException {
    // ANSI escape code: clear screen and move cursor to home
    write("\033[2J\033[H");
    cursorRow = 0;
    cursorColumn = 0;
  }

  @Override
  public void setCursorPosition(int column, int row) throws IOException {
    // ANSI escape code: move cursor to (row+1, column+1) - ANSI is 1-based
    write(String.format("\033[%d;%dH", row + 1, column + 1));
    cursorRow = row;
    cursorColumn = column;
  }

  @Override
  public void setCursorPosition(TerminalPosition position) throws IOException {
    setCursorPosition(position.getColumn(), position.getRow());
  }

  @Override
  public TerminalPosition getCursorPosition() throws IOException {
    return new TerminalPosition(cursorColumn, cursorRow);
  }

  @Override
  public void setCursorVisible(boolean visible) throws IOException {
    cursorVisible = visible;
    if (visible) {
      write("\033[?25h"); // Show cursor
    } else {
      write("\033[?25l"); // Hide cursor
    }
  }

  @Override
  public void enableSGR(com.googlecode.lanterna.SGR sgr) throws IOException {
    // Map Lanterna SGR enum to ANSI escape code
    int code = sgrCode(sgr);
    if (code >= 0) {
      write("\033[" + code + "m");
    }
  }

  @Override
  public void disableSGR(com.googlecode.lanterna.SGR sgr) throws IOException {
    // For most SGR attributes, there's no simple "disable" code.
    // Reset all attributes as a best-effort approach.
    write("\033[0m");
  }

  @Override
  public void resetColorAndSGR() throws IOException {
    write("\033[0m");
  }

  @Override
  public void setForegroundColor(com.googlecode.lanterna.TextColor color) throws IOException {
    if (color == null) {
      write("\033[39m"); // Default foreground
      return;
    }
    byte[] seq = color.getForegroundSGRSequence();
    if (seq != null && seq.length > 0) {
      write(new String(seq, java.nio.charset.StandardCharsets.UTF_8));
    }
  }

  @Override
  public void setBackgroundColor(com.googlecode.lanterna.TextColor color) throws IOException {
    if (color == null) {
      write("\033[49m"); // Default background
      return;
    }
    byte[] seq = color.getBackgroundSGRSequence();
    if (seq != null && seq.length > 0) {
      write(new String(seq, java.nio.charset.StandardCharsets.UTF_8));
    }
  }

  @Override
  public TerminalSize getTerminalSize() throws IOException {
    return new TerminalSize(terminalColumns, terminalRows);
  }

  @Override
  public byte[] enquireTerminal(int timeout, TimeUnit unit) throws IOException {
    return new byte[0];
  }

  @Override
  public void bell() throws IOException {
    write("\007"); // Bell character
  }

  @Override
  public void flush() throws IOException {
    out.flush();
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
  public void close() throws IOException {
    // Restore cursor visibility
    setCursorVisible(true);
    // Reset colors
    resetColorAndSGR();
    flush();
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
      case FRAKTUR: return 20;
      case BORDERED: return 51;
      case CIRCLED: return 52;
      default: return -1;
    }
  }

  private void write(String s) throws IOException {
    out.write(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }
}
