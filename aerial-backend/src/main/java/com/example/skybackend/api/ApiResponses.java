package com.example.skybackend.api;

import com.fasterxml.jackson.databind.JsonNode;

public final class ApiResponses {

    private ApiResponses() {
    }

    public record IntentAnalysis(
            String status,
            String transcript,
            String keywordHint,
            JsonNode flightIntent,
            String summary,
            String reason
    ) {
        public static IntentAnalysis unknown(String transcript, String hint, String reason) {
            return new IntentAnalysis("unknown", transcript, hint, null, null, reason);
        }
    }

    public record VisionAnalysis(
            String status,
            String title,
            String description
    ) {
    }

    public record Health(String status) {
    }

    public record Error(String message) {
    }
}
