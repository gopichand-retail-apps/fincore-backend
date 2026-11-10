package com.portfolio.fincore.service;

import static net.logstash.logback.argument.StructuredArguments.keyValue;

import com.portfolio.fincore.api.ApiException;
import com.portfolio.fincore.api.ApiModels.TransferRequest;
import com.portfolio.fincore.api.ApiModels.TransferResponse;
import com.portfolio.fincore.domain.AuditEvent;
import com.portfolio.fincore.domain.BankAccount;
import com.portfolio.fincore.domain.DemoPrincipal;
import com.portfolio.fincore.domain.DemoRole;
import com.portfolio.fincore.domain.DemoUser;
import com.portfolio.fincore.domain.LedgerCategory;
import com.portfolio.fincore.domain.LedgerEntry;
import com.portfolio.fincore.domain.Transfer;
import com.portfolio.fincore.repository.AuditEventRepository;
import com.portfolio.fincore.repository.BankAccountRepository;
import com.portfolio.fincore.repository.DemoUserRepository;
import com.portfolio.fincore.repository.LedgerEntryRepository;
import com.portfolio.fincore.repository.TransferRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.logstash.logback.argument.StructuredArguments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferService {
    private static final Logger LOGGER = LoggerFactory.getLogger(TransferService.class);
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9._:-]{16,128}");

    private final AccountService accountService;
    private final BeneficiaryService beneficiaryService;
    private final BankAccountRepository accounts;
    private final DemoUserRepository users;
    private final TransferRepository transfers;
    private final LedgerEntryRepository ledgerEntries;
    private final AuditEventRepository auditEvents;

    public TransferService(
            AccountService accountService,
            BeneficiaryService beneficiaryService,
            BankAccountRepository accounts,
            DemoUserRepository users,
            TransferRepository transfers,
            LedgerEntryRepository ledgerEntries,
            AuditEventRepository auditEvents) {
        this.accountService = accountService;
        this.beneficiaryService = beneficiaryService;
        this.accounts = accounts;
        this.users = users;
        this.transfers = transfers;
        this.ledgerEntries = ledgerEntries;
        this.auditEvents = auditEvents;
    }

    @Transactional
    public TransferResponse create(DemoPrincipal principal, TransferRequest request, String idempotencyKey) {
        accountService.requireCustomer(principal);
        String key = validateIdempotencyKey(idempotencyKey);
        String memo = normalizeMemo(request.memo());
        String requestHash = requestHash(request, memo);
        DemoUser owner = users.lockById(principal.userId())
                .orElseThrow(() -> ApiException.notFound("CUSTOMER_NOT_FOUND", "Customer was not found."));

        Transfer existing = transfers.findByOwner_IdAndIdempotencyKey(owner.getId(), key).orElse(null);
        if (existing != null) return existingResponse(existing, requestHash);

        List<String> accountIds = List.of(request.sourceAccountId(), request.destinationAccountId()).stream()
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
        Map<String, BankAccount> lockedAccounts = accounts.lockAllByIds(accountIds).stream()
                .collect(Collectors.toMap(BankAccount::getId, Function.identity()));
        BankAccount source = requireAccount(lockedAccounts, request.sourceAccountId());
        BankAccount destination = requireAccount(lockedAccounts, request.destinationAccountId());

        if (!source.getOwner().getId().equals(owner.getId())) {
            throw ApiException.notFound("ACCOUNT_NOT_FOUND", "Account was not found.");
        }
        if (!destination.getOwner().getId().equals(owner.getId())
                && !beneficiaryService.isAuthorizedDestination(owner.getId(), destination.getId())) {
            throw ApiException.notFound("RECIPIENT_NOT_FOUND", "Recipient was not found.");
        }
        if (source.getId().equals(destination.getId())) {
            throw ApiException.badRequest("SAME_ACCOUNT_TRANSFER", "Choose two different accounts.");
        }
        if (!source.getCurrency().equals(destination.getCurrency())) {
            throw ApiException.badRequest("CURRENCY_MISMATCH", "Both accounts must use the same currency.");
        }
        if (source.getStatus().name().equals("FROZEN") || destination.getStatus().name().equals("FROZEN")) {
            throw ApiException.conflict("ACCOUNT_UNAVAILABLE", "One of these demo accounts is unavailable.");
        }
        if (source.getBalanceMinor() < request.amountMinor()) {
            throw ApiException.conflict(
                    "INSUFFICIENT_FUNDS",
                    "The source account does not have enough available funds.");
        }

        long sourceBalance = Math.subtractExact(source.getBalanceMinor(), request.amountMinor());
        long destinationBalance;
        try {
            destinationBalance = Math.addExact(destination.getBalanceMinor(), request.amountMinor());
        } catch (ArithmeticException exception) {
            throw ApiException.conflict("BALANCE_LIMIT", "The destination account cannot accept this amount.");
        }

        source.setBalanceMinor(sourceBalance);
        destination.setBalanceMinor(destinationBalance);
        String transferId = UUID.randomUUID().toString();
        Transfer transfer = transfers.save(new Transfer(
                transferId, owner, source, destination, request.amountMinor(), memo, key, requestHash));
        Instant postedAt = transfer.getCreatedAt();
        ledgerEntries.save(new LedgerEntry(
                UUID.randomUUID().toString(),
                source,
                transfer,
                Math.negateExact(request.amountMinor()),
                sourceBalance,
                LedgerCategory.TRANSFER,
                "Transfer to " + destinationName(owner.getId(), destination),
                postedAt));
        ledgerEntries.save(new LedgerEntry(
                UUID.randomUUID().toString(),
                destination,
                transfer,
                request.amountMinor(),
                destinationBalance,
                LedgerCategory.TRANSFER,
                "Transfer from " + source.getDisplayName(),
                postedAt));
        auditEvents.save(new AuditEvent(
                UUID.randomUUID().toString(), owner, "fincore.transfer.completed", "transfer", transferId));
        LOGGER.atInfo()
                .addKeyValue("eventName", "fincore.transfer.completed")
                .addKeyValue("outcome", "success")
                .log("Synthetic internal transfer completed");

        return toResponse(transfer, false);
    }

    @Transactional(readOnly = true)
    public List<TransferResponse> list(DemoPrincipal principal) {
        List<Transfer> visible = principal.role() == DemoRole.CUSTOMER
                ? transfers.findTop100ByOwner_IdOrderByCreatedAtDesc(principal.userId())
                : transfers.findTop100ByOrderByCreatedAtDesc();
        return visible.stream().map(transfer -> toResponse(transfer, false)).toList();
    }

    private TransferResponse existingResponse(Transfer existing, String requestHash) {
        if (!existing.getRequestHash().equals(requestHash)) {
            throw ApiException.conflict("IDEMPOTENCY_CONFLICT", "That request key was used for a different transfer.");
        }
        return toResponse(existing, true);
    }

    private BankAccount requireAccount(Map<String, BankAccount> lockedAccounts, String id) {
        BankAccount account = lockedAccounts.get(id);
        if (account == null) {
            throw ApiException.notFound("ACCOUNT_NOT_FOUND", "Account was not found.");
        }
        return account;
    }

    private String destinationName(String ownerId, BankAccount destination) {
        String alias = beneficiaryService.destinationAlias(ownerId, destination.getId());
        return alias == null ? destination.getDisplayName() : alias;
    }

    private TransferResponse toResponse(Transfer transfer, boolean duplicate) {
        String alias = beneficiaryService.destinationAlias(
                transfer.getOwner().getId(), transfer.getDestinationAccount().getId());
        String destinationName = alias == null
                ? transfer.getDestinationAccount().getDisplayName()
                : alias;
        return new TransferResponse(
                transfer.getId(),
                transfer.getSourceAccount().getId(),
                transfer.getSourceAccount().getDisplayName(),
                transfer.getDestinationAccount().getId(),
                destinationName,
                transfer.getAmountMinor(),
                transfer.getCurrency(),
                transfer.getMemo(),
                transfer.getStatus().name(),
                transfer.getCreatedAt(),
                duplicate);
    }

    private String validateIdempotencyKey(String key) {
        if (key == null || !IDEMPOTENCY_KEY.matcher(key).matches()) {
            throw ApiException.badRequest("IDEMPOTENCY_KEY_REQUIRED", "Provide a valid Idempotency-Key header.");
        }
        return key;
    }

    private String normalizeMemo(String memo) {
        String normalized = memo == null ? "Internal transfer" : memo.strip();
        if (normalized.isEmpty()) normalized = "Internal transfer";
        if (normalized.codePoints().anyMatch(Character::isISOControl)) {
            throw ApiException.badRequest("INVALID_MEMO", "Memo must not contain control characters.");
        }
        return normalized;
    }

    private String requestHash(TransferRequest request, String memo) {
        String material = request.sourceAccountId().length() + ":" + request.sourceAccountId()
                + request.destinationAccountId().length() + ":" + request.destinationAccountId()
                + request.amountMinor() + ":" + memo.length() + ":" + memo;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
        }
    }
}