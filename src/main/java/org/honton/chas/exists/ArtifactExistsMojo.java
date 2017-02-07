package org.honton.chas.exists;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * check for presence of a remote artifact
 * @since 0.0.1
 * @deprecated Use remote goal
 */
@Mojo(name = "exists", defaultPhase = LifecyclePhase.INITIALIZE)
public class ArtifactExistsMojo extends AbstractMojo {

  @Parameter(defaultValue = "${project}", readonly = true)
  private MavenProject maven;

  @Parameter(defaultValue = "${project.distributionManagement.repository.url}")
  private String repository;

  @Parameter(defaultValue = "${project.groupId}:${project.artifactId}:${project.version}")
  private String project;

  @Parameter(defaultValue = "${project.artifactId}-${project.version}.pom")
  private String artifact;

  @Parameter(defaultValue = "maven.deploy.skip")
  private String property;

  @Parameter(defaultValue = "true")
  private boolean skipIfSnapshot;

  private final Pattern GAV_PARSER = Pattern.compile("^([^:]*):([^:]*):([^:]*)$");

  public void execute() throws MojoExecutionException {
    try {
      doWork();
    } catch (Exception e) {
      throw new MojoExecutionException(e.getMessage(), e);
    }
  }

  private void doWork() throws IOException {
    if(skipIfSnapshot && project.endsWith("-SNAPSHOT")) {
      getLog().debug("skipping -SNAPSHOT");
      return;
    }

    String uri = getFetchUri();
    getLog().debug("checking for resource at " + uri);

    HttpURLConnection con = (HttpURLConnection) new URL(uri).openConnection();
    con.setRequestMethod("HEAD");

    boolean exists = con.getResponseCode() == HttpURLConnection.HTTP_OK;
    getLog().debug(uri + (exists ? " exists" : " does not exist"));
    maven.getProperties().setProperty(property, Boolean.toString(exists));
  }

  public String getFetchUri() {
    Matcher matcher = GAV_PARSER.matcher(project);
    if (!matcher.matches()) {
      return repository + '/' + project + '/' + artifact;
    }
    String groupId = matcher.group(1);
    String artifactId = matcher.group(2);
    String version = matcher.group(3);

    return repository + '/'
        + groupId.replace('.', '/') + '/'
        + artifactId.replace('.', '/') + '/'
        + version + '/'
        + artifact;
  }
}
