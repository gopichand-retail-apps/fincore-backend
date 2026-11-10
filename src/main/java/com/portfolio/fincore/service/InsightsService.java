package com.portfolio.fincore.service;

import com.portfolio.fincore.api.ApiModels.InsightCategory;
import com.portfolio.fincore.api.ApiModels.InsightsResponse;
import com.portfolio.fincore.domain.DemoPrincipal;
import com.portfolio.fincore.domain.LedgerCategory;
import com.portfolio.fincore.domain.LedgerEntry;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InsightsService {
    private final AccountService accounts;

    public InsightsService(AccountService accounts) {
        this.accounts = accounts;
    }

    @Transactional(readOnly = true)
    public InsightsResponse forAccount(DemoPrincipal principal, String accountId) {
        Instant start = Instant.now().minus(30, ChronoUnit.DAYS);
        List<LedgerEntry> entries = accounts.entriesSince(principal, accountId, start);
        long income = 0;
        long spend = 0;
        Map<LedgerCategory, long[]> totals = new EnumMap<>(LedgerCategory.class);

        for (LedgerEntry entry : entries) {
            long amount = entry.getAmountMinor();
            if (amount > 0 && entry.getCategory() == LedgerCategory.INCOME) {
                income = Math.addExact(income, amount);
            }
            if (amount >= 0 || entry.getCategory() == LedgerCategory.TRANSFER) continue;
            long absoluteAmount = Math.negateExact(amount);
            spend = Math.addExact(spend, absoluteAmount);
            long[] categoryTotal = totals.computeIfAbsent(entry.getCategory(), ignored -> new long[2]);
            categoryTotal[0] = Math.addExact(categoryTotal[0], absoluteAmount);
            categoryTotal[1]++;
        }

        List<InsightCategory> categories = totals.entrySet().stream()
            .map(entry -> new InsightCategory(
                entry.getKey().name(), entry.getValue()[0], (int) entry.getValue()[1]))
                .sorted(Comparator.comparingLong(InsightCategory::amountMinor).reversed())
                .toList();
        return new InsightsResponse(income, spend, categories);
    }
}