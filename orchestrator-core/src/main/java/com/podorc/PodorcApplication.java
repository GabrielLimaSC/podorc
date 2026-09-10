package com.podorc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Composition root for podorc v1.
 *
 * <p>Boots {@code orchestrator-core} and {@code agent-runtime} in a single JVM. The component
 * scan covers the whole {@code com.podorc} tree so beans from both modules are picked up. The
 * two modules communicate over Kafka topics ({@code orchestrator.inbound} /
 * {@code orchestrator.outbound}), so the process can be split later without changing the
 * message contracts.
 */
@SpringBootApplication(scanBasePackages = "com.podorc")
public class PodorcApplication {

    public static void main(String[] args) {
        SpringApplication.run(PodorcApplication.class, args);
    }
}
