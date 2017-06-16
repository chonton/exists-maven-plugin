# exists-maven-plugin

Check if a maven artifact exists. Designed around the use case of skipping deployment if the stable version already exists.

Mojo details at [plugin info](https://chonton.github.io/exists-maven-plugin/0.0.3/plugin-info.html)

Two basic goals: [local](https://chonton.github.io/exists-maven-plugin/0.0.3/local-mojo.html) checks
if the just built artifact is already in the local repository;
and [remote](https://chonton.github.io/exists-maven-plugin/0.0.3/remote-mojo.html) checks
if the just built artifact is already in the remote repository.

| Parameter | Default | Description |
|-----------|---------|-------------|
|project    |${project.groupId}:${project.artifactId}:${project.version}| The project within the repository to query|
|artifact   |${project.artifactId}-${project.version}.pom|The artifact within the project to query|
|property   |maven.deploy.skip|The property to receive the result of the query|
|userProperty|false|If the property should be set as a user property, to be available in child projects|
|useChecksum|${createChecksum}|Use checksum to compare artifacts (Checksums only available when install plugin is so configured.)|
|skipIfSnapshot|true|If checksums are not used, skip the query if the project ends with -SNAPSHOT|
|repository |${project.distributionManagement.repository.url}| For remote goal, the repository to query for artifacts|
|snapshotRepository |${project.distributionManagement.snapshotRepository.url}| For remote goal, the repository to query for snapshot artifacts|
|serverId|${project.distributionManagement.repository.id}|For remote goal, the server ID to use for authentication and proxy settings|
|snapshotServerId|${project.distributionManagement.snapshotRepository.id}|For remote goal, the server ID to use for snapshot authentication and proxy settings|
|failIfExists|${failIfExists}|Fail the build if the artifact already exists|
|failIfNotExists|${failIfNotExists}|Fail the build if the artifact does not exist|

Typical use:

```xml
  <build>
    <plugins>

      <plugin>
        <groupId>org.honton.chas</groupId>
        <artifactId>exists-maven-plugin</artifactId>
        <version>0.0.3</version>
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
