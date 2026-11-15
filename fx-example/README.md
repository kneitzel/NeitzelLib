# Person desk reference application

This runnable application demonstrates [NeitzelLib FX](../fx/README.md) and [injection](../injection/README.md) together. It edits one Person with a home Address, nested Note, visit list, selected visit and tags. Use it as a reference for wiring a real screen; the [minimal quick start](../fx/docs/quick-start.md) is the smaller starting point for a new project.

Separate Java/FXML launchers demonstrate [standalone bars](../fx/docs/bars.md)
and [docking](../fx/docs/docking.md) without initializing MVVM or injection.
Their guides describe the implemented behavior and launch options.

## Build and run

From the NeitzelLib root with JDK 25:

```bash
./mvnw -pl fx-example -am install
./mvnw -f fx-example/pom.xml javafx:run
```

The first command installs local `1.0-SNAPSHOT` artifacts; the second runs only the application module. Do not combine `javafx:run` with `-am`, since the parent has no JavaFX launcher. See the [quick start](../fx/docs/quick-start.md) for test reports.

The packaged alternative, after `package` or `install`, is:

```bash
java -jar fx-example/target/fx-example-1.0-SNAPSHOT.jar
```

Keep the adjacent `target/libs` directory with the JAR: the manifest refers to those copied runtime dependencies. A desktop session is required. This example has no `module-info.java`; it is a classpath application.

## Follow the code

Paths below are relative to this module. Java sources share `src/main/java/de/neitzel/fx/example/`; FXML resources share `src/main/resources/de/neitzel/fx/example/view/`.

| File/type | Responsibility |
| --- | --- |
| `Main` | Plain Java launcher calling `Application.launch` |
| `DeskApp` | Scan configuration, fresh sample Model, ViewLoader, Scene and message popup registration |
| `model/Person`, `Address`, `Note`, `Role` | JavaBean data and enum; no JavaFX properties required |
| `view/DeskController` and `Desk.fxml` | Screen handlers, computed greeting, controls and binding declarations |
| `view/PersonViewModel` | Handwritten ViewModel subclass with a derived title |
| `view/AddressForm` and `AddressForm.fxml` | Reusable home subform and injected heading, containing NoteForm |
| `view/NoteForm` and `NoteForm.fxml` | Nested Component bound to the note |
| `view/VisitForm` and `VisitForm.fxml` | Fresh form per repeated Address |
| `view/VisitRow` and `VisitRow.fxml` | Table row with editable fields and a trailing Remove button |
| `src/test/resources/de/neitzel/fx/example/NameLine.fxml` | Test-only second View of the same Person without reusable Component nodes |
| `view/AgeConverter`, `StreetConverter`, `VisitedOnConverter` | Text adaptation, validation failures and ISO dates |

Use the actual source directories to locate helper services such as `AgeSuffix`, `StreetPolicy` and `FormHeading`. The injection guide explains constructor, field and setter injection with these types. No Designer code generation is required to run this example; `PersonViewModel` is handwritten.

## Lombok and annotation processing

`Person`, `Address` and `Note` use `@Getter`/`@Setter` to generate ordinary JavaBean
accessors. Their defaults, mutable lists and object identity are unchanged; no
`@Data` equality or string representation is generated. JavaFX property methods
remain explicit. `AgeConverter` and `VisitRow` use
`@RequiredArgsConstructor(onConstructor_ = @Inject)` so the generated public
constructors retain the injection annotation.

Lombok is inherited from the parent as a compile-time-only dependency. The
optional `designer-preview` profile explicitly lists **both** Lombok and the
Designer Model metadata processor. Keep both when changing that profile:
metadata discovery must see the generated accessors during compilation. The
normal build needs no Designer processor; Lombok still runs.

## Try the behaviors

