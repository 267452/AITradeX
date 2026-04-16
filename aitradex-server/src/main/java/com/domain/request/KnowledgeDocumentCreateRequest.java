package com.domain.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KnowledgeDocumentCreateRequest(
        @JsonProperty("knowledge_base_id") Long knowledgeBaseId,
        @JsonProperty("file_name") String fileName,
        @JsonProperty("parse_status") String parseStatus,
        @JsonProperty("chunk_count") Integer chunkCount,
        @JsonProperty("page_count") Integer pageCount,
        @JsonProperty("sync_note") String syncNote,
        @JsonProperty("source_path") String sourcePath,
        @JsonProperty("trigger_parse") Boolean triggerParse) {
}
