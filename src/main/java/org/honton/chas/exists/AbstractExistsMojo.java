package org.honton.chas.exists;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
      Boolean matches = useChecksum ? verifyWithChecksum() : verifyWithExistence();
      if (matches != null) {
        if (failIfExists && matches) {
          throw new MojoFailureException("Artifact already exists in repository: " + project + "/" + artifact);
        } else if (failIfNotExists && !matches) {
          throw new MojoFailureException("Artifact does not exist in repository: " + project + "/" + artifact);
        }
        String propertyName = getPropertyName();
        String value = Boolean.toString(matches);
        if (userProperty) {
          getLog().info("setting user property " + propertyName + "=" + value);
          session.getUserProperties().setProperty(propertyName, value);
        } else {
          getLog().info("setting " + propertyName + "=" + value);
          mavenProject.getProperties().setProperty(propertyName, value);
        }
      }
    } catch (Exception e) {
      throw new MojoExecutionException(e.getMessage(), e);
    }
  }

  private Boolean verifyWithExistence() throws IOException, MojoExecutionException {
    if (skipIfSnapshot && isSnapshot()) {
      getLog().debug("skipping -SNAPSHOT");
      return null;
    }

    String uri = getRepositoryUri();
    getLog().debug("checking for resource at " + uri);
    return checkArtifactExists(uri);
  }

  protected boolean isSnapshot() {
    return project.endsWith("-SNAPSHOT");
  }

  protected abstract Boolean checkArtifactExists(String uri) throws IOException, MojoExecutionException;

  private Boolean verifyWithChecksum()
      throws IOException, MojoFailureException, NoSuchAlgorithmException, MojoExecutionException {
    String uri = getRepositoryUri();
    getLog().debug("checking for resource " + uri);

    byte[] priorChecksumBytes = getRemoteChecksum(uri  + ".sha1" );
    if(priorChecksumBytes == null) {
      return null;
    }

    String priorChecksum = new String(priorChecksumBytes, StandardCharsets.ISO_8859_1);
    String buildChecksum = getArtifactChecksum();
    if(priorChecksum.equalsIgnoreCase(buildChecksum)) {
      return true;
    }
    getLog().debug("buildChecksum(" + buildChecksum + ") != priorChecksum(" + priorChecksum + ")");
    return false;
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
