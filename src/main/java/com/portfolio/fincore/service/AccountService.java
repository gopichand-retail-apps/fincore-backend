package com.portfolio.fincore.service;

import com.portfolio.fincore.api.ApiException;
import com.portfolio.fincore.api.ApiModels.AccountResponse;
import com.portfolio.fincore.api.ApiModels.InsightCategory;
import com.portfolio.fincore.api.ApiModels.LedgerEntryResponse;
import com.portfolio.fincore.api.ApiModels.OverviewResponse;
import com.portfolio.fincore.api.ApiModels.StatementResponse;
import com.portfolio.fincore.domain.BankAccount;
import com.portfolio.fincore.domain.DemoPrincipal;
import com.portfolio.fincore.domain.DemoRole;
import com.portfolio.fincore.domain.LedgerCategory;
import com.portfolio.fincore.domain.LedgerEntry;
import com.portfolio.fincore.repository.BankAccountRepository;
import com.portfolio.fincore.repository.LedgerEntryRepository;
import com.portfolio.fincore.repository.TransferRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
    private final BankAccountRepository accounts;
    private final LedgerEntryRepository ledgerEntries;
    private final TransferRepository transfers;

    public AccountService(
            BankAccountRepository accounts,
            LedgerEntryRepository ledgerEntries,
            TransferRepository transfers) {
        this.accounts = accounts;
        this.ledgerEntries = ledgerEntries;
        this.transfers = transfers;
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> listAccounts(DemoPrincipal principal) {
        if (principal.role() == DemoRole.CUSTOMER) {
            return accounts.findByOwner_IdOrderByDisplayNameAsc(principal.userId()).stream()
                    .map(account -> toAccountResponse(account, false))
                    .toList();
        }
        return accounts.findAllWithOwner().stream()
                .map(account -> toAccountResponse(account, true))
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccount(DemoPrincipal principal, String accountId) {
        BankAccount account = findVisibleAccount(principal, accountId);
        return toAccountResponse(account, principal.role() != DemoRole.CUSTOMER);
    }

    @Transactional(readOnly = true)
    public List<LedgerEntryResponse> listTransactions(DemoPrincipal principal, String accountId, int limit) {
        findVisibleAccount(principal, accountId);
        return ledgerEntries.findTop100ByAccount_IdInOrderByCreatedAtDesc(List.of(accountId)).stream()
                .limit(Math.max(1, Math.min(limit, 100)))
                .map(this::toLedgerResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public StatementResponse statement(DemoPrincipal principal, String accountId, YearMonth month) {
        BankAccount account = findVisibleAccount(principal, accountId);
        Instant start = month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = month.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        List<LedgerEntry> entries = ledgerEntries
                .findByAccount_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
                        accountId, start, end);
        long opening = ledgerEntries.sumBefore(accountId, start);
        long periodNet = entries.stream().mapToLong(LedgerEntry::getAmountMinor).reduce(0L, Math::addExact);

        return new StatementResponse(
                account.getId(),
                account.getDisplayName(),
                account.getMaskedAccountNumber(),
                month.toString(),
                opening,
                Math.addExact(opening, periodNet),
                entries.stream().map(this::toLedgerResponse).toList());
    }

    @Transactional(readOnly = true)
    public OverviewResponse overview(DemoPrincipal principal) {
        List<BankAccount> visibleAccounts = visibleAccounts(principal);
        List<String> accountIds = visibleAccounts.stream().map(BankAccount::getId).toList();
        long totalBalance = visibleAccounts.stream().mapToLong(BankAccount::getBalanceMinor).reduce(0L, Math::addExact);
        if (accountIds.isEmpty()) {
            return new OverviewResponse(0, 0, 0, 0, 0, List.of(), List.of());
        }

        Instant monthStart = YearMonth.from(LocalDate.now(ZoneOffset.UTC))
                .atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        List<LedgerEntry> monthEntries = ledgerEntries
                .findByAccount_IdInAndCreatedAtAfterOrderByCreatedAtDesc(accountIds, monthStart);
        long income = sumPositive(monthEntries);
        long spending = sumSpending(monthEntries);
        long transferCount = uniqueTransferCount(monthEntries);
        List<LedgerEntryResponse> recent = recentActivity(accountIds);
        int activeAccountCount = (int) visibleAccounts.stream()
            .filter(account -> account.getStatus().name().equals("ACTIVE"))
            .count();
        List<AccountResponse> accountResponses = visibleAccounts.stream()
            .map(account -> toAccountResponse(account, principal.role() != DemoRole.CUSTOMER))
            .toList();

        return new OverviewResponse(
                totalBalance,
                income,
                spending,
                transferCount,
                activeAccountCount,
                accountResponses,
                recent);
    }

    @Transactional(readOnly = true)
    public List<LedgerEntry> entriesSince(DemoPrincipal principal, String accountId, Instant start) {
        findVisibleAccount(principal, accountId);
        return ledgerEntries.findByAccount_IdInAndCreatedAtAfterOrderByCreatedAtDesc(List.of(accountId), start);
    }

    @Transactional(readOnly = true)
    public List<LedgerEntry> entriesForCustomerAccounts(DemoPrincipal principal, Instant start) {
        requireCustomer(principal);
        List<String> accountIds = accounts.findByOwner_IdOrderByDisplayNameAsc(principal.userId()).stream()
                .map(BankAccount::getId)
                .toList();
        if (accountIds.isEmpty()) return List.of();
        return ledgerEntries.findByAccount_IdInAndCreatedAtAfterOrderByCreatedAtDesc(
            accountIds, start);
    }

    public BankAccount findVisibleAccount(DemoPrincipal principal, String accountId) {
        if (principal.role() == DemoRole.CUSTOMER) {
            return accounts.findByIdAndOwner_Id(accountId, principal.userId())
                    .orElseThrow(() -> ApiException.notFound("ACCOUNT_NOT_FOUND", "Account was not found."));
        }
        return accounts.findById(accountId)
                .orElseThrow(() -> ApiException.notFound("ACCOUNT_NOT_FOUND", "Account was not found."));
    }

    public void requireCustomer(DemoPrincipal principal) {
        if (principal.role() != DemoRole.CUSTOMER) {
            throw ApiException.forbidden(
                    "CUSTOMER_ROLE_REQUIRED",
                    "This action is available to customer profiles only.");
        }
    }

    private List<BankAccount> visibleAccounts(DemoPrincipal principal) {
        if (principal.role() == DemoRole.CUSTOMER) {
            return accounts.findByOwner_IdOrderByDisplayNameAsc(principal.userId());
        }
        return accounts.findAllWithOwner();
    }

    private List<LedgerEntryResponse> recentActivity(Collection<String> accountIds) {
        return ledgerEntries.findTop100ByAccount_IdInOrderByCreatedAtDesc(accountIds).stream()
                .limit(8)
                .map(this::toLedgerResponse)
                .toList();
    }

    private long sumPositive(List<LedgerEntry> entries) {
        return entries.stream()
                .filter(entry -> entry.getAmountMinor() > 0 && entry.getCategory() == LedgerCategory.INCOME)
                .mapToLong(LedgerEntry::getAmountMinor)
                .reduce(0L, Math::addExact);
    }

    private long sumSpending(List<LedgerEntry> entries) {
        return entries.stream()
                .filter(entry -> entry.getAmountMinor() < 0 && entry.getCategory() != LedgerCategory.TRANSFER)
                .mapToLong(entry -> Math.negateExact(entry.getAmountMinor()))
                .reduce(0L, Math::addExact);
    }

    private long uniqueTransferCount(List<LedgerEntry> entries) {
        Set<String> transferIds = new HashSet<>();
        for (LedgerEntry entry : entries) {
            if (entry.getTransfer() != null) transferIds.add(entry.getTransfer().getId());
        }
        return transferIds.size();
    }

    private AccountResponse toAccountResponse(BankAccount account, boolean includeOwner) {
        String ownerName = includeOwner ? account.getOwner().getDisplayName() : null;
        return new AccountResponse(
                account.getId(),
                account.getDisplayName(),
                account.getAccountType().name(),
                account.getMaskedAccountNumber(),
                account.getCurrency(),
                account.getBalanceMinor(),
                account.getStatus().name(),
                ownerName);
    }

    private LedgerEntryResponse toLedgerResponse(LedgerEntry entry) {
        return new LedgerEntryResponse(
                entry.getId(),
                entry.getAccount().getId(),
                entry.getTransfer() == null ? null : entry.getTransfer().getId(),
                entry.getCategory().name(),
                entry.getDescription(),
                entry.getAmountMinor(),
                entry.getBalanceAfterMinor(),
                entry.getCreatedAt());
    }
}