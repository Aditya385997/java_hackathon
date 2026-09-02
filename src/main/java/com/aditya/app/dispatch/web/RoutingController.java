package com.aditya.app.dispatch.web;

import com.aditya.app.dispatch.dto.RoutingStrategyResponse;
import com.aditya.app.dispatch.dto.UpdateRoutingStrategyRequest;
import com.aditya.app.dispatch.routing.RoutingStrategySelector;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/routing")
public class RoutingController {

    private final RoutingStrategySelector selector;

    public RoutingController(RoutingStrategySelector selector) {
        this.selector = selector;
    }

    @GetMapping("/strategy")
    public RoutingStrategyResponse current() {
        return new RoutingStrategyResponse(selector.activeKey(), selector.availableKeys());
    }

    /** Takes effect on the next routing call. No restart, no redeploy. */
    @PatchMapping("/strategy")
    public RoutingStrategyResponse switchStrategy(
            @Valid @RequestBody UpdateRoutingStrategyRequest request) {
        selector.activate(request.strategy());
        return new RoutingStrategyResponse(selector.activeKey(), selector.availableKeys());
    }
}
