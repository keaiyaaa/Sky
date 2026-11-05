package com.example.skybackend.ai;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Component
public class BailianAsrGateway {

    private final RestClient restClient;
    private final String baseUrl;
    private final String apiKey;
    private final String model;

    public BailianAsrGateway(
            @Value("${aerial.bailian.base-url:}") String baseUrl,
            @Value("${aerial.bailian.api-key:}") String apiKey,
            @Value("${aerial.bailian.model:qwen3-asr-flash}") String model
    ) {
        this.restClient = UpstreamRestClient.create();
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
    }

    public String transcribe(byte[] wavBytes) {
        if (baseUrl.isBlank() || apiKey.isBlank()) {
            throw new UpstreamServiceException();
        }

        String dataUrl = "data:audio/wav;base64," + Base64.getEncoder().encodeToString(wavBytes);
        Map<String, Object> message = Map.of(
                "role", "user",
                "content", List.of(Map.of(
                        "type", "input_audio",
                        "input_audio", Map.of("data", dataUrl)
                ))
        );
        Map<String, Object> request = Map.of(
                "model", model,
                "messages", List.of(message),
                "stream", false,
                "asr_options", Map.of("language", "zh", "enable_itn", true)
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
        String transcript = response == null ? "" : response.path("choices").path(0)
                .path("message").path("content").asText("").trim();
        if (transcript.isBlank()) {
            throw new UpstreamServiceException();
        }
        return transcript;
    }

    private URI endpoint() {
        return URI.create(baseUrl.replaceAll("/+$", "") + "/chat/completions");
    }
}
