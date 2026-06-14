package com.magicmirror.controller;

import com.magicmirror.graph.GraphService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/graph")
public class GraphController {

    private final GraphService graphService;

    public GraphController(GraphService graphService) {
        this.graphService = graphService;
    }

    @GetMapping("/search")
    public List<Map<String, Object>> search(@RequestParam(defaultValue = "") String query,
                                            @RequestParam(defaultValue = "") String relType) {
        return graphService.searchGraph(query, relType.isBlank() ? null : relType, 20);
    }

    @GetMapping("/stats")
    public Map<String, Long> stats() {
        return Map.of("entities", graphService.countEntities(), "relations", graphService.countRelations());
    }

    @DeleteMapping
    public Map<String, String> clear() {
        graphService.clear();
        return Map.of("status", "ok");
    }
}
