/**
 * CLI-backed {@link com.podorc.runtime.llm.LlmProvider} adapters and the thin subprocess seam they
 * sit on ({@link com.podorc.runtime.llm.cli.CliRunner}). All Claude-CLI specifics — argv,
 * {@code --output-format json} envelope parsing, error classification — are contained in
 * {@link com.podorc.runtime.llm.cli.ClaudeCliLlmProvider} and
 * {@link com.podorc.runtime.llm.cli.ClaudeCliResponseParser}. The Codex adapter (S1-10) will add a
 * sibling here against the same {@code CliRunner}.
 */
package com.podorc.runtime.llm.cli;
