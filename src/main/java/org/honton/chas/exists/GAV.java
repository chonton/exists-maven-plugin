package org.honton.chas.exists;

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

  GAV(String project, String packaging, String configuredClassifier) throws MojoFailureException {
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
  }

  GAV(String project, String packaging) throws MojoFailureException {
    this(project, packaging, null);
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
    return '/' + groupId.replace('.', '/') + '/' + artifactId + '/' + version + '/';
  }

  private String artifactFile(String version) {
    // ${artifactId}${platformId==null?'':'-'+platformId}-${version}${classifier==null?'':'-'+classifier}.${type}
    return artifactId
        + '-'
        + version
        + (classifier != null ? "-" + classifier : "")
        + '.'
        + extension();
  }

  // https://maven.apache.org/ref/3.9.3/maven-core/artifact-handlers.html
  private String extension() {
    switch (type) {
      case "ejb":
      case "java-doc":
      case "maven-plugin":
        return "jar";
      default:
        return type;
    }
  }
}
