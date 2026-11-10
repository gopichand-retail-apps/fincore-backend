package com.portfolio.fincore.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.portfolio.fincore.api.ApiException;
import com.portfolio.fincore.api.ApiModels.AccountResponse;
import com.portfolio.fincore.api.ApiModels.OverviewResponse;
import com.portfolio.fincore.api.ApiModels.StatementResponse;
import com.portfolio.fincore.domain.AccountStatus;
import com.portfolio.fincore.domain.AccountType;
import com.portfolio.fincore.domain.BankAccount;
import com.portfolio.fincore.domain.DemoPrincipal;
import com.portfolio.fincore.domain.DemoRole;
import com.portfolio.fincore.domain.DemoUser;
import com.portfolio.fincore.domain.LedgerCategory;
import com.portfolio.fincore.domain.LedgerEntry;
import com.portfolio.fincore.domain.Transfer;
import com.portfolio.fincore.repository.BankAccountRepository;
import com.portfolio.fincore.repository.LedgerEntryRepository;
import com.portfolio.fincore.repository.TransferRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {
    @Mock
    private BankAccountRepository accounts;

    @Mock
    private LedgerEntryRepository ledgerEntries;

    @Mock
    private TransferRepository transfers;

    private AccountService service;
    private DemoUser customer;
    private DemoPrincipal customerPrincipal;
    private DemoPrincipal auditorPrincipal;
    private BankAccount checking;
    private BankAccount savings;

    @BeforeEach
    void setUp() {
        service = new AccountService(accounts, ledgerEntries, transfers);
        customer = new DemoUser("customer-1", "customer-one", "Avery Quinn", DemoRole.CUSTOMER);
        customerPrincipal = new DemoPrincipal(customer.getId(), customer.getDisplayName(), DemoRole.CUSTOMER);
        auditorPrincipal = new DemoPrincipal("auditor", "Casey Lane", DemoRole.AUDITOR);
        checking = account("checking", customer, 100_000);
        savings = account("savings", customer, 250_000);
        lenient().when(accounts.findByOwner_IdOrderByDisplayNameAsc(customer.getId()))
                .thenReturn(List.of(checking, savings));
        lenient().when(accounts.findAllWithOwner()).thenReturn(List.of(checking, savings));
        lenient().when(accounts.findByIdAndOwner_Id(anyString(), anyString())).thenReturn(Optional.empty());
        lenient().when(accounts.findById(anyString())).thenReturn(Optional.empty());
        lenient().when(ledgerEntries.findTop100ByAccount_IdInOrderByCreatedAtDesc(anyCollection()))
                .thenReturn(List.of());
        lenient().when(ledgerEntries.findByAccount_IdInAndCreatedAtAfterOrderByCreatedAtDesc(
                        anyCollection(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());
    }

    @Test
    void returnsOwnerDetailsOnlyForReadOnlyProfiles() {
        List<AccountResponse> customerAccounts = service.listAccounts(customerPrincipal);
        assertEquals(2, customerAccounts.size());
        assertNull(customerAccounts.get(0).ownerDisplayName());

        List<AccountResponse> auditedAccounts = service.listAccounts(auditorPrincipal);
        assertEquals("Avery Quinn", auditedAccounts.get(0).ownerDisplayName());
        assertEquals(checking.getBalanceMinor(), auditedAccounts.get(0).balanceMinor());
    }

    @Test
    void aggregatesIncomeSpendingTransfersRecentActivityAndFrozenAccountCounts() {
        ReflectionTestUtils.setField(savings, "status", AccountStatus.FROZEN);
        Transfer transfer = new Transfer(
                "transfer-1",
                customer,
                checking,
                savings,
                2_500,
                "Demo",
                "account-service-key",
                "request-hash");
        List<LedgerEntry> monthEntries = List.of(
                entry("income", checking, null, 40_000, LedgerCategory.INCOME),
                entry("housing", checking, null, -5_000, LedgerCategory.HOUSING),
                entry("transfer-out", checking, transfer, -2_500, LedgerCategory.TRANSFER),
                entry("transfer-in", savings, transfer, 2_500, LedgerCategory.TRANSFER),
                entry("positive-other", checking, null, 900, LedgerCategory.OTHER),
                entry("negative-income", checking, null, -100, LedgerCategory.INCOME));
        List<LedgerEntry> activity = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            activity.add(entry("recent-" + index, checking, null, index == 0 ? 50 : -50, LedgerCategory.OTHER));
        }
        when(ledgerEntries.findByAccount_IdInAndCreatedAtAfterOrderByCreatedAtDesc(
                        org.mockito.ArgumentMatchers.eq(List.of(checking.getId(), savings.getId())),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(monthEntries);
        when(ledgerEntries.findTop100ByAccount_IdInOrderByCreatedAtDesc(
                        List.of(checking.getId(), savings.getId())))
                .thenReturn(activity);

        OverviewResponse overview = service.overview(customerPrincipal);

        assertEquals(350_000, overview.totalBalanceMinor());
        assertEquals(40_000, overview.monthlyIncomeMinor());
        assertEquals(5_100, overview.monthlySpendMinor());
        assertEquals(1, overview.transferCount());
        assertEquals(1, overview.activeAccountCount());
        assertEquals(8, overview.recentActivity().size());
        assertTrue(overview.accounts().stream().allMatch(account -> account.ownerDisplayName() == null));
    }

    @Test
    void returnsAnEmptyOverviewAndCustomerAccountEntriesWithoutRepositoryQueries() {
        when(accounts.findByOwner_IdOrderByDisplayNameAsc(customer.getId())).thenReturn(List.of());

        OverviewResponse overview = service.overview(customerPrincipal);
        assertEquals(0, overview.totalBalanceMinor());
        assertEquals(0, overview.activeAccountCount());
        assertTrue(overview.accounts().isEmpty());
        assertTrue(service.entriesForCustomerAccounts(customerPrincipal, Instant.now()).isEmpty());
        assertThrows(
                ApiException.class,
                () -> service.entriesForCustomerAccounts(auditorPrincipal, Instant.now()));
    }

    @Test
    void includesOwnersForReadOnlyProfilesAndReturnsCustomerAccountEntries() {
        when(accounts.findById(checking.getId())).thenReturn(Optional.of(checking));

        assertEquals("Avery Quinn", service.getAccount(auditorPrincipal, checking.getId()).ownerDisplayName());

        OverviewResponse overview = service.overview(auditorPrincipal);
        assertEquals(2, overview.accounts().size());
        assertEquals("Avery Quinn", overview.accounts().get(0).ownerDisplayName());

        Instant start = Instant.parse("2026-10-01T00:00:00Z");
        LedgerEntry recent = entry("customer-entry", checking, null, -100, LedgerCategory.OTHER);
        when(ledgerEntries.findByAccount_IdInAndCreatedAtAfterOrderByCreatedAtDesc(
                        org.mockito.ArgumentMatchers.eq(List.of(checking.getId(), savings.getId())),
                        org.mockito.ArgumentMatchers.eq(start)))
                .thenReturn(List.of(recent));

        assertEquals(
                List.of(recent),
                service.entriesForCustomerAccounts(customerPrincipal, start));
    }

    @Test
    void createsStatementsAndAppliesTransactionLimits() {
        Instant start = Instant.parse("2026-10-01T00:00:00Z");
        Instant end = Instant.parse("2026-11-01T00:00:00Z");
        List<LedgerEntry> entries = List.of(
                entry("pay", checking, null, 20_000, LedgerCategory.INCOME),
                entry("rent", checking, null, -5_000, LedgerCategory.HOUSING));
        when(accounts.findByIdAndOwner_Id(checking.getId(), customer.getId())).thenReturn(Optional.of(checking));
        when(ledgerEntries.findByAccount_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
                        checking.getId(), start, end))
                .thenReturn(entries);
        when(ledgerEntries.sumBefore(checking.getId(), start)).thenReturn(70_000L);

        StatementResponse statement = service.statement(
                customerPrincipal, checking.getId(), java.time.YearMonth.of(2026, 10));
        assertEquals(70_000, statement.openingBalanceMinor());
        assertEquals(85_000, statement.closingBalanceMinor());
        assertEquals(2, statement.entries().size());

        when(ledgerEntries.findTop100ByAccount_IdInOrderByCreatedAtDesc(List.of(checking.getId())))
                .thenReturn(entries);
        assertEquals(1, service.listTransactions(customerPrincipal, checking.getId(), 0).size());
        assertEquals(2, service.listTransactions(customerPrincipal, checking.getId(), 150).size());
        verify(accounts, times(3)).findByIdAndOwner_Id(checking.getId(), customer.getId());
    }

    @Test
    void enforcesAccountOwnershipForReadsAndCustomerOnlyLedgerQueries() {
        when(accounts.findByIdAndOwner_Id(checking.getId(), customer.getId())).thenReturn(Optional.of(checking));
        assertEquals(checking.getId(), service.getAccount(customerPrincipal, checking.getId()).id());
        assertThrows(
                ApiException.class,
                () -> service.getAccount(customerPrincipal, "another-customers-account"));
        assertThrows(ApiException.class, () -> service.findVisibleAccount(customerPrincipal, "missing"));

        when(accounts.findById(checking.getId())).thenReturn(Optional.of(checking));
        assertEquals(checking.getId(), service.findVisibleAccount(auditorPrincipal, checking.getId()).getId());
        assertThrows(ApiException.class, () -> service.findVisibleAccount(auditorPrincipal, "missing"));
        assertThrows(ApiException.class, () -> service.requireCustomer(auditorPrincipal));

        Instant start = Instant.parse("2026-10-01T00:00:00Z");
        when(ledgerEntries.findByAccount_IdInAndCreatedAtAfterOrderByCreatedAtDesc(List.of(checking.getId()), start))
                .thenReturn(List.of(entry("recent", checking, null, -100, LedgerCategory.OTHER)));
        assertEquals(1, service.entriesSince(customerPrincipal, checking.getId(), start).size());
    }

    private BankAccount account(String id, DemoUser owner, long balance) {
        return new BankAccount(
                id,
                owner,
                id,
                AccountType.CHECKING,
                "**** 0001",
                "USD",
                balance);
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
