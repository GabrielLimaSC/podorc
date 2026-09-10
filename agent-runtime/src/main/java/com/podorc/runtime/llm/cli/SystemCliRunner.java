package com.podorc.runtime.llm.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link CliRunner} backed by {@link ProcessBuilder}. Reads stdout and stderr on separate threads so
 * a chatty process cannot deadlock on a full pipe buffer, and force-kills the process (plus its
 * descendants) when the timeout elapses.
 */
public final class SystemCliRunner implements CliRunner {

    private static final Logger log = LoggerFactory.getLogger(SystemCliRunner.class);

    /** Grace period to collect output after a forced kill or normal exit. */
    private static final long DRAIN_TIMEOUT_SECONDS = 5;

    @Override
    public CliInvocationResult run(CliInvocation invocation) {
        ProcessBuilder pb = new ProcessBuilder(invocation.command());
        if (invocation.workingDir() != null) {
            pb.directory(invocation.workingDir().toFile());
        }
        pb.redirectErrorStream(false);

        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            throw new CliExecutionException(
                    "failed to start '" + invocation.command().get(0) + "'", e);
        }

        ExecutorService io = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "cli-io");
            t.setDaemon(true);
            return t;
        });
        try {
            Future<String> stdout = io.submit(() -> readFully(process.getInputStream()));
            Future<String> stderr = io.submit(() -> readFully(process.getErrorStream()));

            writeStdin(process, invocation);

            boolean finished = process.waitFor(invocation.timeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                process.waitFor(DRAIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                return new CliInvocationResult(-1, drain(stdout), drain(stderr), true);
            }
            return new CliInvocationResult(process.exitValue(), drain(stdout), drain(stderr), false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new CliExecutionException("interrupted while waiting for '"
                    + invocation.command().get(0) + "'", e);
        } finally {
            io.shutdownNow();
        }
    }

    private static void writeStdin(Process process, CliInvocation invocation) {
        try (OutputStream os = process.getOutputStream()) {
            if (invocation.stdin() != null) {
                os.write(invocation.stdin().getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            // The process may have exited before reading stdin; the exit code and output still tell
            // the real story, so don't fail here.
            log.debug("writing stdin to '{}' failed: {}", invocation.command().get(0), e.toString());
        }
    }

    private static String readFully(InputStream in) {
        try (in) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.debug("reading process stream failed: {}", e.toString());
            return "";
        }
    }

    private static String drain(Future<String> stream) {
        try {
            return stream.get(DRAIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            return "";
        } catch (ExecutionException e) {
            log.debug("collecting process stream failed: {}", e.getCause().toString());
            return "";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        }
    }
}
