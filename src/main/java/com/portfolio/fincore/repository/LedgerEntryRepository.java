package com.portfolio.fincore.repository;

import com.portfolio.fincore.domain.LedgerEntry;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, String> {
    List<LedgerEntry> findTop100ByAccount_IdInOrderByCreatedAtDesc(Collection<String> accountIds);

    List<LedgerEntry> findByAccount_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
            String accountId,
            Instant start,
            Instant end);

    List<LedgerEntry> findByAccount_IdInAndCreatedAtAfterOrderByCreatedAtDesc(
            Collection<String> accountIds,
            Instant start);

    @Query("select coalesce(sum(entry.amountMinor), 0) from LedgerEntry entry "
            + "where entry.account.id = :accountId and entry.createdAt < :start")
    long sumBefore(@Param("accountId") String accountId, @Param("start") Instant start);
}