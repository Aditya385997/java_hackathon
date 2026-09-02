package com.aditya.app.dispatch;

import com.aditya.app.dispatch.repo.AgentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** @Transactional so each test rolls back the shared seeded H2 database. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OrderControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AgentRepository agentRepository;

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    @Test
    void createsOrderAsAssignedAndIncrementsAgentLoad() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("id", "ORD-009",
                                "description", "Flowers — Frazer Town to Cooke Town",
                                "assignedAgentId", "AGT-002"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("ORD-009"))
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.assignedAgentId").value("AGT-002"));

        assertThat(agentRepository.findById("AGT-002")).get()
                .extracting(a -> a.getActiveOrderCount())
                .isEqualTo(1);
    }

    @Test
    void rejectsDuplicateOrderIdWithConflict() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("id", "ORD-001",
                                "description", "Duplicate",
                                "assignedAgentId", "AGT-002"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void returnsNotFoundWhenAssignedAgentIsUnknown() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("id", "ORD-010",
                                "description", "Orphan parcel",
                                "assignedAgentId", "AGT-999"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void rejectsBlankDescriptionWithFieldViolation() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("id", "ORD-011",
                                "description", " ",
                                "assignedAgentId", "AGT-002"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("description"));
    }

    @Test
    void listsEverySeededOrderWhenNoFilterIsGiven() throws Exception {
        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(8));
    }

    @Test
    void filtersOrdersByStatus() throws Exception {
        mockMvc.perform(get("/api/v1/orders").param("status", "ASSIGNED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(8));

        mockMvc.perform(get("/api/v1/orders").param("status", "DELIVERED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void rejectsUnknownStatusFilterWithBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/orders").param("status", "BOGUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("status"));
    }
}
