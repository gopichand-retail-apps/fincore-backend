package com.portfolio.fincore.repository;

import com.portfolio.fincore.domain.DemoUser;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DemoUserRepository extends JpaRepository<DemoUser, String> {
    Optional<DemoUser> findByDemoKey(String demoKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select demoUser from DemoUser demoUser where demoUser.id = :userId")
    Optional<DemoUser> lockById(@Param("userId") String userId);
}