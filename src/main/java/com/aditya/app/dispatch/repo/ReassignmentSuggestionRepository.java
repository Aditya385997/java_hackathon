package com.aditya.app.dispatch.repo;

import com.aditya.app.dispatch.domain.ReassignmentSuggestion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReassignmentSuggestionRepository extends JpaRepository<ReassignmentSuggestion, Long> {
}
