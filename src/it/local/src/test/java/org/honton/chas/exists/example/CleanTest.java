package org.honton.chas.exists.example;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class CleanTest {
  private BuildProperties buildProperties;

  @Test
  void clean() throws IOException {
    buildProperties = new BuildProperties();
    String groupPath = buildProperties.groupId.replace('.', '/');
    Path repoDir =
        Paths.get(buildProperties.localRepository, groupPath, buildProperties.artifactId);
    if (Files.exists(repoDir)) {
      try (Stream<Path> walk = Files.walk(repoDir)) {
        walk.sorted(Comparator.reverseOrder()).forEach(this::deleteFile);
      }
    }
  }

  void deleteFile(Path path) {
    Assertions.assertDoesNotThrow(() -> Files.delete(path));
  }
}
