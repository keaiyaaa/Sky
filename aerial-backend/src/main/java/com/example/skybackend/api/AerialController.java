package com.example.skybackend.api;

import com.example.skybackend.ai.AerialAnalysisService;
import com.example.skybackend.api.ApiResponses.Health;
import com.example.skybackend.api.ApiResponses.IntentAnalysis;
import com.example.skybackend.api.ApiResponses.VisionAnalysis;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class AerialController {

    private final AerialAnalysisService analysisService;

    public AerialController(AerialAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @GetMapping("/health")
    public Health health() {
        return new Health("UP");
    }

    @PostMapping("/intent/analyze")
    public IntentAnalysis analyzeIntent(@RequestBody TextCommand request) {
        return analysisService.analyzeIntent(request.text());
    }

    @PostMapping(value = "/voice/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public IntentAnalysis analyzeVoice(@RequestPart("audio") MultipartFile audio) {
        return analysisService.analyzeVoice(audio);
    }

    @PostMapping(value = "/inspection/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public VisionAnalysis analyzeInspection(@RequestPart("image") MultipartFile image) {
        return analysisService.analyzeInspection(image);
    }

    public record TextCommand(String text) {
    }
}
