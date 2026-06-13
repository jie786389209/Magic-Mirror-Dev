package com.magicmirror.tool.builtin;

import com.magicmirror.tool.api.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 文件工具集：读取、写入、搜索
 * 参考 hermes-agent files/tools.py 设计
 */
@Slf4j
@Component
public class FileTools {

    private static final int MAX_READ_CHARS = 100_000;
    private static final int MAX_LINES = 2_000;

    // 危险路径
    private static final Set<String> BLOCKED_PREFIXES = Set.of(
            "/etc/", "/boot/", "/dev/", "/proc/", "/sys/", "/root/"
    );

    // 二进制扩展名
    private static final Set<String> BINARY_EXTS = Set.of(
            "exe", "dll", "so", "dylib", "bin", "class", "jar", "war",
            "zip", "tar", "gz", "bz2", "xz", "7z", "rar",
            "png", "jpg", "jpeg", "gif", "bmp", "ico", "webp",
            "mp3", "mp4", "avi", "mov", "wmv", "flv",
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "o", "obj", "pyc", "pyo"
    );

    // ── Read File ──
    @Component("read_file")
    public static class ReadFileTool implements Tool {
        @Override public String getName() { return "read_file"; }
        @Override public String getDescription() { return "读取文件内容，支持分页（offset + limit）"; }

        @Override
        public Map<String, Object> getParametersSchema() {
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            Map<String, Object> props = new LinkedHashMap<>();

            Map<String, Object> path = new LinkedHashMap<>();
            path.put("type", "string");
            path.put("description", "文件路径");
            props.put("path", path);

            Map<String, Object> offset = new LinkedHashMap<>();
            offset.put("type", "integer");
            offset.put("description", "起始行号（从 1 开始）");
            props.put("offset", offset);

            Map<String, Object> limit = new LinkedHashMap<>();
            limit.put("type", "integer");
            limit.put("description", "读取行数，默认 " + MAX_LINES);
            props.put("limit", limit);

            schema.put("properties", props);
            schema.put("required", List.of("path"));
            return schema;
        }

        @Override
        public String execute(Map<String, Object> arguments) {
            String path = (String) arguments.get("path");
            if (path == null || path.isBlank()) return "错误：请提供文件路径";

            Path filePath = Paths.get(path).toAbsolutePath().normalize();
            String err = checkReadSafety(filePath);
            if (err != null) return err;

            if (hasBinaryExtension(filePath)) return "错误：二进制文件，无法读取";

            int startLine = arguments.containsKey("offset") ? ((Number) arguments.get("offset")).intValue() : 1;
            int limit = arguments.containsKey("limit") ? ((Number) arguments.get("limit")).intValue() : MAX_LINES;

            try {
                List<String> allLines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
                int totalLines = allLines.size();
                int start = Math.max(0, startLine - 1);
                int end = Math.min(start + limit, totalLines);

                if (start >= totalLines) {
                    return String.format("文件共 %d 行，offset=%d 超出范围", totalLines, startLine);
                }

                StringBuilder sb = new StringBuilder();
                sb.append(String.format("%s  (%d-%d / %d 行)\n", filePath.getFileName(), start + 1, end, totalLines));
                for (int i = start; i < end; i++) {
                    String line = allLines.get(i);
                    if (line.length() > 2_000) line = line.substring(0, 2_000) + "...";
                    sb.append(String.format("%6d|%s\n", i + 1, line));
                }

                String result = sb.toString();
                if (result.length() > MAX_READ_CHARS) {
                    result = result.substring(0, MAX_READ_CHARS) + "\n... [输出截断]";
                }
                return result;
            } catch (IOException e) {
                return "读取文件失败：" + e.getMessage();
            }
        }
    }

    // ── Write File ──
    @Component("write_file")
    public static class WriteFileTool implements Tool {
        @Override public String getName() { return "write_file"; }
        @Override public String getDescription() { return "创建或覆盖写入文件，自动创建父目录"; }

        @Override
        public Map<String, Object> getParametersSchema() {
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            Map<String, Object> props = new LinkedHashMap<>();

            Map<String, Object> path = new LinkedHashMap<>();
            path.put("type", "string");
            path.put("description", "文件路径");
            props.put("path", path);

            Map<String, Object> content = new LinkedHashMap<>();
            content.put("type", "string");
            content.put("description", "要写入的内容");
            props.put("content", content);

            schema.put("properties", props);
            schema.put("required", List.of("path", "content"));
            return schema;
        }

