package com.portfolio.fincore.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.portfolio.fincore.api.ApiModels.InsightCategory;
import com.portfolio.fincore.api.ApiModels.InsightsResponse;
import com.portfolio.fincore.domain.AccountType;
import com.portfolio.fincore.domain.BankAccount;
import com.portfolio.fincore.domain.DemoPrincipal;
import com.portfolio.fincore.domain.DemoRole;
import com.portfolio.fincore.domain.DemoUser;
import com.portfolio.fincore.domain.LedgerCategory;
import com.portfolio.fincore.domain.LedgerEntry;
import com.portfolio.fincore.domain.Transfer;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InsightsServiceTest {
    @Mock
    private AccountService accounts;

    @Test
    void separatesIncomeAndTransfersFromSpendingAndOrdersCategories() {
        DemoUser owner = new DemoUser("customer-1", "customer-one", "Avery Quinn", DemoRole.CUSTOMER);
        DemoPrincipal principal = new DemoPrincipal(owner.getId(), owner.getDisplayName(), owner.getRole());
        BankAccount source = account("source", owner);
        BankAccount destination = account("destination", owner);
        Transfer transfer = new Transfer(
                "transfer-1",
                owner,
                source,
                destination,
                100,
                "Demo",
                "insights-test-key",
                "request-hash");
        List<LedgerEntry> entries = List.of(
                entry("income", source, null, 2_000, LedgerCategory.INCOME),
                entry("positive-other", source, null, 50, LedgerCategory.OTHER),
                entry("zero", source, null, 0, LedgerCategory.HOUSING),
                entry("transfer", source, transfer, -100, LedgerCategory.TRANSFER),
                entry("housing-one", source, null, -500, LedgerCategory.HOUSING),
                entry("dining", source, null, -300, LedgerCategory.DINING),
                entry("housing-two", source, null, -200, LedgerCategory.HOUSING));
        when(accounts.entriesSince(
                        org.mockito.ArgumentMatchers.eq(principal),
                        org.mockito.ArgumentMatchers.eq(source.getId()),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(entries);

        InsightsResponse response = new InsightsService(accounts).forAccount(principal, source.getId());

        assertEquals(2_000, response.periodIncomeMinor());
        assertEquals(1_000, response.periodSpendMinor());
        assertEquals(
                List.of(new InsightCategory("HOUSING", 700, 2), new InsightCategory("DINING", 300, 1)),
                response.categories());
        verify(accounts).entriesSince(
                org.mockito.ArgumentMatchers.eq(principal),
                org.mockito.ArgumentMatchers.eq(source.getId()),
                org.mockito.ArgumentMatchers.any());
    }

    private BankAccount account(String id, DemoUser owner) {
        return new BankAccount(
                id,
                owner,
                id,
                AccountType.CHECKING,
                "**** 0001",
                "USD",
                100_000);
    }

    private LedgerEntry entry(
            String id,
            BankAccount account,
            Transfer transfer,
            long amount,
            LedgerCategory category) {
        return new LedgerEntry(
                id,
                account,
                transfer,
                amount,
                account.getBalanceMinor(),
                category,
                id,
                Instant.parse("2026-10-15T12:00:00Z"));
    }
}
