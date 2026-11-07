# fx-example

A person desk that uses [`fx`](../fx/README.md) and [`injection`](../injection/README.md). The guides use this module as the worked example.

The desk shows a direct binding in each direction, a converter, a computed binding, a view model subclass, a component with a nested note, a repeat, a table with a starting width and an open column, a date column, a list of simple values, and messages. The container side shows `@Config`, `@Singleton`, `@Named`, and injection by constructor, field, and setter.

From the NeitzelLib root, with Java 21:

```bash
mvn -pl fx-example -am javafx:run
```

The launch class is `de.neitzel.fx.example.Main`. The model is an ordinary `Person`. The view is `Desk.fxml`. `DeskLoadsTest` loads that same desk.
