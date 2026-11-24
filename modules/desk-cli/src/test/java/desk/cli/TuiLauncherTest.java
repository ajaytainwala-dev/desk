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
  void rejectsShellLauncherAsTuiBinary() throws Exception {
    Path launcher = temp.resolve("desk");
    Files.writeString(launcher, "#!/bin/sh\nexec java -jar app.jar \"$@\"\n");
    launcher.toFile().setExecutable(true);
    assertFalse(TuiLauncher.isUsableTuiBinary(launcher));
    assertFalse(TuiLauncher.looksLikeNativeBinary(launcher));
  }

  @Test
  void rejectsBatchLauncherAsTuiBinary() throws Exception {
    Path launcher = temp.resolve("desk.cmd");
    Files.writeString(launcher, "@echo off\r\njava -jar app.jar %*\r\n");
    assertFalse(TuiLauncher.looksLikeNativeBinary(launcher));
  }

  @Test
  void acceptsElfMagicAsNativeBinary() throws Exception {
    Path elf = temp.resolve("desk-tui");
    Files.write(elf, new byte[]{0x7F, 'E', 'L', 'F', 0, 1, 2, 3});
    elf.toFile().setExecutable(true);
    assertTrue(TuiLauncher.looksLikeNativeBinary(elf));
    assertTrue(TuiLauncher.isUsableTuiBinary(elf));
  }

  @Test
  void acceptsPeMagicAsNativeBinary() throws Exception {
    Path pe = temp.resolve("desk-tui.exe");
    Files.write(pe, new byte[]{'M', 'Z', 0, 0, 1, 2, 3, 4});
    assertTrue(TuiLauncher.looksLikeNativeBinary(pe));
  }

  @Test
  void locateEditorBinaryIgnoresShellDeskInBin() throws Exception {
    Path bin = temp.resolve("bin");
    Files.createDirectories(bin);
    Path launcher = bin.resolve("desk");
    Files.writeString(launcher, "#!/bin/sh\necho nope\n");
    launcher.toFile().setExecutable(true);

    Path previous = Path.of("").toAbsolutePath();
    try {
      System.setProperty("user.dir", temp.toString());
      // Candidate list includes cwd-relative bin/desk; ensure shell script is skipped.
      assertFalse(TuiLauncher.isUsableTuiBinary(launcher));
    } finally {
      System.setProperty("user.dir", previous.toString());
    }
  }
}
