package com.portfolio.fincore;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:fincore-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "logging.level.root=OFF",
        "logging.level.com.portfolio.fincore=OFF"
})
class FinCoreFlowTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void echoesValidatedRequestAndTraceIds() throws Exception {
        mockMvc.perform(get("/health")
                        .header("X-Request-ID", "fincore-request-123")
                        .header("X-Trace-ID", "fincore-trace-456"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-ID", "fincore-request-123"))
                .andExpect(header().string("X-Trace-ID", "fincore-trace-456"));
    }

        @Test
        void exposesTheApplicationVersionWithoutAuthentication() throws Exception {
                mockMvc.perform(get("/api/v1/version"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.service").value("fincore-api"))
                                .andExpect(jsonPath("$.version").value("1.0.0"));
        }

    @Test
    void corsAllowsBothLoopbackHostsAndRejectsUntrustedOrigins() throws Exception {
        for (String origin : new String[] {"http://127.0.0.1:5501", "http://localhost:5501"}) {
            mockMvc.perform(options("/api/v1/transfers")
                            .header("Origin", origin)
                            .header("Access-Control-Request-Method", "POST")
                            .header("Access-Control-Request-Headers", "content-type,x-csrf-token"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", origin))
                    .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
        }

        mockMvc.perform(options("/api/v1/transfers")
                        .header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void demoProfilesComeFromTheServerAndUnknownProfilesAreRejected() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/demo-profiles"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode profiles = objectMapper.readTree(result.getResponse().getContentAsString()).get("profiles");

        org.junit.jupiter.api.Assertions.assertEquals(4, profiles.size());
        org.junit.jupiter.api.Assertions.assertEquals("CUSTOMER", roleForDemoAccount(profiles, "customer-avery"));
        org.junit.jupiter.api.Assertions.assertEquals("CUSTOMER", roleForDemoAccount(profiles, "customer-riley"));
        org.junit.jupiter.api.Assertions.assertEquals("OPERATIONS", roleForDemoAccount(profiles, "operations"));
        org.junit.jupiter.api.Assertions.assertEquals("AUDITOR", roleForDemoAccount(profiles, "auditor"));

        mockMvc.perform(post("/api/v1/auth/demo-login")
                        .with(csrf().asHeader())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("demoAccount", "unknown-profile"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("DEMO_ACCOUNT_NOT_FOUND"));
    }

    @Test
    void loginCreatesAServerSessionThatPersistsUntilLogout() throws Exception {
        MockHttpSession session = login("customer-avery");
        org.junit.jupiter.api.Assertions.assertNotNull(session);

        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value("demo-customer-avery"))
                .andExpect(jsonPath("$.user.role").value("CUSTOMER"));

        mockMvc.perform(post("/api/v1/auth/logout")
                        .session(session)
                        .with(csrf().asHeader()))
                .andExpect(status().isOk());

        org.junit.jupiter.api.Assertions.assertTrue(session.isInvalid());
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_REQUIRED"));
    }

    @Test
    void anonymousRequestsAreDeniedAndOperationsCannotInitiateTransfers() throws Exception {
        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_REQUIRED"));

        MockHttpSession operations = login("operations");
        mockMvc.perform(post("/api/v1/transfers")
                        .session(operations)
                        .with(csrf().asHeader())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content("""
                                {"sourceAccountId":"account-avery-checking",
                                 "destinationAccountId":"account-riley-checking",
                                 "amountMinor":1000,"memo":"Demo only"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("CUSTOMER_ROLE_REQUIRED"));
    }

    @Test
    void deniesUnknownRoutesForAuthenticatedProfiles() throws Exception {
        MockHttpSession customer = login("customer-avery");

        mockMvc.perform(get("/unknown-route").session(customer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"))
                .andExpect(header().exists("X-Request-ID"));
    }

    @Test
    void customerTransferIsBalancedAndIdempotent() throws Exception {
        MockHttpSession customer = login("customer-avery");
        MockHttpSession auditor = login("auditor");
        long sourceBefore = accountBalance(customer, "account-avery-checking");
        long destinationBefore = accountBalance(auditor, "account-riley-savings");
        String key = "fincore-test-" + UUID.randomUUID();
        String body = """
                {"sourceAccountId":"account-avery-checking",
                 "destinationAccountId":"account-riley-savings",
                 "amountMinor":12500,"memo":"Demo transfer"}
                """;

        mockMvc.perform(post("/api/v1/transfers")
                        .session(customer)
                        .with(csrf().asHeader())
                        .header("Idempotency-Key", key)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amountMinor").value(12500))
                .andExpect(jsonPath("$.duplicate").value(false));

        mockMvc.perform(post("/api/v1/transfers")
                        .session(customer)
                        .with(csrf().asHeader())
                        .header("Idempotency-Key", key)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(true));

        mockMvc.perform(post("/api/v1/transfers")
                        .session(customer)
                        .with(csrf().asHeader())
                        .header("Idempotency-Key", key)
                        .contentType("application/json")
                        .content(body.replace("12500", "13000")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_CONFLICT"));

        org.junit.jupiter.api.Assertions.assertEquals(
                sourceBefore - 12_500, accountBalance(customer, "account-avery-checking"));
        org.junit.jupiter.api.Assertions.assertEquals(
                destinationBefore + 12_500, accountBalance(auditor, "account-riley-savings"));
        mockMvc.perform(get("/api/v1/accounts/account-avery-checking/transactions").session(customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].amountMinor").value(-12_500));
        mockMvc.perform(get("/api/v1/accounts/account-riley-savings/transactions").session(auditor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].amountMinor").value(12_500));
    }

    @Test
    void insufficientFundsDoNotChangeBalancesOrPersistLedgerEntries() throws Exception {
        MockHttpSession customer = login("customer-avery");
        MockHttpSession auditor = login("auditor");
        String sourceAccountId = "account-avery-checking";
        String destinationAccountId = "account-riley-savings";
        long sourceBefore = accountBalance(customer, sourceAccountId);
        long destinationBefore = accountBalance(auditor, destinationAccountId);
        JsonNode transfersBefore = transferRows(customer);
        JsonNode sourceEntriesBefore = ledgerRows(customer, sourceAccountId);
        JsonNode destinationEntriesBefore = ledgerRows(auditor, destinationAccountId);
        String key = "fincore-test-" + UUID.randomUUID();
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "sourceAccountId", sourceAccountId,
                "destinationAccountId", destinationAccountId,
                "amountMinor", Math.addExact(sourceBefore, 1),
                "memo", "Insufficient funds regression"));

        mockMvc.perform(post("/api/v1/transfers")
                        .session(customer)
                        .with(csrf().asHeader())
                        .header("Idempotency-Key", key)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INSUFFICIENT_FUNDS"));

        org.junit.jupiter.api.Assertions.assertEquals(
                sourceBefore, accountBalance(customer, sourceAccountId));
        org.junit.jupiter.api.Assertions.assertEquals(
                destinationBefore, accountBalance(auditor, destinationAccountId));
        org.junit.jupiter.api.Assertions.assertEquals(
                transfersBefore.size(), transferRows(customer).size());
        org.junit.jupiter.api.Assertions.assertEquals(
                sourceEntriesBefore.size(), ledgerRows(customer, sourceAccountId).size());
        org.junit.jupiter.api.Assertions.assertEquals(
                destinationEntriesBefore.size(), ledgerRows(auditor, destinationAccountId).size());
    }

    @Test
    void customerCannotReadAnotherCustomersAccountAndAuditorIsReadOnly() throws Exception {
        MockHttpSession riley = login("customer-riley");
        mockMvc.perform(get("/api/v1/accounts/account-avery-checking").session(riley))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/accounts/account-avery-checking/transactions").session(riley))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_NOT_FOUND"));

        MockHttpSession auditor = login("auditor");
        mockMvc.perform(get("/api/v1/accounts").session(auditor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accounts.length()").value(4));
    }

    @Test
    void customerCannotTransferFromAnotherCustomersAccountOrToAnUnsavedRecipient() throws Exception {
        MockHttpSession riley = login("customer-riley");
        MockHttpSession auditor = login("auditor");
        long averyBalanceBefore = accountBalance(auditor, "account-avery-checking");
        long rileyBalanceBefore = accountBalance(riley, "account-riley-checking");
        int transferCountBefore = transferRows(riley).size();

        mockMvc.perform(post("/api/v1/transfers")
                        .session(riley)
                        .with(csrf().asHeader())
                        .header("Idempotency-Key", "owner-denied-" + UUID.randomUUID())
                        .contentType("application/json")
                        .content("""
                                {"sourceAccountId":"account-avery-checking",
                                 "destinationAccountId":"account-riley-checking",
                                 "amountMinor":1000,"memo":"Unauthorized source"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_NOT_FOUND"));

        mockMvc.perform(post("/api/v1/transfers")
                        .session(riley)
                        .with(csrf().asHeader())
                        .header("Idempotency-Key", "recipient-denied-" + UUID.randomUUID())
                        .contentType("application/json")
                        .content("""
                                {"sourceAccountId":"account-riley-checking",
                                 "destinationAccountId":"account-avery-savings",
                                 "amountMinor":1000,"memo":"Unauthorized destination"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RECIPIENT_NOT_FOUND"));

        org.junit.jupiter.api.Assertions.assertEquals(
                averyBalanceBefore, accountBalance(auditor, "account-avery-checking"));
        org.junit.jupiter.api.Assertions.assertEquals(
                rileyBalanceBefore, accountBalance(riley, "account-riley-checking"));
        org.junit.jupiter.api.Assertions.assertEquals(transferCountBefore, transferRows(riley).size());
    }

    @Test
    void customerCanReadOverviewTransactionsStatementsAndInsights() throws Exception {
        MockHttpSession customer = login("customer-avery");

        mockMvc.perform(get("/api/v1/overview").session(customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accounts.length()").value(2))
                .andExpect(jsonPath("$.totalBalanceMinor").isNumber())
                .andExpect(jsonPath("$.recentActivity.length()").isNumber());

        mockMvc.perform(get("/api/v1/accounts/account-avery-checking").session(customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("account-avery-checking"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        JsonNode entries = ledgerRows(customer, "account-avery-checking");
        org.junit.jupiter.api.Assertions.assertTrue(entries.size() > 1);
        mockMvc.perform(get("/api/v1/accounts/account-avery-checking/transactions")
                        .session(customer)
                        .param("limit", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/v1/accounts/account-avery-checking/transactions")
                        .session(customer)
                        .param("limit", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(entries.size()));

        String createdAt = entries.get(0).get("createdAt").asText();
        String month = YearMonth.from(Instant.parse(createdAt).atZone(ZoneOffset.UTC)).toString();
        mockMvc.perform(get("/api/v1/statements")
                        .session(customer)
                        .param("accountId", "account-avery-checking")
                        .param("month", month))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("account-avery-checking"))
                .andExpect(jsonPath("$.month").value(month))
                .andExpect(jsonPath("$.entries.length()").isNumber());

        mockMvc.perform(get("/api/v1/statements")
                        .session(customer)
                        .param("accountId", "account-avery-checking")
                        .param("month", "not-a-month"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_MONTH"));

        mockMvc.perform(get("/api/v1/insights")
                        .session(customer)
                        .param("accountId", "account-avery-checking"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodIncomeMinor").isNumber())
                .andExpect(jsonPath("$.periodSpendMinor").isNumber())
                .andExpect(jsonPath("$.categories.length()").isNumber());
    }

    @Test
    void customerCanManageBeneficiariesAndCannotRemoveAnotherCustomersRecord() throws Exception {
        MockHttpSession customer = login("customer-avery");
        MockHttpSession riley = login("customer-riley");

        mockMvc.perform(get("/api/v1/beneficiaries/directory").session(customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipients.length()").value(2))
                .andExpect(jsonPath("$.recipients[0].accountId").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.startsWith("account-avery"))));
        mockMvc.perform(get("/api/v1/beneficiaries").session(customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.beneficiaries.length()").value(1));

        String requestBody = objectMapper.writeValueAsString(Map.of(
                "destinationAccountId", "account-riley-checking",
                "alias", "Riley checking test"));
        MvcResult added = mockMvc.perform(post("/api/v1/beneficiaries")
                        .session(customer)
                        .with(csrf().asHeader())
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.alias").value("Riley checking test"))
                .andReturn();
        String beneficiaryId = objectMapper.readTree(added.getResponse().getContentAsString())
                .get("id")
                .asText();

        mockMvc.perform(post("/api/v1/beneficiaries")
                        .session(customer)
                        .with(csrf().asHeader())
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("BENEFICIARY_EXISTS"));
        mockMvc.perform(post("/api/v1/beneficiaries")
                        .session(customer)
                        .with(csrf().asHeader())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "destinationAccountId", "account-avery-checking",
                                "alias", "My own account"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("OWN_ACCOUNT_BENEFICIARY"));
        mockMvc.perform(post("/api/v1/beneficiaries")
                        .session(customer)
                        .with(csrf().asHeader())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "destinationAccountId", "account-riley-checking",
                                "alias", "\u0001X"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_BENEFICIARY"));
        mockMvc.perform(post("/api/v1/beneficiaries")
                        .session(customer)
                        .with(csrf().asHeader())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "destinationAccountId", "account-riley-checking",
                                "alias", "A"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

        mockMvc.perform(delete("/api/v1/beneficiaries/" + beneficiaryId)
                        .session(riley)
                        .with(csrf().asHeader()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("BENEFICIARY_NOT_FOUND"));
        mockMvc.perform(delete("/api/v1/beneficiaries/" + beneficiaryId)
                        .session(customer)
                        .with(csrf().asHeader()))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/beneficiaries/" + beneficiaryId)
                        .session(customer)
                        .with(csrf().asHeader()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("BENEFICIARY_NOT_FOUND"));
    }

    @Test
    void rejectsMalformedAuthenticationRequestsAndRegeneratesUnsafeCorrelationIds() throws Exception {
        MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf")
                        .header("X-Request-ID", "invalid request id")
                        .header("X-Trace-ID", "<unsafe>"))
                .andExpect(status().isOk())
                .andReturn();
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> UUID.fromString(csrfResult.getResponse().getHeader("X-Request-ID")));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> UUID.fromString(csrfResult.getResponse().getHeader("X-Trace-ID")));

        mockMvc.perform(post("/api/v1/auth/demo-login")
                        .with(csrf().asHeader())
                        .contentType("application/json")
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        mockMvc.perform(post("/api/v1/auth/demo-login")
                        .with(csrf().asHeader())
                        .contentType("application/json")
                        .content("{\"demoAccount\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    private String roleForDemoAccount(JsonNode profiles, String demoAccount) {
        for (JsonNode profile : profiles) {
            if (profile.get("demoAccount").asText().equals(demoAccount)) {
                return profile.get("role").asText();
            }
        }
        throw new AssertionError("Expected server profile was not returned");
    }

    private MockHttpSession login(String demoAccount) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/demo-login")
                        .with(csrf().asHeader())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(java.util.Map.of("demoAccount", demoAccount))))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private JsonNode transferRows(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/transfers").session(session))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("transfers");
    }

    private JsonNode ledgerRows(MockHttpSession session, String accountId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/accounts/" + accountId + "/transactions").session(session))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private long accountBalance(MockHttpSession session, String accountId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/accounts").session(session))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode accounts = objectMapper.readTree(result.getResponse().getContentAsString()).get("accounts");
        for (JsonNode account : accounts) {
            if (account.get("id").asText().equals(accountId)) {
                return account.get("balanceMinor").asLong();
            }
        }
        throw new AssertionError("Expected demo account was not returned");
    }
}