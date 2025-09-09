package com.ainovel.server.web.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ainovel.server.domain.model.AIRequest;
import com.ainovel.server.domain.model.AIResponse;
import com.ainovel.server.domain.model.AIFeatureType;
import com.ainovel.server.service.NovelAIService;
import com.ainovel.server.web.dto.RevisionRequest;
import com.ainovel.server.web.dto.SuggestionRequest;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 小说AI控制器
 */
@RestController
@RequestMapping("/api/novels")
public class NovelAIController {

    private final NovelAIService novelAIService;
    private final com.ainovel.server.service.UsageQuotaService usageQuotaService;

    @Autowired
    public NovelAIController(NovelAIService novelAIService,
                             com.ainovel.server.service.UsageQuotaService usageQuotaService) {
        this.novelAIService = novelAIService;
        this.usageQuotaService = usageQuotaService;
    }

    /**
     * 生成小说内容
     *
     * @param request AI请求
     * @return AI响应
     */
    @PostMapping("/ai/generate")
    public Mono<AIResponse> generateNovelContent(@RequestBody AIRequest request) {
        // 计入限次（按功能 NOVEL_GENERATION）
        return usageQuotaService.isWithinLimit(request.getUserId(), AIFeatureType.NOVEL_GENERATION)
            .flatMap(can -> {
                if (!can) {
                    return Mono.error(new RuntimeException("今日AI使用次数已达上限"));
                }
                return novelAIService.generateNovelContent(request)
                    .flatMap(res -> usageQuotaService.incrementUsage(request.getUserId(), AIFeatureType.NOVEL_GENERATION).thenReturn(res));
            });
    }

    /**
     * 生成小说内容（流式）
     *
     * @param request AI请求
     * @return 流式AI响应
     */
    @PostMapping(value = "/ai/generate/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> generateNovelContentStream(@RequestBody AIRequest request) {
        return novelAIService.generateNovelContentStream(request)
                .map(content -> ServerSentEvent.<String>builder()
                .data(content)
                .build());
    }

    /**
     * 获取创作建议
     *
     * @param novelId 小说ID
     * @param request 建议请求
     * @return 创作建议
     */
    @PostMapping("/{novelId}/ai/suggest")
    public Mono<AIResponse> getWritingSuggestion(
            @PathVariable String novelId,
            @RequestBody SuggestionRequest request) {
        return novelAIService.getWritingSuggestion(
                novelId,
                request.getSceneId(),
                request.getSuggestionType());
    }

    /**
     * 获取创作建议（流式）
     *
     * @param novelId 小说ID
     * @param request 建议请求
     * @return 流式创作建议
     */
    @PostMapping(value = "/{novelId}/ai/suggest/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> getWritingSuggestionStream(
            @PathVariable String novelId,
            @RequestBody SuggestionRequest request) {
        return novelAIService.getWritingSuggestionStream(
                novelId,
                request.getSceneId(),
                request.getSuggestionType())
                .map(content -> ServerSentEvent.<String>builder()
                .data(content)
                .build());
    }

    /**
     * 修改内容
     *
     * @param novelId 小说ID
     * @param request 修改请求
     * @return 修改后的内容
     */
    @PostMapping("/{novelId}/ai/revise")
    public Mono<AIResponse> reviseContent(
            @PathVariable String novelId,
            @RequestBody RevisionRequest request) {
        return novelAIService.reviseContent(
                novelId,
                request.getSceneId(),
                request.getContent(),
                request.getInstruction());
    }

    /**
     * 修改内容（流式）
     *
     * @param novelId 小说ID
     * @param request 修改请求
     * @return 流式修改后的内容
     */
    @PostMapping(value = "/{novelId}/ai/revise/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> reviseContentStream(
            @PathVariable String novelId,
            @RequestBody RevisionRequest request) {
        return novelAIService.reviseContentStream(
                novelId,
                request.getSceneId(),
                request.getContent(),
                request.getInstruction())
                .map(content -> ServerSentEvent.<String>builder()
                .data(content)
                .build());
    }

}
