package com.portfolio.fincore.api;

import com.portfolio.fincore.domain.DemoRole;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class ApiModels {
    private ApiModels() {
    }

    public record ApiErrorBody(String code, String message, String requestId) {
    }

    public record ApiErrorResponse(ApiErrorBody error) {
    }

    public record UserResponse(String id, String displayName, DemoRole role) {
    }

    public record AuthResponse(UserResponse user) {
    }

    public record DemoProfileResponse(String demoAccount, DemoRole role) {
    }

    public record DemoProfilesResponse(List<DemoProfileResponse> profiles) {
    }

    public record DemoLoginRequest(
            @NotBlank
            @Size(max = 40)
            @Pattern(regexp = "[A-Za-z0-9._:-]+")
            String demoAccount) {
    }

    public record CsrfResponse(String token, String headerName) {
    }

    public record AccountResponse(
            String id,
            String displayName,
            String type,
            String maskedAccountNumber,
            String currency,
            long balanceMinor,
            String status,
            String ownerDisplayName) {
    }

    public record AccountListResponse(List<AccountResponse> accounts) {
    }

    public record LedgerEntryResponse(
            String id,
            String accountId,
            String transferId,
            String category,
            String description,
            long amountMinor,
            long balanceAfterMinor,
            Instant createdAt) {
    }

    public record OverviewResponse(
            long totalBalanceMinor,
            long monthlyIncomeMinor,
            long monthlySpendMinor,
            long transferCount,
            int activeAccountCount,
            List<AccountResponse> accounts,
            List<LedgerEntryResponse> recentActivity) {
    }

    public record InsightCategory(String category, long amountMinor, int transactionCount) {
    }

    public record InsightsResponse(
            long periodIncomeMinor,
            long periodSpendMinor,
            List<InsightCategory> categories) {
    }

    public record BeneficiaryResponse(
            String id,
            String alias,
            String destinationAccountId,
            String destinationAccountName,
            String maskedAccountNumber,
            Instant createdAt) {
    }

    public record BeneficiaryListResponse(List<BeneficiaryResponse> beneficiaries) {
    }

    public record RecipientOption(
            String accountId,
            String displayName,
            String accountName,
            String maskedAccountNumber) {
    }

    public record RecipientDirectoryResponse(List<RecipientOption> recipients) {
    }

    public record AddBeneficiaryRequest(
            @NotBlank @Size(max = 80) String destinationAccountId,
            @NotBlank @Size(min = 2, max = 80) String alias) {
    }

    public record TransferRequest(
            @NotBlank @Size(max = 80) String sourceAccountId,
            @NotBlank @Size(max = 80) String destinationAccountId,
            @Positive @Max(25_000_000L) long amountMinor,
            @Size(max = 120) String memo) {
    }

    public record TransferResponse(
            String id,
            String sourceAccountId,
            String sourceAccountName,
            String destinationAccountId,
            String destinationName,
            long amountMinor,
            String currency,
            String memo,
            String status,
            Instant createdAt,
            boolean duplicate) {
    }

    public record TransferListResponse(List<TransferResponse> transfers) {
    }

    public record StatementResponse(
            String accountId,
            String accountName,
            String maskedAccountNumber,
            String month,
            long openingBalanceMinor,
            long closingBalanceMinor,
            List<LedgerEntryResponse> entries) {
    }
}