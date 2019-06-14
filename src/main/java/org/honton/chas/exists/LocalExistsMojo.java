package org.honton.chas.exists;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Set a property if the artifact in the local repository is same as the just built artifact. The
 * local repository is either defined in settings.xml or defaults to ~/.m2/repository.  Using the
 * defaults, executing this plugin will prevent the install plugin from reinstalling identical
 * artifacts to the local repository.  This situation will often occur with a recurring schedule build job.
 *
 * @since 0.0.2
 */
@Mojo(name = "local", defaultPhase = LifecyclePhase.VERIFY)
public class LocalExistsMojo extends AbstractExistsMojo {

  /**
   * The property to set if the artifact exists in the deploy repository.
   * The default property of <em>maven.deploy.skip</em> may cause the deploy plugin to skip execution.
   */
  @Parameter(defaultValue = "maven.install.skip")
  private String property;

  @Parameter( property = "localRepository", required = true, readonly = true )
  protected ArtifactRepository localRepository;

  @Override
  protected String getPropertyName() {
    return property;
  }

  @Override
  protected String getRepositoryBase() {
    return localRepository.getBasedir();
  }

  @Override
  protected boolean checkArtifactExists(String uri) throws IOException {
    return new File(uri).isFile();
  }

  @Override
  protected byte[] getRemoteChecksum(String uri) throws MojoExecutionException, IOException {
    File file = new File(uri);
    if (!file.isFile()) {
      return null;
    }
    return Files.readAllBytes(file.toPath());
  }
}
