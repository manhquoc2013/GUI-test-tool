package com.studio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AssertResult {

    private int index;
    private String type;
    private String detail;
    private String assertStatus;
    private String status;
    private String message;

    public AssertResult() {
    }

    public AssertResult(int index, String type, String detail, String assertStatus, String status, String message) {
        this.index = index;
        this.type = type;
        this.detail = detail;
        this.assertStatus = assertStatus;
        this.status = status;
        this.message = message;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public String getAssertStatus() {
        return assertStatus;
    }

    public void setAssertStatus(String assertStatus) {
        this.assertStatus = assertStatus;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
