package org.honton.chas.exists;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.maven.plugin.MojoFailureException;

public class GAV {

  // groupId:artifactId[:packaging]:version.
  private static final Pattern GAV_PARSER = Pattern.compile("^([^:]+):([^:]+):(([^:]+):)?([^:]+)$");

  final String groupId;
  final String artifactId;
  final String classifier;
  final String version;
  final String extension;

  GAV(
      String project,
      String packaging,
      String configuredClassifier,
      Map<String, String> packageExtensions)
      throws MojoFailureException {
    Matcher matcher = GAV_PARSER.matcher(project);
    if (!matcher.matches()) {
      throw new MojoFailureException(
          "Project property must be in format groupId:artifactId:[packaging:]version");
    }

    groupId = matcher.group(1);
    artifactId = matcher.group(2);
    String optional = matcher.group(4);
    version = matcher.group(5);
    String packagingType = packaging != null ? packaging : "jar";
    classifier = configuredClassifier != null ? configuredClassifier : classifier(packagingType);
    extension = extension(packageExtensions, optional != null ? optional : packagingType);
  }

  // https://maven.apache.org/ref/current/maven-core/artifact-handlers.html
  private static String classifier(String packagingType) {
    return switch (packagingType) {
      case "test-jar" -> "tests";
      case "ejb-client" -> "client";
      case "java-source", "javadoc" -> "sources";
      default -> null;
    };
  }

  // https://maven.apache.org/ref/current/maven-core/artifact-handlers.html
  private static String extension(Map<String, String> packageExtensions, String packagingType) {
    return switch (packagingType) {
      case "pom", "war", "ear", "rar" -> packagingType;
      case "jar", "test-jar", "maven-plugin", "ejb", "ejb-client", "java-source", "javadoc" ->
          "jar";
      default ->
          packageExtensions != null
              ? packageExtensions.getOrDefault(packagingType, packagingType)
              : packagingType;
    };
  }

  // https://cwiki.apache.org/confluence/display/MAVEN/Remote+repository+layout
  String artifactLocation() {
    return artifactDirectory() + artifactFile(version);
  }

  String snapshotLocation(String buildVersion) {
    return artifactDirectory() + artifactFile(buildVersion);
  }

  String artifactDirectory() {
    // ${groupId.replace('.','/')}/${artifactId}${platformId==null?'':'-'+platformId}/${version}/
    return groupId.replace('.', '/') + '/' + artifactId + '/' + version + '/';
  }

  private String artifactFile(String version) {
    // ${artifactId}${platformId==null?'':'-'+platformId}-${version}${classifier==null?'':'-'+classifier}.${type}
    return artifactId
        + '-'
        + version
        + (classifier != null ? "-" + classifier : "")
        + '.'
        + extension;
  }
}
