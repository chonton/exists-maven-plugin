package org.honton.chas.exists;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class GAVTest {

  @Test
  void artifactLocation() throws MojoFailureException {
    GAV gav = new GAV("groupId:artifactId:packaging:version", "packaging", "classifier", null);
    Assertions.assertEquals(
        "groupId/artifactId/version/artifactId-version-classifier.packaging",
        gav.artifactLocation());
  }

  @Test
  void noClassifier() throws MojoFailureException {
    GAV gav = new GAV("groupId:artifactId:packaging:version", "packaging", null, Map.of());
    Assertions.assertEquals(
        "groupId/artifactId/version/artifactId-version.packaging", gav.artifactLocation());
  }

  @Test
  void mapClassifier() throws MojoFailureException {
    GAV gav =
        new GAV(
            "groupId:artifactId:packaging:version",
            "packaging",
            null,
            Map.of("packaging", "wrapped"));
    Assertions.assertEquals(
        "groupId/artifactId/version/artifactId-version.wrapped", gav.artifactLocation());
  }

  @Test
  void nulls() throws MojoFailureException {
    GAV gav = new GAV("org.honton.chas:exists-maven-plugin:maven-plugin:0.14.0", null, null, null);
    Assertions.assertEquals(
        "org/honton/chas/exists-maven-plugin/0.14.0/exists-maven-plugin-0.14.0.jar",
        gav.artifactLocation());
  }
}
