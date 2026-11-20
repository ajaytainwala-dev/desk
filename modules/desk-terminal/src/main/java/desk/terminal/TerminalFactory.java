package desk.terminal;

/**
 * Factory for creating {@link Terminal} instances. Used by the application
 * to obtain a terminal, and by tests to substitute a mock terminal.
 */
public final class TerminalFactory {

  private TerminalFactory() {
    // utility class
  }

  /**
   * Creates a real Lanterna-backed terminal.
   *
   * @throws IOException if the terminal cannot be created.
   */
  public static Terminal createTerminal() throws java.io.IOException {
    return new LanternaTerminal();
  }
}
