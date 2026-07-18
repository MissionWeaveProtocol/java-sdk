package org.missionweaveprotocol.sdk;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;

class ProtocolBundlePackagingIT {
  private static final Path ROOT = Path.of("").toAbsolutePath().normalize();

  @Test
  void builtJarContainsTheVerifiedProtocolBundle() throws IOException {
    Path jarPath =
        Path.of(
            System.getProperty("project.build.directory"),
            System.getProperty("project.build.finalName") + ".jar");
    assertTrue(Files.isRegularFile(jarPath), () -> "Missing built JAR: " + jarPath);

    URL jarUrl = jarPath.toUri().toURL();
    try (URLClassLoader loader = new URLClassLoader(new URL[] {jarUrl}, null);
        JarFile jar = new JarFile(jarPath.toFile())) {
      ProtocolBundle.Verification verification = ProtocolBundle.verifyPackaged(loader);
      assertEquals(ProtocolBundle.BUNDLE_SHA256, verification.bundleSha256());

      assertNotNull(jar.getJarEntry(ProtocolBundle.PIN_RESOURCE));
      assertNotNull(jar.getJarEntry(ProtocolBundle.INDEX_RESOURCE));

      List<String> protocolEntries =
          jar.stream()
              .filter(entry -> !entry.isDirectory())
              .map(JarEntry::getName)
              .filter(
                  name ->
                      (name.startsWith("schemas/") || name.startsWith("conformance/"))
                          && name.endsWith(".json"))
              .sorted()
              .toList();
      assertEquals(74, protocolEntries.size());
      assertEquals(Set.copyOf(ProtocolBundle.resourcePaths(loader)), Set.copyOf(protocolEntries));

      for (String path : protocolEntries) {
        try (InputStream packaged = jar.getInputStream(jar.getJarEntry(path))) {
          assertArrayEquals(Files.readAllBytes(ROOT.resolve(path)), packaged.readAllBytes(), path);
        }
      }
    }
  }
}
