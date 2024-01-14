package org.honton.chas.exists;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CheckSumTest {
  private String localRepositoryPath;
  private String projectGAV;

  @BeforeEach
  void initializePath() {
    localRepositoryPath = System.getProperty("localRepositoryPath");
    projectGAV = System.getProperty("projectGAV");
  }

  private Path getPath(String packaging) throws MojoFailureException {
    Assertions.assertNotNull(projectGAV);
    GAV gav = new GAV(projectGAV, packaging, null, Map.of());
    Assertions.assertNotNull(localRepositoryPath);
    return FileSystems.getDefault().getPath(localRepositoryPath, gav.artifactLocation());
  }

  @Test
  void writeCheckSums() throws NoSuchAlgorithmException, IOException, MojoFailureException {
    CheckSum checkSum = new CheckSum();
    checkSum.writeChecksum(getPath("jar"));
    checkSum.writeChecksum(getPath("pom"));
  }
}
