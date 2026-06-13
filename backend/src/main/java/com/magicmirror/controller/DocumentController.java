package com.magicmirror.controller;

import com.magicmirror.rag.DocumentService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> upload(@RequestParam("file") MultipartFile file) throws Exception {
        String result = documentService.upload(file);
        return Map.of("status", "ok", "message", result);
    }

    @PostMapping("/search")
    public List<Map<String, Object>> search(@RequestBody Map<String, Object> body) {
        String query = (String) body.getOrDefault("query", "");
        int topK = body.containsKey("topK") ? ((Number) body.get("topK")).intValue() : 5;
        return documentService.search(query, topK);
    }

    @GetMapping("/count")
    public Map<String, Long> count() {
        return Map.of("count", documentService.countDocs());
    }

    @DeleteMapping
    public Map<String, String> clear() {
        documentService.clear();
        return Map.of("status", "ok");
    }
}
