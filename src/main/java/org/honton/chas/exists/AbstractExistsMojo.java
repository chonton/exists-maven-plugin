package org.honton.chas.exists;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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
import org.codehaus.plexus.util.IOUtil;

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

  @Parameter(defaultValue = "${session}", required = true, readonly = true)
  private MavenSession session;

  private static final Pattern GAV_PARSER = Pattern.compile("^([^:]*):([^:]*):([^:]*)$");

  protected abstract InputStream getRemoteArtifactStream(String uri) throws IOException, MojoExecutionException;

  protected abstract String getRepositoryBase() throws MojoExecutionException;

  protected abstract String getPropertyName();

  public void execute() throws MojoExecutionException, MojoFailureException {
    try {
      Boolean matches = useChecksum ?verifyWithChecksum() :verifyWithExistence();
      if(matches != null) {
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
    } catch (MojoFailureException e) {
      throw e;
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

    InputStream inputStream = getRemoteArtifactStream(uri  + ".sha1" );
    if(inputStream == null) {
      return null;
    }
    try {
      String priorChecksum = IOUtil.toString(inputStream, "ISO_8859_1", 1000);
      String buildChecksum = getArtifactChecksum();
      if(priorChecksum.equalsIgnoreCase(buildChecksum)) {
        return true;
      }
      getLog().debug("buildChecksum(" + buildChecksum + ") != priorChecksum(" + priorChecksum + ")");
      return false;
    }
    finally {
      IOUtil.close(inputStream);
    }
  }

  private String getRepositoryUri() throws MojoExecutionException {
    Matcher matcher = GAV_PARSER.matcher(project);
    if (!matcher.matches()) {
      return getRepositoryBase() + '/' + project + '/' + artifact;
    }
    String groupId = matcher.group(1);
    String artifactId = matcher.group(2);
    String version = matcher.group(3);

    return getRepositoryBase() + '/'
        + groupId.replace('.', '/') + '/'
        + artifactId.replace('.', '/') + '/'
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
    if (file != null && file.isFile()) {
      getLog().debug("Calculating checksum for " + file);
      return signer.getChecksum(file);
    } else {
      throw new MojoFailureException("The project artifact " + file + " has not been created.");
    }
  }
}
