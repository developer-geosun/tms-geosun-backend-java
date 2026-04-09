package com.geosun.tms.auth.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geosun.tms.auth.domain.user.Role;
import com.geosun.tms.auth.domain.user.User;
import com.geosun.tms.auth.dto.request.LoginRequest;
import com.geosun.tms.auth.dto.request.RefreshRequest;
import com.geosun.tms.auth.dto.request.RegisterRequest;
import com.geosun.tms.auth.dto.request.ResendVerificationRequest;
import com.geosun.tms.auth.dto.request.VerifyEmailRequest;
import com.geosun.tms.auth.ratelimit.RateLimitService;
import com.geosun.tms.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Інтеграційні сценарії auth API (H2, Mock mail).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RateLimitService rateLimitService;

    @MockBean
    private JavaMailSender javaMailSender;

    @BeforeEach
    void setUp() {
        rateLimitService.resetForTests();
        org.mockito.Mockito.reset(javaMailSender);
        doNothing().when(javaMailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void actuatorHealth_isOk() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void register_valid_returns201() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest("new@example.com", "Secret123"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("new@example.com"))
                .andExpect(jsonPath("$.role").value("USER"));
        verify(javaMailSender, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    void register_smtpFailure_stillReturns201() throws Exception {
        doThrow(new MailSendException("smtp down")).when(javaMailSender).send(any(SimpleMailMessage.class));
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest("smtp-fail@example.com", "Secret123"))))
                .andExpect(status().isCreated());
    }

    @Test
    void register_duplicateEmail_returns409() throws Exception {
        String body = objectMapper.writeValueAsString(new RegisterRequest("dup@example.com", "Secret123"));
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void register_invalidPassword_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest("bad@example.com", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void login_beforeVerify_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest("unverified@example.com", "Secret123"))));
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("unverified@example.com", "Secret123"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
    }

    @Test
    void verify_then_login_me_logout_flow() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest("flow@example.com", "Secret123"))));

        ArgumentCaptor<SimpleMailMessage> mailCap = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(javaMailSender, times(1)).send(mailCap.capture());
        String token = extractVerificationToken(mailCap.getValue().getText());

        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyEmailRequest(token))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("flow@example.com", "Secret123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andReturn();

        JsonNode loginJson = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String access = loginJson.get("accessToken").asText();
        String refresh = loginJson.get("refreshToken").asText();

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("flow@example.com"));

        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(refresh))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void verifyEmail_invalidToken_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyEmailRequest("invalid-token"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void me_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void resend_unknownEmail_returns200() throws Exception {
        mockMvc.perform(post("/api/v1/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResendVerificationRequest("ghost@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        verify(javaMailSender, times(0)).send(any(SimpleMailMessage.class));
    }

    @Test
    void resend_smtpFailure_returns503() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest("resend503@example.com", "Secret123"))));
        verify(javaMailSender, times(1)).send(any(SimpleMailMessage.class));

        org.mockito.Mockito.reset(javaMailSender);
        doThrow(new MailSendException("fail")).when(javaMailSender).send(any(SimpleMailMessage.class));

        mockMvc.perform(post("/api/v1/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResendVerificationRequest("resend503@example.com"))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("EMAIL_DELIVERY_FAILED"));
    }

    @Test
    void refresh_rotation_andReuse_invalidatesAllSessions() throws Exception {
        registerVerifyLogin("rotate@example.com");
        Session s0 = login("rotate@example.com", "Secret123");

        MvcResult r1 = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(s0.refresh()))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode j1 = objectMapper.readTree(r1.getResponse().getContentAsString());
        String refresh1 = j1.get("refreshToken").asText();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(s0.refresh()))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_SESSION"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(refresh1))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_wrongPassword_then_rateLimited() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest("ratelimit@example.com", "Secret123"))));
        ArgumentCaptor<SimpleMailMessage> cap = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(javaMailSender).send(cap.capture());
        mockMvc.perform(post("/api/v1/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new VerifyEmailRequest(extractVerificationToken(cap.getValue().getText())))));

        String loginBody = objectMapper.writeValueAsString(new LoginRequest("ratelimit@example.com", "WrongPass99"));
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    void disabledUser_login_returns403() throws Exception {
        User u = new User();
        u.setEmail("disabled@example.com");
        u.setPasswordHash(passwordEncoder.encode("Secret123"));
        u.setRole(Role.USER);
        u.setEmailVerified(true);
        u.setActive(false);
        userRepository.save(u);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("disabled@example.com", "Secret123"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    }

    @Test
    void softDeletedUser_login_returns403() throws Exception {
        User u = new User();
        u.setEmail("deletedlogin@example.com");
        u.setPasswordHash(passwordEncoder.encode("Secret123"));
        u.setRole(Role.USER);
        u.setEmailVerified(true);
        u.setDeleted(true);
        u.setActive(false);
        userRepository.save(u);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("deletedlogin@example.com", "Secret123"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_DELETED"));
    }

    @Test
    void adminSoftDelete_idempotent() throws Exception {
        User admin = new User();
        admin.setEmail("admin@example.com");
        admin.setPasswordHash(passwordEncoder.encode("Admin123!"));
        admin.setRole(Role.ADMIN);
        admin.setEmailVerified(true);
        userRepository.save(admin);

        User victim = new User();
        victim.setEmail("victim@example.com");
        victim.setPasswordHash(passwordEncoder.encode("Secret123"));
        victim.setRole(Role.USER);
        victim.setEmailVerified(true);
        userRepository.save(victim);
        String victimId = victim.getId();

        Session adminSession = login("admin@example.com", "Admin123!");

        mockMvc.perform(delete("/api/v1/users/" + victimId).header("Authorization", "Bearer " + adminSession.access()))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/users/" + victimId).header("Authorization", "Bearer " + adminSession.access()))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/v1/users/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + adminSession.access()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void deleteUser_asUser_forbidden() throws Exception {
        User u = new User();
        u.setEmail("plain@example.com");
        u.setPasswordHash(passwordEncoder.encode("Secret123"));
        u.setRole(Role.USER);
        u.setEmailVerified(true);
        userRepository.save(u);
        String otherId = UUID.randomUUID().toString();

        Session s = login("plain@example.com", "Secret123");
        mockMvc.perform(delete("/api/v1/users/" + otherId).header("Authorization", "Bearer " + s.access()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private void registerVerifyLogin(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest(email, "Secret123"))));
        ArgumentCaptor<SimpleMailMessage> cap = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(javaMailSender, times(1)).send(cap.capture());
        String token = extractVerificationToken(cap.getValue().getText());
        mockMvc.perform(post("/api/v1/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new VerifyEmailRequest(token))));
        org.mockito.Mockito.reset(javaMailSender);
        doNothing().when(javaMailSender).send(any(SimpleMailMessage.class));
    }

    private Session login(String email, String password) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode n = objectMapper.readTree(r.getResponse().getContentAsString());
        return new Session(n.get("accessToken").asText(), n.get("refreshToken").asText());
    }

    private static String extractVerificationToken(String mailText) {
        int idx = mailText.lastIndexOf("\n\n");
        assertThat(idx).isGreaterThan(-1);
        return mailText.substring(idx + 2).trim();
    }

    private record Session(String access, String refresh) {
    }
}
