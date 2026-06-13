package com.magicmirror.controller;

import com.magicmirror.memory.MemoryEntry;
import com.magicmirror.memory.MemoryService;
import com.magicmirror.model.ChatMessage;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/memory")
public class MemoryController {

    private final MemoryService memoryService;

    public MemoryController(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @GetMapping("/short/{sessionId}")
    public List<ChatMessage> getShortTerm(@PathVariable String sessionId) {
        return memoryService.getShortTerm(sessionId, 20);
    }

    @GetMapping("/short")
    public Map<String, List<ChatMessage>> getAllShortTerm() {
        return memoryService.getAllShortTerm();
    }

    @DeleteMapping("/short")
    public Map<String, String> clearAllShortTerm() {
        memoryService.clearAllShortTerm();
        return Map.of("status", "ok");
    }

    @DeleteMapping("/short/{sessionId}")
    public Map<String, String> clearShortTerm(@PathVariable String sessionId) {
        memoryService.clearShortTerm(sessionId);
        return Map.of("status", "ok");
    }

    @PostMapping("/long/search")
    public List<MemoryEntry> searchLongTerm(@RequestBody Map<String, String> body) {
        return memoryService.searchLongTerm(body.getOrDefault("query", ""), 10);
    }

    @DeleteMapping("/long")
    public Map<String, String> clearLongTerm() {
        memoryService.clearLongTerm();
        return Map.of("status", "ok");
    }

    @GetMapping("/long/count")
    public Map<String, Long> countLongTerm() {
        return Map.of("count", memoryService.countLongTerm());
    }
}
