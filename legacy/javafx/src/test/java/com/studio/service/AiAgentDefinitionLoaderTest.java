package com.studio.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The agent/skill markdown files used to be documentation only — nothing read them. These tests pin
 * the loader that now renders them into the AI system prompt, and pin that missing files degrade to
 * "no extra guidance" instead of breaking the AI flow.
 */
public class AiAgentDefinitionLoaderTest {

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    @Test
    public void loadsAgentsAndSkillsFromFrontMatter(@TempDir Path home) throws IOException {
        write(home.resolve(".agents/qa-planner.md"), """
                ---
                name: qa-planner
                description: QA Architect & Spec Miner
                tools: Read, Grep
                model: inherit
                ---

                # QA Planner
                Extract user journeys and assign TEST-IDs.
                """);
        write(home.resolve("skills/test-quality-gate/SKILL.md"), """
                ---
                name: test-quality-gate
                description: Multi-phase test quality gate
                ---

                # Quality Gate
                Reject empty catch blocks.
                """);

        AiAgentDefinitionLoader loader = AiAgentDefinitionLoader.forHome(home);

        assertFalse(loader.isEmpty());
        assertEquals(1, loader.agents().size());
        assertEquals(1, loader.skills().size());

        AiAgentDefinitionLoader.Definition planner = loader.findAgent("qa-planner");
        assertNotNull(planner);
        assertEquals("QA Architect & Spec Miner", planner.description());
        assertTrue(planner.body().contains("assign TEST-IDs"));
        assertFalse(planner.body().contains("---"), "front matter must not leak into the prompt body");

        assertNotNull(loader.findSkill("test-quality-gate"));
    }

    @Test
    public void missingDirectoriesDegradeToNoGuidance(@TempDir Path home) {
        AiAgentDefinitionLoader loader = AiAgentDefinitionLoader.forHome(home);

        assertTrue(loader.isEmpty());
        assertTrue(loader.selectFor("thiết kế test cho màn hình đăng nhập", "playwright_e2e").isEmpty());
        assertEquals("", AiAgentDefinitionLoader.toPromptSection(loader.agents()));
    }

    @Test
    public void selectsNamedAgentAndChosenSkill(@TempDir Path home) throws IOException {
        write(home.resolve(".agents/quality-auditor.md"),
                "---\nname: quality-auditor\ndescription: coverage auditor\n---\nAudit assertions.\n");
        write(home.resolve(".agents/qa-planner.md"),
                "---\nname: qa-planner\ndescription: spec miner\n---\nMine specs.\n");
        write(home.resolve("skills/playwright-test-healer/SKILL.md"),
                "---\nname: playwright-test-healer\ndescription: healer\n---\nHeal locators.\n");

        AiAgentDefinitionLoader loader = AiAgentDefinitionLoader.forHome(home);

        List<AiAgentDefinitionLoader.Definition> selected =
                loader.selectFor("nhờ quality-auditor rà soát bộ test này", "playwright-test-healer");

        List<String> names = selected.stream().map(AiAgentDefinitionLoader.Definition::name).toList();
        assertTrue(names.contains("quality-auditor"));
        assertTrue(names.contains("playwright-test-healer"));
        assertFalse(names.contains("qa-planner"), "agents not named in the prompt must not be injected");
    }

    @Test
    public void promptSectionCarriesNameDescriptionAndBody(@TempDir Path home) throws IOException {
        write(home.resolve(".agents/qa-planner.md"),
                "---\nname: qa-planner\ndescription: spec miner\n---\nMine specs.\n");

        String section = AiAgentDefinitionLoader.toPromptSection(
                AiAgentDefinitionLoader.forHome(home).agents());

        assertTrue(section.contains("[qa-planner]"));
        assertTrue(section.contains("spec miner"));
        assertTrue(section.contains("Mine specs."));
        assertTrue(section.contains("KHÔNG mở rộng quyền hạn"),
                "the prompt must state that definitions cannot widen agent permissions");
    }

    @Test
    public void longDefinitionIsCapped(@TempDir Path home) throws IOException {
        String hugeBody = "x".repeat(20_000);
        write(home.resolve(".agents/big-agent.md"),
                "---\nname: big-agent\ndescription: big\n---\n" + hugeBody);

        String block = AiAgentDefinitionLoader.forHome(home).findAgent("big-agent").toPromptBlock();

        assertTrue(block.length() < 8_000, "one definition must not eat the context budget");
        assertTrue(block.contains("đã rút gọn định nghĩa big-agent"));
    }
}
