# exists-maven-plugin

Check if a maven artifact exists. Designed around the use case of skipping deployment if the stable version already exists.

You can use the following configuration:

| Parameter | Default | Description |
|-----------|---------|-------------|
|repository |${project.distributionManagement.repository.url}| The repository to query for artifacts|
|project    |${project.groupId}:${project.artifactId}:${project.version}| The project within the repository to query|
|artifact   |${project.artifactId}-${project.version}.pom|The artifact within the project to query|
|property   |maven.deploy.skip|The property to receive the result of the query|
|skipIfSnapshot|true|Skip the query if the project ends with -SNAPSHOT|

Typical use:

```xml
  <build>
    <plugins>

      <plugin>
        <groupId>org.honton.chas</groupId>
        <artifactId>exists-maven-plugin</artifactId>
        <version>0.0.1</version>
        <executions>
          <execution>
            <goals>
              <goal>exists</goal>
            </goals>
          </execution>
        </executions>
      </plugin>

    </plugins>
  </build>
```
