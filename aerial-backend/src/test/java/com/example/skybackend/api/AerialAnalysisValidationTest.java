package com.example.skybackend.api;

import com.example.skybackend.ai.AerialAnalysisService;
import com.example.skybackend.ai.BailianAsrGateway;
import com.example.skybackend.ai.DeepSeekGateway;
import com.example.skybackend.ai.KeywordIntentMatcher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertThrows;

class AerialAnalysisValidationTest {

    private final AerialAnalysisService service = new AerialAnalysisService(
            new BailianAsrGateway("", "", "qwen3-asr-flash"),
            new DeepSeekGateway("https://api.deepseek.com", "", "deepseek-flash", "deepseek-flash"),
            new KeywordIntentMatcher(),
            new ObjectMapper()
    );

    @Test
    void rejectsNonWavAudioBeforeCallingCloudAsr() {
        MockMultipartFile audio = new MockMultipartFile("audio", "audio.wav", "audio/wav", new byte[]{1, 2, 3});
        assertThrows(InvalidRequestException.class, () -> service.analyzeVoice(audio));
    }

    @Test
    void rejectsNonJpegInspectionImageBeforeCallingCloudVision() {
        MockMultipartFile image = new MockMultipartFile("image", "image.jpg", "image/jpeg", new byte[]{1, 2, 3});
        assertThrows(InvalidRequestException.class, () -> service.analyzeInspection(image));
    }
}
