package com.aditya.app.dispatch;

import com.aditya.app.dispatch.routing.RoutingStrategySelector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The active strategy is process-wide in-memory state, not database state, so @Transactional
 * would not undo a switch. Each test restores it explicitly instead.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RoutingControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoutingStrategySelector selector;

    @AfterEach
    void restoreDefaultStrategy() {
        selector.activate("rule-based");
    }

    @Test
    void reportsTheActiveAndRegisteredStrategies() throws Exception {
        // Membership, not position: the registry sorts keys, so "ai" now precedes
        // "rule-based" and an index-based assertion would break on every new strategy.
        mockMvc.perform(get("/api/v1/routing/strategy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value("rule-based"))
                .andExpect(jsonPath("$.available").isArray())
                .andExpect(jsonPath("$.available",
                        org.hamcrest.Matchers.hasItems("ai", "rule-based")));
    }

    @Test
    void switchesToARegisteredStrategyWithoutRestart() throws Exception {
        mockMvc.perform(patch("/api/v1/routing/strategy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"strategy\":\"rule-based\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value("rule-based"));

        assertThat(selector.activeKey()).isEqualTo("rule-based");
    }

    @Test
    void rejectsAnUnknownStrategyAndLeavesTheActiveOneUnchanged() throws Exception {
        // "ai" is a registered strategy since T-3; this probe needs a key nothing answers to.
        mockMvc.perform(patch("/api/v1/routing/strategy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"strategy\":\"zone-affinity\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString(
                                "Unknown routing strategy 'zone-affinity'")));

        mockMvc.perform(get("/api/v1/routing/strategy"))
                .andExpect(jsonPath("$.active").value("rule-based"));
    }

    @Test
    void rejectsABlankStrategyWithFieldViolation() throws Exception {
        mockMvc.perform(patch("/api/v1/routing/strategy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"strategy\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("strategy"));
    }
}
