package com.portfolio.fincore.config;

import com.portfolio.fincore.domain.AccountType;
import com.portfolio.fincore.domain.BankAccount;
import com.portfolio.fincore.domain.Beneficiary;
import com.portfolio.fincore.domain.DemoRole;
import com.portfolio.fincore.domain.DemoUser;
import com.portfolio.fincore.domain.LedgerCategory;
import com.portfolio.fincore.domain.LedgerEntry;
import com.portfolio.fincore.repository.BankAccountRepository;
import com.portfolio.fincore.repository.BeneficiaryRepository;
import com.portfolio.fincore.repository.DemoUserRepository;
import com.portfolio.fincore.repository.LedgerEntryRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile({"local", "test"})
public class DemoSeeder implements CommandLineRunner {
    private static final String AVERY_ID = "demo-customer-avery";
    private static final String RILEY_ID = "demo-customer-riley";

    private final DemoUserRepository users;
    private final BankAccountRepository accounts;
    private final BeneficiaryRepository beneficiaries;
    private final LedgerEntryRepository entries;

    public DemoSeeder(
            DemoUserRepository users,
            BankAccountRepository accounts,
            BeneficiaryRepository beneficiaries,
            LedgerEntryRepository entries) {
        this.users = users;
        this.accounts = accounts;
        this.beneficiaries = beneficiaries;
        this.entries = entries;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (users.existsById(AVERY_ID)) return;

        DemoUser avery = users.save(new DemoUser(AVERY_ID, "customer-avery", "Avery Quinn", DemoRole.CUSTOMER));
        DemoUser riley = users.save(new DemoUser(
                RILEY_ID, "customer-riley", "Riley Morgan", DemoRole.CUSTOMER));
        users.save(new DemoUser("demo-operations", "operations", "Morgan Reed", DemoRole.OPERATIONS));
        users.save(new DemoUser("demo-auditor", "auditor", "Casey Lane", DemoRole.AUDITOR));

        BankAccount averyChecking = seedAccount(
                avery,
                "account-avery-checking",
                "Everyday checking",
                AccountType.CHECKING,
                "**** 1042",
                List.of(
                        new SeedEntry(320_000, LedgerCategory.INCOME, "Monthly pay", 25),
                        new SeedEntry(-125_000, LedgerCategory.HOUSING, "Rent", 22),
                        new SeedEntry(-26_340, LedgerCategory.GROCERIES, "Market groceries", 12),
                        new SeedEntry(-4_825, LedgerCategory.TRANSPORT, "Transit pass", 7),
                        new SeedEntry(-13_219, LedgerCategory.UTILITIES, "Utility bill", 4),
                        new SeedEntry(-6_450, LedgerCategory.DINING, "Cafe lunch", 2),
                        new SeedEntry(-2_999, LedgerCategory.OTHER, "Bookshop", 1)));
        seedAccount(
                avery,
                "account-avery-savings",
                "Rainy day savings",
                AccountType.SAVINGS,
                "**** 9028",
                List.of(
                        new SeedEntry(1_200_000, LedgerCategory.INCOME, "Opening deposit", 40),
                        new SeedEntry(60_000, LedgerCategory.INCOME, "Monthly savings deposit", 8)));
        seedAccount(
                riley,
                "account-riley-checking",
                "Everyday checking",
                AccountType.CHECKING,
                "**** 3176",
                List.of(
                        new SeedEntry(260_000, LedgerCategory.INCOME, "Monthly pay", 25),
                        new SeedEntry(-105_000, LedgerCategory.HOUSING, "Rent", 20),
                        new SeedEntry(-22_500, LedgerCategory.GROCERIES, "Market groceries", 9),
                        new SeedEntry(-3_200, LedgerCategory.TRANSPORT, "Transit pass", 3)));
        BankAccount rileySavings = seedAccount(
                riley,
                "account-riley-savings",
                "Weekend savings",
                AccountType.SAVINGS,
                "**** 6401",
                List.of(
                        new SeedEntry(750_000, LedgerCategory.INCOME, "Opening deposit", 42),
                        new SeedEntry(15_000, LedgerCategory.INCOME, "Monthly savings deposit", 10)));

        beneficiaries.save(new Beneficiary(
                UUID.randomUUID().toString(),
                avery,
                rileySavings,
                "Riley savings"));
        beneficiaries.save(new Beneficiary(
                UUID.randomUUID().toString(),
                riley,
                averyChecking,
                "Avery checking"));
    }

    private BankAccount seedAccount(
            DemoUser owner,
            String id,
            String displayName,
            AccountType type,
            String maskedNumber,
            List<SeedEntry> seedEntries) {
        BankAccount account = accounts.save(new BankAccount(
                id, owner, displayName, type, maskedNumber, "USD", 0));
        long balance = 0;

        for (SeedEntry seedEntry : seedEntries) {
            balance = Math.addExact(balance, seedEntry.amountMinor());
            if (balance < 0) throw new IllegalStateException("Seed ledger cannot overdraw an account");
            account.setBalanceMinor(balance);
            entries.save(new LedgerEntry(
                    UUID.randomUUID().toString(),
                    account,
                    null,
                    seedEntry.amountMinor(),
                    balance,
                    seedEntry.category(),
                    seedEntry.description(),
                    Instant.now().minus(seedEntry.daysAgo(), ChronoUnit.DAYS)));
        }
        return account;
    }

    private record SeedEntry(long amountMinor, LedgerCategory category, String description, long daysAgo) {
    }
}