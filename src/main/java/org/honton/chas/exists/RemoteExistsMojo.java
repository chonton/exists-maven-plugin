package org.honton.chas.exists;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.maven.artifact.repository.metadata.SnapshotVersion;
import org.apache.maven.configuration.BeanConfigurationException;
import org.apache.maven.configuration.BeanConfigurationRequest;
import org.apache.maven.configuration.BeanConfigurator;
import org.apache.maven.configuration.DefaultBeanConfigurationRequest;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.settings.Proxy;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.apache.maven.wagon.ConnectionException;
import org.apache.maven.wagon.StreamingWagon;
import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.authentication.AuthenticationInfo;
import org.apache.maven.wagon.proxy.ProxyInfo;
import org.apache.maven.wagon.repository.Repository;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.context.Context;
import org.codehaus.plexus.context.ContextException;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Contextualizable;
import org.sonatype.plexus.components.sec.dispatcher.DefaultSecDispatcher;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcherException;

/**
 * Set a property if the artifact in the remote repository is same as the just built artifact. The
 * remote repository is usually defined in the pom's distributionManagement section. Using the
 * defaults, executing this plugin will prevent the install plugin from reinstalling identical
 * artifacts to the remote repository. This situation will often occur with a recurring schedule
 * build job.
 *
 * @since 0.0.2
 */
