package org.honton.chas.exists;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
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
 * Set a property if the artifact in a local or remote repository is same as the just built artifact.
 */
public abstract class AbstractExistsMojo extends AbstractMojo {

  @Parameter(defaultValue = "${project}", readonly = true)
  private MavenProject mavenProject;

  /**
   * The project Group:Artifact:Version to compare.  Defaults to the current project's GAV.
   */
  @Parameter(defaultValue = "${project.groupId}:${project.artifactId}:${project.version}")
  private String project;

  /**
   * The artifact of the project to compare.  Defaults to the project's principal artifact.
   */
  @Parameter(defaultValue = "${project.build.finalName}.${project.packaging}")
  private String artifact;

  /**
   * Set whether checksum is used to compare artifacts.  The default is the <em>install</em>
   * plugin's configuration to create checksums (property <em>createChecksum</em>).
   */
  @Parameter(defaultValue = "${createChecksum}")
  private boolean useChecksum;

  /**
   * If checksums are not used, should this plugin skip checking SNAPSHOT versions?
   */
  @Parameter(defaultValue = "true")
  private boolean skipIfSnapshot;

  /**
   * Set the property as a user property instead of a project property.  This will make the property
   * available in the modules of a parent POM.
   *
   * @since 0.0.3
   */
  @Parameter(defaultValue = "false")
  private boolean userProperty;

  /**
   * Fail the build if the artifact already exists in the repository.
   *
   * @since 0.0.3
   */
  @Parameter(defaultValue = "${failIfExists}")
  private boolean failIfExists;

  /**
   * Fail the build if the artifact does not exist in the repository.
   *
   * @since 0.0.3
   */
  @Parameter(defaultValue = "${failIfNotExists}")
  private boolean failIfNotExists;


  /**
   * Fail the build if the artifact checksum does not match the current repository artifact.
   *
   * @since 0.0.7
   */
  @Parameter(defaultValue = "${failIfNotMatches}")
  private boolean failIfNotMatches;

  /**
   * Skip executing this plugin
   *
   * @since 0.0.4
   */
  @Parameter(defaultValue = "false", property = "exists.skip")
  private boolean skip;

  @Parameter(defaultValue = "${session}", required = true, readonly = true)
  private MavenSession session;

  private static final Pattern GAV_PARSER = Pattern.compile("^([^:]*):([^:]*):([^:]*)$");

  protected abstract byte[] getRemoteChecksum(String s) throws MojoExecutionException, IOException;

  protected abstract String getRepositoryBase() throws MojoExecutionException;

  protected abstract String getPropertyName();

  public void execute() throws MojoExecutionException, MojoFailureException {
    if(skip) {
      getLog().info("skipping exists execution");
      return;
    }
    try {
      if (skipIfSnapshot && isSnapshot()) {
        getLog().debug("skipping -SNAPSHOT");
        return;
      }
      boolean exists = verifyExistence();

      if(exists && useChecksum) {
        exists = verifyChecksum();
      }

      String propertyName = getPropertyName();
      String value = Boolean.toString(exists);
      if (userProperty) {
        getLog().info("setting user property " + propertyName + "=" + value);
        session.getUserProperties().setProperty(propertyName, value);
      } else {
        getLog().info("setting " + propertyName + "=" + value);
        mavenProject.getProperties().setProperty(propertyName, value);
      }
    } catch (MojoExecutionException|MojoFailureException e) {
      throw e;
    } catch (Exception e) {
      throw new MojoExecutionException(e.getMessage(), e);
    }
  }

  private boolean verifyExistence() throws IOException, MojoExecutionException, MojoFailureException {
    String uri = getRepositoryUri();
    getLog().debug("checking for resource at " + uri);
    boolean exists = checkArtifactExists(uri);
    if (exists) {
      if (failIfExists) {
        throw new MojoFailureException(
            "Artifact already exists in repository: " + project + "/" + artifact);
      }
    } else {
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

  protected abstract boolean checkArtifactExists(String uri) throws IOException, MojoExecutionException;

  private boolean verifyChecksum()
      throws IOException, MojoFailureException, NoSuchAlgorithmException, MojoExecutionException {
    String uri = getRepositoryUri();
    getLog().debug("checking for resource " + uri);

    String priorChecksum = getPriorChecksum(uri);
    String buildChecksum = getArtifactChecksum();
    if(buildChecksum.equalsIgnoreCase(priorChecksum)) {
      return true;
    }
    String message = "buildChecksum(" + buildChecksum + ") != priorChecksum(" + priorChecksum + ")";
    getLog().debug(message);
    if (failIfNotMatches) {
      throw new MojoFailureException(message);
    }
    return false;
  }

  private String getPriorChecksum(String uri) throws IOException {
    try {
      byte[] priorChecksumBytes = getRemoteChecksum(uri + ".sha1");
      return new String(priorChecksumBytes, StandardCharsets.ISO_8859_1);
    } catch (MojoExecutionException ex) {
      getLog().debug("Unable to get prior checksum. Reason:" + ex.getMessage(), ex);
      return null;
    }
  }

  // https://cwiki.apache.org/confluence/display/MAVEN/Remote+repository+layout

  private String getRepositoryUri() throws MojoExecutionException {
    Matcher matcher = GAV_PARSER.matcher(project);
    if (!matcher.matches()) {
      return getRepositoryBase() + '/' + project + '/' + artifact;
    }
    String groupId = matcher.group(1);
    String artifactId = matcher.group(2);
    String version = matcher.group(3);

    // ${groupId.replace('.','/')}/${artifactId}${platformId==null?'':'-'+platformId}/${version}/
    // ${artifactId}${platformId==null?'':'-'+platformId}-${version}${classifier==null?'':'-'+classifier}.${type}
    return getRepositoryBase() + '/'
        + groupId.replace('.', '/') + '/'
        + artifactId + '/'
        + version + '/'
        + artifact;
  }

  private String getArtifactChecksum() throws IOException, MojoFailureException, NoSuchAlgorithmException {
    CheckSum signer = new CheckSum("SHA-1");
    Artifact mavenArtifact = mavenProject.getArtifact();
    File file;
    if("pom".equals(mavenArtifact.getType())) {
      file = new File(mavenProject.getBasedir(), "pom.xml");
    }
    else {
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
