package com.studio.service;

import com.studio.service.DocumentParserService.ParsedDocument;
import com.studio.util.StudioPaths;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves the user guide (docs/guide/USER_GUIDE.md) for the in-app help.
 *
 * Lookup order — first hit wins:
 *  1. {@code -Dstudio.guide=<file>} (explicit override, useful for tests / custom docs)
 *  2. {@code <data dir>/docs/guide/USER_GUIDE.md} — shipped inside the portable package
 *     ({@code build-portable.sh} copies this directory next to the app)
 *  3. {@code docs/guide/USER_GUIDE.md} relative to the working directory (running from source)
 *  4. classpath resource {@code /docs/guide/USER_GUIDE.md} (bundled in the JAR)
 *
 * Images inside the guide use paths relative to the markdown file ({@code images/x.png}).
 * The preview dialog renders raw Markdown text only, so {@link #inlineImages} rewrites those
 * references into {@code data:} URLs — the content does not depend on a viewer that can
 * resolve sibling files.
 */
public final class UserGuideService {

    public static final String GUIDE_RELATIVE = "docs/guide/USER_GUIDE.md";
    private static final String RESOURCE_PATH = "/" + GUIDE_RELATIVE;

    private static final Pattern MD_IMAGE = Pattern.compile("!\\[([^\\]]*)]\\(([^)\\s]+)\\)");

    private UserGuideService() {
    }

    /** Path of the guide on disk, or {@code null} when it only exists as a classpath resource. */
    public static Path resolveGuidePath() {
        String override = System.getProperty("studio.guide");
        if (override != null && !override.isBlank()) {
            Path p = Path.of(override);
            if (Files.isRegularFile(p)) return p;
        }
        Path inData = StudioPaths.getDataDir().resolve(GUIDE_RELATIVE);
        if (Files.isRegularFile(inData)) return inData;
        Path inWorkspace = Path.of(GUIDE_RELATIVE);
        if (Files.isRegularFile(inWorkspace)) return inWorkspace;
        return null;
    }

    /** Loads the guide, or returns {@code null} when it cannot be found anywhere. */
    public static ParsedDocument load() throws IOException {
        Path path = resolveGuidePath();
        String markdown;
        if (path != null) {
            markdown = Files.readString(path, StandardCharsets.UTF_8);
        } else {
            markdown = readResource();
            if (markdown == null) return null;
        }
        Path imageBase = path != null ? path.getParent() : null;
        String rendered = inlineImages(markdown, imageBase);
        int words = MarkdownConverterEngine.countWords(rendered);
        var conversion = new MarkdownConverterEngine.ConversionResult(
                rendered, "USER_GUIDE.md", "markdown", words, (int) Math.round(words / 1.5),
                0, java.time.LocalDateTime.now().toString(), "studio-guide");
        return new ParsedDocument("USER_GUIDE.md", path, false, rendered,
                "text/markdown", rendered.length(), path, conversion);
    }

    private static String readResource() throws IOException {
        try (InputStream in = UserGuideService.class.getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) return null;
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Rewrites every local {@code ![alt](path)} reference into an inline data URL so the guide
     * renders identically from disk, from the portable package and from the JAR. Remote URLs
     * are left untouched; unresolvable files keep their original Markdown (alt text remains).
     */
    static String inlineImages(String markdown, Path imageBase) {
        Matcher m = MD_IMAGE.matcher(markdown);
        StringBuilder out = new StringBuilder(markdown.length());
        int last = 0;
        while (m.find()) {
            String alt = m.group(1);
            String ref = m.group(2);
            String replacement = m.group();
            if (!ref.startsWith("http://") && !ref.startsWith("https://") && !ref.startsWith("data:")) {
                Path imagePath = findImage(ref, imageBase);
                if (imagePath != null) {
                    String dataUrl = toDataUrl(imagePath);
                    if (dataUrl != null) replacement = "![" + alt + "](" + dataUrl + ")";
                }
            }
            out.append(markdown, last, m.start());
            out.append(replacement);
            last = m.end();
        }
        out.append(markdown, last, markdown.length());
        return out.toString();
    }

    private static Path findImage(String ref, Path imageBase) {
        String clean = ref.split("[?#]")[0];
        List<Path> candidates = new ArrayList<>();
        if (imageBase != null) candidates.add(imageBase.resolve(clean));
        candidates.add(StudioPaths.getDataDir().resolve("docs/guide").resolve(clean));
        candidates.add(Path.of("docs/guide").resolve(clean));
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) return candidate;
        }
        // Fall back to the packaged copy of the images.
        try (InputStream in = UserGuideService.class.getResourceAsStream("/docs/guide/" + clean)) {
            if (in == null) return null;
        } catch (IOException e) {
            return null;
        }
        Path temp = temporaryCopy(clean);
        return temp;
    }

    private static Path temporaryCopy(String clean) {
        try (InputStream in = UserGuideService.class.getResourceAsStream("/docs/guide/" + clean)) {
            if (in == null) return null;
            Path target = Files.createTempFile("studio-guide-", "-" + Path.of(clean).getFileName());
            target.toFile().deleteOnExit();
            Files.write(target, in.readAllBytes());
            return target;
        } catch (IOException e) {
            return null;
        }
    }

    private static String toDataUrl(Path imagePath) {
        try {
            String fileName = imagePath.getFileName().toString().toLowerCase();
            String mime = fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") ? "image/jpeg"
                    : fileName.endsWith(".gif") ? "image/gif"
                    : fileName.endsWith(".webp") ? "image/webp"
                    : "image/png";
            byte[] bytes = Files.readAllBytes(imagePath);
            return "data:" + mime + ";base64," + java.util.Base64.getEncoder().encodeToString(bytes);
        } catch (IOException e) {
            return null;
        }
    }
}
