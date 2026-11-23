package de.neitzel.fx.test;

import java.lang.reflect.Method;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

final class FxExtension implements BeforeEachCallback, AfterEachCallback, ParameterResolver {
    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(FxExtension.class);

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        var fixture = new FxFixture();
        fixture.start();
        context.getStore(NAMESPACE).put(FxFixture.class, fixture);
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        var fixture = context.getStore(NAMESPACE).remove(FxFixture.class, FxFixture.class);
        if (fixture != null) fixture.finish();
    }

    @Override
    public boolean supportsParameter(ParameterContext parameter, ExtensionContext context) {
        return parameter.getParameter().getType() == FxFixture.class
                && parameter.getDeclaringExecutable() instanceof Method
                && context.getStore(NAMESPACE).get(FxFixture.class) != null;
    }

    @Override
    public Object resolveParameter(ParameterContext parameter, ExtensionContext context) {
        return context.getStore(NAMESPACE).get(FxFixture.class, FxFixture.class);
    }
}