@Mojo(name = "remote", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public class RemoteExistsMojo extends AbstractExistsMojo implements Contextualizable {

  /**
   * The property to set if the artifact exists in the deploy repository. The default property of
   * <em>maven.deploy.skip</em> may cause the deploy plugin to skip execution.
   */
  @Parameter(property = "exists.property", defaultValue = "maven.deploy.skip")
  private String property;

  /**
   * The URL of the remote repository to check for distribution artifacts. The default value is the
   * repository defined in the pom's distributionManagement / repository section.
   */
  @Parameter(
      property = "exists.repository",
      defaultValue = "${project.distributionManagement.repository.url}")
  private String repository;

  /**
   * The URL of the remote repository to check for snapshot versioned artifacts. The default value
   * is the snapshot repository defined in the pom's distributionManagement / snapshotRepository
   * section.
   */
  @Parameter(
      property = "exists.snapshotRepository",
      defaultValue = "${project.distributionManagement.snapshotRepository.url}")
  private String snapshotRepository;

  /**
   * The server ID to use when checking for distribution artifacts. Settings like proxy,
   * authentication or mirrors will be applied when this value is set.
   *
   * @since 0.0.3
   */
  @Parameter(
      property = "exists.serverId",
      defaultValue = "${project.distributionManagement.repository.id}")
  private String serverId;

  /**
   * The server ID to use when checking for snapshot versioned artifacts. Settings like proxy,
   * authentication or mirrors will be applied when this value is set.
   *
   * @since 0.0.3
   */
  @Parameter(
      property = "exists.snapshotServerId",
      defaultValue = "${project.distributionManagement.snapshotRepository.id}")
  private String snapshotServerId;

  @Parameter(defaultValue = "${settings}", required = true, readonly = true)
  private Settings settings;

  @Component(
      role = org.sonatype.plexus.components.sec.dispatcher.SecDispatcher.class,
      hint = "default")
  private SecDispatcher securityDispatcher;

  @Component(role = org.apache.maven.configuration.BeanConfigurator.class, hint = "default")
  private BeanConfigurator beanConfigurator;

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
  protected String getVersionedPath(SnapshotVersion version) {
    return gav.snapshotLocation(version.getVersion());
  }

  @Override
  protected String getMavenMetadata(String path) throws Exception {
    return getRemoteFile(path + "maven-metadata.xml");
  }

  private String getRepositoryBase() throws MojoExecutionException {
    String repositoryBase;
    if (isSnapshot()) {
      if (snapshotRepository == null) {
        throw new MojoExecutionException("distributionManagement snapshotRepository is not set");
      }
      repositoryBase = snapshotRepository;
    } else {
      if (repository == null) {
        throw new MojoExecutionException("distributionManagement repository is not set");
      }
      repositoryBase = repository;
    }

    int lastIdx = repositoryBase.length() - 1;
    return repositoryBase.charAt(lastIdx) == '/'
        ? repositoryBase.substring(0, lastIdx)
        : repositoryBase;
  }

  @Override
  protected boolean checkArtifactExists(String path) throws Exception {
    String repositoryBase = getRepositoryBase();
    getLog().info("Checking for artifact at " + repositoryBase + path);
    try (WagonHelper wagonHelper = new WagonHelper(repositoryBase)) {
      return wagonHelper.resourceExists(path);
    }
  }

  @Override
  protected String getArtifactChecksum(String path) throws Exception {
    return getRemoteFile(path + ".sha1");
  }

  private String getRemoteFile(String path) throws Exception {
    // This method is only called to small files, so we can safely read all the content into
    // memory!
    String repositoryBase = getRepositoryBase();
    getLog().debug("Fetching " + repositoryBase + path);
    try (WagonHelper wagonHelper = new WagonHelper(repositoryBase)) {
      return wagonHelper.getContent(path);
    }
  }

  private static String stripTrailingSlash(String path) {
    int lastIdx = path.length() - 1;
    return path.charAt(lastIdx) == '/' ? path.substring(0, lastIdx) : path;
  }

  private class WagonHelper implements AutoCloseable {

    private final Wagon wagon;

    WagonHelper(String uri) throws Exception {
      String id = isSnapshot() ? snapshotServerId : serverId;
      // https://github.com/chonton/exists-maven-plugin/issues/41
      wagon = connectWagon(id == null ? "" : id, stripTrailingSlash(uri));
    }

    Wagon connectWagon(String serverId, String url) throws Exception {
      Repository repo = new Repository(serverId, url);

      Wagon wgn = container.lookup(Wagon.class, repo.getProtocol());
      configureWagon(wgn);

      wgn.connect(repo, getAuthInfo(serverId), getProxyInfo());
      return wgn;
    }

    /* begin
    https://github.com/chonton/exists-maven-plugin/issues/16,
    https://github.com/chonton/exists-maven-plugin/issues/27 */
    void configureWagon(Wagon wagon) throws BeanConfigurationException {
      Server server = settings.getServer(serverId);
      if (server == null) {
        getLog().debug("no server for id " + serverId);
        return;
      }

      Object serverConfiguration = server.getConfiguration();
      if (serverConfiguration == null) {
        getLog().debug("no server configuration");
        return;
      }

      BeanConfigurationRequest bcr = new DefaultBeanConfigurationRequest();
      bcr.setBean(wagon);
      bcr.setConfiguration(serverConfiguration);
      beanConfigurator.configureBean(bcr);
    }
    /* end
    https://github.com/chonton/exists-maven-plugin/issues/16,
    https://github.com/chonton/exists-maven-plugin/issues/27 */

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

    AuthenticationInfo getAuthInfo(String serverId) throws SecDispatcherException {
      Server server = settings.getServer(serverId);
      if (server == null) {
        return null;
      }

      /* begin https://github.com/chonton/exists-maven-plugin/issues/22 */
      if (securityDispatcher instanceof DefaultSecDispatcher) {
        ((DefaultSecDispatcher) securityDispatcher)
            .setConfigurationFile("~/.m2/settings-security.xml");
      }
      /* end https://github.com/chonton/exists-maven-plugin/issues/22 */

      AuthenticationInfo authInfo = new AuthenticationInfo();
      authInfo.setUserName(server.getUsername());
      authInfo.setPassword(securityDispatcher.decrypt(server.getPassword()));
      authInfo.setPassphrase(server.getPassphrase());
      authInfo.setPrivateKey(server.getPrivateKey());
      return authInfo;
    }

    boolean resourceExists(String resourceName) throws Exception {
      /* https://github.com/chonton/exists-maven-plugin/issues/37 */
      return wagon.resourceExists(
          resourceName.charAt(0) == '/' ? resourceName.substring(1) : resourceName);
    }

    String getContent(String resourceName) throws Exception {
      byte[] bytes;
      if (wagon instanceof StreamingWagon) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ((StreamingWagon) wagon).getToStream(resourceName, baos);
        bytes = baos.toByteArray();
      } else {
        Path tmpFilePath = Files.createTempFile("checksum", null);
        File tmpFile = tmpFilePath.toFile();
        tmpFile.deleteOnExit();
        wagon.get(resourceName, tmpFile);
        bytes = Files.readAllBytes(tmpFilePath);
      }
      return new String(bytes, StandardCharsets.ISO_8859_1);
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
