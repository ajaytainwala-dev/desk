package desk.terminal.colors;

/**
 * A terminal color. Supports the ANSI 16-color palette and true-color (RGB)
 * representations.
 */
public final class TerminalColor {

  // ANSI 16 colors
  public static final TerminalColor BLACK = new TerminalColor("black", null);
  public static final TerminalColor RED = new TerminalColor("red", null);
  public static final TerminalColor GREEN = new TerminalColor("green", null);
  public static final TerminalColor YELLOW = new TerminalColor("yellow", null);
  public static final TerminalColor BLUE = new TerminalColor("blue", null);
  public static final TerminalColor MAGENTA = new TerminalColor("magenta", null);
  public static final TerminalColor CYAN = new TerminalColor("cyan", null);
  public static final TerminalColor WHITE = new TerminalColor("white", null);

  // Default foreground/background
  public static final TerminalColor DEFAULT_FOREGROUND = new TerminalColor("default-fg", null);
  public static final TerminalColor DEFAULT_BACKGROUND = new TerminalColor("default-bg", null);

  // Bright colors (RGB)
  public static final TerminalColor BRIGHT_BLACK = TerminalColor.rgb(0x80, 0x80, 0x80);
  public static final TerminalColor BRIGHT_RED = TerminalColor.rgb(0xff, 0x00, 0x00);
  public static final TerminalColor BRIGHT_GREEN = TerminalColor.rgb(0x00, 0xff, 0x00);
  public static final TerminalColor BRIGHT_YELLOW = TerminalColor.rgb(0xff, 0xff, 0x00);
  public static final TerminalColor BRIGHT_BLUE = TerminalColor.rgb(0x00, 0x00, 0xff);
  public static final TerminalColor BRIGHT_MAGENTA = TerminalColor.rgb(0xff, 0x00, 0xff);
  public static final TerminalColor BRIGHT_CYAN = TerminalColor.rgb(0x00, 0xff, 0xff);
  public static final TerminalColor BRIGHT_WHITE = TerminalColor.rgb(0xff, 0xff, 0xff);

  private final String name;
  private final Integer rgb;

  private TerminalColor(String name, Integer rgb) {
    this.name = name;
    this.rgb = rgb;
  }

  public boolean isRgb() {
    return rgb != null;
  }

  public int rgb() {
    if (rgb == null) {
      throw new IllegalStateException("Color " + name + " is not an RGB color");
    }
    return rgb;
  }

  public static TerminalColor rgb(int r, int g, int b) {
    int value = (r << 16) | (g << 8) | b;
    return new TerminalColor("rgb(" + r + "," + g + "," + b + ")", value);
  }

  @Override
  public String toString() {
    if (isRgb()) {
      return String.format("TerminalColor{rgb=#%06x}", rgb);
    }
    return "TerminalColor{" + name + "}";
  }
}
