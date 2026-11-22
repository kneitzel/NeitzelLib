# NeitzelLib Maven Project

## A Java Utilities Library

This repository is **not** a production-ready library, but rather a personal collection of small helpers, utilities, and
ideas that I find useful or interesting as a Java developer.

It serves primarily as a **knowledge base** and **inspiration pool** for my own development work. You're welcome to
explore, copy, modify, or improve whatever you find helpful.

> ⚠️ Use at your own discretion — no guarantees of stability, backwards compatibility, or completeness.

---

## License

This is free and unencumbered software released into the public domain. Please see the [License](LICENSE.md) for
details.

---

## Note on Content

The content of this repository — including source code, documentation, and examples — was partially created with the
assistance of ChatGPT (an AI model by OpenAI). The generated content has been reviewed, edited, and integrated into the
overall context by me.

Responsibility for all content lies entirely with me. Any generated content is subject
to [OpenAI’s Terms of Use](https://openai.com/policies/terms-of-use) and is — where possible — either in the public
domain or usable under a license compatible with this project's license.

## Components

### core

Core utilities, including [foreground desktop observations](core/docs/foreground-detection.md)
using JNA for optional native desktop integration, and [native application configuration folders](core/docs/application-directories.md). Use [ImageScaler.fitOpaque](core/docs/image-fitting.md) to fit images within bounds and composite transparent pixels onto a background.

### encryption

Some encryption classes that use format-preserving-encryption.

**Important** This is not for production use! Please use secure encryption in production!

### data-grid

Toolkit-independent filter, query/result and setup contracts for DataGrid. See [data-grid/README.md](data-grid/README.md).

### data-grid-jdbc

Optional mapped JDBC DataGrid source, initially qualified with H2. See [data-grid-jdbc/README.md](data-grid-jdbc/README.md).

### fx

JavaFX docking, saved HTML/SingleFile viewing, standalone bars and optional MVVM bindings from FXML views to ordinary Java models. Docking works with plain JavaFX and standard FXMLLoader without initializing MVVM. See [fx/README.md](fx/README.md) and the [docking guide](fx/docs/docking.md).

### injection

Package scanner and type-based container used by `fx`. See [injection/README.md](injection/README.md).

### fx-example

Person desk that shows how to use `fx` and `injection`. Build with `./mvnw -pl fx-example -am install`, then run `./mvnw -f fx-example/pom.xml javafx:run`. See [fx-example/README.md](fx-example/README.md).

### gson

Gson configuration and type adapters, plus JSON storage for versioned DataGrid setups. See the [Gson guide](gson/README.md).

### log4j

Configuration helpers for legacy Log4j 1 applications.

### net

IMAP account monitoring, new-message events and message utilities built on JavaMail.

### quarkus

Request filters and route logging for Quarkus applications.

## Start a JavaFX project

The [complete quick start](fx/docs/quick-start.md) includes local installation, a standalone Maven POM, Java classes, FXML and launch commands. Continue with [MVVM and runtime architecture](fx/docs/architecture.md), the [FXML reference](fx/README.md), and the [person desk walkthrough](fx-example/README.md). The [injection guide](injection/README.md) also covers use without JavaFX.

Build with Java 25. Maven wrapper version is 3.9.11; the project requires Maven 3.9.11 or later. Surefire fails the build if tests fail.

## Changes in 1.0.2

- Display saved HTML and SingleFile archives offline with [HtmlViewer](fx/docs/html-viewer.md). Each viewer can grant external image access for its current document.
- Configure single, multiple or disabled [DataGrid row selection](fx/docs/data-grid.md). Applications can track selected keys across pages and access the selected rows on the current page.
- Offer prefix suggestions with `AutoCompleteTextField`, removable tags with `TagChip`, and dismissible toolbars with `ToolBarPane`. See the [FXML control reference](fx/docs/fxml-controls.md).
- Give each window a [platform menu bar](fx/docs/bars.md#window-menus), including floating docking windows. Use [message catalogs](fx/docs/i18n.md) to translate FXML and Java controls and offer a language menu.

## Use a release from Maven

To use release `1.0.2`, add the Gitea registry to the consuming project's POM and use the release
version for the module you need:

```xml
<repositories>
    <repository>
        <id>gitea-neitzellib</id>
        <url>https://gitea.neitzel.de/api/packages/konrad/maven</url>
        <snapshots><enabled>false</enabled></snapshots>
    </repository>
</repositories>
<dependencies>
    <dependency>
        <groupId>de.neitzel.lib</groupId>
        <artifactId>fx</artifactId>
        <version>1.0.2</version>
    </dependency>
</dependencies>
```

The POM and transitive NeitzelLib modules resolve from the same registry.
See the [Gitea releases](https://gitea.neitzel.de/konrad/NeitzelLib/releases)
for the source tags and release notes.

## Java source conventions

Use focused Lombok annotations for mechanical accessors, constructors and logging;
see [the contributor instructions](AGENTS.md). The parent already configures
Lombok 1.18.48 as a provided dependency and an explicit annotation processor.
It is not required at runtime. Each checkout has a `lombok.config` boundary so
workstation-level settings cannot silently change generated APIs.

Keep behavior-bearing methods and JavaFX property operations explicit. Records
remain records, and identity-based Models do not acquire generated equality.
The [Person desk example](fx-example/README.md) demonstrates Lombok JavaBeans
and constructor injection while retaining ordinary JavaBean APIs.

API documentation runs Lombok's [delombok](https://projectlombok.org/features/delombok)
step at `process-classes` and reads `target/delombok`, including generated methods.
The plugin uses the same `${lombok.version}` as compilation; its output is not
added to the compiler source roots. Run `verify` (or at least `process-classes`)
before invoking standalone Javadoc/site reporting.
