package de.neitzel.fx.view;

import de.neitzel.fx.grid.filter.*;
import de.neitzel.grid.*;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;

import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;

/**
 * Explicit desktop acceptance probe, not part of unattended Surefire execution. Uses native OS
 * input.
 */
public final class FilterPopupNativeProbe {
    private static Stage stage;
    private static Button opener;
    private static TextField outside;
    private static BuiltInFilterEditor editor;
    private static DatePicker date;
    private static FilterPopup popup;
    private static FilterCondition committed =
            new NumericFilter("x", NumericFilter.Operator.EQUALS, BigDecimal.TEN, null);
    private static FilterPopup.Options options = FilterPopup.Options.defaults();
    private static Robot robot;

    private static <T> T fx(Callable<T> work) throws Exception {
        var task = new FutureTask<>(work);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }

    private static void settle() throws Exception {
        Thread.sleep(350);
        fx(() -> null);
    }

    private static void click(Node node) throws Exception {
        double[] point =
                fx(
                        () -> {
                            var b = node.localToScreen(node.getBoundsInLocal());
                            return new double[] {b.getCenterX(), b.getCenterY()};
                        });
        robot.mouseMove((int) point[0], (int) point[1]);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        settle();
    }

    private static void key(int code) throws Exception {
        robot.keyPress(code);
        robot.keyRelease(code);
        settle();
    }

    private static TextField operand() {
        return (TextField) editor.view().lookup("#operand");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        System.out.println("PASS " + message);
    }

    private static void open() {
        if (popup != null && popup.isOpen()) {
            popup.outside();
            return;
        }
        editor =
                BuiltInFilterEditor.load(
                        new FilterEditorContext(
                                "x",
                                FilterColumn.number(Locale.US, true, null, null),
                                committed,
                                List.of(),
                                FilterColumn.Checklist.DISABLED));
        date = new DatePicker();
        date.setAccessibleText("Nested date chooser probe");
        VBox content = new VBox(8, editor.view(), date);
        FilterEditor wrapped =
                new FilterEditor() {
                    public Parent view() {
                        return content;
                    }

                    public FilterCondition draft() {
                        return editor.draft();
                    }

                    public ReadOnlyStringProperty validationMessageProperty() {
                        return editor.validationMessageProperty();
                    }

                    public void close() {
                        date.hide();
                        editor.close();
                    }
                };
        popup = new FilterPopup(opener, wrapped, options, c -> {}, c -> committed = c);
        popup.show();
    }

    private static Region spacer() {
        Region spacer = new Region();
        spacer.setPrefHeight(300);
        return spacer;
    }

