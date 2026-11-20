package desk.terminal;

import desk.terminal.colors.TerminalColor;
import desk.terminal.input.KeyEvent;

/**
 * The internal terminal abstraction. All terminal interaction in the
 * application goes through this interface, which is backed by Lanterna.
 *
 * <p>This keeps the editor and other components independent of Lanterna's
 * API and makes it possible to swap the terminal backend or test without a
 * real terminal.</p>
 */
public interface Terminal {

  /**
   * Enters raw/alternate screen mode. Must be called before rendering.
   */
  void enterRawMode();

  /**
   * Exits raw mode and restores the terminal to its original state.
   * Must be called on shutdown, even after errors.
   */
  void exitRawMode();

  /**
   * Returns whether the terminal is currently in raw mode.
   */
  boolean isRawMode();

  /**
   * Returns the current terminal dimensions.
   */
  TerminalDimensions getDimensions();

  /**
   * Clears the entire screen.
   */
  void clearScreen();

  /**
   * Sets the cursor position.
   */
  void setCursorPosition(int column, int row);

  /**
   * Hides the cursor.
   */
  void hideCursor();

  /**
   * Shows the cursor.
   */
  void showCursor();

  /**
   * Sets the foreground and background color for subsequent drawing.
   */
  void setColor(TerminalColor foreground, TerminalColor background);

  /**
   * Draws a single character at the given position with the current color.
   */
  void putCharacter(int column, int row, char ch);

  /**
   * Draws a string at the given position with the current color.
   */
  void putString(int column, int row, String text);

  /**
   * Flushes pending drawing operations to the terminal.
   */
  void refresh();

  /**
   * Reads the next key event, blocking until one is available.
   * Returns null if the terminal has been closed.
   */
  KeyEvent readKey();

  /**
   * Closes the terminal and releases all resources.
   */
  void close();
}
