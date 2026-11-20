package desk.core.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads configuration from a JSON file.
 *
 * <p>Uses a small hand-written JSON parser so the core module has no heavy
 * JSON dependency. Supports objects, arrays, strings, numbers, booleans, and
 * null. Duplicate keys keep the last value. The parser operates on an
 * in-memory string with an index, which avoids the pitfalls of
 * {@code mark}/{@code reset} based lookahead on {@link Reader}.</p>
 */
public final class JsonConfigLoader {

  private JsonConfigLoader() {
    // utility class
  }

  /**
   * Loads configuration from a file. Returns an empty {@link Config} if the
   * file does not exist. A missing file is never an error.
   *
   * @throws ConfigException if the file exists but cannot be read or parsed.
   */
  public static Config load(Path file) {
    if (file == null || !Files.isRegularFile(file)) {
      return Config.EMPTY;
    }

    String content;
    try {
      content = Files.readString(file, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new ConfigException("Unable to read configuration file " + file, e);
    }

    if (content == null || content.trim().isEmpty()) {
      return Config.EMPTY;
    }

    try {
      Map<String, Object> values = parseObject(content);
      return new DefaultConfig(values);
    } catch (ConfigException e) {
      throw e;
    } catch (Exception e) {
      throw new ConfigException("Unable to parse configuration file " + file + ": " + e.getMessage(), e);
    }
  }

  /* ---------- Minimal JSON parser ---------- */

  /**
   * Parses a top-level JSON object into a map.
   */
  private static Map<String, Object> parseObject(String text) {
    int i = skipWhitespace(text, 0);
    if (i >= text.length() || text.charAt(i) != '{') {
      throw new ConfigException("Expected '{'");
    }
    return parseObject(text, i);
  }

  private static Map<String, Object> parseObject(String text, int start) {
    Map<String, Object> map = new LinkedHashMap<>();
    int i = start + 1; // skip '{'
    i = skipWhitespace(text, i);
    if (i < text.length() && text.charAt(i) == '}') {
      return map;
    }
    while (true) {
      i = skipWhitespace(text, i);
      if (i >= text.length() || text.charAt(i) != '"') {
        throw new ConfigException("Expected string key in object");
      }
      String key = readString(text, i);
      i = endOfString(text, i);
      i = skipWhitespace(text, i);
      if (i >= text.length() || text.charAt(i) != ':') {
        throw new ConfigException("Expected ':' after key");
      }
      i++; // skip ':'
      java.util.Map.Entry<Object, Integer> parsed = parseValue(text, i);
      map.put(key, parsed.getKey());
      i = parsed.getValue();
      i = skipWhitespace(text, i);
      if (i >= text.length()) {
        throw new ConfigException("Unterminated object");
      }
      char sep = text.charAt(i);
      if (sep == ',') {
        i++;
        continue;
      }
      if (sep == '}') {
        return map;
      }
      throw new ConfigException("Expected ',' or '}' in object, got " + sep);
    }
  }

  /**
   * Parses a JSON value starting at {@code start}, returning the value and the
   * index just past the value.
   */
  private static java.util.Map.Entry<Object, Integer> parseValue(String text, int start) {
    int i = skipWhitespace(text, start);
    if (i >= text.length()) {
      throw new ConfigException("Expected a value");
    }
    char ch = text.charAt(i);
    switch (ch) {
      case '{':
        return new java.util.AbstractMap.SimpleEntry<>(parseObject(text, i), endOfObject(text, i));
      case '[':
        return new java.util.AbstractMap.SimpleEntry<>(parseArray(text, i), endOfArray(text, i));
      case '"':
        String s = readString(text, i);
        return new java.util.AbstractMap.SimpleEntry<>(s, endOfString(text, i));
      case 't':
        return literal(text, i, "true", Boolean.TRUE);
      case 'f':
        return literal(text, i, "false", Boolean.FALSE);
      case 'n':
        return literal(text, i, "null", null);
      default:
        return readNumber(text, i);
    }
  }

  private static int endOfObject(String text, int start) {
    int i = start;
    int depth = 0;
    boolean inString = false;
    boolean escape = false;
    while (i < text.length()) {
      char c = text.charAt(i);
      if (escape) {
        escape = false;
      } else if (c == '\\') {
        escape = true;
      } else if (inString) {
        if (c == '"') {
          inString = false;
        }
      } else {
        if (c == '"') {
          inString = true;
        } else if (c == '{') {
          depth++;
        } else if (c == '}') {
          depth--;
          if (depth == 0) {
            return i + 1;
          }
        }
      }
      i++;
    }
    throw new ConfigException("Unterminated object");
  }

  private static int endOfArray(String text, int start) {
    int i = start;
    int depth = 0;
    boolean inString = false;
    boolean escape = false;
    while (i < text.length()) {
      char c = text.charAt(i);
      if (escape) {
        escape = false;
      } else if (c == '\\') {
        escape = true;
      } else if (inString) {
        if (c == '"') {
          inString = false;
        }
      } else {
        if (c == '"') {
          inString = true;
        } else if (c == '[') {
          depth++;
        } else if (c == ']') {
          depth--;
          if (depth == 0) {
            return i + 1;
          }
        }
      }
      i++;
    }
    throw new ConfigException("Unterminated array");
  }

  private static int endOfString(String text, int start) {
    int i = start + 1; // skip opening quote
    boolean escape = false;
    while (i < text.length()) {
      char c = text.charAt(i);
      if (escape) {
        escape = false;
      } else if (c == '\\') {
        escape = true;
      } else if (c == '"') {
        return i + 1;
      }
      i++;
    }
    throw new ConfigException("Unterminated string");
  }

  private static String readString(String text, int start) {
    // start points at the opening quote
    int i = start + 1;
    StringBuilder sb = new StringBuilder();
    while (i < text.length()) {
      char c = text.charAt(i);
      if (c == '\\') {
        i++;
        if (i >= text.length()) {
          throw new ConfigException("Unterminated escape sequence");
        }
        char esc = text.charAt(i);
        switch (esc) {
          case '"':
            sb.append('"');
            break;
          case '\\':
            sb.append('\\');
            break;
          case '/':
            sb.append('/');
            break;
          case 'b':
            sb.append('\b');
            break;
          case 'f':
            sb.append('\f');
            break;
          case 'n':
            sb.append('\n');
            break;
          case 'r':
            sb.append('\r');
            break;
          case 't':
            sb.append('\t');
            break;
          case 'u':
            if (i + 4 >= text.length()) {
              throw new ConfigException("Invalid unicode escape");
            }
            String hex = text.substring(i + 1, i + 5);
            if (!isHexString(hex)) {
              throw new ConfigException("Invalid unicode escape");
            }
            sb.append((char) Integer.parseInt(hex, 16));
            i += 4;
            break;
          default:
            throw new ConfigException("Invalid escape sequence \\" + esc);
        }
      } else if (c == '"') {
        return sb.toString();
      } else {
        sb.append(c);
      }
      i++;
    }
    throw new ConfigException("Unterminated string");
  }

  private static java.util.Map.Entry<Object, Integer> literal(String text, int start,
                                                              String lit, Object value) {
    int end = start + lit.length();
    if (end > text.length() || !text.regionMatches(start, lit, 0, lit.length())) {
      throw new ConfigException("Invalid literal");
    }
    return new java.util.AbstractMap.SimpleEntry<>(value, end);
  }

  private static java.util.Map.Entry<Object, Integer> readNumber(String text, int start) {
    int i = start;
    while (i < text.length()) {
      char c = text.charAt(i);
      if (Character.isDigit(c) || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') {
        i++;
      } else {
        break;
      }
    }
    String numText = text.substring(start, i);
    if (numText.isEmpty()) {
      throw new ConfigException("Expected a value");
    }
    try {
      if (numText.indexOf('.') >= 0 || numText.indexOf('e') >= 0 || numText.indexOf('E') >= 0) {
        return new java.util.AbstractMap.SimpleEntry<>(Double.parseDouble(numText), i);
      }
      return new java.util.AbstractMap.SimpleEntry<>(Long.parseLong(numText), i);
    } catch (NumberFormatException e) {
      throw new ConfigException("Invalid number: " + numText, e);
    }
  }

  private static int skipWhitespace(String text, int start) {
    int i = start;
    while (i < text.length()) {
      char c = text.charAt(i);
      if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
        i++;
      } else {
        break;
      }
    }
    return i;
  }

  private static boolean isHexString(String s) {
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) {
        return false;
      }
    }
    return true;
  }

  private static java.util.List<Object> parseArray(String text, int start) {
    java.util.List<Object> list = new java.util.ArrayList<>();
    int i = start;
    if (i >= text.length() || text.charAt(i) != '[') {
      throw new ConfigException("Expected '['");
    }
    i++;
    i = skipWhitespace(text, i);
    if (i < text.length() && text.charAt(i) == ']') {
      return list;
    }
    while (true) {
      java.util.Map.Entry<Object, Integer> parsed = parseValue(text, i);
      list.add(parsed.getKey());
      i = parsed.getValue();
      i = skipWhitespace(text, i);
      if (i >= text.length()) {
        throw new ConfigException("Unterminated array");
      }
      char sep = text.charAt(i);
      if (sep == ',') {
        i++;
        continue;
      }
      if (sep == ']') {
        return list;
      }
      throw new ConfigException("Expected ',' or ']' in array, got " + sep);
    }
  }
}
