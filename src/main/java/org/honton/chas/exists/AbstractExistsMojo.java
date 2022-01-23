package org.honton.chas.exists;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.maven.artifact.Artifact;
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

  private static final Pattern GAV_PARSER = Pattern.compile("^([^:]*):([^:]*):([^:]*)$");

  @Parameter(defaultValue = "${project}", required = true, readonly = true)
  private MavenProject mavenProject;

  @Parameter(defaultValue = "${session}", required = true, readonly = true)
  private MavenSession session;

  /** The project Group:Artifact:Version to compare. Defaults to the current project's GAV. */
  @Parameter(
      property = "exists.project",
      defaultValue = "${project.groupId}:${project.artifactId}:${project.version}")
  private String project;

  /** The artifact of the project to compare. Defaults to the project's principal artifact. */
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

  protected abstract String getRemoteChecksum(String s) throws Exception;

  protected abstract String getRepositoryBase() throws MojoExecutionException;

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
      if (skipIfSnapshot && isSnapshot()) {
        getLog().debug("skipping -SNAPSHOT");
        return;
      }

      String uri = getRepositoryUri();
      if (!artifactExists(uri)) {
        return;
      }

      if (cmpChecksum && !checksumMatches(uri)) {
        return;
      }

      String propertyName = getPropertyName();
      if (userProperty) {
        getLog().info("setting user property " + propertyName + "=true");
        session.getUserProperties().setProperty(propertyName, "true");
      } else {
        getLog().info("setting " + propertyName + "=true");
        mavenProject.getProperties().setProperty(propertyName, "true");
      }
    } catch (MojoExecutionException | MojoFailureException e) {
      throw e;
    } catch (Exception e) {
      throw new MojoExecutionException(e.getMessage(), e);
    }
  }

  private boolean artifactExists(String uri) throws Exception {
    getLog().debug("Checking for artifact at " + uri);
    boolean exists = checkArtifactExists(uri);
    if (exists) {
      if (failIfExists) {
        throw new MojoFailureException(
            "Artifact already exists in repository: " + project + "/" + artifact);
      }
    } else {
      getLog().info(project + " does not exist");
      if (failIfNotExists) {
        throw new MojoFailureException(
            "Artifact does not exist in repository: " + project + "/" + artifact);
      }
    }
    return exists;
  }

  protected boolean isSnapshot() {
    return project.endsWith("-SNAPSHOT");
  }

  protected abstract boolean checkArtifactExists(String uri) throws Exception;

  private boolean checksumMatches(String uri) throws Exception {
    getLog().debug("checking for resource " + uri);
    String prior = getPriorChecksum(uri);
    String build = getArtifactChecksum();
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

  private String getPriorChecksum(String uri) throws Exception {
    return getRemoteChecksum(uri);
  }

  // https://cwiki.apache.org/confluence/display/MAVEN/Remote+repository+layout
  private String getRepositoryUri() throws Exception {
    Matcher matcher = GAV_PARSER.matcher(project);
    if (!matcher.matches()) {
      throw new MojoFailureException(
          "Project property must be in format groupId:artifactId:version");
    }

    String groupId = matcher.group(1);
    String artifactId = matcher.group(2);
    String version = matcher.group(3);

    // ${groupId.replace('.','/')}/${artifactId}${platformId==null?'':'-'+platformId}/${version}/
    // ${artifactId}${platformId==null?'':'-'+platformId}-${version}${classifier==null?'':'-'+classifier}.${type}
    return getRepositoryBase()
        + '/'
        + groupId.replace('.', '/')
        + '/'
        + artifactId
        + '/'
        + version
        + '/'
        + artifact;
  }

  private String getArtifactChecksum() throws Exception {
    CheckSum signer = new CheckSum("SHA-1");
    Artifact mavenArtifact = mavenProject.getArtifact();
    File file;
    if ("pom".equals(mavenArtifact.getType())) {
      file = new File(mavenProject.getBasedir(), "pom.xml");
    } else {
      file = new File(mavenProject.getBuild().getDirectory(), artifact);
    }
    if (file.isFile()) {
      getLog().debug("Calculating checksum for " + file);
      return signer.getChecksum(file);
    } else {
      throw new MojoFailureException("The project artifact " + file + " has not been created.");
    }
  }
}
