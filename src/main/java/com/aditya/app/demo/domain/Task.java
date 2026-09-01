package com.aditya.app.demo.domain;

import com.aditya.app.common.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "tasks")
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TaskStatus status = TaskStatus.NEW;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected Task() { /* for JPA */ }

    public Task(String title) {
        this.title = title;
    }

    /** Behaviour lives on the entity where it belongs to the entity. */
    public void transitionTo(TaskStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new BusinessRuleException(
                    "Cannot move task from " + status + " to " + target);
        }
        this.status = target;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public TaskStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
