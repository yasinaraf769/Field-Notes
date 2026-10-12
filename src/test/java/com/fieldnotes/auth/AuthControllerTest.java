package com.fieldnotes.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fieldnotes.auth.dto.AuthResponse;
import com.fieldnotes.auth.dto.LoginRequest;
import com.fieldnotes.auth.dto.RefreshTokenRequest;
import com.fieldnotes.auth.dto.RegisterRequest;
import com.fieldnotes.auth.repository.RefreshTokenRepository;
import com.fieldnotes.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:fieldnotes_auth_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "jwt.secret=test-only-signing-secret-with-at-least-32-bytes-length-required",
        "jwt.expiration-ms=3600000"
})
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void registerShouldSucceedForValidPayload() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "john.doe@example.com",
                "SecurePassword123!",
                "John",
                "Doe",
                "+1234567890"
        );

        mockMvc.perform(post("/api/fieldnotesatl/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.refreshToken", notNullValue()))
                .andExpect(jsonPath("$.tokenType", is("Bearer")))
                .andExpect(jsonPath("$.user.email", is("john.doe@example.com")))
                .andExpect(jsonPath("$.user.firstName", is("John")))
                .andExpect(jsonPath("$.user.lastName", is("Doe")));
    }

    @Test
    void registerShouldFailForDuplicateEmail() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "alice@example.com",
                "SecurePassword123!",
                "Alice",
                "Smith",
                null
        );

        mockMvc.perform(post("/api/fieldnotesatl/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/fieldnotesatl/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("already registered")));
    }

    @Test
    void registerShouldFailForInvalidInputs() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "invalid-email",
                "short",
                "",
                null,
                null
        );

        mockMvc.perform(post("/api/fieldnotesatl/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.details", hasSize(greaterThanOrEqualTo(2))));
    }

    @Test
    void loginShouldSucceedWithValidCredentials() throws Exception {
        RegisterRequest register = new RegisterRequest(
                "bob@example.com",
                "Password123!",
                "Bob",
                "Builder",
                null
        );

        mockMvc.perform(post("/api/fieldnotesatl/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated());

        LoginRequest login = new LoginRequest("bob@example.com", "Password123!");

        mockMvc.perform(post("/api/fieldnotesatl/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.refreshToken", notNullValue()))
                .andExpect(jsonPath("$.user.email", is("bob@example.com")));
    }

    @Test
    void loginShouldReturn401ForInvalidPassword() throws Exception {
        RegisterRequest register = new RegisterRequest(
                "carol@example.com",
                "Password123!",
                "Carol",
                "Danvers",
                null
        );

        mockMvc.perform(post("/api/fieldnotesatl/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated());

        LoginRequest login = new LoginRequest("carol@example.com", "WrongPassword!");

        mockMvc.perform(post("/api/fieldnotesatl/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", containsString("Invalid email or password")));
    }

    @Test
    void loginShouldReturn401ForNonExistentUser() throws Exception {
        LoginRequest login = new LoginRequest("nobody@example.com", "Password123!");

        mockMvc.perform(post("/api/fieldnotesatl/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshTokenAndRotationShouldWork() throws Exception {
        RegisterRequest register = new RegisterRequest(
                "dave@example.com",
                "Password123!",
                "Dave",
                "Bowman",
                null
        );

        MvcResult registerResult = mockMvc.perform(post("/api/fieldnotesatl/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated())
                .andReturn();

        AuthResponse initialAuth = objectMapper.readValue(
                registerResult.getResponse().getContentAsString(),
                AuthResponse.class
        );
        String initialRefreshToken = initialAuth.refreshToken();
        assertNotNull(initialRefreshToken);

        // Refresh tokens
        RefreshTokenRequest refreshRequest = new RefreshTokenRequest(initialRefreshToken);
        MvcResult refreshResult = mockMvc.perform(post("/api/fieldnotesatl/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.refreshToken", notNullValue()))
                .andReturn();

        AuthResponse refreshedAuth = objectMapper.readValue(
                refreshResult.getResponse().getContentAsString(),
                AuthResponse.class
        );

        // Reusing the rotated refresh token must fail with 401
        mockMvc.perform(post("/api/fieldnotesatl/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isUnauthorized());

        // But using the new refresh token succeeds
        RefreshTokenRequest secondRefresh = new RefreshTokenRequest(refreshedAuth.refreshToken());
        mockMvc.perform(post("/api/fieldnotesatl/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secondRefresh)))
                .andExpect(status().isOk());
    }

    @Test
    void unauthenticatedAccessToProtectedRouteShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/fieldnotesatl/customers"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.message", containsString("authentication is required")));
    }

    @Test
    void logoutShouldRevokeRefreshTokens() throws Exception {
        RegisterRequest register = new RegisterRequest(
                "eve@example.com",
                "Password123!",
                "Eve",
                "Polastri",
                null
        );

        MvcResult result = mockMvc.perform(post("/api/fieldnotesatl/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated())
                .andReturn();

        AuthResponse auth = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                AuthResponse.class
        );

        // Logout with Bearer token
        mockMvc.perform(post("/api/fieldnotesatl/auth/logout")
                        .header("Authorization", "Bearer " + auth.accessToken()))
                .andExpect(status().isNoContent());

        // Refreshing with the token now fails because it was revoked
        RefreshTokenRequest refreshReq = new RefreshTokenRequest(auth.refreshToken());
        mockMvc.perform(post("/api/fieldnotesatl/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq)))
                .andExpect(status().isUnauthorized());
    }
}
