package de.neitzel.fx.injectfx;

import de.neitzel.inject.InjectableComponentScanner;
import javafx.fxml.FXMLLoader;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URL;

@Slf4j
public class InjectingFXMLLoader {

    private final InjectingControllerFactory controllerFactory;

    public InjectingFXMLLoader() {
        controllerFactory = new InjectingControllerFactory();
    }

    public InjectingFXMLLoader(InjectingControllerFactory controllerFactory) {
        this.controllerFactory = controllerFactory;
    }

    public InjectingFXMLLoader(String packageName) {
        controllerFactory = new InjectingControllerFactory();
        InjectableComponentScanner scanner = new InjectableComponentScanner(packageName);
        FXMLComponentInstances instances = new FXMLComponentInstances(scanner);
        addInjectingData(instances);
    }

    private void addInjectingData(FXMLComponentInstances instances) {
        for (var clazz: instances.getInstanceMap().keySet()) {
            addInjectingData(clazz, instances.getInstance(clazz));
        }
    }

    public void addInjectingData(Class<?> clazz, Object object) {
        controllerFactory.addInjectingData(clazz, object);
    }

    public <T> T load(URL url) throws IOException {
        FXMLLoader loader = new FXMLLoader(url);
        loader.setControllerFactory(controllerFactory);
        return loader.load();
    }
}
