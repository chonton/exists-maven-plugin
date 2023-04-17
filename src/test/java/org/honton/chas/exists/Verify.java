package org.honton.chas.exists;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Verify {

  private final Path basePath;
  private final BuildProperties gav;
  private final String goal;
  private final String property;

  Verify(File basedir, String goal) throws IOException {
    basePath = basedir.toPath();
    gav = new BuildProperties(basePath);
    this.goal = goal;
    property = goal.equals("local") ?"maven.install.skip" :"maven.deploy.skip";
  }

  private static void findExactLine(BufferedReader reader, String expected) throws IOException {
    for (; ; ) {
      String line = reader.readLine();
      if (line == null) {
        throw new IllegalStateException(expected + " not found");
      }
      if (line.equals(expected)) {
        return;
      }
    }
  }

  private static void findMatchLine(BufferedReader reader, String expected) throws IOException {
    for (; ; ) {
      String line = reader.readLine();
      if (line == null) {
        throw new IllegalStateException(expected + " not found");
      }
      if (line.startsWith(expected)) {
        return;
      }
    }
  }

  private void checkBuildLog(LineConsumer... consumers) throws IOException {
    Path path = basePath.resolve("build.log");
    try (BufferedReader reader = Files.newBufferedReader(path)) {
      for (LineConsumer consumer : consumers) {
        consumer.accept(reader);
      }
    }
  }

  private void doesNotExist(BufferedReader reader) throws IOException {
    findExactLine(reader, "[INFO] " + coordinates() + " does not exist");
  }

  private void beforeInstallation(BufferedReader reader) throws IOException {
    findExactLine(reader, goalExecution("before-installation"));
  }

  private void afterInstallation(BufferedReader reader) throws IOException {
    findExactLine(reader, goalExecution("after-installation"));
  }

  public void snapshotTimeSet(BufferedReader reader) throws IOException {
    findMatchLine(reader, "[INFO] setting artifactTimestamp=");
  }

  private void settingProperty(BufferedReader reader) throws IOException {
    findExactLine(reader, "[INFO] setting "+property+"=true");
  }

  public void afterInstallationError(BufferedReader reader) throws IOException {
    findExactLine(
        reader,
        "[ERROR] Failed to execute goal "
            + "org.honton.chas:"
            + getPlugin("exists-maven-plugin")
            + " (after-installation) on project "
            + gav.artifactId
            + ": Artifact already exists in repository: "
            + coordinates()
            + " -> [Help 1]");
  }

  private void requireGoal(BufferedReader reader) throws IOException {
    findExactLine(reader, "[INFO] install is not present in [verify], skipping execution");
  }

  public void checkBuildLog() throws IOException {
    checkBuildLog(
        this::beforeInstallation,
        this::doesNotExist,
        this::afterInstallation,
        this::settingProperty);
  }

  public void checkInstallWithTestJar() throws IOException {
    checkBuildLog(
        this::beforeInstallation,
        this::afterInstallation,
        this::settingProperty);
  }

  public void failedToExecuteGoal() throws IOException {
    checkBuildLog(this::beforeInstallation, this::afterInstallation, this::afterInstallationError);
  }

  public void checkSnapshotTime() throws IOException {
    checkBuildLog(
        this::beforeInstallation,
        this::doesNotExist,
        this::afterInstallation,
        this::snapshotTimeSet,
        this::settingProperty);
  }

  public void requireGoal() throws IOException {
    checkBuildLog(
        this::beforeInstallation, this::requireGoal, this::afterInstallation, this::requireGoal);
  }

  private String goalExecution(String executionId) {
    return "[INFO]" + " --- " + getPlugin("exists-maven-plugin") + " (" + executionId + ") @ " + gav.artifactId + " ---";
  }

  private String coordinates() {
    return gav.groupId + ':' + gav.artifactId + ":jar:" + gav.version;
  }

  private String getPlugin(String name) {
    return name +':'+gav.pluginVersion + ':' + goal;
  }

  @FunctionalInterface
  public interface LineConsumer {
    void accept(BufferedReader r) throws IOException;
  }
}
