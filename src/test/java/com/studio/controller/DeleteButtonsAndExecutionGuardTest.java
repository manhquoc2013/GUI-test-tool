package com.studio.controller;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.shape.SVGPath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class DeleteButtonsAndExecutionGuardTest {

    @BeforeAll
    static void initFx() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Already initialized
        }
    }

    @Test
    void testCaseRowActionButtonEvent_recognizesDeleteButton() throws Exception {
        Method m = SuiteWorkspacePageController.class.getDeclaredMethod("isCaseRowRunButtonEvent", MouseEvent.class);
        m.setAccessible(true);

        Button deleteBtn = new Button();
        deleteBtn.getStyleClass().add("case-row-delete-btn");
        SVGPath svg = new SVGPath();
        deleteBtn.setGraphic(svg);

        HBox row = new HBox(deleteBtn);
        new javafx.scene.Scene(row);
        row.applyCss();
        row.layout();

        MouseEvent clickOnDeleteSvg = new MouseEvent(
                svg, svg, MouseEvent.MOUSE_PRESSED,
                0, 0, 0, 0, MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false, true, false, false, null
        );

        boolean isAction = (boolean) m.invoke(null, clickOnDeleteSvg);
        assertTrue(isAction, "Click trên icon delete của case row phải được nhận diện là action button event");
    }

    @Test
    void testExplorerPage_hasDeleteButtonsInjected() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        final Exception[] err = new Exception[1];

        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/explorer_page.fxml"));
                loader.load();
                ExplorerPageController controller = loader.getController();

                Field deleteBtnField = ExplorerPageController.class.getDeclaredField("deleteBtn");
                deleteBtnField.setAccessible(true);
                Button deleteBtn = (Button) deleteBtnField.get(controller);
                assertNotNull(deleteBtn, "deleteBtn trong toolbar phải được inject từ FXML");
                assertEquals("Xóa", deleteBtn.getText());
                assertTrue(deleteBtn.getStyleClass().contains("toolbar-btn-danger"));

                Field previewDeleteBtnField = ExplorerPageController.class.getDeclaredField("previewDeleteBtn");
                previewDeleteBtnField.setAccessible(true);
                Button previewDeleteBtn = (Button) previewDeleteBtnField.get(controller);
                assertNotNull(previewDeleteBtn, "previewDeleteBtn trong preview panel phải được inject từ FXML");
                assertEquals("Xóa", previewDeleteBtn.getText());
            } catch (Exception e) {
                err[0] = e;
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        if (err[0] != null) throw err[0];
    }

    @Test
    void testSuiteWorkspacePage_hasDeleteButtonsInjected() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        final Exception[] err = new Exception[1];

        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/suite_workspace_page.fxml"));
                loader.load();
                SuiteWorkspacePageController controller = loader.getController();

                Field deleteCaseBtnField = SuiteWorkspacePageController.class.getDeclaredField("deleteCaseBtn");
                deleteCaseBtnField.setAccessible(true);
                Button deleteCaseBtn = (Button) deleteCaseBtnField.get(controller);
                assertNotNull(deleteCaseBtn, "deleteCaseBtn trong workspace action bar phải được inject từ FXML");
                assertEquals("Xóa case", deleteCaseBtn.getText());

                Field deleteSuiteBtnField = SuiteWorkspacePageController.class.getDeclaredField("deleteSuiteBtn");
                deleteSuiteBtnField.setAccessible(true);
                Button deleteSuiteBtn = (Button) deleteSuiteBtnField.get(controller);
                assertNotNull(deleteSuiteBtn, "deleteSuiteBtn trong suite info panel phải được inject từ FXML");
                assertEquals("Xóa Suite", deleteSuiteBtn.getText());
            } catch (Exception e) {
                err[0] = e;
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        if (err[0] != null) throw err[0];
    }
}

