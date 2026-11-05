package com.example.skybackend.ai;

import com.example.skybackend.api.ApiResponses;
import com.example.skybackend.api.InvalidRequestException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;

@Service
public class AerialAnalysisService {

    private static final int MAX_AUDIO_BYTES = 512 * 1024;
    private static final int MAX_IMAGE_BYTES = 768 * 1024;
    private static final Set<String> FLIGHT_TYPES = Set.of(
            "takeoff", "stop_takeoff", "land", "stop_landing", "confirm_landing",
            "return_home", "stop_return_home", "hover", "photo", "video",
            "fly_to", "change_altitude", "rotate", "set_return_home_height",
            "gimbal_control", "orbit", "waypoint"
    );

    private final BailianAsrGateway asrGateway;
    private final DeepSeekGateway deepSeekGateway;
    private final KeywordIntentMatcher keywordMatcher;
    private final ObjectMapper objectMapper;

    public AerialAnalysisService(
            BailianAsrGateway asrGateway,
            DeepSeekGateway deepSeekGateway,
            KeywordIntentMatcher keywordMatcher,
            ObjectMapper objectMapper
    ) {
        this.asrGateway = asrGateway;
        this.deepSeekGateway = deepSeekGateway;
        this.keywordMatcher = keywordMatcher;
        this.objectMapper = objectMapper;
    }

    public ApiResponses.IntentAnalysis analyzeVoice(MultipartFile audio) {
        byte[] bytes = readUpload(audio, MAX_AUDIO_BYTES, "音频");
        if (!isWav(bytes)) {
            throw new InvalidRequestException("语音上传格式无效，请重新录制");
        }
        String transcript = asrGateway.transcribe(bytes);
        return analyzeIntent(transcript);
    }

    public ApiResponses.IntentAnalysis analyzeIntent(String transcript) {
        String text = transcript == null ? "" : transcript.trim();
        if (text.isBlank() || text.length() > 500) {
            throw new InvalidRequestException("指令不能为空且不能超过 500 个字符");
        }

        String hint = keywordMatcher.suggest(text);
        String modelContent = deepSeekGateway.analyzeIntent(text, hint);
        JsonNode parsed = parseModelJson(modelContent);
        String status = parsed.path("status").asText("unknown");
        JsonNode intent = parsed.path("flightIntent");
        if (intent.isMissingNode() || intent.isNull()) {
            ObjectNode flatIntent = objectMapper.createObjectNode();
            flatIntent.set("flightType", parsed.path("flightType"));
            flatIntent.set("parameters", parsed.path("parameters").isMissingNode()
                    ? objectMapper.createObjectNode() : parsed.path("parameters"));
            intent = flatIntent;
        }

        String flightType = intent.path("flightType").asText("");
        if (!"success".equals(status) || !FLIGHT_TYPES.contains(flightType)) {
            return ApiResponses.IntentAnalysis.unknown(
                    text,
                    hint,
                    safeString(parsed.path("reason"), "指令不明确或缺少必要参数")
            );
        }
        if (!intent.path("parameters").isObject()) {
            return ApiResponses.IntentAnalysis.unknown(text, hint, "飞控参数格式无效");
        }

        return new ApiResponses.IntentAnalysis(
                "success",
                text,
                hint,
                intent,
                safeString(parsed.path("summary"), "飞行参数预览"),
                null
        );
    }

    public ApiResponses.VisionAnalysis analyzeInspection(MultipartFile image) {
        byte[] bytes = readUpload(image, MAX_IMAGE_BYTES, "图像");
        if (!isJpeg(bytes)) {
            throw new InvalidRequestException("巡检图像必须为 JPEG 格式");
        }
        JsonNode parsed = parseModelJson(deepSeekGateway.analyzeInspection(bytes));
        String status = parsed.path("status").asText("uncertain");
        if (!Set.of("suspected_anomaly", "no_obvious_anomaly", "uncertain").contains(status)) {
            status = "uncertain";
        }
        return new ApiResponses.VisionAnalysis(
                status,
                safeString(parsed.path("title"), statusLabel(status)),
                safeString(parsed.path("description"), "图像证据不足，需人工复核")
        );
    }

    private byte[] readUpload(MultipartFile file, int maxBytes, String name) {
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException(name + "不能为空");
        }
        if (file.getSize() > maxBytes) {
            throw new InvalidRequestException(name + "超过大小限制");
        }
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new InvalidRequestException(name + "读取失败");
        }
    }

    private JsonNode parseModelJson(String content) {
        try {
            return objectMapper.readTree(content);
        } catch (JsonProcessingException exception) {
            throw new UpstreamServiceException();
        }
    }

    private static String safeString(JsonNode node, String fallback) {
        String value = node.asText("").trim();
        return value.isEmpty() ? fallback : value;
    }

    private static boolean isWav(byte[] bytes) {
        return bytes.length >= 44
                && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'A' && bytes[10] == 'V' && bytes[11] == 'E';
    }

    private static boolean isJpeg(byte[] bytes) {
        return bytes.length >= 4
                && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8
                && (bytes[bytes.length - 2] & 0xff) == 0xff && (bytes[bytes.length - 1] & 0xff) == 0xd9;
    }

    private static String statusLabel(String status) {
        return switch (status) {
            case "suspected_anomaly" -> "疑似异常";
            case "no_obvious_anomaly" -> "未发现明显异常";
            default -> "需要人工复核";
        };
    }
}
