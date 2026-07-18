package org.missionweaveprotocol.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProtocolImplementationPackagingIT {
  @Test
  void packagedSchemasVectorsAndCodecWorkFromTheBuiltJar() throws IOException {
    Path jarPath =
        Path.of(
            System.getProperty("project.build.directory"),
            System.getProperty("project.build.finalName") + ".jar");
    assertTrue(Files.isRegularFile(jarPath), () -> "Missing built JAR: " + jarPath);

    URL jarUrl = jarPath.toUri().toURL();
    try (URLClassLoader loader = new URLClassLoader(new URL[] {jarUrl}, null)) {
      SchemaCatalog catalog = SchemaCatalog.packaged(loader);
      ConformanceReport report = ConformanceRunner.runPackaged(loader);
      assertEquals(21, catalog.schemaNames().size());
      assertEquals("52/52 conformance vectors passed", report.summary());
      assertTrue(report.passed());

      try (InputStream input =
          loader.getResourceAsStream("conformance/vectors/valid/websocket-frame.json")) {
        if (input == null) {
          throw new IOException("Built JAR is missing the WebSocket frame vector");
        }
        new FrameCodec(catalog).decode(input.readAllBytes());
      }
    }
  }
}
