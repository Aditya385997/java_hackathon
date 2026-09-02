package com.aditya.app.dispatch.web;

import com.aditya.app.dispatch.domain.OrderStatus;
import com.aditya.app.dispatch.dto.CreateOrderRequest;
import com.aditya.app.dispatch.dto.OrderResponse;
import com.aditya.app.dispatch.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        OrderResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/orders/" + created.id())).body(created);
    }

    @GetMapping
    public List<OrderResponse> list(@RequestParam(required = false) OrderStatus status) {
        return service.findAll(status);
    }
}
