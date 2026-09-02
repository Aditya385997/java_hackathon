package com.aditya.app.dispatch.repo;

import com.aditya.app.dispatch.domain.ReassignmentSuggestion;
import com.aditya.app.dispatch.domain.SuggestionStatus;
import com.aditya.app.dispatch.domain.TriggerReason;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReassignmentSuggestionRepository extends JpaRepository<ReassignmentSuggestion, Long> {

    List<ReassignmentSuggestion> findByStatus(SuggestionStatus status);

    /** Guards against raising a second offline suggestion for an order already awaiting a decision. */
    boolean existsByOrderIdAndStatusAndTriggerReason(
            String orderId, SuggestionStatus status, TriggerReason triggerReason);
}
