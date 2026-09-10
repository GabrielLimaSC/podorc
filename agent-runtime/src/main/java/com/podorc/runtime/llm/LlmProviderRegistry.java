package com.podorc.runtime.llm;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Indexes the available {@link LlmProvider} adapters by {@link ProviderId}. S1-08 looks a provider
 * up from the loaded agent's {@code llm.provider} instead of autowiring one concrete adapter.
 */
public final class LlmProviderRegistry {

    private final Map<ProviderId, LlmProvider> byId;

    public LlmProviderRegistry(Collection<LlmProvider> providers) {
        Map<ProviderId, LlmProvider> map = new EnumMap<>(ProviderId.class);
        for (LlmProvider provider : providers) {
            LlmProvider previous = map.put(provider.id(), provider);
            if (previous != null) {
                throw new IllegalStateException(
                        "two LlmProvider beans registered for " + provider.id());
            }
        }
        this.byId = Map.copyOf(map);
    }

    /**
     * @throws IllegalArgumentException if no adapter is registered for {@code id}
     */
    public LlmProvider get(ProviderId id) {
        LlmProvider provider = byId.get(id);
        if (provider == null) {
            throw new IllegalArgumentException(
                    "no LlmProvider registered for " + id + " (available: " + byId.keySet() + ")");
        }
        return provider;
    }

    public boolean has(ProviderId id) {
        return byId.containsKey(id);
    }

    public Set<ProviderId> available() {
        return byId.keySet();
    }
}
