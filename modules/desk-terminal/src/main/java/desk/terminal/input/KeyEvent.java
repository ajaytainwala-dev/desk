package desk.terminal.input;

/**
 * A single key event from the terminal.
 */
public final class KeyEvent {

  private final Key key;
  private final char character;
  private final boolean ctrl;
  private final boolean alt;
  private final boolean shift;

  private KeyEvent(Key key, char character, boolean ctrl, boolean alt, boolean shift) {
    this.key = key;
    this.character = character;
    this.ctrl = ctrl;
    this.alt = alt;
    this.shift = shift;
  }

  public Key key() {
    return key;
  }

  public char character() {
    return character;
  }

  public boolean isCtrl() {
    return ctrl;
  }

  public boolean isAlt() {
    return alt;
  }

  public boolean isShift() {
    return shift;
  }

  public static KeyEvent key(Key key) {
    return new KeyEvent(key, '\0', false, false, false);
  }

  public static KeyEvent character(char ch) {
    return new KeyEvent(Key.CHARACTER, ch, false, false, false);
  }

  public static KeyEvent ctrl(char ch) {
    return new KeyEvent(Key.CHARACTER, ch, true, false, false);
  }

  public static KeyEvent alt(char ch) {
    return new KeyEvent(Key.CHARACTER, ch, false, true, false);
  }

  @Override
  public String toString() {
    return String.format("KeyEvent{key=%s, char='%c', ctrl=%b, alt=%b, shift=%b}",
        key, character, ctrl, alt, shift);
  }
}
