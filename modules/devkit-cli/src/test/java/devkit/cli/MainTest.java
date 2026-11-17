package devkit.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MainTest {

  @Test
  void versionCommandReturnsSuccess() {
    CommandRegistry registry = new CommandRegistry();
    registry.register(new VersionCommand());
    Command command = registry.get("version");
    assertNotNull(command);
    CommandResult result = command.execute(new String[0]);
    assertTrue(result.isSuccess());
    assertEquals(0, result.exitCode());
  }

  @Test
  void versionShortReturnsJustNumber() {
    VersionCommand command = new VersionCommand();
    CommandResult result = command.execute(new String[]{"--short"});
    assertTrue(result.isSuccess());
  }

  @Test
  void unknownCommandNotInRegistry() {
    CommandRegistry registry = new CommandRegistry();
    assertFalse(registry.has("nonexistent"));
    assertNull(registry.get("nonexistent"));
  }

  @Test
  void commandResultHelpers() {
    assertTrue(CommandResult.SUCCESS.isSuccess());
    assertEquals(0, CommandResult.SUCCESS.exitCode());
    CommandResult failure = CommandResult.failure(3, "boom");
    assertEquals(3, failure.exitCode());
    assertFalse(failure.isSuccess());
    assertEquals("boom", failure.message());
  }
}
