package com.portfolio.fincore.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.portfolio.fincore.api.ApiException;
import com.portfolio.fincore.api.ApiModels.TransferRequest;
import com.portfolio.fincore.api.ApiModels.TransferResponse;
import com.portfolio.fincore.domain.AccountStatus;
import com.portfolio.fincore.domain.AccountType;
import com.portfolio.fincore.domain.BankAccount;
import com.portfolio.fincore.domain.DemoPrincipal;
import com.portfolio.fincore.domain.DemoRole;
import com.portfolio.fincore.domain.DemoUser;
import com.portfolio.fincore.domain.LedgerEntry;
import com.portfolio.fincore.domain.Transfer;
import com.portfolio.fincore.repository.AuditEventRepository;
import com.portfolio.fincore.repository.BankAccountRepository;
import com.portfolio.fincore.repository.DemoUserRepository;
import com.portfolio.fincore.repository.LedgerEntryRepository;
import com.portfolio.fincore.repository.TransferRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {
    private static final String IDEMPOTENCY_KEY = "transfer-key-0001";

    @Mock
    private AccountService accountService;

    @Mock
    private BeneficiaryService beneficiaryService;

    @Mock
    private BankAccountRepository accounts;

    @Mock
    private DemoUserRepository users;

    @Mock
    private TransferRepository transfers;

    @Mock
    private LedgerEntryRepository ledgerEntries;

    @Mock
    private AuditEventRepository auditEvents;

    @InjectMocks
    private TransferService service;

    private DemoUser owner;
    private DemoPrincipal principal;
    private BankAccount source;
    private BankAccount destination;

    @BeforeEach
    void setUp() {
        owner = new DemoUser("customer-1", "customer-one", "Avery Quinn", DemoRole.CUSTOMER);
        principal = new DemoPrincipal(owner.getId(), owner.getDisplayName(), owner.getRole());
        source = account("source", owner, "USD", 100_000);
        destination = account("destination", owner, "USD", 50_000);
        lenient().when(users.lockById(owner.getId())).thenReturn(Optional.of(owner));
        lenient().when(transfers.findByOwner_IdAndIdempotencyKey(owner.getId(), IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty());
        lenient().when(accounts.lockAllByIds(anyCollection())).thenReturn(List.of(source, destination));
        lenient().when(transfers.save(any(Transfer.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(beneficiaryService.destinationAlias(anyString(), anyString())).thenReturn(null);
    }

    @Test
    void completesAnAtomicTransferAndDefaultsBlankMemos() {
        TransferResponse response = service.create(
                principal,
                request(source.getId(), destination.getId(), 12_500, "  "),
                IDEMPOTENCY_KEY);

        assertEquals(87_500, source.getBalanceMinor());
        assertEquals(62_500, destination.getBalanceMinor());
        assertEquals("Internal transfer", response.memo());
        assertEquals("COMPLETED", response.status());
        assertFalse(response.duplicate());
        ArgumentCaptor<LedgerEntry> entries = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntries, times(2)).save(entries.capture());
        assertEquals(-12_500, entries.getAllValues().get(0).getAmountMinor());
        assertEquals(12_500, entries.getAllValues().get(1).getAmountMinor());
        verify(auditEvents).save(any());
    }

    @Test
    void rejectsNonCustomerProfilesAndInvalidIdempotencyKeys() {
        DemoPrincipal auditor = new DemoPrincipal("auditor", "Casey Lane", DemoRole.AUDITOR);
        doThrow(ApiException.forbidden("CUSTOMER_ROLE_REQUIRED", "Customer only."))
                .when(accountService)
                .requireCustomer(auditor);
        assertEquals(
                "CUSTOMER_ROLE_REQUIRED",
                assertThrows(ApiException.class, () -> service.create(
                        auditor, request(source.getId(), destination.getId(), 100, null), IDEMPOTENCY_KEY))
                        .getCode());
        assertEquals(
                "IDEMPOTENCY_KEY_REQUIRED",
                assertThrows(ApiException.class, () -> service.create(
                        principal, request(source.getId(), destination.getId(), 100, null), null))
                        .getCode());
        assertEquals(
                "IDEMPOTENCY_KEY_REQUIRED",
                assertThrows(ApiException.class, () -> service.create(
                        principal, request(source.getId(), destination.getId(), 100, null), "short"))
                        .getCode());
    }

    @Test
    void rejectsMissingCustomersAndAccounts() {
        when(users.lockById(owner.getId())).thenReturn(Optional.empty());
        assertEquals(
                "CUSTOMER_NOT_FOUND",
                assertThrows(ApiException.class, () -> service.create(
                        principal, request(source.getId(), destination.getId(), 100, null), IDEMPOTENCY_KEY))
                        .getCode());

        when(users.lockById(owner.getId())).thenReturn(Optional.of(owner));
        when(accounts.lockAllByIds(anyCollection())).thenReturn(List.of(source));
        assertEquals(
                "ACCOUNT_NOT_FOUND",
                assertThrows(ApiException.class, () -> service.create(
                        principal, request(source.getId(), destination.getId(), 100, null), IDEMPOTENCY_KEY))
                        .getCode());
    }

    @Test
    void rejectsAccountsOutsideTheCustomerBoundaryAndUnauthorizedRecipients() {
        DemoUser recipient = new DemoUser("customer-2", "customer-two", "Riley Morgan", DemoRole.CUSTOMER);
        BankAccount foreignSource = account("foreign-source", recipient, "USD", 100_000);
        when(accounts.lockAllByIds(anyCollection())).thenReturn(List.of(foreignSource, destination));
        assertEquals(
                "ACCOUNT_NOT_FOUND",
                assertThrows(ApiException.class, () -> service.create(
                        principal,
                        request(foreignSource.getId(), destination.getId(), 100, null),
                        IDEMPOTENCY_KEY))
                        .getCode());

        when(accounts.lockAllByIds(anyCollection())).thenReturn(List.of(source, foreignSource));
        when(beneficiaryService.isAuthorizedDestination(owner.getId(), foreignSource.getId())).thenReturn(false);
        assertEquals(
                "RECIPIENT_NOT_FOUND",
                assertThrows(ApiException.class, () -> service.create(
                        principal,
                        request(source.getId(), foreignSource.getId(), 100, null),
                        IDEMPOTENCY_KEY))
                        .getCode());
    }

    @Test
    void rejectsSameAccountCurrencyFrozenAndInsufficientBalanceTransfers() {
        when(accounts.lockAllByIds(anyCollection())).thenReturn(List.of(source));
        assertEquals(
                "SAME_ACCOUNT_TRANSFER",
                assertThrows(ApiException.class, () -> service.create(
                        principal, request(source.getId(), source.getId(), 100, null), IDEMPOTENCY_KEY))
                        .getCode());

        BankAccount euroDestination = account("euro-destination", owner, "EUR", 50_000);
        when(accounts.lockAllByIds(anyCollection())).thenReturn(List.of(source, euroDestination));
        assertEquals(
                "CURRENCY_MISMATCH",
                assertThrows(ApiException.class, () -> service.create(
                        principal, request(source.getId(), euroDestination.getId(), 100, null), IDEMPOTENCY_KEY))
                        .getCode());

        ReflectionTestUtils.setField(source, "status", AccountStatus.FROZEN);
        when(accounts.lockAllByIds(anyCollection())).thenReturn(List.of(source, destination));
        assertEquals(
                "ACCOUNT_UNAVAILABLE",
                assertThrows(ApiException.class, () -> service.create(
                        principal, request(source.getId(), destination.getId(), 100, null), IDEMPOTENCY_KEY))
                        .getCode());

        ReflectionTestUtils.setField(source, "status", AccountStatus.ACTIVE);
        source.setBalanceMinor(50);
        assertEquals(
                "INSUFFICIENT_FUNDS",
                assertThrows(ApiException.class, () -> service.create(
                        principal, request(source.getId(), destination.getId(), 100, null), IDEMPOTENCY_KEY))
                        .getCode());
    }

    @Test
    void rejectsControlCharactersAndDestinationBalanceOverflow() {
        assertEquals(
                "INVALID_MEMO",
                assertThrows(ApiException.class, () -> service.create(
                        principal,
                        request(source.getId(), destination.getId(), 100, "\u0001memo"),
                        IDEMPOTENCY_KEY))
                        .getCode());

        destination.setBalanceMinor(Long.MAX_VALUE);
        assertEquals(
                "BALANCE_LIMIT",
                assertThrows(ApiException.class, () -> service.create(
                        principal, request(source.getId(), destination.getId(), 1, null), IDEMPOTENCY_KEY))
                        .getCode());
    }

    @Test
    void returnsIdempotentReplaysAndListsTransfersByRole() {
        TransferRequest original = request(source.getId(), destination.getId(), 12_500, "memo");
        service.create(principal, original, IDEMPOTENCY_KEY);
        ArgumentCaptor<Transfer> saved = ArgumentCaptor.forClass(Transfer.class);
        verify(transfers).save(saved.capture());
        when(transfers.findByOwner_IdAndIdempotencyKey(owner.getId(), IDEMPOTENCY_KEY))
                .thenReturn(Optional.of(saved.getValue()));

        TransferResponse duplicate = service.create(principal, original, IDEMPOTENCY_KEY);
        assertTrue(duplicate.duplicate());
        when(transfers.findTop100ByOwner_IdOrderByCreatedAtDesc(owner.getId()))
                .thenReturn(List.of(saved.getValue()));
        assertEquals(1, service.list(principal).size());

        DemoPrincipal operations = new DemoPrincipal("operations", "Morgan Reed", DemoRole.OPERATIONS);
        when(transfers.findTop100ByOrderByCreatedAtDesc()).thenReturn(List.of(saved.getValue()));
        assertEquals(1, service.list(operations).size());
    }

    private TransferRequest request(String sourceId, String destinationId, long amount, String memo) {
        return new TransferRequest(sourceId, destinationId, amount, memo);
    }

    private BankAccount account(String id, DemoUser accountOwner, String currency, long balance) {
        BankAccount account = new BankAccount(
                id,
                accountOwner,
                id,
                AccountType.CHECKING,
                "**** 0001",
                currency,
                balance);
        return account;
    }
}
