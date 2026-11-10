package com.portfolio.fincore.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.portfolio.fincore.api.ApiException;
import com.portfolio.fincore.api.ApiModels.AddBeneficiaryRequest;
import com.portfolio.fincore.api.ApiModels.BeneficiaryResponse;
import com.portfolio.fincore.api.ApiModels.RecipientOption;
import com.portfolio.fincore.domain.AccountType;
import com.portfolio.fincore.domain.BankAccount;
import com.portfolio.fincore.domain.Beneficiary;
import com.portfolio.fincore.domain.DemoPrincipal;
import com.portfolio.fincore.domain.DemoRole;
import com.portfolio.fincore.domain.DemoUser;
import com.portfolio.fincore.repository.BankAccountRepository;
import com.portfolio.fincore.repository.BeneficiaryRepository;
import com.portfolio.fincore.repository.DemoUserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BeneficiaryServiceTest {
    @Mock
    private AccountService accountService;

    @Mock
    private BankAccountRepository accounts;

    @Mock
    private BeneficiaryRepository beneficiaries;

    @Mock
    private DemoUserRepository users;

    private BeneficiaryService service;
    private DemoUser owner;
    private DemoUser recipientOwner;
    private DemoUser operations;
    private DemoPrincipal principal;
    private BankAccount ownAccount;
    private BankAccount recipientAccount;
    private BankAccount operationsAccount;

    @BeforeEach
    void setUp() {
        service = new BeneficiaryService(accountService, accounts, beneficiaries, users);
        owner = new DemoUser("customer-1", "customer-one", "Avery Quinn", DemoRole.CUSTOMER);
        recipientOwner = new DemoUser("customer-2", "customer-two", "Riley Morgan", DemoRole.CUSTOMER);
        operations = new DemoUser("operations", "operations", "Morgan Reed", DemoRole.OPERATIONS);
        principal = new DemoPrincipal(owner.getId(), owner.getDisplayName(), owner.getRole());
        ownAccount = account("own-account", owner);
        recipientAccount = account("recipient-account", recipientOwner);
        operationsAccount = account("operations-account", operations);
        lenient().when(users.getReferenceById(owner.getId())).thenReturn(owner);
        lenient().when(beneficiaries.save(any(Beneficiary.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void listsOnlyOtherCustomerAccountsInTheRecipientDirectory() {
        when(accounts.findAllWithOwner()).thenReturn(List.of(ownAccount, recipientAccount, operationsAccount));

        List<RecipientOption> directory = service.directory(principal);

        assertEquals(1, directory.size());
        assertEquals(recipientAccount.getId(), directory.get(0).accountId());
        assertEquals(recipientOwner.getDisplayName(), directory.get(0).displayName());
        assertEquals(recipientAccount.getDisplayName(), directory.get(0).accountName());
    }

    @Test
    void createsAndListsActiveBeneficiariesWithSafeAliases() {
        when(accounts.findById(recipientAccount.getId())).thenReturn(Optional.of(recipientAccount));
        when(beneficiaries.existsByOwner_IdAndDestinationAccount_IdAndActiveTrue(
                        owner.getId(), recipientAccount.getId()))
                .thenReturn(false);
        BeneficiaryResponse added = service.add(
                principal,
                new AddBeneficiaryRequest(recipientAccount.getId(), "  Riley account  "));
        assertEquals("Riley account", added.alias());
        assertEquals(recipientAccount.getId(), added.destinationAccountId());

        Beneficiary beneficiary = new Beneficiary(
                "saved-beneficiary", owner, recipientAccount, "Riley saved");
        when(beneficiaries.findByOwner_IdAndActiveTrueOrderByAliasAsc(owner.getId()))
                .thenReturn(List.of(beneficiary));
        assertEquals("Riley saved", service.list(principal).get(0).alias());

        when(beneficiaries.existsByOwner_IdAndDestinationAccount_IdAndActiveTrue(
                        owner.getId(), recipientAccount.getId()))
                .thenReturn(true);
        assertTrue(service.isAuthorizedDestination(owner.getId(), recipientAccount.getId()));
        when(beneficiaries.findByOwner_IdAndDestinationAccount_IdAndActiveTrue(
                        owner.getId(), recipientAccount.getId()))
                .thenReturn(Optional.of(beneficiary));
        assertEquals("Riley saved", service.destinationAlias(owner.getId(), recipientAccount.getId()));

        when(beneficiaries.existsByOwner_IdAndDestinationAccount_IdAndActiveTrue(
                        owner.getId(), "unknown-account"))
                .thenReturn(false);
        when(beneficiaries.findByOwner_IdAndDestinationAccount_IdAndActiveTrue(
                        owner.getId(), "unknown-account"))
                .thenReturn(Optional.empty());
        assertFalse(service.isAuthorizedDestination(owner.getId(), "unknown-account"));
        assertNull(service.destinationAlias(owner.getId(), "unknown-account"));
    }

    @Test
    void rejectsInvalidAliasesMissingRecipientsOwnAccountsAndNonCustomerRecipients() {
        assertEquals(
                "INVALID_BENEFICIARY",
                assertThrows(ApiException.class, () -> service.add(
                        principal, new AddBeneficiaryRequest("recipient-account", "  ")))
                        .getCode());
        assertEquals(
                "INVALID_BENEFICIARY",
                assertThrows(ApiException.class, () -> service.add(
                        principal, new AddBeneficiaryRequest("recipient-account", "\u0001X")))
                        .getCode());

        when(accounts.findById("missing")).thenReturn(Optional.empty());
        assertEquals(
                "RECIPIENT_NOT_FOUND",
                assertThrows(ApiException.class, () -> service.add(
                        principal, new AddBeneficiaryRequest("missing", "Riley")))
                        .getCode());

        when(accounts.findById(ownAccount.getId())).thenReturn(Optional.of(ownAccount));
        assertEquals(
                "OWN_ACCOUNT_BENEFICIARY",
                assertThrows(ApiException.class, () -> service.add(
                        principal, new AddBeneficiaryRequest(ownAccount.getId(), "My account")))
                        .getCode());

        when(accounts.findById(operationsAccount.getId())).thenReturn(Optional.of(operationsAccount));
        assertEquals(
                "RECIPIENT_NOT_FOUND",
                assertThrows(ApiException.class, () -> service.add(
                        principal, new AddBeneficiaryRequest(operationsAccount.getId(), "Operations")))
                        .getCode());

        when(accounts.findById(recipientAccount.getId())).thenReturn(Optional.of(recipientAccount));
        when(beneficiaries.existsByOwner_IdAndDestinationAccount_IdAndActiveTrue(
                        owner.getId(), recipientAccount.getId()))
                .thenReturn(true);
        assertEquals(
                "BENEFICIARY_EXISTS",
                assertThrows(ApiException.class, () -> service.add(
                        principal, new AddBeneficiaryRequest(recipientAccount.getId(), "Riley")))
                        .getCode());
    }

    @Test
    void removesOnlyActiveBeneficiariesOwnedByTheCurrentCustomer() {
        Beneficiary saved = new Beneficiary(
                "saved-beneficiary", owner, recipientAccount, "Riley saved");
        when(beneficiaries.findByIdAndOwner_IdAndActiveTrue(saved.getId(), owner.getId()))
                .thenReturn(Optional.of(saved));
        service.remove(principal, saved.getId());
        assertFalse(saved.isActive());

        when(beneficiaries.findByIdAndOwner_IdAndActiveTrue("missing", owner.getId()))
                .thenReturn(Optional.empty());
        assertEquals(
                "BENEFICIARY_NOT_FOUND",
                assertThrows(ApiException.class, () -> service.remove(principal, "missing"))
                        .getCode());
    }

    private BankAccount account(String id, DemoUser accountOwner) {
        return new BankAccount(
                id,
                accountOwner,
                id,
                AccountType.CHECKING,
                "**** 0001",
                "USD",
                100_000);
    }
}
