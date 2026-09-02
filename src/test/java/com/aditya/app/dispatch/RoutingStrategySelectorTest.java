package com.aditya.app.dispatch;

import com.aditya.app.common.BusinessRuleException;
import com.aditya.app.dispatch.routing.RoutingContext;
import com.aditya.app.dispatch.routing.RoutingRecommendation;
import com.aditya.app.dispatch.routing.RoutingStrategy;
import com.aditya.app.dispatch.routing.RoutingStrategyRegistry;
import com.aditya.app.dispatch.routing.RoutingStrategySelector;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoutingStrategySelectorTest {

    /** Stands in for a strategy registered later, e.g. T-3's AI strategy. */
    private record StubStrategy(String key) implements RoutingStrategy {
        @Override
        public List<RoutingRecommendation> recommend(RoutingContext context) {
            return List.of();
        }
    }

    private static final RoutingStrategy RULE_BASED = new StubStrategy("rule-based");
    private static final RoutingStrategy SECOND = new StubStrategy("zone-affinity");

    private static RoutingStrategySelector selector(String defaultKey) {
        return new RoutingStrategySelector(
                new RoutingStrategyRegistry(List.of(RULE_BASED, SECOND)), defaultKey);
    }

    @Test
    void startsOnTheConfiguredDefaultStrategy() {
        RoutingStrategySelector selector = selector("rule-based");

        assertThat(selector.activeKey()).isEqualTo("rule-based");
        assertThat(selector.active()).isSameAs(RULE_BASED);
    }

    @Test
    void reportsEveryRegisteredStrategy() {
        assertThat(selector("rule-based").availableKeys())
                .containsExactlyInAnyOrder("rule-based", "zone-affinity");
    }

    @Test
    void switchingIsVisibleToAnAlreadyHeldSelectorWithoutRestart() {
        RoutingStrategySelector selector = selector("rule-based");
        // A caller that resolved the selector before the switch — as SuggestionService does,
        // holding it as a constructor-injected field for the life of the application.
        assertThat(selector.active()).isSameAs(RULE_BASED);

        selector.activate("zone-affinity");

        assertThat(selector.active())
                .as("the same selector instance must serve the new strategy immediately")
                .isSameAs(SECOND);
        assertThat(selector.activeKey()).isEqualTo("zone-affinity");
    }

    @Test
    void rejectsUnknownStrategyAndKeepsThePreviousOneActive() {
        RoutingStrategySelector selector = selector("rule-based");

        assertThatThrownBy(() -> selector.activate("ai"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ai")
                .hasMessageContaining("rule-based");

        assertThat(selector.activeKey())
                .as("a rejected switch must not leave invalid configuration behind")
                .isEqualTo("rule-based");
        assertThat(selector.active()).isSameAs(RULE_BASED);
    }

    @Test
    void refusesToStartOnAnUnknownConfiguredDefault() {
        assertThatThrownBy(() -> selector("does-not-exist"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void rejectsDuplicateStrategyKeys() {
        assertThatThrownBy(() -> new RoutingStrategyRegistry(
                List.of(RULE_BASED, new StubStrategy("rule-based"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate routing strategy key");
    }
}
