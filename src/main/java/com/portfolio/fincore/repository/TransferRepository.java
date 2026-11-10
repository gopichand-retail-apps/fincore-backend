package com.portfolio.fincore.repository;

import com.portfolio.fincore.domain.Transfer;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferRepository extends JpaRepository<Transfer, String> {
    Optional<Transfer> findByOwner_IdAndIdempotencyKey(String ownerId, String idempotencyKey);

    List<Transfer> findTop100ByOwner_IdOrderByCreatedAtDesc(String ownerId);

    List<Transfer> findTop100ByOrderByCreatedAtDesc();
}