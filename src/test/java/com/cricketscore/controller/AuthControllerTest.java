package com.cricketscore.controller;

import com.cricketscore.model.AuthRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:auth-test;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class AuthControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void registrationHashesPasswordAndCreatesAuthenticatedSession() throws Exception {
        String email = "new-" + UUID.randomUUID() + "@example.com";
        String password = "BoundaryPass123";
        String payload = objectMapper.writeValueAsString(new AuthRequest("Riley Morgan", email, password));

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Riley Morgan"))
                .andExpect(jsonPath("$.email").value(email))
                .andReturn();

        String passwordHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM app_user WHERE email = ?", String.class, email);
        assertNotEquals(password, passwordHash);
        assertTrue(passwordHash.startsWith("$2a$"));

        mockMvc.perform(get("/api/auth/session").session(
                        (org.springframework.mock.web.MockHttpSession) result.getRequest().getSession(false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Riley Morgan"));
    }

    @Test
    void loginRequiresCorrectPasswordAndLogoutEndsSession() throws Exception {
        String email = "login-" + UUID.randomUUID() + "@example.com";
        register(email, "First Name", "CricketPass123");
        String loginPayload = "{\"email\":\"" + email + "\",\"password\":\"CricketPass123\"}";

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(loginPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("First Name"))
                .andReturn();

        mockMvc.perform(post("/api/auth/logout").session(
                        (org.springframework.mock.web.MockHttpSession) login.getRequest().getSession(false)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/auth/session"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsDuplicateEmailAndIncorrectPassword() throws Exception {
        String email = "duplicate-" + UUID.randomUUID() + "@example.com";
        register(email, "First Name", "CricketPass123");
        register(email, "Second Name", "CricketPass456", 409);

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"" + email + "\",\"password\":\"WrongPass123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Email or password is incorrect"));
    }

    @Test
    void profileNameCanBeUpdatedWithAuthenticatedSession() throws Exception {
        String email = "profile-" + UUID.randomUUID() + "@example.com";
        MvcResult registration = register(email, "Old Name", "CricketPass123");

        mockMvc.perform(patch("/api/auth/profile")
                        .session((org.springframework.mock.web.MockHttpSession) registration.getRequest().getSession(false))
                        .contentType("application/json")
                        .content("{\"name\":\"New Name\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Name"));
    }

    private MvcResult register(String email, String name, String password) throws Exception {
        return register(email, name, password, 200);
    }

    private MvcResult register(String email, String name, String password, int expectedStatus) throws Exception {
        String payload = objectMapper.writeValueAsString(new AuthRequest(name, email, password));
        return mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().is(expectedStatus))
                .andReturn();
    }
}