1. Change Name. The Model and name echo follow; title and computed greeting refresh. The draft field uses `direction="model"`: its edits write to the Model but later changes from another field do not overwrite it.
2. Edit Age using its `years ` text format. An invalid value stays visible while the previous Model value is retained. A message explains the failure; a valid value clears that conversion failure.
3. Edit the home street. The path-bound label follows. Empty street input fails through `StreetConverter`; the parent receives the child message with the `home.street` source prefix.
4. Edit the nested note. AddressForm's inner NoteForm binds to its own Model. The `Opened as ...` label records initial setup rather than tracking every later city edit.
5. Add a visit. Both the repeated forms and Table gain a row. Edit a street in one representation and observe the same Address in the other. The Table's Remove button delegates to the controller.
6. Select a visit in the ComboBox or Table. Both use the `selectedVisit` Attribute. Resize, sort and filter Table columns; filtering and sorting affect displayed rows without reordering the Model's list.
7. Edit the visited date in ISO form (`yyyy-MM-dd`). Invalid text keeps the old date. Add a tag to see a shared ObservableList of simple values.
8. Try the information, warning and fatal message buttons. Severity is data and logging behavior; a fatal message does not itself exit the application. Close each non-blocking popup normally.

Changing a JavaBean setter directly after loading does not notify controls. All edits above go through ViewModel properties or observable collections. The [architecture guide](../fx/docs/architecture.md) explains this distinction and why you should normally use a new context for a separate window.

## Automated regression coverage

`src/test/java/de/neitzel/fx/example/DeskLoadsTest.java` loads the real Desk FXML on JavaFX and checks initial values, all binding directions, derived/computed text, conversion failures and recovery, child messages, Table edits/removal, message severities and boolean/enum write-back. It also demonstrates that a direct bean setter leaves controls unchanged and that another simple View reuses the same Model's ViewModel.

Run the reactor tests without installing artifacts with:

```bash
./mvnw -pl fx-example -am clean verify
```

Inspect Surefire XML reports; a successful Maven exit alone is insufficient with the current parent configuration. JavaFX tests need a usable graphical environment. See the Designer integration section below for the optional preview setup; ordinary application tests do not require the Designer.

## Open the example in NeitzelFXDesigner

The repository root includes `neitzel-fx-designer.json` with the `person-desk` context. It selects `fx-example`, classpath launch, the `designer-preview` Maven profile, the local runtime key `java21`, and `de.neitzel.fx.example.preview.DeskPreviewProvider`. Configure your own JDK path for that key; machine paths are not part of the shared project configuration.

The optional profile adds the Designer preview API only to the test source set, compiles `src/designer/java`, includes its resources, and produces Model metadata for Person, Address and Note. It does not add a runtime dependency to the ordinary application, replace its handwritten PersonViewModel, or generate a default ViewModel registry.

After preparing the Designer's local preview API and metadata processor artifacts, build with:

```bash
./mvnw -pl fx-example -am -Pdesigner-preview verify
```

Check XML test reports as for the normal build. Follow `docs/user-guide/quick-start.md` in the [NeitzelFXDesigner repository](https://gitea.neitzel.de/konrad/NeitzelFXDesigner) for the complete artifact preparation, library-example setup script, local runtime mapping, application launch and Build and Refresh workflow. That guide covers both the standalone host and IntelliJ integration; this profile alone is not a Designer installation.

FXML documents carry `neitzel-model` processing instructions for discovery. The preview provider offers `desk`, `address`, `visit`, `row` and `note` samples for the corresponding documents. Every session receives fresh sample Models and an injection context; the preview does not install the application's message popups. Open the document, Build and Refresh, choose its matching sample, then start interactive preview. Editing the preview's data does not save those values into the application source.

The role choices are populated in `DeskController.initialize()` rather than using an application-enum `fx:constant` in FXML, because the current Designer projection cannot resolve that application constant. This is a Designer limitation, not a restriction of NeitzelLib or ordinary FXMLLoader. The regular application still shows the same Role values.
