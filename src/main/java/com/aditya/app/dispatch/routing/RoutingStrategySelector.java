package com.aditya.app.dispatch.routing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds which strategy is currently active. The key lives in memory and is swapped
 * atomically, so a switch takes effect on the next call with no restart and no bean
 * reconstruction. It resets to the configured default when the application restarts —
 * see NOTES.md.
 */
@Service
public class RoutingStrategySelector {

    private static final Logger log = LoggerFactory.getLogger(RoutingStrategySelector.class);

    private final RoutingStrategyRegistry registry;
    private final AtomicReference<String> activeKey;

    public RoutingStrategySelector(RoutingStrategyRegistry registry,
                                   @Value("${routing.strategy}") String defaultKey) {
        this.registry = registry;
        registry.require(defaultKey);   // a bad routing.strategy fails startup, not the first request
        this.activeKey = new AtomicReference<>(defaultKey);
        log.info("Routing strategy '{}' active at startup. Registered: {}",
                defaultKey, registry.keys());
    }

    /** Resolved per call, so callers holding this selector always see the current strategy. */
    public RoutingStrategy active() {
        return registry.require(activeKey.get());
    }

    public String activeKey() {
        return activeKey.get();
    }

    public Set<String> availableKeys() {
        return registry.keys();
    }

    /** Validates before swapping, so a rejected key leaves the previous strategy in place. */
    public String activate(String key) {
        registry.require(key);
        String previous = activeKey.getAndSet(key);
        log.info("Routing strategy switched from '{}' to '{}'", previous, key);
        return key;
    }
}
