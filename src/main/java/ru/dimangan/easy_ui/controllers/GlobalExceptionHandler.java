package ru.dimangan.easy_ui.controllers;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<@NonNull ErrorResponse> handleStatusException(ResponseStatusException e) {
        int status = e.getStatusCode().value();
        return ResponseEntity.status(e.getStatusCode()).body(new ErrorResponse("Запрос не может быть завершен", status));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<@NonNull ErrorResponse> handleException(Exception e) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(e.getMessage(),500));
    }

    @AllArgsConstructor
    @Data
    public static class ErrorResponse{
        private String message;
        private int statusCode;
    }
}
