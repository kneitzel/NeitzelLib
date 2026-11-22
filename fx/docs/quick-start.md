# Your first JavaFX application

This walkthrough creates a complete Maven project with one editable name and a label that follows it. It uses Java 25, JavaFX 27.0.1 and NeitzelLib 1.0.2. The source build below uses the matching release tag.

## Prepare the library

Install a JDK 25. Use the library's Maven wrapper (3.9.11), or a separate Maven installation version 3.9.11 or newer. Check `java -version` and `./mvnw -version` before building; on macOS, select the JDK with `export JAVA_HOME=$(/usr/libexec/java_home -v 25)` if necessary.

```bash
git clone --branch v1.0.2 https://gitea.neitzel.de/konrad/NeitzelLib.git
cd NeitzelLib
./mvnw -pl fx-example -am install
./mvnw -f fx-example/pom.xml javafx:run
```

The `install` command installs the parent POM, `core`, `data-grid`, `injection` and `fx` into your local Maven repository and builds the example. Surefire fails the build when a test fails. The XML reports show which tests ran. The `javafx:run` command opens the [person desk example](../../fx-example/README.md). Run `javafx:run` only on the example POM, not with `-am`: the reactor parent is not a JavaFX application.

Check test results after the build (requires Python 3):

```bash
python3 - <<'PYTHON'
from pathlib import Path
import xml.etree.ElementTree as ET
reports = list(Path('.').glob('*/target/surefire-reports/TEST-*.xml'))
assert reports, 'No test reports found'
failures = []
for report in reports:
    suite = ET.parse(report).getroot()
    if int(suite.get('failures', 0)) or int(suite.get('errors', 0)):
        failures.append(str(report))
assert not failures, 'Failed test reports: ' + ', '.join(failures)
print(f'{len(reports)} test suites passed')
PYTHON
```

Use a clean build when qualifying a change so stale reports cannot stand in for tests that did not run.

## Create the application

Create a separate directory `hello-fx` with these files. This is a classpath application; it deliberately has no `module-info.java`.

```text
hello-fx/
  pom.xml
  src/main/java/example/Main.java
  src/main/java/example/HelloApp.java
  src/main/java/example/Person.java
  src/main/resources/example/Hello.fxml
```

`pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>example</groupId>
  <artifactId>hello-fx</artifactId>
  <version>1.0-SNAPSHOT</version>
  <properties>
    <maven.compiler.release>25</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>
  <dependencies>
    <dependency>
      <groupId>de.neitzel.lib</groupId>
      <artifactId>fx</artifactId>
      <version>1.0.2</version>
    </dependency>
    <dependency>
      <groupId>org.slf4j</groupId>
      <artifactId>slf4j-simple</artifactId>
      <version>2.0.20</version>
      <scope>runtime</scope>
    </dependency>
  </dependencies>
  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-compiler-plugin</artifactId>
        <version>3.16.0</version>
      </plugin>
      <plugin>
        <groupId>org.openjfx</groupId>
        <artifactId>javafx-maven-plugin</artifactId>
        <version>0.0.8</version>
        <configuration><mainClass>example.Main</mainClass></configuration>
      </plugin>
    </plugins>
  </build>
</project>
```

`src/main/java/example/Person.java`:

```java
package example;

public final class Person {
    private String name = "Ada";

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
```

`src/main/java/example/Main.java`:

```java
package example;

import javafx.application.Application;

public final class Main {
    public static void main(String[] args) {
        Application.launch(HelloApp.class, args);
    }
}
```

`src/main/java/example/HelloApp.java`:

```java
package example;

import de.neitzel.fx.view.LoadedView;
import de.neitzel.fx.view.ViewLoader;
import de.neitzel.injection.ApplicationContext;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public final class HelloApp extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        Person person = new Person();
        ApplicationContext context = new ApplicationContext("example");
        LoadedView view = ViewLoader.load(
                HelloApp.class.getResource("Hello.fxml"), person, context);
        stage.setScene(new Scene(view.getRoot()));
        stage.setTitle("Hello NeitzelLib");
        stage.show();
    }
}
```

