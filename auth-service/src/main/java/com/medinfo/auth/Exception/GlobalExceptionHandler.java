package com.medinfo.auth.Exception;

import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler
    public ResponseEntity<Map<String,String>> handleRuntimeException(RuntimeException ex){
        return  ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "Message",
                        ex.getMessage()
                ));
    }
}
