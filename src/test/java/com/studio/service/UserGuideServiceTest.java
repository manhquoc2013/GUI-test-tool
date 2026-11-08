package com.studio.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * T1-T4 for the help drawer's full-guide entry point: the guide must resolve from source,
 * from disk and from the packaged classpath, and every local image must be inlined so the
 * Markdown preview dialog needs no viewer-side file resolution.
 */
class UserGuideServiceTest {

    @Test
    void loadResolvesGuideAndInlinesImages() throws Exception {
        Path resolved = UserGuideService.resolveGuidePath();
        if (resolved == null) {
            // Classpath-only run (packaged build): the resource must still be there.
            assertNotNull(getClass().getResource("/docs/guide/USER_GUIDE.md"),
                    "guide missing from disk AND from classpath");
        }
        var doc = UserGuideService.load();
        assertNotNull(doc, "guide could not be loaded");
        assertEquals("USER_GUIDE.md", doc.getFileName());
        String content = doc.getContent();
        assertTrue(content.startsWith("# Hướng dẫn sử dụng Test Studio"),
                "unexpected guide header: " + content.substring(0, Math.min(80, content.length())));
        assertTrue(content.contains("data:image/png;base64,"),
                "images were not inlined as data URLs");
        assertFalse(content.contains("](images/"),
                "a relative image reference survived inlining");
    }

    @Test
    void inlineImagesLeavesRemoteDataAndMissingReferencesUntouched() {
        String md = "![a](https://example.com/x.png)\n"
                + "![b](data:image/png;base64,AAAA)\n"
                + "![c](images/does-not-exist.png)\n";
        String out = UserGuideService.inlineImages(md, null);
        assertTrue(out.contains("![a](https://example.com/x.png)"), "remote URL was rewritten");
        assertTrue(out.contains("![b](data:image/png;base64,AAAA)"), "data URL was rewritten");
        assertTrue(out.contains("![c](images/does-not-exist.png)"), "unresolvable path was dropped");
    }

    @Test
    void inlineImagesInlinesRealPngFromBaseDir(@TempDir Path dir) throws Exception {
        Path images = Files.createDirectories(dir.resolve("images"));
        byte[] png = tinyPng();
        Files.write(images.resolve("shot.png"), png);

        String out = UserGuideService.inlineImages("![ảnh](images/shot.png)", dir);

        assertTrue(out.startsWith("![ảnh](data:image/png;base64,"), "not inlined: " + out);
        String b64 = out.substring("![ảnh](data:image/png;base64,".length(), out.length() - 1);
        assertArrayEquals(png, java.util.Base64.getDecoder().decode(b64),
                "inlined bytes differ from the source png");
    }

    @Test
    void guideAndImagesAreBundledOnTheClasspath() throws Exception {
        assertNotNull(getClass().getResource("/docs/guide/USER_GUIDE.md"),
                "USER_GUIDE.md is not bundled into the build output (pom <resources>)");
        assertNotNull(getClass().getResource("/docs/guide/images/01-explorer.png"),
                "guide images are not bundled into the build output (pom <resources>)");
        try (InputStream in = getClass().getResourceAsStream("/docs/guide/USER_GUIDE.md")) {
            String bundled = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(bundled.contains("# Hướng dẫn sử dụng Test Studio"));
            assertTrue(bundled.contains("images/01-explorer.png"),
                    "bundled guide lost its image references");
        }
    }

    /** 1x1 transparent PNG — enough to prove the bytes survive the Base64 round trip. */
    private static byte[] tinyPng() {
        return java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
    }
}
