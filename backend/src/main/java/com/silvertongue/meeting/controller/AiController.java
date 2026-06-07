package com.silvertongue.meeting.controller;

import com.silvertongue.common.ApiResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@RestController
public class AiController {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${grpc.agent.host:localhost}")
    private String agentHost;

    private String getAgentHttpUrl() {
        return "http://" + agentHost + ":8089";
    }

    /** TTS 语音合成代理 */
    @PostMapping("/api/ai/tts/speak")
    public ResponseEntity<byte[]> ttsSpeak(@RequestBody Map<String, String> body) {
        String url = getAgentHttpUrl() + "/api/ai/tts/speak";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);
            
            ResponseEntity<byte[]> response = restTemplate.postForEntity(url, entity, byte[].class);
            
            HttpHeaders responseHeaders = new HttpHeaders();
            responseHeaders.setContentType(MediaType.valueOf("audio/mpeg"));
            return new ResponseEntity<>(response.getBody(), responseHeaders, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(new byte[0], HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /** STT 语音转写代理 */
    @PostMapping("/api/ai/stt/transcribe")
    public ApiResult<Map<String, Object>> sttTranscribe(@RequestBody Map<String, String> body) {
        String url = getAgentHttpUrl() + "/api/ai/stt/transcribe";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);
            
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            return ApiResult.success(response.getBody());
        } catch (Exception e) {
            return ApiResult.error(500, "STT failed: " + e.getMessage());
        }
    }

    /** 获取预设 AI 角色列表 */
    @GetMapping("/api/room/ai-roles")
    public ApiResult<List<Map<String, Object>>> getAiRoles() {
        String url = getAgentHttpUrl() + "/api/ai/roles";
        try {
            ResponseEntity<List> response = restTemplate.getForEntity(url, List.class);
            return ApiResult.success(response.getBody());
        } catch (Exception e) {
            return ApiResult.error(500, "Failed to fetch AI roles: " + e.getMessage());
        }
    }
}
