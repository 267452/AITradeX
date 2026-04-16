package com.service;

import com.config.AppProperties;
import com.domain.request.KnowledgeDocumentCreateRequest;
import com.repository.AdminModuleRepository;
import io.milvus.client.MilvusServiceClient;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.grpc.DataType;
import io.milvus.grpc.MutationResult;
import io.milvus.param.ConnectParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.collection.FlushParam;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.index.CreateIndexParam;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeDocumentService {
    private static final Logger logger = LoggerFactory.getLogger(KnowledgeDocumentService.class);

    private static final String FIELD_ID = "id";
    private static final String FIELD_KNOWLEDGE_BASE_ID = "knowledge_base_id";
    private static final String FIELD_DOCUMENT_ID = "document_id";
    private static final String FIELD_CHUNK_INDEX = "chunk_index";
    private static final String FIELD_FILE_NAME = "file_name";
    private static final String FIELD_CONTENT = "content";
    private static final String FIELD_VECTOR = "vector";

    private static final int MAX_TEXT_FIELD_LENGTH = 65535;
    private static final int DEFAULT_EMBEDDING_DIM = 384;

    private final AdminModuleRepository adminModuleRepository;
    private final AppProperties properties;

    public KnowledgeDocumentService(AdminModuleRepository adminModuleRepository, AppProperties properties) {
        this.adminModuleRepository = adminModuleRepository;
        this.properties = properties;
    }

    public Map<String, Object> createDocument(KnowledgeDocumentCreateRequest request) {
        Map<String, Object> created = adminModuleRepository.createKnowledgeDocument(request);
        long documentId = asLong(created.get("id"));
        if (documentId <= 0 || !Boolean.TRUE.equals(request.triggerParse())) {
            return created;
        }

        String baseNote = asString(created.get("sync_note"));
        adminModuleRepository.updateKnowledgeDocumentSync(
                documentId,
                "pending",
                null,
                null,
                appendNote(baseNote, "开始解析文档并写入 Milvus"));

        try {
            ParsedDocument parsedDocument = parseDocument(request);
            if (parsedDocument.chunks.isEmpty()) {
                throw new IllegalStateException("未提取到可用文本内容");
            }
            syncToMilvus(created, parsedDocument);
            adminModuleRepository.updateKnowledgeDocumentSync(
                    documentId,
                    "indexed",
                    parsedDocument.chunks.size(),
                    parsedDocument.pageCount,
                    appendNote(baseNote, "已解析完成并写入向量库"));
        } catch (Exception e) {
            logger.error("文档解析/向量化失败, documentId={}, file={}", documentId, created.get("file_name"), e);
            String message = defaultString(e.getMessage(), "未知错误");
            adminModuleRepository.updateKnowledgeDocumentSync(
                    documentId,
                    "failed",
                    null,
                    null,
                    appendNote(baseNote, "解析失败: " + truncate(message, 220)));
        }

        Map<String, Object> latest = adminModuleRepository.getKnowledgeDocument(documentId);
        return latest == null ? created : latest;
    }

    private ParsedDocument parseDocument(KnowledgeDocumentCreateRequest request) throws IOException {
        String sourcePath = defaultString(request.sourcePath());
        if (sourcePath.isBlank()) {
            throw new IllegalArgumentException("trigger_parse=true 时必须提供 source_path");
        }

        Path path = Paths.get(sourcePath).toAbsolutePath().normalize();
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new IllegalArgumentException("source_path 文件不存在: " + path);
        }

        String fileName = defaultString(request.fileName(), path.getFileName().toString());
        String lowerName = fileName.toLowerCase(Locale.ROOT);

        if (lowerName.endsWith(".pdf")) {
            return parsePdf(path);
        }

        String text = Files.readString(path, StandardCharsets.UTF_8);
        List<String> chunks = splitChunks(normalizeText(text));
        return new ParsedDocument(chunks, Math.max(0, defaultInt(request.pageCount())));
    }

    private ParsedDocument parsePdf(Path path) throws IOException {
        try (PDDocument document = Loader.loadPDF(Files.readAllBytes(path))) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            List<String> chunks = splitChunks(normalizeText(text));
            return new ParsedDocument(chunks, document.getNumberOfPages());
        }
    }

    private void syncToMilvus(Map<String, Object> document, ParsedDocument parsedDocument) throws Exception {
        String host = defaultString(properties.getKnowledgeMilvusHost(), "localhost");
        int port = properties.getKnowledgeMilvusPort() <= 0 ? 19530 : properties.getKnowledgeMilvusPort();
        String collection = defaultString(properties.getKnowledgeMilvusCollection(), "knowledge_document_chunks");
        int embeddingDim = properties.getKnowledgeEmbeddingDim() > 0
                ? properties.getKnowledgeEmbeddingDim()
                : DEFAULT_EMBEDDING_DIM;

        MilvusServiceClient client = null;
        try {
            ConnectParam connectParam = ConnectParam.newBuilder()
                    .withHost(host)
                    .withPort(port)
                    .withConnectTimeout(5, TimeUnit.SECONDS)
                    .withRpcDeadline(30, TimeUnit.SECONDS)
                    .build();
            client = new MilvusServiceClient(connectParam);

            ensureCollection(client, collection, embeddingDim);
            insertDocumentChunks(client, collection, embeddingDim, document, parsedDocument.chunks);
        } finally {
            if (client != null) {
                client.close();
            }
        }
    }

    private void ensureCollection(MilvusServiceClient client, String collection, int embeddingDim) {
        R<Boolean> hasCollection = client.hasCollection(
                HasCollectionParam.newBuilder()
                        .withCollectionName(collection)
                        .build());
        ensureSuccess(hasCollection, "检查集合");

        if (!Boolean.TRUE.equals(hasCollection.getData())) {
            int textMaxLength = Math.min(
                    MAX_TEXT_FIELD_LENGTH,
                    Math.max(2048, safeChunkSize() * 4));

            FieldType idField = FieldType.newBuilder()
                    .withName(FIELD_ID)
                    .withDataType(DataType.Int64)
                    .withPrimaryKey(true)
                    .withAutoID(true)
                    .build();
            FieldType knowledgeBaseIdField = FieldType.newBuilder()
                    .withName(FIELD_KNOWLEDGE_BASE_ID)
                    .withDataType(DataType.Int64)
                    .build();
            FieldType documentIdField = FieldType.newBuilder()
                    .withName(FIELD_DOCUMENT_ID)
                    .withDataType(DataType.Int64)
                    .build();
            FieldType chunkIndexField = FieldType.newBuilder()
                    .withName(FIELD_CHUNK_INDEX)
                    .withDataType(DataType.Int64)
                    .build();
            FieldType fileNameField = FieldType.newBuilder()
                    .withName(FIELD_FILE_NAME)
                    .withDataType(DataType.VarChar)
                    .withMaxLength(512)
                    .build();
            FieldType contentField = FieldType.newBuilder()
                    .withName(FIELD_CONTENT)
                    .withDataType(DataType.VarChar)
                    .withMaxLength(textMaxLength)
                    .build();
            FieldType vectorField = FieldType.newBuilder()
                    .withName(FIELD_VECTOR)
                    .withDataType(DataType.FloatVector)
                    .withDimension(embeddingDim)
                    .build();

            R<RpcStatus> createCollection = client.createCollection(
                    CreateCollectionParam.newBuilder()
                            .withCollectionName(collection)
                            .withDescription("AITradeX knowledge chunks")
                            .withConsistencyLevel(ConsistencyLevelEnum.BOUNDED)
                            .addFieldType(idField)
                            .addFieldType(knowledgeBaseIdField)
                            .addFieldType(documentIdField)
                            .addFieldType(chunkIndexField)
                            .addFieldType(fileNameField)
                            .addFieldType(contentField)
                            .addFieldType(vectorField)
                            .build());
            ensureSuccess(createCollection, "创建集合");

            R<RpcStatus> createIndex = client.createIndex(
                    CreateIndexParam.newBuilder()
                            .withCollectionName(collection)
                            .withFieldName(FIELD_VECTOR)
                            .withIndexType(IndexType.IVF_FLAT)
                            .withMetricType(MetricType.COSINE)
                            .withExtraParam("{\"nlist\":1024}")
                            .build());
            ensureSuccess(createIndex, "创建索引");
        }

        R<RpcStatus> loadCollection = client.loadCollection(
                LoadCollectionParam.newBuilder()
                        .withCollectionName(collection)
                        .build());
        ensureSuccess(loadCollection, "加载集合");
    }

    private void insertDocumentChunks(
            MilvusServiceClient client,
            String collection,
            int embeddingDim,
            Map<String, Object> document,
            List<String> chunks) {
        long knowledgeBaseId = asLong(document.get("knowledge_base_id"));
        long documentId = asLong(document.get("id"));
        String fileName = defaultString(asString(document.get("file_name")), "unknown");

        List<Long> knowledgeBaseIds = new ArrayList<>(chunks.size());
        List<Long> documentIds = new ArrayList<>(chunks.size());
        List<Long> chunkIndices = new ArrayList<>(chunks.size());
        List<String> fileNames = new ArrayList<>(chunks.size());
        List<String> contents = new ArrayList<>(chunks.size());
        List<List<Float>> vectors = new ArrayList<>(chunks.size());

        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            knowledgeBaseIds.add(knowledgeBaseId);
            documentIds.add(documentId);
            chunkIndices.add((long) i);
            fileNames.add(fileName);
            contents.add(truncate(chunk, MAX_TEXT_FIELD_LENGTH));
            vectors.add(embedChunk(chunk, embeddingDim, i));
        }

        List<InsertParam.Field> fields = new ArrayList<>();
        fields.add(new InsertParam.Field(FIELD_KNOWLEDGE_BASE_ID, knowledgeBaseIds));
        fields.add(new InsertParam.Field(FIELD_DOCUMENT_ID, documentIds));
        fields.add(new InsertParam.Field(FIELD_CHUNK_INDEX, chunkIndices));
        fields.add(new InsertParam.Field(FIELD_FILE_NAME, fileNames));
        fields.add(new InsertParam.Field(FIELD_CONTENT, contents));
        fields.add(new InsertParam.Field(FIELD_VECTOR, vectors));

        R<MutationResult> insert = client.insert(
                InsertParam.newBuilder()
                        .withCollectionName(collection)
                        .withFields(fields)
                        .build());
        ensureSuccess(insert, "写入向量");

        R<?> flush = client.flush(
                FlushParam.newBuilder()
                        .addCollectionName(collection)
                        .build());
        ensureSuccess(flush, "刷新向量数据");
    }

    private List<Float> embedChunk(String text, int dim, int chunkIndex) {
        float[] vector = new float[dim];
        String[] tokens = text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}_]+");
        int effectiveTokens = 0;

        for (String token : tokens) {
            if (token == null || token.isBlank()) {
                continue;
            }
            int hash = token.hashCode();
            int indexA = Math.floorMod(hash, dim);
            int indexB = Math.floorMod(Integer.rotateLeft(hash ^ (chunkIndex * 131), 11), dim);
            vector[indexA] += 1.0f;
            vector[indexB] += 0.5f;
            effectiveTokens++;
        }

        if (effectiveTokens == 0) {
            vector[Math.floorMod(text.hashCode(), dim)] = 1.0f;
        }

        float norm = 0.0f;
        for (float value : vector) {
            norm += value * value;
        }
        if (norm > 0.0f) {
            float invNorm = (float) (1.0d / Math.sqrt(norm));
            for (int i = 0; i < vector.length; i++) {
                vector[i] = vector[i] * invNorm;
            }
        }

        List<Float> values = new ArrayList<>(dim);
        for (float value : vector) {
            values.add(value);
        }
        return values;
    }

    private List<String> splitChunks(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        int chunkSize = safeChunkSize();
        int overlap = safeChunkOverlap(chunkSize);
        int length = text.length();
        List<String> chunks = new ArrayList<>();
        int start = 0;

        while (start < length) {
            int end = Math.min(start + chunkSize, length);
            if (end < length) {
                int paragraphBreak = text.lastIndexOf('\n', end);
                if (paragraphBreak > start + (chunkSize / 3)) {
                    end = paragraphBreak;
                }
            }
            String chunk = text.substring(start, end).trim();
            if (!chunk.isBlank()) {
                chunks.add(chunk);
            }
            if (end >= length) {
                break;
            }
            int nextStart = end - overlap;
            if (nextStart <= start) {
                nextStart = end;
            }
            start = nextStart;
        }

        return chunks;
    }

    private String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\t\\x0B\\f]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .replaceAll(" {2,}", " ")
                .trim();
        return truncate(normalized, MAX_TEXT_FIELD_LENGTH * 20);
    }

    private void ensureSuccess(R<?> response, String action) {
        if (response == null) {
            throw new IllegalStateException(action + "失败: Milvus 无返回");
        }
        Integer status = response.getStatus();
        if (status == null || status.intValue() != R.Status.Success.getCode()) {
            throw new IllegalStateException(
                    action + "失败: " + defaultString(response.getMessage(), "未知 Milvus 错误"));
        }
    }

    private String appendNote(String base, String extra) {
        String left = defaultString(base);
        String right = defaultString(extra);
        if (left.isBlank()) {
            return right;
        }
        if (right.isBlank()) {
            return left;
        }
        return left + " | " + right;
    }

    private int safeChunkSize() {
        int configured = properties.getKnowledgeChunkSize();
        if (configured < 120) {
            return 120;
        }
        if (configured > 6000) {
            return 6000;
        }
        return configured;
    }

    private int safeChunkOverlap(int chunkSize) {
        int configured = properties.getKnowledgeChunkOverlap();
        int safe = Math.max(0, configured);
        if (safe >= chunkSize) {
            return Math.max(0, chunkSize / 5);
        }
        return safe;
    }

    private long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String str) {
            try {
                return Long.parseLong(str);
            } catch (NumberFormatException ignore) {
                return 0L;
            }
        }
        return 0L;
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String defaultString(String value) {
        return defaultString(value, "");
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private static class ParsedDocument {
        private final List<String> chunks;
        private final int pageCount;

        private ParsedDocument(List<String> chunks, int pageCount) {
            this.chunks = chunks;
            this.pageCount = pageCount;
        }
    }
}
