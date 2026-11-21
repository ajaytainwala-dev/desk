package desk.java.lsp.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON parse/serialize for LSP/JSON-RPC (no external JSON dependency).
 */
public final class Json {

  private Json() {
  }

  public static Object parse(String text) {
    Parser p = new Parser(text);
    Object value = p.parseValue();
    p.skipWs();
    if (p.i < p.s.length()) {
      throw new IllegalArgumentException("Trailing JSON at " + p.i);
    }
    return value;
  }

  @SuppressWarnings("unchecked")
  public static Map<String, Object> parseObject(String text) {
    Object v = parse(text);
    if (!(v instanceof Map)) {
      throw new IllegalArgumentException("Expected JSON object");
    }
    return (Map<String, Object>) v;
  }

  public static String stringify(Object value) {
    StringBuilder sb = new StringBuilder();
    write(sb, value);
    return sb.toString();
  }

  @SuppressWarnings("unchecked")
  private static void write(StringBuilder sb, Object value) {
    if (value == null) {
      sb.append("null");
    } else if (value instanceof String s) {
      sb.append('"');
      for (int i = 0; i < s.length(); i++) {
        char c = s.charAt(i);
        switch (c) {
          case '\\' -> sb.append("\\\\");
          case '"' -> sb.append("\\\"");
          case '\n' -> sb.append("\\n");
          case '\r' -> sb.append("\\r");
          case '\t' -> sb.append("\\t");
          default -> {
            if (c < 0x20) {
              sb.append(String.format("\\u%04x", (int) c));
            } else {
              sb.append(c);
            }
          }
        }
      }
      sb.append('"');
    } else if (value instanceof Number || value instanceof Boolean) {
      sb.append(value);
    } else if (value instanceof Map<?, ?> map) {
      sb.append('{');
      boolean first = true;
      for (Map.Entry<?, ?> e : map.entrySet()) {
        if (!first) {
          sb.append(',');
        }
        first = false;
        write(sb, String.valueOf(e.getKey()));
        sb.append(':');
        write(sb, e.getValue());
      }
      sb.append('}');
    } else if (value instanceof List<?> list) {
      sb.append('[');
      for (int i = 0; i < list.size(); i++) {
        if (i > 0) {
          sb.append(',');
        }
        write(sb, list.get(i));
      }
      sb.append(']');
    } else {
      write(sb, String.valueOf(value));
    }
  }

