package com.sftpmanager.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void validationErrorsAreReturnedAsFieldMessageMapWith400() {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "user");
        bindingResult.addError(new org.springframework.validation.FieldError("user", "email", "Invalid email address"));
        bindingResult.addError(new org.springframework.validation.FieldError("user", "firstName", "First name is required"));

        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);

        ResponseEntity<Map<String, String>> response = handler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody())
            .containsEntry("email", "Invalid email address")
            .containsEntry("firstName", "First name is required");
    }

    @Test
    void generalExceptionReturns500WithMessage() {
        ResponseEntity<Map<String, String>> response = handler.handleGeneral(new RuntimeException("boom"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("error", "boom");
    }

    @Test
    void generalExceptionWithNullMessageGetsPlaceholder() {
        ResponseEntity<Map<String, String>> response = handler.handleGeneral(new RuntimeException());

        assertThat(response.getBody()).containsEntry("error", "Unknown error");
    }
}
