package com.aditya.app.dispatch;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** @Transactional so each test rolls back the shared seeded H2 database. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AgentControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    @Test
    void updatesAgentStatusWithoutMovingOrders() throws Exception {
        mockMvc.perform(patch("/api/v1/agents/AGT-001/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "OFFLINE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("AGT-001"))
                .andExpect(jsonPath("$.status").value("OFFLINE"))
                .andExpect(jsonPath("$.activeOrderCount").value(2));

        mockMvc.perform(get("/api/v1/orders").param("status", "REASSIGNMENT_PENDING"))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void returnsNotFoundForUnknownAgent() throws Exception {
        mockMvc.perform(patch("/api/v1/agents/AGT-999/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "OFFLINE"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void rejectsMissingStatusWithFieldViolation() throws Exception {
        mockMvc.perform(patch("/api/v1/agents/AGT-001/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("status"));
    }
}
