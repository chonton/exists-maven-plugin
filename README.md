# exists-maven-plugin

Check if a maven artifact exists. Designed around the use case of skipping deployment if the stable version already exists.

## Goals
There are two goals: [local](https://chonton.github.io/exists-maven-plugin/0.5.0/local-mojo.html) checks
if the just built artifact is already in the local repository;
and [remote](https://chonton.github.io/exists-maven-plugin/0.5.0/remote-mojo.html) checks
if the just built artifact is already in the remote repository.

Mojo details at [plugin info](https://chonton.github.io/exists-maven-plugin/0.5.0/plugin-info.html)

## Parameters
Every parameter can be set with a maven property **exists.**_<parameter_name\>_.  e.g. skip parameter can 
be set from command line -Dexists.skip=true

| Parameter | Default | Description |
|-----------|---------|-------------|
|project    |${project.groupId}:${project.artifactId}:${project.version}| The project within the repository to query|
|artifact   |${project.artifactId}-${project.version}.{packaging}|The artifact within the project to query|
|property   |${maven.deploy.skip} _or_ ${maven.install.skip}|The property to receive the result of the query|
|userProperty|false|If the property should be set as a user property, to be available in child projects|
|skipIfSnapshot|true|Skip the query if the project ends with -SNAPSHOT|
|repository |${project.distributionManagement.repository.url}| For remote goal, the repository to query for artifacts|
|snapshotRepository|${project.distributionManagement.snapshotRepository.url}| For remote goal, the repository to query for snapshot artifacts|
|serverId|${project.distributionManagement.repository.id}|For remote goal, the server ID to use for authentication and proxy settings|
|snapshotServerId|${project.distributionManagement.snapshotRepository.id}|For remote goal, the server ID to use for snapshot authentication and proxy settings|
|failIfExists|false|Fail the build if the artifact already exists|
|failIfNotExists|false|Fail the build if the artifact does not exist|
|cmpChecksum|false|Compare checksums of artifacts|
|failIfNotMatch|false|Fail the build if the artifact exists and cmpChecksum is set and checksums do not match|
|skip|false|Skip executing the plugin|

## Requirements
- Maven 3.5 or later
- Java 1.8 or later

## Typical Use

```xml
  <build>
    <plugins>

      <plugin>
        <groupId>org.honton.chas</groupId>
        <artifactId>exists-maven-plugin</artifactId>
        <version>0.5.0</version>
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
