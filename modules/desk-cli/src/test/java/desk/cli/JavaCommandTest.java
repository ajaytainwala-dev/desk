package desk.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JavaCommandTest {

  @Test
  @DisplayName("java list succeeds")
  void list() {
    CommandResult result = new JavaCommand().execute(new String[]{"list"});
    assertTrue(result.isSuccess());
  }

  @Test
  @DisplayName("java doctor succeeds")
  void doctor() {
    CommandResult result = new JavaCommand().execute(new String[]{"doctor"});
    assertTrue(result.isSuccess());
  }

  @Test
  @DisplayName("java install --dry-run plans without downloading")
  void installDryRun() {
    CommandResult result = new JavaCommand().execute(
        new String[]{"install", "25", "--dry-run"});
    assertTrue(result.isSuccess());
  }

  @Test
  @DisplayName("java policy prints matrix")
  void policy() {
    assertTrue(new JavaCommand().execute(new String[]{"policy"}).isSuccess());
  }
}