  public static Map<String, Object> obj(Object... kv) {
    Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i + 1 < kv.length; i += 2) {
      map.put(String.valueOf(kv[i]), kv[i + 1]);
    }
    return map;
  }

  public static List<Object> arr(Object... values) {
    List<Object> list = new ArrayList<>();
    for (Object v : values) {
      list.add(v);
    }
    return list;
  }

  @SuppressWarnings("unchecked")
  public static Map<String, Object> asObject(Object v) {
    return v instanceof Map ? (Map<String, Object>) v : null;
  }

  @SuppressWarnings("unchecked")
  public static List<Object> asArray(Object v) {
    return v instanceof List ? (List<Object>) v : null;
  }

  public static String asString(Object v) {
    return v == null ? null : String.valueOf(v);
  }

  public static int asInt(Object v, int defaultValue) {
    if (v instanceof Number n) {
      return n.intValue();
    }
    if (v instanceof String s) {
      try {
        return Integer.parseInt(s);
      } catch (NumberFormatException e) {
        return defaultValue;
      }
    }
    return defaultValue;
  }

  private static final class Parser {
    final String s;
    int i;

    Parser(String s) {
      this.s = s;
      this.i = 0;
    }

    void skipWs() {
      while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
        i++;
      }
    }

    Object parseValue() {
      skipWs();
      if (i >= s.length()) {
        throw new IllegalArgumentException("Unexpected end of JSON");
      }
      char c = s.charAt(i);
      return switch (c) {
        case '{' -> parseObject();
        case '[' -> parseArray();
        case '"' -> parseString();
        case 't' -> parseLiteral("true", Boolean.TRUE);
        case 'f' -> parseLiteral("false", Boolean.FALSE);
        case 'n' -> parseLiteral("null", null);
        default -> parseNumber();
      };
    }

    Object parseLiteral(String lit, Object value) {
      if (!s.startsWith(lit, i)) {
        throw new IllegalArgumentException("Expected " + lit);
      }
      i += lit.length();
      return value;
    }

    Map<String, Object> parseObject() {
      Map<String, Object> map = new LinkedHashMap<>();
      i++; // {
      skipWs();
      if (i < s.length() && s.charAt(i) == '}') {
        i++;
        return map;
      }
      while (true) {
        skipWs();
        String key = parseString();
        skipWs();
        if (i >= s.length() || s.charAt(i) != ':') {
          throw new IllegalArgumentException("Expected ':'");
        }
        i++;
        Object value = parseValue();
        map.put(key, value);
        skipWs();
        if (i < s.length() && s.charAt(i) == ',') {
          i++;
          continue;
        }
        if (i < s.length() && s.charAt(i) == '}') {
          i++;
          return map;
        }
        throw new IllegalArgumentException("Expected ',' or '}'");
      }
    }

    List<Object> parseArray() {
      List<Object> list = new ArrayList<>();
      i++; // [
      skipWs();
      if (i < s.length() && s.charAt(i) == ']') {
        i++;
        return list;
      }
      while (true) {
        list.add(parseValue());
        skipWs();
        if (i < s.length() && s.charAt(i) == ',') {
          i++;
          continue;
        }
        if (i < s.length() && s.charAt(i) == ']') {
          i++;
          return list;
        }
        throw new IllegalArgumentException("Expected ',' or ']'");
      }
    }

    String parseString() {
      if (s.charAt(i) != '"') {
        throw new IllegalArgumentException("Expected string");
      }
      i++;
      StringBuilder sb = new StringBuilder();
      while (i < s.length()) {
        char c = s.charAt(i++);
        if (c == '"') {
          return sb.toString();
        }
        if (c == '\\') {
          if (i >= s.length()) {
            throw new IllegalArgumentException("Bad escape");
          }
          char e = s.charAt(i++);
          switch (e) {
            case '"', '\\', '/' -> sb.append(e);
            case 'b' -> sb.append('\b');
            case 'f' -> sb.append('\f');
            case 'n' -> sb.append('\n');
            case 'r' -> sb.append('\r');
            case 't' -> sb.append('\t');
            case 'u' -> {
              if (i + 4 > s.length()) {
                throw new IllegalArgumentException("Bad unicode escape");
              }
              int code = Integer.parseInt(s.substring(i, i + 4), 16);
              sb.append((char) code);
              i += 4;
            }
            default -> throw new IllegalArgumentException("Bad escape \\" + e);
          }
        } else {
          sb.append(c);
        }
      }
      throw new IllegalArgumentException("Unterminated string");
    }

    Number parseNumber() {
      int start = i;
      if (s.charAt(i) == '-') {
        i++;
      }
      while (i < s.length() && Character.isDigit(s.charAt(i))) {
        i++;
      }
      boolean frac = false;
      if (i < s.length() && s.charAt(i) == '.') {
        frac = true;
        i++;
        while (i < s.length() && Character.isDigit(s.charAt(i))) {
          i++;
        }
      }
      if (i < s.length() && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
        frac = true;
        i++;
        if (i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
          i++;
        }
        while (i < s.length() && Character.isDigit(s.charAt(i))) {
          i++;
        }
      }
      String num = s.substring(start, i);
      if (frac) {
        return Double.parseDouble(num);
      }
      long v = Long.parseLong(num);
      if (v >= Integer.MIN_VALUE && v <= Integer.MAX_VALUE) {
        return (int) v;
      }
      return v;
    }
  }
}
