package com.studio.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TestStepAndDataTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testTestStepCreationAndSerialization() throws Exception {
        TestStep step = new TestStep(1, "FILL", "[data-testid=\"email\"]", "test@example.com", "Nhập email", "Email được điền");
        assertEquals(1, step.getStepNumber());
        assertEquals("FILL", step.getAction());
        assertEquals("[data-testid=\"email\"]", step.getTarget());
        assertEquals("test@example.com", step.getTestData());
        assertEquals("Nhập email", step.getDescription());
        assertEquals("Email được điền", step.getExpectedResult());
        assertTrue(step.toString().contains("Step 1: [FILL]"));

        String json = mapper.writeValueAsString(step);
        TestStep deserialized = mapper.readValue(json, TestStep.class);
        assertEquals(step, deserialized);
    }

    @Test
    void testTestDataItemCreationAndSerialization() throws Exception {
        TestDataItem item = new TestDataItem("Login Happy Path", Map.of("username", "admin", "password", "Secret123"), "SUCCESS");
        assertEquals("Login Happy Path", item.getScenario());
        assertEquals("admin", item.getParameters().get("username"));
        assertEquals("Secret123", item.getParameters().get("password"));
        assertEquals("SUCCESS", item.getExpectedOutcome());

        String json = mapper.writeValueAsString(item);
        TestDataItem deserialized = mapper.readValue(json, TestDataItem.class);
        assertEquals(item, deserialized);
    }

    @Test
    void testAiGeneratedCaseWithStepsAndData() throws Exception {
        AiGeneratedCase genCase = new AiGeneratedCase("TC_Checkout", "Kiểm thử thanh toán", "https://shop.example.com");
        genCase.setPreconditions(List.of("Giỏ hàng có ít nhất 1 sản phẩm"));
        genCase.setTestSteps(List.of(
                new TestStep(1, "NAVIGATE", "https://shop.example.com/checkout", "", "Vào trang thanh toán", "Trang thanh toán hiển thị"),
                new TestStep(2, "CLICK", "#pay-btn", "", "Bấm thanh toán", "Chuyển sang trang cảm ơn")
        ));
        genCase.setTestDataSets(List.of(
                new TestDataItem("Visa Valid", Map.of("card", "4111222233334444"), "SUCCESS"),
                new TestDataItem("Expired Card", Map.of("card", "4111000000000000"), "CARD_EXPIRED_ERROR")
        ));

        String json = mapper.writeValueAsString(genCase);
        AiGeneratedCase deserialized = mapper.readValue(json, AiGeneratedCase.class);

        assertEquals("TC_Checkout", deserialized.getName());
        assertEquals(1, deserialized.getPreconditions().size());
        assertEquals(2, deserialized.getTestSteps().size());
        assertEquals("CLICK", deserialized.getTestSteps().get(1).getAction());
        assertEquals(2, deserialized.getTestDataSets().size());
        assertEquals("CARD_EXPIRED_ERROR", deserialized.getTestDataSets().get(1).getExpectedOutcome());
    }

    @Test
    void testTestCaseStepsAndDataPropagation() {
        TestCase tc = new TestCase("case-1", "Case 1", "Desc", "Case1.java", "Draft");
        assertTrue(tc.getTestSteps().isEmpty());
        assertTrue(tc.getTestDataSets().isEmpty());

        tc.setTestSteps(List.of(new TestStep(1, "NAVIGATE", "https://example.com", "", "Open", "Opened")));
        tc.setTestDataSets(List.of(new TestDataItem("Default", Map.of("key", "val"), "OK")));

        assertEquals(1, tc.getTestSteps().size());
        assertEquals(1, tc.getTestDataSets().size());
        assertEquals("NAVIGATE", tc.getTestSteps().get(0).getAction());
        assertEquals("val", tc.getTestDataSets().get(0).getParameters().get("key"));
    }
}

