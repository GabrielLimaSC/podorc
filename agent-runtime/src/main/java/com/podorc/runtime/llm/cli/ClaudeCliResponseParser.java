package com.podorc.runtime.llm.cli;

import java.math.BigDecimal;
import java.util.Locale;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.podorc.runtime.llm.LlmProviderInvocationException;
import com.podorc.runtime.llm.LlmProviderNotAuthenticatedException;
import com.podorc.runtime.llm.LlmRateLimitException;
import com.podorc.runtime.llm.LlmResult;
import com.podorc.runtime.llm.LlmUsage;
import com.podorc.runtime.llm.ProviderId;
import com.podorc.runtime.llm.ProviderMeta;

/**
 * Turns the raw stdout/stderr of {@code claude -p --output-format json} into an {@link LlmResult},
 * or into the appropriate {@code LlmProviderException} subtype.
 *
 * <p>The envelope shape is documented by the Claude Code CLI; the fields read here are
 * {@code type}, {@code subtype}, {@code is_error}, {@code result}, {@code usage.*},
 * {@code total_cost_usd}, {@code modelUsage}, {@code session_id}, {@code stop_reason} and
 * {@code api_error_status}. Anything the CLI omits becomes {@code null} — never a guessed value.
 */
public final class ClaudeCliResponseParser {

    private static final ProviderId PROVIDER = ProviderId.CLAUDE;

    private final ObjectMapper mapper;

    public ClaudeCliResponseParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * @param result    the finished (non-timed-out) invocation result
     * @param durationMs wall-clock time measured by the adapter
     */
    public LlmResult parse(CliInvocationResult result, long durationMs) {
        JsonNode root = tryReadJson(result.stdout());

        if (root == null || !root.isObject()) {
            // No parseable envelope. Exit code and stderr are all we have to go on.
            throw classifyFailure(result.exitCode(), null, result.stdout(), result.stderr(),
                    "claude CLI produced no JSON envelope");
        }

        boolean isError = root.path("is_error").asBoolean(false);
        String subtype = text(root, "subtype");
        if (isError || (subtype != null && !subtype.equals("success")) || result.exitCode() != 0) {
            String detail = firstNonBlank(text(root, "result"), result.stderr(),
                    "subtype=" + subtype);
            throw classifyFailure(result.exitCode(), root, text(root, "result"), result.stderr(),
                    "claude CLI returned an error (subtype=" + subtype + "): " + snippet(detail));
        }

        String completion = root.path("result").asText("");
        LlmUsage usage = readUsage(root);
        ProviderMeta meta = new ProviderMeta(
                PROVIDER,
                usage.model(),
                text(root, "session_id"),
                text(root, "stop_reason"),
                durationMs);
        return new LlmResult(completion, usage, result.stdout(), meta);
    }

    private RuntimeException classifyFailure(int exitCode, JsonNode envelope, String bodyText,
                                            String stderr, String message) {
        Integer apiStatus = null;
        if (envelope != null && envelope.has("api_error_status")
                && envelope.get("api_error_status").isInt()) {
            apiStatus = envelope.get("api_error_status").asInt();
        }
        String haystack = ((bodyText == null ? "" : bodyText) + "\n" + (stderr == null ? "" : stderr))
                .toLowerCase(Locale.ROOT);

        if (matchesAny(apiStatus, 401, 403) || containsAny(haystack,
                "invalid api key", "authentication_error", "please run /login", "not logged in",
                "run `claude login`", "oauth token has expired", "unauthorized")) {
            return new LlmProviderNotAuthenticatedException(PROVIDER, exitCode, stderr,
                    "claude CLI is not authenticated — log in with `claude login` (or set a valid "
                            + "credential) before the runtime can call it. " + message);
        }
        if (matchesAny(apiStatus, 429) || containsAny(haystack,
                "rate limit", "usage limit", "too many requests", "quota", "resets at",
                "try again later")) {
            return new LlmRateLimitException(PROVIDER, exitCode, stderr,
                    "claude CLI subscription window is exhausted — retry after it resets. " + message);
        }
        return new LlmProviderInvocationException(PROVIDER, exitCode, stderr, message);
    }

    private LlmUsage readUsage(JsonNode root) {
        JsonNode u = root.path("usage");
        Long input = longOrNull(u, "input_tokens");
        Long output = longOrNull(u, "output_tokens");
        Long cacheRead = longOrNull(u, "cache_read_input_tokens");
        Long cacheCreate = longOrNull(u, "cache_creation_input_tokens");
        Long reasoning = longOrNull(u.path("output_tokens_details"), "thinking_tokens");
        BigDecimal cost = decimalOrNull(root, "total_cost_usd");
        String model = firstFieldName(root.path("modelUsage"));
        return new LlmUsage(input, output, cacheRead, cacheCreate, reasoning, cost, model, null);
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

    private static BigDecimal decimalOrNull(JsonNode parent, String field) {
        JsonNode n = parent.get(field);
        return (n != null && n.isNumber()) ? n.decimalValue() : null;
    }

    private static String firstFieldName(JsonNode node) {
        if (node != null && node.isObject() && node.fieldNames().hasNext()) {
            return node.fieldNames().next();
        }
        return null;
    }

    private static String text(JsonNode node, String field) {
        JsonNode n = node.get(field);
        return (n != null && n.isTextual() && !n.asText().isBlank()) ? n.asText() : null;
    }

    private static boolean matchesAny(Integer value, int... candidates) {
        if (value == null) {
            return false;
        }
        for (int c : candidates) {
            if (value == c) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String n : needles) {
            if (haystack.contains(n)) {
                return true;
            }
        }
        return false;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return "";
    }

    private static String snippet(String s) {
        String t = s == null ? "" : s.strip();
        return t.length() <= 300 ? t : t.substring(0, 300) + "…";
    }
}
