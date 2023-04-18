package org.honton.chas.exists.example;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.junit.jupiter.api.Assertions;

class BuildProperties {
  String localRepository;
  String groupId;
  String artifactId;
  String version;
  String pluginVersion;

  BuildProperties() throws IOException {
    Properties properties = new Properties();
    try (InputStream is = getClass().getResourceAsStream("/test.properties")) {
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
