# exists-maven-plugin

Check if a maven artifact exists. Designed around the use case of skipping deployment if the stable version already exists.

Mojo details at [plugin info](https://chonton.github.io/exists-maven-plugin/0.1.0/plugin-info.html)

Two basic goals: [local](https://chonton.github.io/exists-maven-plugin/0.1.0/local-mojo.html) checks
if the just built artifact is already in the local repository;
and [remote](https://chonton.github.io/exists-maven-plugin/0.1.0/remote-mojo.html) checks
if the just built artifact is already in the remote repository.

| Parameter | Property | Default | Description |
|-----------|---------|---------|-------------|
|project    |exists.project|${project.groupId}:${project.artifactId}:${project.version}| The project within the repository to query|
|artifact   |exists.artifact|${project.artifactId}-${project.version}.{packaging}|The artifact within the project to query|
|property   |exists.property|${maven.deploy.skip} / ${maven.install.skip}|The property to receive the result of the query|
|userProperty|exists.userProperty|false|If the property should be set as a user property, to be available in child projects|
|skipIfSnapshot|exists.skipIfSnapshot|true|Skip the query if the project ends with -SNAPSHOT|
|repository |exists.repository|${project.distributionManagement.repository.url}| For remote goal, the repository to query for artifacts|
|snapshotRepository|exists.snapshotRepository|${project.distributionManagement.snapshotRepository.url}| For remote goal, the repository to query for snapshot artifacts|
|serverId|exists.serverId|${project.distributionManagement.repository.id}|For remote goal, the server ID to use for authentication and proxy settings|
|snapshotServerId|exists.snapshotServerId|${project.distributionManagement.snapshotRepository.id}|For remote goal, the server ID to use for snapshot authentication and proxy settings|
|failIfExists|exists.failIfExists|false|Fail the build if the artifact already exists|
|failIfNotExists|exists.failIfNotExists|false|Fail the build if the artifact does not exist|
|cmpChecksum|exists.cmpChecksum|false|Compare checksums of artifacts|
|failIfNotMatch|exists.failIfNotMatch|false|Fail the build if the artifact exists and cmpChecksum is set and checksums do not match|
|skip|exists.skip|false|Skip executing the plugin|

Typical use:

```xml
  <build>
    <plugins>

      <plugin>
        <groupId>org.honton.chas</groupId>
        <artifactId>exists-maven-plugin</artifactId>
        <version>0.1.0</version>
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