    public static void main(String[] args) throws Exception {
        try {
            DataGridTest.start();
            fx(
                    () -> {
                        Platform.setImplicitExit(false);
                        stage = new Stage();
                        opener = new Button("Filter");
                        opener.setOnAction(e -> open());
                        outside = new TextField();
                        outside.setPromptText("Outside target");
                        VBox root =
                                new VBox(
                                        20,
                                        new Label("Native filter popup qualification"),
                                        opener,
                                        spacer(),
                                        outside);
                        root.setStyle("-fx-padding: 24;");
                        stage.setScene(new Scene(root, 650, 600));
                        stage.setTitle("DataGrid native filter qualification");
                        stage.show();
                        stage.toFront();
                        stage.requestFocus();
                        return null;
                    });
            if (Arrays.asList(args).contains("--manual")) {
                var finished = new CountDownLatch(1);
                fx(
                        () -> {
                            CheckBox acceptOutside = new CheckBox("Accept on outside click");
                            CheckBox showActions = new CheckBox("Show OK / Cancel");
                            showActions.setSelected(true);
                            Runnable configure =
                                    () ->
                                            options =
                                                    new FilterPopup.Options(
                                                            acceptOutside.isSelected()
                                                                    ? FilterPopup.Outside.ACCEPT
                                                                    : FilterPopup.Outside.CANCEL,
                                                            showActions.isSelected(),
                                                            showActions.isSelected());
                            acceptOutside.setOnAction(e -> configure.run());
                            showActions.setOnAction(e -> configure.run());
                            ((VBox) stage.getScene().getRoot())
                                    .getChildren()
                                    .addAll(acceptOutside, showActions);
                            stage.setOnHidden(e -> finished.countDown());
                            return null;
                        });
                System.out.println("MANUAL PROBE READY; close its window to finish");
                finished.await();
                return;
            }
            robot = new Robot();
            robot.setAutoDelay(100);
            settle();
            click(opener);
            check(
                    fx(() -> popup != null && popup.isShowing()),
                    "native opener click and anchored placement");
            click(operand());
            key(KeyEvent.VK_END);
            key(KeyEvent.VK_2);
            key(KeyEvent.VK_ENTER);
            check(
                    fx(
                            () ->
                                    !popup.isOpen()
                                            && ((NumericFilter) committed)
                                                    .operand()
                                                    .equals(new BigDecimal("102"))),
                    "native typing and Enter commit");
            check(fx(() -> opener.isFocused()), "explicit acceptance returns focus to opener");
            click(opener);
            fx(
                    () -> {
                        operand().setText("44");
                        return null;
                    });
            key(KeyEvent.VK_ESCAPE);
            check(
                    fx(
                            () ->
                                    !popup.isOpen()
                                            && ((NumericFilter) committed)
                                                    .operand()
                                                    .equals(new BigDecimal("102"))),
                    "Escape rollback and fresh reopen");
            click(opener);
            fx(
                    () -> {
                        operand().setText("55");
                        return null;
                    });
            click(outside);
            check(
                    fx(
                            () ->
                                    !popup.isOpen()
                                            && outside.isFocused()
                                            && ((NumericFilter) committed)
                                                    .operand()
                                                    .equals(new BigDecimal("102"))),
                    "outside Cancel preserves filter and clicked target focus");
            fx(
                    () -> {
                        options = new FilterPopup.Options(FilterPopup.Outside.ACCEPT, false, false);
                        return null;
                    });
            click(opener);
            fx(
                    () -> {
                        operand().setText("66");
                        return null;
                    });
            click(outside);
            check(
                    fx(
                            () ->
                                    !popup.isOpen()
                                            && outside.isFocused()
                                            && ((NumericFilter) committed)
                                                    .operand()
                                                    .equals(new BigDecimal("66"))),
                    "outside Accept with hidden OK/Cancel");
            click(opener);
            fx(
                    () -> {
                        operand().setText("bad");
                        return null;
                    });
            click(outside);
            settle();
            check(
                    fx(
                            () ->
                                    popup.isShowing()
                                            && !popup.errorMessage().isBlank()
                                            && ((NumericFilter) committed)
                                                    .operand()
                                                    .equals(new BigDecimal("66"))),
                    "invalid outside Accept reopens with error and retains committed filter");
            fx(
                    () -> {
                        operand().setText("77");
                        return null;
                    });
            key(KeyEvent.VK_ENTER);
            check(
                    fx(
                            () ->
                                    !popup.isOpen()
                                            && ((NumericFilter) committed)
                                                    .operand()
                                                    .equals(new BigDecimal("77"))),
                    "Enter works without action buttons");
            click(opener);
            fx(
                    () -> {
                        operand().setText("88");
                        return null;
                    });
            click(opener);
            check(
                    fx(
                            () ->
                                    !popup.isOpen()
                                            && ((NumericFilter) committed)
                                                    .operand()
                                                    .equals(new BigDecimal("88"))),
                    "opener re-click follows outside policy without reopening");
            click(opener);
            ComboBox<?> combo = fx(() -> (ComboBox<?>) editor.view().lookup("#operator"));
            click(combo);
            check(fx(combo::isShowing), "native nested ComboBox opens");
            key(KeyEvent.VK_ESCAPE);
            check(
                    fx(() -> !combo.isShowing() && popup.isShowing()),
                    "first Escape closes nested ComboBox only");
            click(combo);
            key(KeyEvent.VK_DOWN);
            key(KeyEvent.VK_ENTER);
            check(
                    fx(() -> !combo.isShowing() && popup.isOpen()),
                    "nested ComboBox selection does not accept parent");
            Node arrow = fx(() -> date.lookup(".arrow-button"));
            click(arrow);
            check(fx(date::isShowing), "native nested DatePicker opens");
            key(KeyEvent.VK_ESCAPE);
            check(
                    fx(() -> !date.isShowing() && popup.isOpen()),
                    "first Escape closes nested DatePicker only");
            key(KeyEvent.VK_ESCAPE);
            check(fx(() -> !popup.isOpen()), "second Escape cancels parent");
            click(opener);
            new ProcessBuilder("osascript", "-e", "tell application \"Finder\" to activate")
                    .start()
                    .waitFor();
            settle();
            check(fx(() -> !popup.isOpen()), "other-application dismissal");
            fx(
                    () -> {
                        stage.toFront();
                        stage.requestFocus();
                        return null;
                    });
            settle();
            click(opener);
            fx(
                    () -> {
                        stage.close();
                        return null;
                    });
            settle();
            check(fx(() -> !popup.isOpen()), "owner disposal cancels and releases editor");
            System.out.println("NATIVE MATRIX PASSED");
        } catch (Throwable failure) {
            failure.printStackTrace();
            System.exit(1);
        } finally {
            if (stage != null)
                fx(
                        () -> {
                            stage.close();
                            return null;
                        });
            Platform.exit();
        }
    }
}
