package com.hmdp.service.impl;

import com.hmdp.service.VisionCaptionProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.time.Duration;
import java.util.*;

@Component
public class VisionCaptionProviderApi implements VisionCaptionProvider {

    @Resource
    private WebClient webClient;

    @Value("${ai.vision.base-url}")
    private String baseUrl;

    @Value("${ai.vision.chat-path:/chat/completions}")
    private String chatPath;

    @Value("${ai.vision.api-key}")
    private String apiKey;

    @Value("${ai.vision.model:qwen3-vl-30b-a3b-instruct}")
    private String model;

    @Value("${ai.vision.timeout-ms:20000}")
    private long timeoutMs;

    @Override
    public String generateDraft(List<String> imageUrls, String style) {
        // 参数校验（JDK8：不用 isBlank）
        if (imageUrls == null || imageUrls.isEmpty()) {
            throw new IllegalArgumentException("imageUrls is empty");
        }
        String prompt = buildStylePrompt(style);

        // --- 构建 messages（compatible-mode: chat/completions） ---
        // system 消息
        Map<String, Object> systemMsg = new HashMap<String, Object>();
        systemMsg.put("role", "system");
        systemMsg.put("content", "You are a helpful assistant that writes concise, vivid Chinese dining reviews.");

        // user.content = [ {image_url:{url}}, {image_url:{url}}, ..., {text: prompt} ]
        List<Map<String, Object>> content = new ArrayList<Map<String, Object>>();
        for (String url : imageUrls) {
            if (url == null) continue;
            Map<String, Object> img = new HashMap<String, Object>();
            img.put("type", "image_url");
            Map<String, Object> imageUrl = new HashMap<String, Object>();
            imageUrl.put("url", url);
            img.put("image_url", imageUrl);
            content.add(img);
        }
        Map<String, Object> txt = new HashMap<String, Object>();
        txt.put("type", "text");
        txt.put("text", prompt);
        content.add(txt);

        Map<String, Object> userMsg = new HashMap<String, Object>();
        userMsg.put("role", "user");
        userMsg.put("content", content);

        List<Object> messages = new ArrayList<Object>();
        messages.add(systemMsg);
        messages.add(userMsg);

        Map<String, Object> reqBody = new HashMap<String, Object>();
        reqBody.put("model", model);
        reqBody.put("messages", messages);

        // --- 发起请求 ---
        @SuppressWarnings("unchecked")
        Map<String, Object> resp = webClient.post()
                .uri(baseUrl + chatPath)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(h -> h.set("Authorization", "Bearer " + apiKey))
                .bodyValue(reqBody)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofMillis(timeoutMs))
                .block();

        String draft = extractContent(resp);
        if (draft == null || draft.trim().isEmpty()) {
            throw new RuntimeException("Empty response from Qwen (choices[0].message.content is null)");
        }
        return draft.trim();
    }

    private String buildStylePrompt(String style) {
        String s = (style == null || style.trim().isEmpty()) ? "concise" : style.toLowerCase();
        if ("humor".equals(s)) {
            return "根据这些餐厅/菜品图片，用中文写一段80~120字的幽默风格评价，涵盖环境、口味、服务与性价比，避免夸大与虚构。";
        } else if ("formal".equals(s)) {
            return "根据这些餐厅/菜品图片，用中文写一段80~120字的正式风格评价，涵盖环境、口味、服务与性价比，语言克制、表述客观。";
        } else {
            return "根据这些餐厅/菜品图片，用中文写一段80~120字的简洁评价，涵盖环境、口味、服务与性价比，不要虚构菜单。";
        }
    }

    @SuppressWarnings("unchecked")
    private String extractContent(Map<String, Object> resp) {
        if (resp == null) return null;
        Object choicesObj = resp.get("choices");
        if (!(choicesObj instanceof List)) return null;

        List<Object> choices = (List<Object>) choicesObj;
        if (choices.isEmpty()) return null;

        Object first = choices.get(0);
        if (!(first instanceof Map)) return null;

        Map<String, Object> firstChoice = (Map<String, Object>) first;
        Object msgObj = firstChoice.get("message");
        if (!(msgObj instanceof Map)) return null;

        Map<String, Object> message = (Map<String, Object>) msgObj;
        Object content = message.get("content");
        return content == null ? null : content.toString();
    }
}
