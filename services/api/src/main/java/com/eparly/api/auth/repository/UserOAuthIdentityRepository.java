package com.eparly.api.auth.repository;

import com.eparly.api.auth.domain.UserOAuthIdentity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserOAuthIdentityRepository extends JpaRepository<UserOAuthIdentity, UUID> {
    @Query("""
            SELECT identity FROM UserOAuthIdentity identity JOIN FETCH identity.user
            WHERE identity.provider = :provider AND identity.providerSubject = :subject
            """)
    Optional<UserOAuthIdentity> findByProviderAndSubject(@Param("provider") String provider,
                                                         @Param("subject") String subject);

    boolean existsByUser_IdAndProvider(UUID userId, String provider);
}
