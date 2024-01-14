# exists-maven-plugin

Check if a maven artifact exists. Designed around the use case of skipping deployment if the stable
version already exists.

## Goals

There are two goals: [local](https://chonton.github.io/exists-maven-plugin/local-mojo.html)
checks if the just built artifact is already in the local repository;
and [remote](https://chonton.github.io/exists-maven-plugin/remote-mojo.html) checks if the
just built artifact is already in the remote repository.

Mojo details at [plugin info](https://chonton.github.io/exists-maven-plugin/plugin-info.html)

## Parameters

Most parameter can be set with a maven property **exists.**_<parameter_name\>_. e.g. skip parameter
can be set from command line -Dexists.skip=true.

In the following table `p:` indicates the default constituent properties are prefixed with
`project.` and `dm:` indicates the default constituent properties are prefixed with
`project.distributionManagement.` e.g. for artifact parameter, the full default is
`${project.artifactId}-${project.version}.${project.packaging}`

| Parameter          | Default                                             | Description                                                                             |
|--------------------|-----------------------------------------------------|-----------------------------------------------------------------------------------------|
| artifact           | p: ${artifactId}-${version}.${packaging}            | The artifact within the project to query                                                |
| cmpChecksum        | false                                               | Compare checksums of artifacts                                                          |
| failIfExists       | false                                               | Fail the build if the artifact already exists                                           |
| failIfNotExists    | false                                               | Fail the build if the artifact does not exist                                           |
| failIfNotMatch     | false                                               | Fail the build if the artifact exists and cmpChecksum is set and checksums do not match |
| lastSnapshotTime   |                                                     | The property to set with the timestamp of the last snapshot install / deploy            |
| project            | p: ${groupId}:${artifactId}:${packaging}:${version} | The project within the repository to query                                              |
| classifier         |                                                     | The classifier to use for checking the repository, e.g. 'tests'                         |
| property           | ${maven.deploy.skip} _or_ ${maven.install.skip}     | The property to receive the result of the query                                         |
| repository         | dm: ${repository.url}                               | For remote goal, the repository to query for artifacts                                  |
| requireGoal        |                                                     | Execute goal only if requireGoal value matches one of the maven command line goals      |
| serverId           | dm: ${repository.id}                                | For remote goal, the server ID to use for authentication and proxy settings             |
| skip               | false                                               | Skip executing the plugin                                                               |
| skipIfSnapshot     | true                                                | Skip the query if the project ends with -SNAPSHOT                                       |
| snapshotRepository | dm: ${snapshotRepository.url}                       | For remote goal, the repository to query for snapshot artifacts                         |
| snapshotServerId   | dm: ${snapshotRepository.id}                        | For remote goal, the server ID to use for snapshot authentication and proxy settings    |
| userProperty       | false                                               | If the property should be set as a user property, to be available in child projects     |

## Typical Use

```xml

<build>
  <plugins>

    <plugin>
      <groupId>org.honton.chas</groupId>
      <artifactId>exists-maven-plugin</artifactId>
      <version>0.14.0</version>
      <executions>
        <execution>
          <goals>
            <goal>remote</goal>
          </goals>
        </execution>
      </executions>
    </plugin>

  </plugins>
</build>
```

## How This Plugin Determines if Builds are the "Same"
There are two strategies to determine if a maven artifacts are the "same" as what the project just
built: version comparison, and checksum comparison.  By default, this plugin uses version comparison.
Version comparison simply checks if the group:artifact:version matches an artifact in the local or
remote repository.  This simple check will not catch the situation where the developer has failed to
update the version in pom.xml.

Alternatively, when `<cmpChecksum>` is true, this plugin compares the checksum of the local or remote
artifact with the just built artifact.  Checksum comparison requires that the maven build be
reproducible.  Without specific configuration, maven builds are **not** reproducible.  See
[Configuring for Reproducible Builds](https://maven.apache.org/guides/mini/guide-reproducible-builds.html)
for details on making your build reproducible.

## Custom Packaging

If your build uses a custom packaging type, (not one of the 
[Default Artifact Handlers](https://maven.apache.org/ref/3.9.6/maven-core/artifact-handlers.html)),
then you will need to specify a `packageExtension` if the file extension does not match the package
type. e.g. for `content-package` packaging specify a zip extension with the following configuration:

```xml

<pluginManagement>
  <plugins>

    <plugin>
      <groupId>org.honton.chas</groupId>
      <artifactId>exists-maven-plugin</artifactId>
      <version>0.14.0</version>
      <configuration>
        <packageExtension>
          <content-package>zip</content-package>
        </packageExtension>
      </configuration>
    </plugin>

  </plugins>
</pluginManagement>
```

## Snapshot builds

When checking snapshot builds against a remote/local repository, the last deployed/installed
snapshot of the correct version will be matched. Optionally, you can configure a property with
the `lastSnapshotTime` parameter which will receive the build timestamp. If you need additional date
math on the timestamp value, open a feature request with your use case.

## Preventing failures of `remote` goal

Consider the scenario where there is an artifact that can only be deployed from a specific build
server to a corporate repository with a specialized workflow that ensures various security and
license policies. The exists-maven-plugin remote goal is also used to avoid duplicate deployments.

Running maven with the `install` phase will cause the exists-maven-plugin to execute the `remote`
task. This might fail for various reasons; including the developer laptop is not connected to the
internet, or the corporate repository is only available to specific build machines.

We could change the binding of the `remote` goal to the `deploy` phase. However, the
maven-deploy-plugin's `deploy` goal will run before exists-maven-plugin's `remote` goal because
["When multiple executions are given that match a particular phase, they are executed in the order
specified in the POM, with inherited executions running first."](https://maven.apache.org/guides/introduction/introduction-to-the-lifecycle.html#plugins)

The solution is to leave the `remote` goal bound to the `install` phase and add a `requireGoal`
configuration:

```xml

<configuration>
  <!-- run only if deploy goal is specified in maven command line -->
  <requireGoal>deploy</requireGoal>
</configuration>
```
