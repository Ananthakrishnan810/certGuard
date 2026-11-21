package com.certguard.web;

import com.certguard.service.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/** Every error leaves the API as {timestamp, status, error, message, path[, fields]}. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Map<String, Object>> api(ApiException e, HttpServletRequest req) {
        return body(e.getStatus(), e.getMessage(), req, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException e, HttpServletRequest req) {
        Map<String, String> fields = e.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(f -> f.getField(), f -> String.valueOf(f.getDefaultMessage()),
                        (a, b) -> a, LinkedHashMap::new));
        String msg = fields.entrySet().stream().map(x -> x.getKey() + ": " + x.getValue())
                .collect(Collectors.joining("; "));
        return body(HttpStatus.BAD_REQUEST, msg.isEmpty() ? "Validation failed" : msg, req, fields);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<Map<String, Object>> tooBig(MaxUploadSizeExceededException e, HttpServletRequest req) {
        return body(HttpStatus.PAYLOAD_TOO_LARGE, "File is too large (maximum 10 MB)", req, null);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestPartException.class,
            MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<Map<String, Object>> badInput(Exception e, HttpServletRequest req) {
        String msg = e instanceof HttpMessageNotReadableException ? "Malformed request body" : e.getMessage();
        return body(HttpStatus.BAD_REQUEST, msg, req, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<Map<String, Object>> method(HttpRequestMethodNotSupportedException e, HttpServletRequest req) {
        return body(HttpStatus.METHOD_NOT_ALLOWED, e.getMessage(), req, null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<Map<String, Object>> notFound(NoResourceFoundException e, HttpServletRequest req) {
        return body(HttpStatus.NOT_FOUND, "Not found", req, null);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> other(Exception e, HttpServletRequest req) {
        log.error("Unhandled error on {} {}", req.getMethod(), req.getRequestURI(), e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error", req, null);
    }

    private static ResponseEntity<Map<String, Object>> body(HttpStatus status, String message, HttpServletRequest req,
                                                            Map<String, String> fields) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("timestamp", Instant.now().toString());
        b.put("status", status.value());
        b.put("error", status.getReasonPhrase());
        b.put("message", message);
        b.put("path", req.getRequestURI());
        if (fields != null && !fields.isEmpty()) b.put("fields", fields);
        return ResponseEntity.status(status).body(b);
    }
}
