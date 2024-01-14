package org.honton.chas.exists;

import java.io.StringReader;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.metadata.Metadata;
import org.apache.maven.artifact.repository.metadata.SnapshotVersion;
import org.apache.maven.artifact.repository.metadata.Versioning;
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Reader;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * Set a property if the artifact in a local or remote repository is same as the just built
 * artifact.
 */
public abstract class AbstractExistsMojo extends AbstractMojo {

  @Parameter(defaultValue = "${project}", required = true, readonly = true)
  private MavenProject mavenProject;

  @Parameter(defaultValue = "${session}", required = true, readonly = true)
  private MavenSession session;

  /**
   * The project Group:Artifact:[:Type]:Version to compare. Defaults to the current project's GATV.
   */
  @Parameter(
      property = "exists.project",
      defaultValue =
          "${project.groupId}:${project.artifactId}:${project.packaging}:${project.version}")
  private String project;

  /**
   * The classifier to use, e.g., 'tests'. Will be appended to the artifact name. Useful if there is
   * no main artifact.
   */
  @Parameter(property = "exists.classifier")
  private String classifier;

  /** The build artifact of the project to compare. Defaults to the project's principal artifact. */
  @Parameter(
      property = "exists.artifact",
      defaultValue = "${project.build.finalName}.${project.packaging}")
  private String artifact;

  /** Set whether checksum is used to compare artifacts. */
  @Parameter(property = "exists.cmpChecksum", defaultValue = "false")
  private boolean cmpChecksum;

  /** If checksums are not used, should this plugin skip checking SNAPSHOT versions? */
  @Parameter(property = "exists.skipIfSnapshot", defaultValue = "true")
  private boolean skipIfSnapshot;

  /**
   * Set the property as a user property instead of a project property. This will make the property
   * available in the modules of a parent POM.
   *
   * @since 0.0.3
   */
  @Parameter(property = "exists.userProperty", defaultValue = "false")
  private boolean userProperty;

  /**
   * Fail the build if the artifact already exists in the repository.
   *
   * @since 0.0.3
   */
  @Parameter(property = "exists.failIfExists", defaultValue = "false")
  private boolean failIfExists;

  /**
   * Fail the build if the artifact does not exist in the repository.
   *
   * @since 0.0.3
   */
  @Parameter(property = "exists.failIfNotExists", defaultValue = "false")
  private boolean failIfNotExists;

  /**
   * Fail the build if the artifact checksum does not match the current repository artifact.
   *
   * @since 0.1.0
   */
  @Parameter(property = "exists.failIfNotMatch", defaultValue = "true")
  private boolean failIfNotMatch;

  /**
   * Skip executing this plugin
   *
   * @since 0.0.4
   */
  @Parameter(property = "exists.skip", defaultValue = "false")
  private boolean skip;

  /**
   * Execute goal only if requireGoal value matches one of the maven command line goals
   *
   * @since 0.6.0
   */
  @Parameter(property = "exists.requireGoal")
  private String requireGoal;

  /**
   * The property to set with the timestamp of the last snapshot artifact available in the
   * repository.
   *
   * @since 0.7.0
   */
  @Parameter(property = "exists.lastSnapshotTime")
  private String lastSnapshotTime;

  /**
   * Package to file extension mappings.
   *
   * @since 0.14.0
   */
  @Parameter private Map<String, String> packageExtensions;

  protected GAV gav;

  static Path getPath(String first, String... more) {
    return FileSystems.getDefault().getPath(first, more);
  }

  protected abstract String getArtifactChecksum(String s) throws Exception;

  protected abstract String getPropertyName();

