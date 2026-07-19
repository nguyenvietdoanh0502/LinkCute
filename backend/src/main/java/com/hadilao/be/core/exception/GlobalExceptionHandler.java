package com.hadilao.be.core.exception;

import com.hadilao.be.core.common.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(value = Exception.class)
    ResponseEntity<ApiResponse<Void>> handlingException(Exception exception) {
        log.error("Exception: ", exception);
        ErrorCode errorCode = ErrorCode.UNCATEGORIZED_EXCEPTION;
        ApiResponse<Void> apiResponse = ApiResponse.error(
                errorCode.getHttpStatus(),
                errorCode.getMessage(),
                errorCode.getCode());

        return ResponseEntity.status(errorCode.getHttpStatus()).body(apiResponse);
    }

    @ExceptionHandler(value = {
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    ResponseEntity<ApiResponse<Void>> handlingInvalidInput(Exception exception) {
        log.debug("Invalid request input: {}", exception.getMessage());
        ErrorCode errorCode = ErrorCode.INVALID_INPUT;
        ApiResponse<Void> apiResponse = ApiResponse.error(
                errorCode.getHttpStatus(),
                errorCode.getMessage(),
                errorCode.getCode());

        return ResponseEntity.status(errorCode.getHttpStatus()).body(apiResponse);
    }

    @ExceptionHandler(value = DataIntegrityViolationException.class)
    ResponseEntity<ApiResponse<Void>> handlingDataIntegrityViolationException(DataIntegrityViolationException exception) {
        log.error("DataIntegrityViolationException: ", exception);
        ErrorCode errorCode = ErrorCode.USER_EXISTED;
        ApiResponse<Void> apiResponse = ApiResponse.error(
                errorCode.getHttpStatus(),
                errorCode.getMessage(),
                errorCode.getCode());

        return ResponseEntity.status(errorCode.getHttpStatus()).body(apiResponse);
    }

    @ExceptionHandler(value = AppException.class)
    ResponseEntity<ApiResponse<Void>> handlingAppException(AppException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        ApiResponse<Void> apiResponse = ApiResponse.error(
                errorCode.getHttpStatus(),
                errorCode.getMessage(),
                errorCode.getCode());

        return ResponseEntity.status(errorCode.getHttpStatus()).body(apiResponse);
    }

    @ExceptionHandler(value = AccessDeniedException.class)
    ResponseEntity<ApiResponse<Void>> handlingAccessDeniedException(AccessDeniedException exception) {
        ErrorCode errorCode = ErrorCode.UNAUTHORIZED;
        ApiResponse<Void> apiResponse = ApiResponse.error(
                errorCode.getHttpStatus(),
                errorCode.getMessage(),
                errorCode.getCode());

        return ResponseEntity.status(errorCode.getHttpStatus()).body(apiResponse);
    }

    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Void>> handlingValidation(MethodArgumentNotValidException exception) {
        String enumKey = exception.hasFieldErrors()
                ? exception.getFieldError().getDefaultMessage()
                : exception.getGlobalError().getDefaultMessage();

        ErrorCode errorCode = ErrorCode.INVALID_KEY;

        try {
            if (enumKey != null) {
                errorCode = ErrorCode.valueOf(enumKey);
            }
        } catch (IllegalArgumentException e) {
            log.error("Invalid ErrorCode key: {}", enumKey);
        }

        ApiResponse<Void> apiResponse = ApiResponse.error(
                errorCode.getHttpStatus(),
                errorCode.getMessage(),
                errorCode.getCode());

        return ResponseEntity.status(errorCode.getHttpStatus()).body(apiResponse);
    }
}
