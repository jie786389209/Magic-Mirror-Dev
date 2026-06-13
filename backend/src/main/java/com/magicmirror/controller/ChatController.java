package com.magicmirror.controller;

import com.magicmirror.model.ChatRequest;
import com.magicmirror.service.ChatService;
import com.magicmirror.tool.api.ToolRegistry;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChatService chatService;
    private final ToolRegistry toolRegistry;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public ChatController(ChatService chatService, ToolRegistry toolRegistry) {
        this.chatService = chatService;
        this.toolRegistry = toolRegistry;
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@Valid @RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L);

        executor.execute(() -> {
            try {
                chatService.chatStream(
                        request.getMessage(),
                        request.getHistory(),
                        chunk -> {
                            try {
                                emitter.send(SseEmitter.event().name("chunk").data(chunk));
                            } catch (IOException e) {
                                log.warn("SSE send failed: {}", e.getMessage());
                            }
                        }
                );

                emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                emitter.complete();

            } catch (Exception e) {
                log.error("Chat error", e);
                try {
                    emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
                } catch (IOException ex) {
                    log.warn("Error event send failed", ex);
                }
                emitter.completeWithError(e);
            }
        });

        emitter.onTimeout(emitter::complete);
        emitter.onError(t -> log.warn("SSE error: {}", t.getMessage()));

        return emitter;
    }

    @GetMapping("/tools")
    public List<Map<String, Object>> listTools() {
        return toolRegistry.toDeepSeekTools();
    }
}
