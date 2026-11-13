package com.studio;

import javafx.application.Application;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Launcher Entry Point Specification & Regression Guard")
class LauncherTest {

    @Test
    @DisplayName("Launcher must NOT extend javafx.application.Application (prevents silent missing-components crash on classpath/jpackage)")
    void launcherMustNotExtendApplication() {
        assertFalse(Application.class.isAssignableFrom(Launcher.class),
                "Launcher must NOT extend Application, otherwise JVM LauncherHelper triggers " +
                "'Error: JavaFX runtime components are missing' when running non-modularly");
    }

    @Test
    @DisplayName("Launcher must expose a public static void main(String[]) method")
    void launcherMustHavePublicStaticMainMethod() throws NoSuchMethodException {
        Method mainMethod = Launcher.class.getMethod("main", String[].class);
        assertNotNull(mainMethod);
        int modifiers = mainMethod.getModifiers();
        assertTrue(Modifier.isPublic(modifiers), "main must be public");
        assertTrue(Modifier.isStatic(modifiers), "main must be static");
        assertEquals(void.class, mainMethod.getReturnType(), "main must return void");
    }

    @Test
    @DisplayName("App class remains the JavaFX Application implementation")
    void appExtendsApplication() {
        assertTrue(Application.class.isAssignableFrom(App.class),
                "App must extend javafx.application.Application for JavaFX lifecycle");
    }
}
