package com.aditya.app.dispatch;

import com.aditya.app.common.NotFoundException;
import com.aditya.app.dispatch.domain.Agent;
import com.aditya.app.dispatch.domain.AgentStatus;
import com.aditya.app.dispatch.domain.AgentWentOfflineEvent;
import com.aditya.app.dispatch.dto.AgentResponse;
import com.aditya.app.dispatch.repo.AgentRepository;
import com.aditya.app.dispatch.service.AgentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentServiceTest {

    @Mock
    private AgentRepository repository;

    @Mock
    private ApplicationEventPublisher events;

    @InjectMocks
    private AgentService service;

    @Test
    void updatesAgentStatusWithoutTouchingLoad() {
        Agent agent = new Agent("AGT-001", "Priya Sharma", AgentStatus.BUSY);
        agent.incrementLoad();
        when(repository.findById("AGT-001")).thenReturn(Optional.of(agent));
        when(repository.save(any(Agent.class))).thenAnswer(inv -> inv.getArgument(0));

        AgentResponse response = service.updateStatus("AGT-001", AgentStatus.OFFLINE);

        assertThat(response.status()).isEqualTo(AgentStatus.OFFLINE);
        assertThat(response.activeOrderCount())
                .as("taking an agent offline must not move their orders in T-1")
                .isEqualTo(1);
    }

    @Test
    void publishesAnOfflineEventSoReplanningCanRunOffTheRequestThread() {
        stubAgent(AgentStatus.AVAILABLE);

        service.updateStatus("AGT-001", AgentStatus.OFFLINE);

        verify(events).publishEvent(new AgentWentOfflineEvent("AGT-001"));
    }

    @Test
    void doesNotPublishForAnyOtherStatusChange() {
        stubAgent(AgentStatus.OFFLINE);

        service.updateStatus("AGT-001", AgentStatus.AVAILABLE);
        service.updateStatus("AGT-001", AgentStatus.BUSY);

        verify(events, never()).publishEvent(any(AgentWentOfflineEvent.class));
    }

    private void stubAgent(AgentStatus initial) {
        Agent agent = new Agent("AGT-001", "Priya Sharma", initial);
        when(repository.findById("AGT-001")).thenReturn(Optional.of(agent));
        when(repository.save(any(Agent.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void throwsNotFoundForUnknownAgent() {
        when(repository.findById("AGT-999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateStatus("AGT-999", AgentStatus.OFFLINE))
                .isInstanceOf(NotFoundException.class);
    }
}
