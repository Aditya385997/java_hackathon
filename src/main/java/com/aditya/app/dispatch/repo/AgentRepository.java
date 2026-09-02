package com.aditya.app.dispatch.repo;

import com.aditya.app.dispatch.domain.Agent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentRepository extends JpaRepository<Agent, String> {
}
