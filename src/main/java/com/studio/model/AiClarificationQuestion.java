package com.studio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Model representing an interactive clarification question sent by the AI Agent
 * when requirements or test criteria contain ambiguities.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiClarificationQuestion {

    private String id;
    private String question;
    private List<String> options = new ArrayList<>();
    private boolean multiSelect;
    private boolean allowCustomInput = true;
    private String selectedAnswer;

    public AiClarificationQuestion() {
    }

    public AiClarificationQuestion(String id, String question, List<String> options) {
        this(id, question, options, false, true);
    }

    public AiClarificationQuestion(String id, String question, List<String> options, boolean multiSelect, boolean allowCustomInput) {
        this.id = id;
        this.question = question;
        this.options = options != null ? new ArrayList<>(options) : new ArrayList<>();
        this.multiSelect = multiSelect;
        this.allowCustomInput = allowCustomInput;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public List<String> getOptions() {
        return options;
    }

    public void setOptions(List<String> options) {
        this.options = options != null ? new ArrayList<>(options) : new ArrayList<>();
    }

    public boolean isMultiSelect() {
        return multiSelect;
    }

    public void setMultiSelect(boolean multiSelect) {
        this.multiSelect = multiSelect;
    }

    public boolean isAllowCustomInput() {
        return allowCustomInput;
    }

    public void setAllowCustomInput(boolean allowCustomInput) {
        this.allowCustomInput = allowCustomInput;
    }

    public String getSelectedAnswer() {
        return selectedAnswer;
    }

    public void setSelectedAnswer(String selectedAnswer) {
        this.selectedAnswer = selectedAnswer;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AiClarificationQuestion that = (AiClarificationQuestion) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

