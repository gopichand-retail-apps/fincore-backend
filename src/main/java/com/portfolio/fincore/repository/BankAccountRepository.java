package com.portfolio.fincore.repository;

import com.portfolio.fincore.domain.BankAccount;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BankAccountRepository extends JpaRepository<BankAccount, String> {
    List<BankAccount> findByOwner_IdOrderByDisplayNameAsc(String ownerId);

    List<BankAccount> findByOwner_IdInOrderByDisplayNameAsc(Collection<String> ownerIds);

    Optional<BankAccount> findByIdAndOwner_Id(String accountId, String ownerId);

    List<BankAccount> findByIdInOrderByIdAsc(Collection<String> accountIds);

    @Query("select account from BankAccount account join fetch account.owner order by account.displayName")
    List<BankAccount> findAllWithOwner();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from BankAccount account where account.id in :accountIds order by account.id")
    List<BankAccount> lockAllByIds(@Param("accountIds") Collection<String> accountIds);
}