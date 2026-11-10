package com.portfolio.fincore.repository;

import com.portfolio.fincore.domain.Beneficiary;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BeneficiaryRepository extends JpaRepository<Beneficiary, String> {
    List<Beneficiary> findByOwner_IdAndActiveTrueOrderByAliasAsc(String ownerId);

    Optional<Beneficiary> findByIdAndOwner_IdAndActiveTrue(String id, String ownerId);

    Optional<Beneficiary> findByOwner_IdAndDestinationAccount_IdAndActiveTrue(String ownerId, String accountId);

    boolean existsByOwner_IdAndDestinationAccount_IdAndActiveTrue(String ownerId, String accountId);
}