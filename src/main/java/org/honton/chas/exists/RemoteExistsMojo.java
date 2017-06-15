package org.honton.chas.exists;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import org.apache.maven.artifact.manager.WagonManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.settings.Settings;
import org.apache.maven.wagon.ConnectionException;
import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.apache.maven.wagon.StreamingWagon;
import org.apache.maven.wagon.TransferFailedException;
import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.WagonException;
import org.apache.maven.wagon.authorization.AuthorizationException;
import org.codehaus.mojo.wagon.shared.WagonUtils;

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

  /**
   * The server ID to use when checking for distribution artifacts.
   * Settings like proxy, authentication or mirrors will be applied when this value is set.
   *
   * @since 0.0.3
   */
  @Parameter(defaultValue = "${project.distributionManagement.repository.id}")
  private String serverId;

  /**
   * The server ID to use when checking for snapshot versioned artifacts.
   * Settings like proxy, authentication or mirrors will be applied when this value is set.
   *
   * @since 0.0.3
   */
  @Parameter(defaultValue = "${project.distributionManagement.snapshotRepository.id}")
  private String snapshotServerId;

  @Parameter(defaultValue = "${settings}", required = true, readonly = true)
  private Settings settings;

  @Component
  private WagonManager wagonManager;

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
  protected Boolean checkArtifactExists(String uri) throws IOException, MojoExecutionException {
    String path = getPath(uri);
    try (WagonHelper wagonHelper = new WagonHelper(getRepositoryBase())) {
      return wagonHelper.resourceExists(path);
    }
  }

  @Override
  protected byte[] getRemoteChecksum(String uri) throws MojoExecutionException {
    // This method is only called to read the hash, so we can safely read all the content into memory!
    try (WagonHelper wagonHelper = new WagonHelper(getRepositoryBase())) {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      wagonHelper.get(getPath(uri), baos);
      return baos.toByteArray();
    }
  }

  private String getPath(String uri) throws MojoExecutionException {
    String repositoryBase = getRepositoryBase();
    if (!uri.startsWith(repositoryBase + "/")) {
      throw new IllegalArgumentException("Invalid URL: " + uri);
    }
    return uri.substring(repositoryBase.length() + 1);
  }

  private class WagonHelper implements AutoCloseable {
    private final String uri;
    private final Wagon wagon;

    public WagonHelper(String uri) throws MojoExecutionException {
      this.uri = uri;
      try {
        String id = isSnapshot() ? snapshotServerId : serverId;
        wagon = WagonUtils.createWagon(id == null ? "" : id, uri, wagonManager, settings, getLog());
      } catch (WagonException e) {
        throw new MojoExecutionException("Could not create Wagon for " + uri, e);
      }
    }

    public boolean resourceExists(String resourceName) throws MojoExecutionException {
      try {
        return wagon.resourceExists(resourceName);
      } catch (WagonException e) {
        throw new MojoExecutionException("Checking remote resource failed for " + uri, e);
      }
    }

    public void get(String resourceName, OutputStream outputStream) throws MojoExecutionException {
      try {
        ((StreamingWagon)wagon).getToStream(resourceName, outputStream);
      } catch (WagonException e) {
        throw new MojoExecutionException("Fetching remote checksum failed for " + uri, e);
      }
    }

    @Override
    public void close() {
      try {
        wagon.disconnect();
      } catch (ConnectionException e) {
        getLog().debug("Error disconnecting wagon - ignored", e);
      }
    }
  }
}