`src/main/resources/example/Hello.fxml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<?import javafx.scene.layout.VBox?>
<?import javafx.scene.control.Label?>
<?import javafx.scene.control.TextField?>
<?import de.neitzel.fx.view.BindingList?>
<?import de.neitzel.fx.view.DirectBinding?>
<VBox xmlns="http://javafx.com/javafx/27" xmlns:fx="http://javafx.com/fxml/1"
      spacing="12" prefWidth="360" prefHeight="120">
    <fx:define>
        <BindingList>
            <DirectBinding control="name" property="text" path="name" direction="both"/>
            <DirectBinding control="echo" property="text" path="name" direction="view"/>
        </BindingList>
    </fx:define>
    <TextField fx:id="name"/>
    <Label fx:id="echo"/>
</VBox>
```

From `hello-fx`, run:

```bash
mvn clean compile javafx:run
```

Without a separate Maven installation, use the absolute path to the cloned library's `mvnw` with `-f /absolute/path/to/hello-fx/pom.xml clean compile javafx:run`.

Type another name: the label follows immediately, and the `Person` setter receives the new value. No controller is required for this form. `ViewLoader` creates a runtime ViewModel that holds the JavaFX properties. Updating `person.setName(...)` directly later does **not** notify the ViewModel; update `((javafx.beans.property.StringProperty) view.property("name")).set("Grace")` on the JavaFX application thread instead.

## Add behavior and reusable forms

Use an injected controller for event handlers or Computed bindings, and a Component for a reusable FXML form. The [person desk](../../fx-example/README.md) demonstrates both, converters, lists, tables and messages. Read the [architecture guide](architecture.md) before introducing nested Models or sharing windows: Model identity, controller lifetime and list notifications matter.

`fx` brings `core`, `injection` and JavaFX base, graphics, controls, FXML and web transitively. You do not need Lombok or a generated ViewModel to use this example. Java ViewModel generation and the Designer are optional tools, not prerequisites for loading an FXML view.

For a named-module application, see the [module and lifecycle notes](architecture.md#modules-and-reflection). Do not simply add `module-info.java` without opening the packages used through reflection.

## Troubleshooting the first launch

| Symptom | Check |
| --- | --- |
| Maven cannot resolve `de.neitzel.lib:fx:1.0.2` or its parent | Install the library reactor first; use the same Maven local repository for both projects. For direct downloads, see the [Maven repository setup](../../README.md#use-a-release-from-maven). |
| `No plugin found for prefix 'javafx'` | Invoke the example POM directly, or use the complete application POM above. Do not invoke the goal on the library reactor parent. |
| JavaFX runtime/native library failure | Check the JDK version, architecture and JavaFX dependencies actually resolved; avoid mixing native artifacts from different platforms or JavaFX versions. |
| `getResource(...)` returns null | The FXML belongs under `src/main/resources` in the matching package; verify it was copied to `target/classes`. Resource names are case-sensitive. |
| `No component found for type` | Annotate the controller/converter/Component, include it in the context's scan root, and inspect `ComponentScanner.getErrors()` for unresolved dependencies. |
| Binding cannot find control/property/path | Match `fx:id`, a public JavaFX `nameProperty()` target, and a readable/writable Model Attribute. Use `ViewLoader`, not plain `FXMLLoader`. |
| Initial data shows but a bean setter does not update the UI | Update the observable ViewModel property; the bean is not itself observable. |
| A second window fails with an already-parented node | Create a separate context for that window; avoid reusing cached Component roots and controllers. |
| Reflective access fails only on module path | Add the appropriate `opens` declarations and verify registry service declarations, not only dependencies. |

Do not build a single ordinary JAR and expect it to include JavaFX's platform-specific runtime automatically. The Maven run goal handles the development launch; deployment requires the application's dependencies or a separately built runtime image. The person desk's JAR-plus-`libs` packaging is an example of the former, and the Designer has its own bundled runtime packaging.
