package com.portfolio.fincore.api;

import com.portfolio.fincore.api.ApiModels.AccountListResponse;
import com.portfolio.fincore.api.ApiModels.AccountResponse;
import com.portfolio.fincore.api.ApiModels.AddBeneficiaryRequest;
import com.portfolio.fincore.api.ApiModels.BeneficiaryListResponse;
import com.portfolio.fincore.api.ApiModels.BeneficiaryResponse;
import com.portfolio.fincore.api.ApiModels.InsightsResponse;
import com.portfolio.fincore.api.ApiModels.LedgerEntryResponse;
import com.portfolio.fincore.api.ApiModels.OverviewResponse;
import com.portfolio.fincore.api.ApiModels.RecipientDirectoryResponse;
import com.portfolio.fincore.api.ApiModels.StatementResponse;
import com.portfolio.fincore.api.ApiModels.TransferListResponse;
import com.portfolio.fincore.api.ApiModels.TransferRequest;
import com.portfolio.fincore.api.ApiModels.TransferResponse;
import com.portfolio.fincore.domain.DemoPrincipal;
import com.portfolio.fincore.service.AccountService;
import com.portfolio.fincore.service.BeneficiaryService;
import com.portfolio.fincore.service.InsightsService;
import com.portfolio.fincore.service.TransferService;
import jakarta.validation.Valid;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class BankingController {
    private final AccountService accounts;
    private final BeneficiaryService beneficiaries;
    private final TransferService transfers;
    private final InsightsService insights;

    public BankingController(
            AccountService accounts,
            BeneficiaryService beneficiaries,
            TransferService transfers,
            InsightsService insights) {
        this.accounts = accounts;
        this.beneficiaries = beneficiaries;
        this.transfers = transfers;
        this.insights = insights;
    }

    @GetMapping("/overview")
    public OverviewResponse overview(@AuthenticationPrincipal DemoPrincipal principal) {
        return accounts.overview(principal);
    }

    @GetMapping("/accounts")
    public AccountListResponse accountList(@AuthenticationPrincipal DemoPrincipal principal) {
        return new AccountListResponse(accounts.listAccounts(principal));
    }

    @GetMapping("/accounts/{accountId}")
    public AccountResponse account(
            @AuthenticationPrincipal DemoPrincipal principal,
            @PathVariable String accountId) {
        return accounts.getAccount(principal, accountId);
    }

    @GetMapping("/accounts/{accountId}/transactions")
    public List<LedgerEntryResponse> transactions(
            @AuthenticationPrincipal DemoPrincipal principal,
            @PathVariable String accountId,
            @RequestParam(defaultValue = "50") int limit) {
        return accounts.listTransactions(principal, accountId, limit);
    }

    @GetMapping("/statements")
    public StatementResponse statement(
            @AuthenticationPrincipal DemoPrincipal principal,
            @RequestParam String accountId,
            @RequestParam String month) {
        return accounts.statement(principal, accountId, parseMonth(month));
    }

    @GetMapping("/insights")
    public InsightsResponse insights(
            @AuthenticationPrincipal DemoPrincipal principal,
            @RequestParam String accountId) {
        return insights.forAccount(principal, accountId);
    }

    @GetMapping("/beneficiaries")
    public BeneficiaryListResponse beneficiaryList(@AuthenticationPrincipal DemoPrincipal principal) {
        return new BeneficiaryListResponse(beneficiaries.list(principal));
    }

    @GetMapping("/beneficiaries/directory")
    public RecipientDirectoryResponse recipientDirectory(@AuthenticationPrincipal DemoPrincipal principal) {
        return new RecipientDirectoryResponse(beneficiaries.directory(principal));
    }

    @PostMapping("/beneficiaries")
    @ResponseStatus(HttpStatus.CREATED)
    public BeneficiaryResponse addBeneficiary(
            @AuthenticationPrincipal DemoPrincipal principal,
            @Valid @RequestBody AddBeneficiaryRequest request) {
        return beneficiaries.add(principal, request);
    }

    @DeleteMapping("/beneficiaries/{beneficiaryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeBeneficiary(
            @AuthenticationPrincipal DemoPrincipal principal,
            @PathVariable String beneficiaryId) {
        beneficiaries.remove(principal, beneficiaryId);
    }

    @GetMapping("/transfers")
    public TransferListResponse transferList(@AuthenticationPrincipal DemoPrincipal principal) {
        return new TransferListResponse(transfers.list(principal));
    }

    @PostMapping("/transfers")
    public TransferResponse createTransfer(
            @AuthenticationPrincipal DemoPrincipal principal,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {
        return transfers.create(principal, request, idempotencyKey);
    }

    private YearMonth parseMonth(String value) {
        try {
            return YearMonth.parse(value);
        } catch (DateTimeParseException exception) {
            throw ApiException.badRequest("INVALID_MONTH", "Month must use the YYYY-MM format.");
        }
    }
}