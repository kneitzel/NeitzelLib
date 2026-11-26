package de.neitzel.fx.injection.fixture;

import de.neitzel.fx.view.Converter;
import jakarta.inject.Singleton;

@Singleton
public final class FormatName implements Converter {
    @Override
    public Object toControlValue(Object value) { return "[" + value + "]"; }

    @Override
    public Object toAttributeValue(Object value) { return value; }
}
