# TODO

- SimpleListProperty auch nutzen bei Collections!

# Ideen

# Bindings

Bindngs kommen über ein spezielles Binding Control, das dann notwendige Bindings beinhaltet.

Da kann dann auch eine eigene Logik zur Erkennung des Bindings erfolgen oder zusätziche Informationen bezüglich
notwendiger Elemente in dem ViewModel

## Bidirektional Converter für Bindings

```Java
StringConverter<Instant> converter = new StringConverter<>() {
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    @Override
    public String toString(Instant instant) {
        return instant == null ? "" : formatter.format(instant);
    }

    @Override
    public Instant fromString(String string) {
        if (string == null || string.isBlank()) return null;
        try {
            LocalDateTime dateTime = LocalDateTime.parse(string, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            return dateTime.atZone(ZoneId.systemDefault()).toInstant();
        } catch (DateTimeParseException e) {
            // Optional: Fehlermeldung anzeigen
            return null;
        }
    }
};

// Binding einrichten:
Bindings.

bindBidirectional(textField.textProperty(),instantProperty,converter);
```

Overloads von bindBidirectional
bindBidirectional(Property<String>, Property<?>, Format)
bindBidirectional(Property<String>, Property<T>, StringConverter<T>)
bindBidirectional(Property<T>, Property<T>)

## Unidirektional mit Bindings.createStringBinding()

```Java
DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

textField.

textProperty().

bind(Bindings.createStringBinding(
        () ->{

Instant instant = instantProperty.get();
            return instant ==null?"":formatter.

format(instant);
    },
instantProperty
));
```

Bindings:

- BooleanBinding
- DoubleBinding
- FloatBinding
- IntegerBinding
- LongBinding
- StringBinding
- ObjectBinding
- NumberBinding

Bindings haben Methoden, die dann weitere Bindings erzeugen.
==> Parameter der Methode: Property, Observable und einer Methode, die ein Binding erstellt und das ViewModel<?>, das
dann genutzt werden kann, um weitere Properties zu holen ==> Erzeugung von neuen Properties.

Diese BindingCreators haben Namen, PropertyTyp und ein BindingType.

# FXMLComponent

Dient dem Laden einer Komponente und bekommt dazu das fxml, die Daten und ggf. auch eine ModelView.

# ModelView generieren

Damit eine ModelView nicht ständig manuell generiert werden muss, ist hier ggf. etwas zu generieren?
Ggf. eine eigenes Beschreibungssprache?

# Aufbau einer Validierung

- gehört in das ViewModel
- 