package desk.java.lsp.json;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonTest {

  @Test
  void roundTripObject() {
    Map<String, Object> obj = Json.obj(
        "jsonrpc", "2.0",
        "id", 1,
        "method", "initialize",
        "params", Json.obj("rootUri", "file:///tmp"));
    String text = Json.stringify(obj);
    Map<String, Object> parsed = Json.parseObject(text);
    assertEquals("2.0", parsed.get("jsonrpc"));
    assertEquals(1, Json.asInt(parsed.get("id"), -1));
    assertEquals("initialize", parsed.get("method"));
  }

  @Test
  void escapesStrings() {
    String s = Json.stringify(Json.obj("a", "line\n\"x\""));
    assertTrue(s.contains("\\n"));
    Map<String, Object> parsed = Json.parseObject(s);
    assertEquals("line\n\"x\"", parsed.get("a"));
  }

  @Test
  void arrays() {
    Object v = Json.parse("[1, true, null, \"x\"]");
    List<Object> arr = Json.asArray(v);
    assertNotNull(arr);
    assertEquals(4, arr.size());
  }
}
