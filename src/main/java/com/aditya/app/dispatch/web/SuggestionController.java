package com.aditya.app.dispatch.web;

import com.aditya.app.dispatch.domain.SuggestionStatus;
import com.aditya.app.dispatch.dto.SuggestionResponse;
import com.aditya.app.dispatch.dto.UpdateSuggestionRequest;
import com.aditya.app.dispatch.service.SuggestionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/suggestions")
public class SuggestionController {

    private final SuggestionService service;

    public SuggestionController(SuggestionService service) {
        this.service = service;
    }

    /** Read model for the Ops UI, e.g. ?status=PENDING. */
    @GetMapping
    public List<SuggestionResponse> list(@RequestParam(required = false) SuggestionStatus status) {
        return service.findAll(status);
    }

    @PatchMapping("/{id}")
    public SuggestionResponse decide(@PathVariable Long id,
                                     @Valid @RequestBody UpdateSuggestionRequest request) {
        return service.decide(id, request.status());
    }
}