  public void execute() throws MojoExecutionException, MojoFailureException {
    if (skip) {
      getLog().info("skipping exists execution");
      return;
    }

    if (requireGoal != null && !session.getGoals().contains(requireGoal)) {
      getLog()
          .info(requireGoal + " is not present in " + session.getGoals() + ", skipping execution");
      return;
    }

    try {
      gav = new GAV(project, mavenProject.getPackaging(), classifier, packageExtensions);
      boolean snapshot = isSnapshot();
      if (skipIfSnapshot && snapshot) {
        getLog().debug("skipping -SNAPSHOT");
        return;
      }

      String path;
      if (snapshot) {
        path = snapshotPath();
        if (path == null) {
          checkFailConditions(false);
          return;
        }
      } else {
        path = gav.artifactLocation();
      }

      boolean exists = checkArtifactExists(path);
      checkFailConditions(exists);
      if (!exists) {
        return;
      }

      if (cmpChecksum && !checksumMatches(path)) {
        return;
      }

      setProperty(getPropertyName(), "true");
    } catch (MojoExecutionException | MojoFailureException e) {
      throw e;
    } catch (Exception e) {
      throw new MojoExecutionException(e.getMessage(), e);
    }
  }

  private void setProperty(String propertyName, String value) {
    if (userProperty) {
      getLog().info("setting user property " + propertyName + '=' + value);
      session.getUserProperties().setProperty(propertyName, value);
    } else {
      getLog().info("setting " + propertyName + '=' + value);
      mavenProject.getProperties().setProperty(propertyName, value);
    }
  }

  void setLastSnapshotTime(String updated) {
    if (lastSnapshotTime != null) {
      setProperty(lastSnapshotTime, updated);
    }
  }

  private void checkFailConditions(boolean exists) throws MojoFailureException {
    if (exists) {
      if (failIfExists) {
        throw new MojoFailureException("Artifact already exists in repository: " + project);
      }
      getLog().info(project + " exists");
    } else {
      if (failIfNotExists) {
        throw new MojoFailureException("Artifact does not exist in repository: " + project);
      }
      getLog().info(project + " does not exist");
    }
  }

  protected boolean isSnapshot() {
    return gav.version.endsWith("-SNAPSHOT");
  }

  private String snapshotPath() {
    try {
      String directory = gav.artifactDirectory();

      Metadata metadata =
          new MetadataXpp3Reader().read(new StringReader(getMavenMetadata(directory)));
      Versioning versioning = metadata.getVersioning();

      for (SnapshotVersion version : versioning.getSnapshotVersions()) {
        if (gav.type.equals(version.getExtension())) {
          getLog().debug("version=" + version.getVersion());
          setLastSnapshotTime(version.getUpdated());
          return getVersionedPath(version);
        }
      }
    } catch (Exception e) {
      getLog().debug("Could not fetch/read metadata, assuming no snapshot " + e.getMessage());
    }
    return null;
  }

  protected abstract String getVersionedPath(SnapshotVersion version);

  protected abstract String getMavenMetadata(String path) throws Exception;

  protected abstract boolean checkArtifactExists(String path) throws Exception;

  private boolean checksumMatches(String path) throws Exception {
    String prior = getArtifactChecksum(path);
    String build = getBuildChecksum();
    boolean matches = build.equalsIgnoreCase(prior);
    if (!matches) {
      getLog().info(project + " checksum does not match");
      if (failIfNotMatch) {
        String msg = "buildChecksum(" + build + ") != priorChecksum(" + prior + ")";
        throw new MojoFailureException(msg);
      }
    }
    return matches;
  }

  private String getBuildChecksum() throws Exception {
    Artifact mavenArtifact = mavenProject.getArtifact();
    Path path;
    if ("pom".equals(mavenArtifact.getType())) {
      path = getPath(mavenProject.getBasedir().toString(), "pom.xml");
    } else {
      path = getPath(mavenProject.getBuild().getDirectory(), artifact);
    }
    if (Files.exists(path)) {
      getLog().debug("Calculating checksum for " + path);
      CheckSum signer = new CheckSum();
      return signer.getChecksum(path);
    } else {
      throw new MojoFailureException("The project artifact " + path + " has not been created.");
    }
  }
}
