package za.co.trademesh.modules.access.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import za.co.trademesh.support.PostgresIntegrationTest;

@AutoConfigureMockMvc
class AuthControllerIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanAccessTables() {
        jdbcTemplate.update("DELETE FROM access_refresh_session");
        jdbcTemplate.update("DELETE FROM access_business_membership");
        jdbcTemplate.update("DELETE FROM access_user_role");
        jdbcTemplate.update("DELETE FROM access_user_account");
    }

    @Test
    void exposesRegistrationLoginRefreshAndLogoutContracts() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    {
                      "email": "owner@example.com",
                      "password": "correct-horse-battery",
                      "accountType": "BUSINESS_OWNER"
                    }
                    """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.roles[0]").value("BUSINESS_OWNER"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    {"email":"owner@example.com","password":"wrong-password"}
                    """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    {"refreshToken":"not-a-valid-refresh-token"}
                    """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    {"refreshToken":"already-revoked-or-unknown"}
                    """))
                .andExpect(status().isNoContent());
    }

    @Test
    void publicRegistrationCannotCreatePrivilegedAccounts() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    {
                      "email": "attacker@example.com",
                      "password": "correct-horse-battery",
                      "accountType": "ADMINISTRATOR"
                    }
                    """))
                .andExpect(status().isBadRequest());
    }
}
