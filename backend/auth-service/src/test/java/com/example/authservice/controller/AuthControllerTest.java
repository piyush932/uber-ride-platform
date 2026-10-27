package com.example.authservice.controller;

import com.example.authservice.dto.AuthDtos.LoginResponse;
import com.example.authservice.dto.AuthDtos.PassengerResponse;
import com.example.authservice.exception.EmailAlreadyExistsException;
import com.example.authservice.exception.GlobalExceptionHandler;
import com.example.authservice.exception.InvalidCredentialsException;
import com.example.authservice.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Plain MockMvc without the Spring context: no database or security wiring is needed for these checks.
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(authService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void signupReturns201() throws Exception {
        when(authService.signup(any())).thenReturn(new PassengerResponse(1L, "Asha", "asha@example.com"));

        mvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Asha\",\"email\":\"asha@example.com\",\"password\":\"secret123\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.email").value("asha@example.com"));
    }

    @Test
    void signupWithBadEmailReturns400() throws Exception {
        mvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Asha\",\"email\":\"not-an-email\",\"password\":\"secret123\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void signupWithShortPasswordReturns400() throws Exception {
        mvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Asha\",\"email\":\"asha@example.com\",\"password\":\"123\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void duplicateSignupReturns409() throws Exception {
        when(authService.signup(any())).thenThrow(new EmailAlreadyExistsException("asha@example.com"));

        mvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Asha\",\"email\":\"asha@example.com\",\"password\":\"secret123\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void loginReturnsToken() throws Exception {
        when(authService.login(any())).thenReturn(new LoginResponse("abc.def.ghi", "Bearer", 3600000L, "asha@example.com"));

        mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"asha@example.com\",\"password\":\"secret123\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").value("abc.def.ghi"))
            .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void badCredentialsReturn401() throws Exception {
        when(authService.login(any())).thenThrow(new InvalidCredentialsException());

        mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"asha@example.com\",\"password\":\"wrong-pass\"}"))
            .andExpect(status().isUnauthorized());
    }
}
