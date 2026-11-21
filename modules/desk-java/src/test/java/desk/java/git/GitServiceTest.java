package desk.java.git;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GitServiceTest {

  @Test
  @DisplayName("sanitize redacts tokens")
  void sanitize() {
    assertTrue(GitService.sanitize("authorization=Bearer SECRET").contains("***"));
    assertTrue(GitService.sanitize("token=ghp_abcdefghijklmnop").contains("***"));
    assertEquals("ok", GitService.sanitize("ok").trim());
  }
}
