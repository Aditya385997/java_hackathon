package com.aditya.app.dispatch.routing;

import com.aditya.app.common.BusinessRuleException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Every RoutingStrategy bean, keyed by {@link RoutingStrategy#key()}. Spring injects the
 * list, so registering a new strategy is a matter of adding a bean — no caller changes.
 */
@Component
public class RoutingStrategyRegistry {

    private final Map<String, RoutingStrategy> byKey;

    public RoutingStrategyRegistry(List<RoutingStrategy> strategies) {
        Map<String, RoutingStrategy> registered = new TreeMap<>();
        for (RoutingStrategy strategy : strategies) {
            RoutingStrategy clash = registered.put(strategy.key(), strategy);
            if (clash != null) {
                throw new IllegalStateException("Duplicate routing strategy key '"
                        + strategy.key() + "': " + clash.getClass().getName()
                        + " and " + strategy.getClass().getName());
            }
        }
        this.byKey = new LinkedHashMap<>(registered);
    }

    /** Resolves a strategy, or fails naming what is registered. */
    public RoutingStrategy require(String key) {
        RoutingStrategy strategy = byKey.get(key);
        if (strategy == null) {
            throw new BusinessRuleException(
                    "Unknown routing strategy '" + key + "'. Registered: " + keys());
        }
        return strategy;
    }

    public Set<String> keys() {
        return byKey.keySet();
    }
}
