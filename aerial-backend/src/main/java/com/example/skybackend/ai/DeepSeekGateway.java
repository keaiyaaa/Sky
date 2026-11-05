package com.example.skybackend.ai;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.util.List;
import java.util.Map;

@Component
public class DeepSeekGateway {

    private final RestClient restClient;
    private final String baseUrl;
    private final String apiKey;
    private final String textModel;
    private final String visionModel;

    public DeepSeekGateway(
            @Value("${aerial.deepseek.base-url:https://api.deepseek.com}") String baseUrl,
            @Value("${aerial.deepseek.api-key:}") String apiKey,
            @Value("${aerial.deepseek.text-model:deepseek-flash}") String textModel,
            @Value("${aerial.deepseek.vision-model:deepseek-flash}") String visionModel
    ) {
        this.restClient = UpstreamRestClient.create();
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.textModel = textModel;
        this.visionModel = visionModel;
    }

    public String analyzeIntent(String transcript, String keywordHint) {
        String prompt = "识别文本：" + transcript + "\n关键词候选：" + (keywordHint == null ? "无" : keywordHint)
                + "\n候选仅供参考，必须依据原文。";
        return complete(textModel, List.of(
                Map.of("role", "system", "content", intentSystemPrompt()),
                Map.of("role", "user", "content", prompt)
        ));
    }

    public String analyzeInspection(byte[] imageBytes) {
        String imageUrl = "data:image/jpeg;base64," + java.util.Base64.getEncoder().encodeToString(imageBytes);
        List<Map<String, Object>> content = List.of(
                Map.of("type", "text", "text", "复核这张无人机巡检图像是否存在肉眼可见的巡检异常。只报告图中可直接观察到的疑似问题；看不清或证据不足时标为 uncertain。只返回 status、title、description 三个字段；不要猜测缺陷类别，不要生成置信度、严重程度、检测框或图中不存在的细节。"),
                Map.of("type", "image_url", "image_url", Map.of("url", imageUrl, "detail", "low"))
        );
        return complete(visionModel, List.of(Map.of("role", "user", "content", content)));
    }

    private String complete(String model, List<Map<String, Object>> messages) {
        if (apiKey.isBlank()) {
            throw new UpstreamServiceException();
        }
        Map<String, Object> request = Map.of(
                "model", model,
                "messages", messages,
                "temperature", 0.1,
                "max_tokens", 1024,
                "response_format", Map.of("type", "json_object")
        );
        JsonNode response;
        try {
            response = restClient.post()
                    .uri(endpoint())
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException exception) {
            throw new UpstreamServiceException();
        }
        String content = response == null ? "" : response.path("choices").path(0)
                .path("message").path("content").asText("").trim();
        if (content.isBlank()) {
            throw new UpstreamServiceException();
        }
        return content;
    }

    private URI endpoint() {
        return URI.create(baseUrl.replaceAll("/+$", "") + "/chat/completions");
    }

    private static String intentSystemPrompt() {
        return """
                你只负责把一条明确的无人机指令转换成 JSON 参数预览，不判断设备是否可飞，也不执行动作。
                只输出 JSON：{"status":"success|unknown","flightType":"类型或unknown","parameters":{},"summary":"中文摘要","reason":"原因或空字符串"}。
                不得猜测缺失参数、补默认速度/高度或编造坐标。含否定、条件、多个连续动作、含糊目标或缺少必需参数时输出 unknown。
                支持类型及规则（单位：距离/高度米、速度米每秒、角度度）：
                takeoff、stop_takeoff、land、stop_landing、confirm_landing、return_home、stop_return_home、hover、photo 参数可为空；video 必须提供 action=start|stop。
                fly_to 必须提供 direction=forward|backward|left|right、正数 distance、正数 speed、referenceFrame=body。
                change_altitude 必须提供正数 altitude、正数 speed、altitudeReference=takeoff。
                rotate 必须提供 yaw（-180 到 180）和 referenceFrame=body。
                set_return_home_height 必须提供正数 altitude 和 altitudeReference=takeoff。
                gimbal_control 的 pitch 范围为 -90 到 30 度。
                orbit 必须提供正数 radius、speed、正整数 laps 和 direction=clockwise|counterclockwise。
                waypoint 必须提供 pattern=line|rectangle|zigzag、正数 altitude/speed/length 和 altitudeReference=takeoff；rectangle 还必须提供正数 width。
                参数预览上限：高度 120 米、距离/航线尺寸 500 米、环绕半径 200 米、速度 15 米/秒、环绕 10 圈、悬停 300 秒。超出上限输出 unknown。
                不支持的动作类型输出 unknown。参数预览不代表机型支持或真实飞行许可。
                """;
    }
}
