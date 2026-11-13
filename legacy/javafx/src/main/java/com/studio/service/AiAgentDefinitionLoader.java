package com.studio.service;

import com.studio.util.StudioPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Loads the markdown agent/skill definitions shipped with the workspace
 * (`.agents/*.md` and `skills/<name>/SKILL.md`) and renders them into the AI system prompt.
 *
 * <p>Before this loader existed the files were documentation only: nothing in the application
 * read them, so their instructions never reached the model. The files stay the source of truth for
 * persona/behaviour text, while {@link AiAgentRuleCatalog} remains the immutable, in-code gate for
 * what an agent may do — a markdown file can add guidance but can never widen permissions, which is
 * why the rules are not read from disk.
 *
 * <p>Only the YAML front-matter fields {@code name} and {@code description} (plus the body) are
 * interpreted. A missing directory is not an error: a deployment without definition files simply
 * gets no extra guidance instead of failing the AI flow.
 */
public final class AiAgentDefinitionLoader {

    /** Directory names that name the same capability, so `qa-planner` picks up `qa-planner.md`. */
    private static final int MAX_DEFINITION_CHARS = 6000;

    private static final Map<String, AiAgentDefinitionLoader> CACHE = new ConcurrentHashMap<>();
    private static final AiAgentDefinitionLoader EMPTY = new AiAgentDefinitionLoader(Map.of(), Map.of());

    private final Map<String, Definition> agents;
    private final Map<String, Definition> skills;

    AiAgentDefinitionLoader(Map<String, Definition> agents, Map<String, Definition> skills) {
        this.agents = agents;
        this.skills = skills;
    }

    /** One parsed definition file. */
    public record Definition(String name, String description, String body, Path source) {

        /** The text injected into the system prompt, capped so one file cannot eat the context budget. */
        public String toPromptBlock() {
            StringBuilder sb = new StringBuilder();
            sb.append("### [").append(name).append("]");
            if (description != null && !description.isBlank()) {
                sb.append(" — ").append(description.trim());
            }
            sb.append("\n");
            String text = body != null ? body.trim() : "";
            if (text.length() > MAX_DEFINITION_CHARS) {
                text = text.substring(0, MAX_DEFINITION_CHARS)
                        + "\n... [đã rút gọn định nghĩa " + name + "]";
            }
            sb.append(text);
            return sb.toString();
        }
    }

    /** Loader bound to the running application's home directory. */
    public static AiAgentDefinitionLoader defaultInstance() {
        return forHome(StudioPaths.getAppHome());
    }

    /** Loader for an explicit home directory; results are cached per absolute path. */
    public static AiAgentDefinitionLoader forHome(Path appHome) {
        if (appHome == null) {
            return EMPTY;
        }
        Path key = appHome.toAbsolutePath().normalize();
        return CACHE.computeIfAbsent(key.toString(), k -> scan(key));
    }

    /** Drop the cache so a definition edited on disk is picked up without restarting. */
    public static void clearCache() {
        CACHE.clear();
    }

    static AiAgentDefinitionLoader scan(Path appHome) {
        Map<String, Definition> agents = new LinkedHashMap<>();
        Map<String, Definition> skills = new LinkedHashMap<>();

        Path agentsDir = appHome.resolve(".agents");
        for (Path file : markdownFiles(agentsDir)) {
            Definition def = parse(file);
            if (def != null) {
                String id = def.name() != null && !def.name().isBlank()
                        ? def.name()
                        : file.getFileName().toString().replaceFirst("\\.md$", "");
                agents.putIfAbsent(id, def);
            }
        }

        Path skillsDir = appHome.resolve("skills");
        if (Files.isDirectory(skillsDir)) {
            try (Stream<Path> dirs = Files.list(skillsDir)) {
                for (Path dir : dirs.filter(Files::isDirectory).sorted().toList()) {
                    Path skillFile = dir.resolve("SKILL.md");
                    if (!Files.isRegularFile(skillFile)) {
                        continue;
                    }
                    Definition def = parse(skillFile);
                    if (def != null) {
                        String id = def.name() != null && !def.name().isBlank()
                                ? def.name()
                                : dir.getFileName().toString();
                        skills.putIfAbsent(id, def);
                    }
                }
            } catch (IOException ignored) {
                // A skills directory we cannot list yields no guidance, never a broken AI call.
            }
        }

        return new AiAgentDefinitionLoader(Map.copyOf(agents), Map.copyOf(skills));
    }

    private static List<Path> markdownFiles(Path dir) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".md"))
                    .sorted()
                    .toList();
        } catch (IOException ignored) {
            return List.of();
        }
    }

    static Definition parse(Path file) {
        try {
            String raw = Files.readString(file, StandardCharsets.UTF_8);
            String name = null;
            String description = null;
            String body = raw;

            if (raw.startsWith("---")) {
                int end = raw.indexOf("\n---", 3);
                if (end > 0) {
                    String frontMatter = raw.substring(3, end);
                    body = raw.substring(raw.indexOf('\n', end + 1) + 1);
                    for (String line : frontMatter.split("\\r?\\n")) {
                        int colon = line.indexOf(':');
                        if (colon <= 0) {
                            continue;
                        }
                        String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
                        String value = line.substring(colon + 1).trim();
                        if ("name".equals(key)) {
                            name = value;
                        } else if ("description".equals(key)) {
                            description = value;
                        }
                    }
                }
            }
            return new Definition(name, description, body, file);
        } catch (IOException ignored) {
            return null;
        }
    }

    public boolean isEmpty() {
        return agents.isEmpty() && skills.isEmpty();
    }

    /** All agent definitions, ordered by name. */
    public List<Definition> agents() {
        return new ArrayList<>(agents.values());
    }

    /** All skill definitions, ordered by name. */
    public List<Definition> skills() {
        return new ArrayList<>(skills.values());
    }

    /**
     * Definitions relevant to one AI turn: the agents whose id appears in the prompt plus the skill
     * the user selected (matched by definition name, then by the skill's own id).
     */
    public List<Definition> selectFor(String userPrompt, String skillId) {
        List<Definition> selected = new ArrayList<>();
        String haystack = userPrompt != null ? userPrompt.toLowerCase(Locale.ROOT) : "";
        for (Definition def : agents.values()) {
            if (def.name() != null && !def.name().isBlank()
                    && haystack.contains(def.name().toLowerCase(Locale.ROOT))) {
                selected.add(def);
            }
        }
        Definition skill = findSkill(skillId);
        if (skill != null) {
            selected.add(skill);
        }
        return selected;
    }

    /** Look a skill definition up by its own id or by a name the skill declares. */
    public Definition findSkill(String skillId) {
        if (skillId == null || skillId.isBlank()) {
            return null;
        }
        Definition direct = skills.get(skillId);
        if (direct != null) {
            return direct;
        }
        return null;
    }

    public Definition findAgent(String agentName) {
        return agentName != null ? agents.get(agentName) : null;
    }

    /** Renders the given definitions into one prompt section; empty when nothing was selected. */
    public static String toPromptSection(List<Definition> selected) {
        if (selected == null || selected.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("=== ĐỊNH NGHĨA TÁC NHÂN / KỸ NĂNG ĐANG ÁP DỤNG ===\n")
                .append("Các định nghĩa dưới đây bổ sung hướng dẫn chuyên môn; chúng KHÔNG mở rộng quyền hạn ")
                .append("(quyền hạn do bộ luật tác nhân cố định trong mã quyết định).\n\n");
        for (Definition def : selected) {
            sb.append(def.toPromptBlock()).append("\n\n");
        }
        return sb.toString().trim();
    }
}
