package de.neitzel.fx.tray;

import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class TraySupportTest {
    @Test void closeOpenQuitAndUnavailableTrayKeepTheWindowRecoverable() throws Exception {
        CountDownLatch started=new CountDownLatch(1);
        try { Platform.startup(started::countDown); } catch(IllegalStateException alreadyStarted) { started.countDown(); }
        assertTrue(started.await(10,TimeUnit.SECONDS));
        CountDownLatch done=new CountDownLatch(1); AtomicReference<Throwable> failure=new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                Platform.setImplicitExit(false);
                Stage stage=new Stage(); stage.show();
                AtomicReference<Runnable> open=new AtomicReference<>(), quit=new AtomicReference<>();
                AtomicBoolean allowed=new AtomicBoolean(), exited=new AtomicBoolean(), removed=new AtomicBoolean();
                TraySupport.Backend backend=(icon,label,onOpen,onQuit) -> {
                    open.set(onOpen); quit.set(onQuit);
                    return CompletableFuture.completedFuture(() -> removed.set(true));
                };
                var tray=TraySupport.attach(stage,new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB),"test",allowed::get,() -> exited.set(true),backend);
                assertTrue(tray.isAvailable());
                stage.fireEvent(new WindowEvent(stage,WindowEvent.WINDOW_CLOSE_REQUEST));
                assertFalse(stage.isShowing()); assertFalse(exited.get());
                open.get().run(); assertTrue(stage.isShowing());
                Runnable nativeQuit = System.getProperty("os.name", "").startsWith("Mac")
                        ? () -> {
                            var app = com.sun.glass.ui.Application.GetApplication();
                            app.getEventHandler().handleQuitAction(app, System.nanoTime());
                        } : quit.get();
                // Native Quit must use the same confirmation as the tray, even hidden.
                stage.hide(); nativeQuit.run();
                assertTrue(stage.isShowing()); assertFalse(exited.get());
                allowed.set(true); nativeQuit.run(); assertTrue(exited.get());
                tray.close(); assertTrue(removed.get());
                var unavailable=TraySupport.attach(stage,new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB),"test",() -> true,() -> {},
                        (icon,label,onOpen,onQuit) -> CompletableFuture.completedFuture(null));
                stage.fireEvent(new WindowEvent(stage,WindowEvent.WINDOW_CLOSE_REQUEST));
                assertTrue(stage.isShowing()); assertFalse(unavailable.isAvailable());
                unavailable.close();
                CompletableFuture<TraySupport.Registration> pending=new CompletableFuture<>();
                AtomicBoolean lateRemoved=new AtomicBoolean();
                var late=TraySupport.attach(stage,new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB),"test",() -> true,() -> {},
                        (icon,label,onOpen,onQuit) -> pending);
                late.close(); pending.complete(() -> lateRemoved.set(true));
                assertTrue(lateRemoved.get()); assertFalse(late.isAvailable());
                stage.hide();
            } catch(Throwable error) { failure.set(error); } finally { done.countDown(); }
        });
        assertTrue(done.await(10,TimeUnit.SECONDS));
        if(failure.get()!=null) throw new AssertionError(failure.get());
    }
}
