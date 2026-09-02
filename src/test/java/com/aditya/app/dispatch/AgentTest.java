package com.aditya.app.dispatch;

import com.aditya.app.common.BusinessRuleException;
import com.aditya.app.dispatch.domain.Agent;
import com.aditya.app.dispatch.domain.AgentStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentTest {

    @Test
    void tracksLoadAsOrdersArriveAndLeave() {
        Agent agent = new Agent("AGT-001", "Priya Sharma", AgentStatus.AVAILABLE);

        agent.incrementLoad();
        agent.incrementLoad();
        agent.decrementLoad();

        assertThat(agent.getActiveOrderCount()).isEqualTo(1);
    }

    @Test
    void rejectsReleasingLoadFromAnIdleAgent() {
        Agent agent = new Agent("AGT-002", "Rahul Verma", AgentStatus.AVAILABLE);

        assertThatThrownBy(agent::decrementLoad)
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("AGT-002");
    }

    @Test
    void allowsAnyStatusChangeInSprintOne() {
        Agent agent = new Agent("AGT-003", "Ananya Iyer", AgentStatus.BUSY);

        agent.changeStatus(AgentStatus.OFFLINE);
        agent.changeStatus(AgentStatus.AVAILABLE);

        assertThat(agent.getStatus()).isEqualTo(AgentStatus.AVAILABLE);
    }
}
