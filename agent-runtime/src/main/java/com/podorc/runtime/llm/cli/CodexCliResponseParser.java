package com.podorc.runtime.llm.cli;

import java.util.Locale;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.podorc.runtime.llm.LlmProviderInvocationException;
import com.podorc.runtime.llm.LlmResult;
import com.podorc.runtime.llm.LlmUsage;
import com.podorc.runtime.llm.ProviderId;
import com.podorc.runtime.llm.ProviderMeta;

/**
 * Turns the JSONL event stream of {@code codex exec --json} (plus the {@code --output-last-message}
 * file) into an {@link LlmResult}, or into the right {@code LlmProviderException} subtype.
 *
 * <p>Event shape (Codex CLI): a {@code thread.started} with {@code thread_id}; {@code turn.started};
 * one or more {@code item.completed} whose {@code item.type} is {@code agent_message} (with
 * {@code text}) or {@code error} (with {@code message}); and a terminal {@code turn.completed} with
 * {@code usage.{input_tokens,cached_input_tokens,cache_write_input_tokens,output_tokens,
 * reasoning_output_tokens}}, or {@code turn.failed}/{@code error} on failure. Codex reports no cost
 * and no model, so those stay {@code null}.
 */
public final class CodexCliResponseParser {

    private static final ProviderId PROVIDER = ProviderId.CODEX;

    private final ObjectMapper mapper;

    public CodexCliResponseParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * @param result       finished (non-timed-out) invocation result
     * @param lastMessage  contents of the {@code --output-last-message} file, or {@code null}
     * @param effectiveModel the model the adapter believes was used (config/request), for meta only
     * @param durationMs   wall-clock time measured by the adapter
     */
    public LlmResult parse(CliInvocationResult result, String lastMessage, String effectiveModel,
                           long durationMs) {
        String threadId = null;
        String agentText = null;
        JsonNode usageNode = null;
        String failureText = null;

        for (String line : result.stdout().split("\\R")) {
            JsonNode event = tryReadJson(line);
            if (event == null || !event.isObject()) {
                continue;
            }
            String type = event.path("type").asText("");
            switch (type) {
                case "thread.started" -> threadId = textOrNull(event, "thread_id");
                case "turn.completed" -> {
                    if (event.has("usage")) {
                        usageNode = event.get("usage");
                    }
                }
                case "turn.failed" -> failureText = firstNonBlank(
                        event.path("error").path("message").asText(null), failureText);
                case "error" -> failureText = firstNonBlank(textOrNull(event, "message"), failureText);
                case "item.completed" -> {
                    JsonNode item = event.path("item");
                    String itemType = item.path("type").asText("");
                    if ("agent_message".equals(itemType)) {
                        agentText = firstNonBlank(textOrNull(item, "text"), agentText);
                    } else if ("error".equals(itemType)) {
                        failureText = firstNonBlank(textOrNull(item, "message"), failureText);
                    }
                }
                default -> { /* ignore other events */ }
            }
        }

        if (result.exitCode() != 0 || failureText != null) {
            String detail = firstNonBlank(failureText, result.stderr(), "codex CLI failed");
            throw LlmCliErrors.classify(PROVIDER, httpStatusIn(detail), detail,
                    result.exitCode(),
                    result.stderr().isBlank() ? result.stdout() : result.stderr(),
                    "codex CLI returned an error: " + snippet(detail));
        }

        String text = firstNonBlank(
                lastMessage == null || lastMessage.isBlank() ? null : lastMessage.strip(),
                agentText);
        if (text == null) {
            throw new LlmProviderInvocationException(PROVIDER, result.exitCode(), result.stderr(),
                    "codex CLI produced no assistant message");
        }

        LlmUsage usage = readUsage(usageNode, effectiveModel);
        ProviderMeta meta = new ProviderMeta(PROVIDER, effectiveModel, threadId, "turn.completed",
                durationMs);
        return new LlmResult(text, usage, result.stdout(), meta);
    }

    private LlmUsage readUsage(JsonNode usage, String effectiveModel) {
        if (usage == null || !usage.isObject()) {
            return new LlmUsage(null, null, null, null, null, null, effectiveModel, null);
        }
        return new LlmUsage(
                longOrNull(usage, "input_tokens"),
                longOrNull(usage, "output_tokens"),
                longOrNull(usage, "cached_input_tokens"),
                longOrNull(usage, "cache_write_input_tokens"),
                longOrNull(usage, "reasoning_output_tokens"),
                null,              // Codex reports no per-call cost
                effectiveModel,    // Codex reports no model; use what we asked for
                null);
    }

    /** Codex sometimes nests an OpenAI error JSON inside the message string: pull {@code status} out. */
    private Integer httpStatusIn(String text) {
        JsonNode nested = tryReadJson(text);
        if (nested != null && nested.path("status").isInt()) {
            return nested.get("status").asInt();
        }
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        if (lower.contains("status\":401") || lower.contains(" 401 ")) {
            return 401;
        }
        if (lower.contains("status\":429") || lower.contains(" 429 ")) {
            return 429;
        }
        return null;
    }

    private JsonNode tryReadJson(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return mapper.readTree(s);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static Long longOrNull(JsonNode parent, String field) {
        JsonNode n = parent.get(field);
        return (n != null && n.isNumber()) ? n.asLong() : null;
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode n = node.get(field);
        return (n != null && n.isTextual() && !n.asText().isBlank()) ? n.asText() : null;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private static String snippet(String s) {
        String t = s == null ? "" : s.strip();
        return t.length() <= 300 ? t : t.substring(0, 300) + "…";
    }
}
