package com.aditya.app.common;

/** Thrown when a request is well-formed but violates a domain rule. Maps to HTTP 409. */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
