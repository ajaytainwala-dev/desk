package desk.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TuiLauncherTest {

  @TempDir
  Path temp;

  @Test
  void rejectsShellScriptLaunchers() throws Exception {
    Path script = temp.resolve("desk");
    Files.writeString(script, "#!/bin/sh\nexec java -jar desk-cli.jar \"$@\"\n");
    assertFalse(TuiLauncher.isNativeTuiBinary(script));
  }

  @Test
  void rejectsBatchLaunchers() throws Exception {
    Path bat = temp.resolve("desk.cmd");
    Files.writeString(bat, "@echo off\r\njava -jar desk-cli.jar %*\r\n");
    assertFalse(TuiLauncher.isNativeTuiBinary(bat));
  }

  @Test
  void rejectsEmptyAndMissingFiles() {
    assertFalse(TuiLauncher.isNativeTuiBinary(temp.resolve("missing")));
  }

  @Test
  void acceptsElfLikeBinary() throws Exception {
    Path elf = temp.resolve("desk-tui");
    Files.write(elf, new byte[]{0x7f, 'E', 'L', 'F', 2, 1, 1, 0});
    assertTrue(TuiLauncher.isNativeTuiBinary(elf));
  }

  @Test
  void acceptsPeLikeBinary() throws Exception {
    Path pe = temp.resolve("desk-tui.exe");
    Files.write(pe, new byte[]{'M', 'Z', 0, 0, 0, 0, 0, 0});
    assertTrue(TuiLauncher.isNativeTuiBinary(pe));
  }

  @Test
  void candidatePathsPreferDeskTuiName() {
    assertTrue(TuiLauncher.candidatePaths().stream()
        .anyMatch(p -> p.getFileName().toString().startsWith("desk-tui")));
  }
}