        @Override
        public String execute(Map<String, Object> arguments) {
            String path = (String) arguments.get("path");
            String content = (String) arguments.get("content");
            if (path == null || path.isBlank()) return "错误：请提供文件路径";
            if (content == null) return "错误：请提供写入内容";

            Path filePath = Paths.get(path).toAbsolutePath().normalize();
            String err = checkWriteSafety(filePath);
            if (err != null) return err;

            try {
                Files.createDirectories(filePath.getParent());
                Files.writeString(filePath, content, StandardCharsets.UTF_8);
                long size = Files.size(filePath);
                return String.format("已写入 %s (%d 字节)", filePath, size);
            } catch (IOException e) {
                return "写入文件失败：" + e.getMessage();
            }
        }
    }

    // ── Search Files ──
    @Component("search_files")
    public static class SearchFilesTool implements Tool {
        @Override public String getName() { return "search_files"; }
        @Override public String getDescription() { return "在文件中搜索匹配模式的内容，支持正则表达式"; }

        @Override
        public Map<String, Object> getParametersSchema() {
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            Map<String, Object> props = new LinkedHashMap<>();

            Map<String, Object> pattern = new LinkedHashMap<>();
            pattern.put("type", "string");
            pattern.put("description", "搜索模式（支持正则表达式）");
            props.put("pattern", pattern);

            Map<String, Object> path = new LinkedHashMap<>();
            path.put("type", "string");
            path.put("description", "搜索目录，默认为当前项目目录");
            props.put("path", path);

            Map<String, Object> filePattern = new LinkedHashMap<>();
            filePattern.put("type", "string");
            filePattern.put("description", "文件名匹配模式，如 *.java、*.{js,ts}");
            props.put("file_pattern", filePattern);

            Map<String, Object> maxResults = new LinkedHashMap<>();
            maxResults.put("type", "integer");
            maxResults.put("description", "最大结果数，默认 50");
            props.put("max_results", maxResults);

            schema.put("properties", props);
            schema.put("required", List.of("pattern"));
            return schema;
        }

        @Override
        public String execute(Map<String, Object> arguments) {
            String pattern = (String) arguments.get("pattern");
            if (pattern == null || pattern.isBlank()) return "错误：请提供搜索模式";

            String searchDir = (String) arguments.getOrDefault("path", System.getProperty("user.dir"));
            String filePattern = (String) arguments.get("file_pattern");
            int maxResults = arguments.containsKey("max_results") ? ((Number) arguments.get("max_results")).intValue() : 50;

            Path basePath = Paths.get(searchDir).toAbsolutePath().normalize();
            if (!Files.exists(basePath)) return "错误：搜索目录不存在：" + basePath;

            try {
                Pattern regex = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
                PathMatcher fileMatcher = filePattern != null
                        ? FileSystems.getDefault().getPathMatcher("glob:" + filePattern)
                        : null;

                StringBuilder results = new StringBuilder();
                int count = 0;

                try (Stream<Path> walk = Files.walk(basePath, 10)) {
                    List<Path> files = walk
                            .filter(Files::isRegularFile)
                            .filter(f -> !hasBinaryExtension(f))
                            .filter(f -> fileMatcher == null || fileMatcher.matches(f.getFileName()))
                            .filter(f -> checkReadSafety(f) == null)
                            .collect(Collectors.toList());

                    for (Path file : files) {
                        if (count >= maxResults) break;
                        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                        for (int i = 0; i < lines.size(); i++) {
                            if (count >= maxResults) break;
                            if (regex.matcher(lines.get(i)).find()) {
                                String line = lines.get(i).length() > 500
                                        ? lines.get(i).substring(0, 500) + "..."
                                        : lines.get(i);
                                results.append(String.format("%s:%d: %s\n",
                                        basePath.relativize(file), i + 1, line));
                                count++;
                            }
                        }
                    }
                }

                if (count == 0) return "未找到匹配 \"" + pattern + "\" 的结果";
                return String.format("搜索 \"%s\" 找到 %d 个结果:\n%s", pattern, count, results);
            } catch (Exception e) {
                return "搜索失败：" + e.getMessage();
            }
        }
    }

    // ── 安全检查 ──
    private static String checkReadSafety(Path path) {
        if (!Files.exists(path)) return "错误：文件不存在：" + path;
        String abs = path.toString();
        for (String prefix : BLOCKED_PREFIXES) {
            if (abs.startsWith(prefix)) return "错误：禁止读取系统路径：" + prefix + "*";
        }
        return null;
    }

    private static String checkWriteSafety(Path path) {
        String abs = path.toString();
        for (String prefix : BLOCKED_PREFIXES) {
            if (abs.startsWith(prefix)) return "错误：禁止写入系统路径：" + prefix + "*";
        }
        return null;
    }

    private static boolean hasBinaryExtension(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0) return false;
        return BINARY_EXTS.contains(name.substring(dot + 1).toLowerCase());
    }
}
