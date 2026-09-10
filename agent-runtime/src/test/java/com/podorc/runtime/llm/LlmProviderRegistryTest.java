package com.podorc.runtime.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class LlmProviderRegistryTest {

    private static LlmProvider stub(ProviderId id) {
        return new LlmProvider() {
            @Override
            public ProviderId id() {
                return id;
            }

            @Override
            public LlmResult call(LlmRequest request) {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Test
    void resolvesEachRegisteredProviderById() {
        LlmProvider claude = stub(ProviderId.CLAUDE);
        LlmProvider codex = stub(ProviderId.CODEX);
        LlmProviderRegistry registry = new LlmProviderRegistry(List.of(claude, codex));

        assertThat(registry.get(ProviderId.CLAUDE)).isSameAs(claude);
        assertThat(registry.get(ProviderId.CODEX)).isSameAs(codex);
        assertThat(registry.available()).containsExactlyInAnyOrder(ProviderId.CLAUDE, ProviderId.CODEX);
        assertThat(registry.has(ProviderId.CLAUDE)).isTrue();
    }

    @Test
    void failsClearlyForAnUnregisteredProvider() {
        LlmProviderRegistry registry = new LlmProviderRegistry(List.of(stub(ProviderId.CLAUDE)));

        assertThat(registry.has(ProviderId.CODEX)).isFalse();
        assertThatThrownBy(() -> registry.get(ProviderId.CODEX))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no LlmProvider registered for CODEX");
    }

    @Test
    void rejectsTwoProvidersClaimingTheSameId() {
        assertThatThrownBy(() -> new LlmProviderRegistry(
                List.of(stub(ProviderId.CLAUDE), stub(ProviderId.CLAUDE))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CLAUDE");
    }
}
