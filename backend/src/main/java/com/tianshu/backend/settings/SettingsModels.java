package com.tianshu.backend.settings;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;
import java.time.Instant;

public final class SettingsModels {
    public static final String CLIENT_ID = "user-settings";
    private SettingsModels() {}
    public record Values(
            @JsonProperty(required=true) @JsonDeserialize(using=StrictString.class) String autoSyncRecords,
            @JsonProperty(required=true) @JsonDeserialize(using=StrictString.class) String lowBatteryReturn,
            @JsonProperty(required=true) @JsonDeserialize(using=StrictString.class) String returnAltitudeM,
            @JsonProperty(required=true) @JsonDeserialize(using=StrictString.class) String failsafeAction,
            @JsonProperty(required=true) @JsonDeserialize(using=StrictString.class) String obstacleAction,
            @JsonProperty(required=true) @JsonDeserialize(using=StrictInteger.class) Integer controlSensitivity,
            @JsonProperty(required=true) @JsonDeserialize(using=StrictString.class) String videoQuality,
            @JsonProperty(required=true) @JsonDeserialize(using=StrictString.class) String imageQuality,
            @JsonProperty(required=true) @JsonDeserialize(using=StrictString.class) String beginnerMode) {
        public static Values defaults() { return new Values("1","1","100.00","0","0",5,"1080p","high","0"); }
    }
    public record UpdateRequest(@JsonProperty(required=true) Values settings,
            @JsonProperty(required=true) @JsonDeserialize(using=StrictLong.class) Long expectedVersion,
            @JsonProperty(required=true) @JsonDeserialize(using=StrictString.class) String idempotencyKey) {}
    public record SettingsResponse(String id,String clientId,String userId,Values settings,long version,
                                   Instant updatedAt,boolean cloudSaved,Boolean deviceApplied) {}

    /** Codes and decimal strings must not be silently coerced from JSON booleans/numbers. */
    public static class StrictString extends StdDeserializer<String> {
        public StrictString() { super(String.class); }
        @Override public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (!parser.hasToken(JsonToken.VALUE_STRING)) return context.reportInputMismatch(String.class,"Expected a JSON string");
            return parser.getText();
        }
    }
    public static class StrictInteger extends StdDeserializer<Integer> {
        public StrictInteger() { super(Integer.class); }
        @Override public Integer deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) return context.reportInputMismatch(Integer.class,"Expected a JSON integer");
            return parser.getIntValue();
        }
    }
    public static class StrictLong extends StdDeserializer<Long> {
        public StrictLong() { super(Long.class); }
        @Override public Long deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) return context.reportInputMismatch(Long.class,"Expected a JSON integer");
            return parser.getLongValue();
        }
    }
}
