package com.magicmirror.tool.builtin;

import com.magicmirror.tool.api.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Component
public class TerminalTool implements Tool {

    private static final int DEFAULT_TIMEOUT = 180;
    private static final int MAX_TIMEOUT = 600;
    private static final int MAX_OUTPUT_CHARS = 50_000;
    private static final int BUFFER_SIZE = 8192;

    @Override
    public String getName() { return "terminal"; }

    @Override
    public String getDescription() {
        return "在本地终端执行 shell 命令。支持前后台运行、超时配置、指定工作目录。" +
                "默认超时 " + DEFAULT_TIMEOUT + " 秒，最大 " + MAX_TIMEOUT + " 秒。";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();

        Map<String, Object> cmd = new LinkedHashMap<>();
        cmd.put("type", "string");
        cmd.put("description", "要执行的 shell 命令");
        props.put("command", cmd);

        Map<String, Object> timeout = new LinkedHashMap<>();
        timeout.put("type", "integer");
        timeout.put("description", "超时秒数，默认 " + DEFAULT_TIMEOUT + "，最大 " + MAX_TIMEOUT);
        props.put("timeout", timeout);

        Map<String, Object> workdir = new LinkedHashMap<>();
        workdir.put("type", "string");
        workdir.put("description", "工作目录（绝对路径），默认为当前项目目录");
        props.put("workdir", workdir);

        Map<String, Object> background = new LinkedHashMap<>();
        background.put("type", "boolean");
        background.put("description", "是否后台运行（后台任务不支持超时）");
        props.put("background", background);

        schema.put("properties", props);
        schema.put("required", List.of("command"));
        return schema;
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        String command = (String) arguments.get("command");
        if (command == null || command.isBlank()) {
            return "错误：请提供要执行的命令";
        }

        int timeout = DEFAULT_TIMEOUT;
        if (arguments.containsKey("timeout") && arguments.get("timeout") != null) {
            timeout = Math.min(((Number) arguments.get("timeout")).intValue(), MAX_TIMEOUT);
        }

        String workdir = (String) arguments.getOrDefault("workdir", System.getProperty("user.dir"));
        boolean background = Boolean.TRUE.equals(arguments.get("background"));

        if (background) {
            return runBackground(command, workdir);
        }
        return runForeground(command, workdir, timeout);
    }

    private String runForeground(String command, String workdir, int timeoutSec) {
        log.info("Terminal [fg] timeout={}s dir={}: {}", timeoutSec, workdir, command);

        try {
            ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
            pb.directory(new File(workdir));
            pb.redirectErrorStream(true);

            Process process = pb.start();
            StringBuilder output = new StringBuilder();

            try (InputStream is = process.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8), BUFFER_SIZE)) {

                ExecutorService readerThread = Executors.newSingleThreadExecutor();
                Future<Void> readerFuture = readerThread.submit(() -> {
                    char[] buf = new char[BUFFER_SIZE];
                    int n;
                    while ((n = reader.read(buf)) != -1) {
                        if (output.length() < MAX_OUTPUT_CHARS) {
                            output.append(buf, 0, n);
                        }
                    }
                    return null;
                });

                boolean finished = process.waitFor(timeoutSec, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    readerFuture.cancel(true);
                    readerThread.shutdownNow();
                    output.append("\n[命令超时 ").append(timeoutSec).append("s，已强制终止]");
                } else {
                    readerFuture.get(5, TimeUnit.SECONDS); // 等待读完剩余输出
                    readerThread.shutdownNow();
                }
            }

            int exitCode = process.exitValue();
            StringBuilder result = new StringBuilder();
            result.append("exit_code: ").append(exitCode).append("\n");

            // 截断输出
            String out = output.toString();
            if (out.length() > MAX_OUTPUT_CHARS) {
                int head = MAX_OUTPUT_CHARS / 3;
                int tail = MAX_OUTPUT_CHARS / 3;
                out = out.substring(0, head) +
                        "\n\n... [截断 " + (out.length() - head - tail) + " 字符] ...\n\n" +
                        out.substring(out.length() - tail);
            }
            result.append(out.isBlank() ? "(无输出)" : out);

            return result.toString();
        } catch (Exception e) {
            log.error("Terminal error: {}", command, e);
            return "命令执行异常：" + e.getMessage();
        }
    }

    private String runBackground(String command, String workdir) {
        log.info("Terminal [bg] dir={}: {}", workdir, command);
        try {
            ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
            pb.directory(new File(workdir));
            pb.redirectErrorStream(true);
            pb.inheritIO(); // 后台不捕获输出
            pb.start();
            return "后台任务已启动，PID 无法跟踪（简化模式）";
        } catch (Exception e) {
            return "后台任务启动失败：" + e.getMessage();
        }
    }
}
