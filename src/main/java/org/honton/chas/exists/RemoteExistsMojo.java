package org.honton.chas.exists;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Set a property if the artifact in the remote repository is same as the just built artifact.
 * The remote repository is usually defined in the pom's distributionManagement section.  Using the
 * defaults, executing this plugin will prevent the install plugin from reinstalling identical
 * artifacts to the remote repository.  This situation will often occur with a recurring schedule build job.
 *
 * @since 0.0.2
 */
@Mojo(name = "remote", defaultPhase = LifecyclePhase.VERIFY)
public class RemoteExistsMojo extends AbstractExistsMojo {

  /**
   * The property to set if the artifact exists in the deploy repository.
   * The default property of <em>maven.deploy.skip</em> may cause the deploy plugin to skip execution.
   */
  @Parameter(defaultValue = "maven.deploy.skip")
  private String property;

  /**
   * The URL of the remote repository to check for distribution artifacts.
   * The default value is the repository defined in the pom's distributionManagement / repository section.
   */
  @Parameter(defaultValue = "${project.distributionManagement.repository.url}")
  private String repository;

  /**
   * The URL of the remote repository to check for snapshot versioned artifacts.
   * The default value is the snapshot repository defined in the pom's distributionManagement / snapshotRepository section.
   */
  @Parameter(defaultValue = "${project.distributionManagement.snapshotRepository.url}")
  private String snapshotRepository;

  @Override
  protected String getPropertyName() {
    return property;
  }

  @Override
  protected String getRepositoryBase() throws MojoExecutionException {
    if (isSnapshot()) {
      if (snapshotRepository == null) {
        throw new MojoExecutionException("distributionManagement snapshotRepository is not set");
      }
      return snapshotRepository;
    } else {
      if (repository == null) {
        throw new MojoExecutionException("distributionManagement repository is not set");
      }
      return repository;
    }
  }

  @Override
  protected Boolean checkArtifactExists(String uri) throws IOException {
    URLConnection urlConnection = new URL(uri).openConnection();
    HttpURLConnection httpURLConnection = (HttpURLConnection) urlConnection;
    httpURLConnection.setRequestMethod("HEAD");
    getLog().debug("HEAD " + uri + " returned status " + httpURLConnection.getResponseCode());
    return httpURLConnection.getResponseCode() == HttpURLConnection.HTTP_OK;
  }

  @Override
  protected InputStream getRemoteArtifactStream(String uri) throws IOException {
    HttpURLConnection con = (HttpURLConnection) new URL(uri).openConnection();
    con.setRequestMethod("GET");
    con.setRequestProperty("Accept", "text/plain");
    con.connect();
    if(con.getResponseCode() != HttpURLConnection.HTTP_OK) {
      getLog().debug("GET " + uri + " returned status " + con.getResponseCode() );
      return null;
    }
    return con.getInputStream();
  }
}
