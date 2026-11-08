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
using JNA for optional native desktop integration.

### encryption

Some encryption classes that use format-preserving-encryption.

**Important** This is not for production use! Please use secure encryption in production!

### fx

JavaFX library that binds an FXML view to an ordinary Java model. See [fx/README.md](fx/README.md).

### injection

Package scanner and type-based container used by `fx`. See [injection/README.md](injection/README.md).

### fx-example

Person desk that shows how to use `fx` and `injection`. Run `mvn -pl fx-example -am javafx:run`. See [fx-example/README.md](fx-example/README.md).

### gson

TypeAdapter for gson.

### log4j

Stuff for log4j (Version 1). Please use log4j2 if you want to use log4j! (Or log with slf4j as I do in all other
modules!)

### net

Some network stuff using javax.mail as dependency.

### quarkus

Some additions for quarkus that might be useful.