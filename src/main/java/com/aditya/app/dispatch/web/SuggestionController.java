package com.aditya.app.dispatch.web;

import com.aditya.app.dispatch.dto.SuggestionResponse;
import com.aditya.app.dispatch.dto.UpdateSuggestionRequest;
import com.aditya.app.dispatch.service.SuggestionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/suggestions")
public class SuggestionController {

    private final SuggestionService service;

    public SuggestionController(SuggestionService service) {
        this.service = service;
    }

    @PatchMapping("/{id}")
    public SuggestionResponse decide(@PathVariable Long id,
                                     @Valid @RequestBody UpdateSuggestionRequest request) {
        return service.decide(id, request.status());
    }
}
