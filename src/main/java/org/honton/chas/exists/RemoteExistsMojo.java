package org.honton.chas.exists;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.settings.Proxy;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.apache.maven.wagon.ConnectionException;
import org.apache.maven.wagon.StreamingWagon;
import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.WagonException;
import org.apache.maven.wagon.authentication.AuthenticationInfo;
import org.apache.maven.wagon.proxy.ProxyInfo;
import org.apache.maven.wagon.repository.Repository;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.context.Context;
import org.codehaus.plexus.context.ContextException;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Contextualizable;

/**
 * Set a property if the artifact in the remote repository is same as the just built artifact. The
 * remote repository is usually defined in the pom's distributionManagement section.  Using the
 * defaults, executing this plugin will prevent the install plugin from reinstalling identical
 * artifacts to the remote repository.  This situation will often occur with a recurring schedule
 * build job.
 *
 * @since 0.0.2
 */
@Mojo(name = "remote", defaultPhase = LifecyclePhase.VERIFY)
public class RemoteExistsMojo extends AbstractExistsMojo
    implements Contextualizable {

  /**
   * The property to set if the artifact exists in the deploy repository. The default property of
   * <em>maven.deploy.skip</em> may cause the deploy plugin to skip execution.
   */
  @Parameter(defaultValue = "maven.deploy.skip")
  private String property;

  /**
   * The URL of the remote repository to check for distribution artifacts. The default value is the
   * repository defined in the pom's distributionManagement / repository section.
   */
  @Parameter(defaultValue = "${project.distributionManagement.repository.url}")
  private String repository;

  /**
   * The URL of the remote repository to check for snapshot versioned artifacts. The default value
   * is the snapshot repository defined in the pom's distributionManagement / snapshotRepository
   * section.
   */
  @Parameter(defaultValue = "${project.distributionManagement.snapshotRepository.url}")
  private String snapshotRepository;

  /**
   * The server ID to use when checking for distribution artifacts. Settings like proxy,
   * authentication or mirrors will be applied when this value is set.
   *
   * @since 0.0.3
   */
  @Parameter(defaultValue = "${project.distributionManagement.repository.id}")
  private String serverId;

  /**
   * The server ID to use when checking for snapshot versioned artifacts. Settings like proxy,
   * authentication or mirrors will be applied when this value is set.
   *
   * @since 0.0.3
   */
  @Parameter(defaultValue = "${project.distributionManagement.snapshotRepository.id}")
  private String snapshotServerId;

  @Parameter(defaultValue = "${settings}", required = true, readonly = true)
  private Settings settings;

  private PlexusContainer container;

  @Override
  public void contextualize(Context context) throws ContextException {
    container = (PlexusContainer) context.get(PlexusConstants.PLEXUS_KEY);
  }

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
  protected boolean checkArtifactExists(String uri) throws IOException, MojoExecutionException {
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

    WagonHelper(String uri) throws MojoExecutionException {
      this.uri = uri;
      try {
        String id = isSnapshot() ? snapshotServerId : serverId;
        wagon = connectWagon(id == null ? "" : id, uri);
      } catch (WagonException | ComponentLookupException e) {
        throw new MojoExecutionException("Could not create Wagon for " + uri, e);
      }
    }

    Wagon connectWagon(String serverId, String url) throws WagonException, ComponentLookupException {
      Repository repository = new Repository(serverId, url);
      Wagon wagon = container.lookup(Wagon.class, repository.getProtocol());
      wagon.connect(repository, getAuthInfo(serverId), getProxyInfo());
      return wagon;
    }

    ProxyInfo getProxyInfo() {
      Proxy proxy = settings.getActiveProxy();
      if (proxy == null) {
        return null;
      }

      ProxyInfo proxyInfo = new ProxyInfo();
      proxyInfo.setHost(proxy.getHost());
      proxyInfo.setType(proxy.getProtocol());
      proxyInfo.setPort(proxy.getPort());
      proxyInfo.setNonProxyHosts(proxy.getNonProxyHosts());
      proxyInfo.setUserName(proxy.getUsername());
      proxyInfo.setPassword(proxy.getPassword());
      return proxyInfo;
    }

    AuthenticationInfo getAuthInfo(String serverId) {
      Server server = settings.getServer(serverId);
      if (server == null) {
        return null;
      }

      AuthenticationInfo authInfo = new AuthenticationInfo();
      authInfo.setUserName(server.getUsername());
      authInfo.setPassword(server.getPassword());
      authInfo.setPassphrase(server.getPassphrase());
      authInfo.setPrivateKey(server.getPrivateKey());
      return authInfo;
    }

    boolean resourceExists(String resourceName) throws MojoExecutionException {
      try {
        return wagon.resourceExists(resourceName);
      } catch (WagonException e) {
        throw new MojoExecutionException("Checking remote resource failed for " + uri, e);
      }
    }

    void get(String resourceName, OutputStream outputStream) throws MojoExecutionException {
      try {
        ((StreamingWagon) wagon).getToStream(resourceName, outputStream);
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
