package org.honton.chas.exists;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Assertions;

class BuildProperties {
  String localRepository;
  String groupId;
  String artifactId;
  String version;
  String pluginVersion;

  BuildProperties(Path basePath) throws IOException {
    Properties properties = new Properties();
    Path path = basePath.resolve("target/test-classes/test.properties");
    try (InputStream is = Files.newInputStream(path)) {
      Assertions.assertNotNull(is);
      properties.load(is);
    }

    localRepository = properties.getProperty("localRepository");
    Assertions.assertNotNull(localRepository);

    groupId = properties.getProperty("groupId");
    Assertions.assertNotNull(groupId);

    artifactId = properties.getProperty("artifactId");
    Assertions.assertNotNull(artifactId);

    version = properties.getProperty("version");
    Assertions.assertNotNull(version);

    pluginVersion = properties.getProperty("pluginVersion");
    Assertions.assertNotNull(pluginVersion);
  }
}
