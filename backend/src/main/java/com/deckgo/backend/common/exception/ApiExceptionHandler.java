package com.deckgo.backend.common.exception;

import jakarta.validation.ConstraintViolationException;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError handleNotFound(NotFoundException exception) {
        return new ApiError("not_found", exception.getMessage(), List.of(), OffsetDateTime.now());
    }

    @ExceptionHandler(ValidationException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ApiError handleValidation(ValidationException exception) {
        return new ApiError("validation_error", exception.getMessage(), exception.getErrors(), OffsetDateTime.now());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        List<String> errors = exception.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .toList();
        return new ApiError("bad_request", "请求参数校验失败", errors, OffsetDateTime.now());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleConstraintViolation(ConstraintViolationException exception) {
        List<String> errors = exception.getConstraintViolations().stream()
            .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
            .toList();
        return new ApiError("bad_request", "请求参数校验失败", errors, OffsetDateTime.now());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiError handleUnexpected(Exception exception) {
        return new ApiError("internal_error", exception.getMessage(), List.of(), OffsetDateTime.now());
    }

    public record ApiError(String code, String message, List<String> errors, OffsetDateTime timestamp) {
    }
}
