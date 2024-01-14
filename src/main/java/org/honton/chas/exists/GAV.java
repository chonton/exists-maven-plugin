package org.honton.chas.exists;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.maven.plugin.MojoFailureException;

public class GAV {

  // groupId:artifactId[:packaging]:version.
  private static final Pattern GAV_PARSER = Pattern.compile("^([^:]+):([^:]+):([^:]+:)?([^:]+)$");

  final String groupId;
  final String artifactId;
  final String type;
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
          "Project property must be in format groupId:artifactId:[type:]version");
    }

    groupId = matcher.group(1);
    artifactId = matcher.group(2);
    String optional = matcher.group(3);
    type = optional != null ? optional.substring(0, optional.length() - 1) : packaging;
    classifier = configuredClassifier;
    version = matcher.group(4);

    extension = extension(packageExtensions, packaging);
  }

  // https://maven.apache.org/ref/3.9.3/maven-core/artifact-handlers.html
  private static String extension(Map<String, String> packageExtensions, String packaging) {
    if (packaging == null) {
      packaging = "jar";
    }
    switch (packaging) {
      case "pom":
      case "jar":
      case "war":
      case "ear":
      case "rar":
        return packaging;
      case "test-jar":
      case "maven-plugin":
      case "ejb":
      case "ejb-client":
      case "java-source":
      case "java-doc":
        return "jar";
    }
    return packageExtensions != null
        ? packageExtensions.getOrDefault(packaging, packaging)
        : packaging;
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
