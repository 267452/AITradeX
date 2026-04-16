package com.ai.model.dto;

import java.util.Map;

public class ChatResponse {
    private boolean success;
    private String message;
    private String content;
    private Map<String, Object> data;
    private String error;

    public ChatResponse() {
    }

    public ChatResponse(boolean success, String message, String content, Map<String, Object> data, String error) {
        this.success = success;
        this.message = message;
        this.content = content;
        this.data = data;
        this.error = error;
    }

    public static ChatResponse success(String content) {
        return ChatResponse.builder()
                .success(true)
                .content(content)
                .build();
    }

    public static ChatResponse success(String content, Map<String, Object> data) {
        return ChatResponse.builder()
                .success(true)
                .content(content)
                .data(data)
                .build();
    }

    public static ChatResponse error(String error) {
        return ChatResponse.builder()
                .success(false)
                .error(error)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public static final class Builder {
        private boolean success;
        private String message;
        private String content;
        private Map<String, Object> data;
        private String error;

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder data(Map<String, Object> data) {
            this.data = data;
            return this;
        }

        public Builder error(String error) {
            this.error = error;
            return this;
        }

        public ChatResponse build() {
            return new ChatResponse(success, message, content, data, error);
        }
    }
}
