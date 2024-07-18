package org.honton.chas.exists;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.metadata.SnapshotVersion;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Set a property if the artifact in the local repository is same as the just built artifact. The
 * local repository is either defined in settings.xml or defaults to ~/.m2/repository. Using the
 * defaults, executing this plugin will prevent the install plugin from reinstalling identical
 * artifacts to the local repository. This situation will often occur with a recurring schedule
 * build job.
 *
 * @since 0.0.2
 */
@Mojo(name = "local", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public class LocalExistsMojo extends AbstractExistsMojo {

  @Parameter(property = "localRepository", required = true, readonly = true)
  protected ArtifactRepository localRepository;

  /**
   * The property to set if the artifact exists in the deploy repository. The default property of
   * <em>maven.deploy.skip</em> may cause the deploy plugin to skip execution.
   */
  @Parameter(property = "exists.property", defaultValue = "maven.install.skip")
  private String property;

  @Override
  protected String getPropertyName() {
    return property;
  }

  @Override
  protected String getVersionedPath(SnapshotVersion version) {
    return gav.artifactLocation();
  }

  @Override
  protected String getMavenMetadata(String directory) throws Exception {
    Path path = getPath(localRepository.getBasedir(), directory, "maven-metadata-local.xml");
    getLog().debug("Reading metadata from " + path);
    return Files.readString(path, StandardCharsets.ISO_8859_1);
  }

  @Override
  protected boolean checkArtifactExists(String file) {
    Path path = getPath(localRepository.getBasedir(), file);
    getLog().info("Checking for artifact at " + path);
    return Files.isReadable(path);
  }

  @Override
  protected String getArtifactChecksum(String file) throws IOException, GeneralSecurityException {
    Path path = getPath(localRepository.getBasedir(), file);
    getLog().debug("checking for resource " + path);
    return new CheckSum().getChecksum(path);
  }
}
