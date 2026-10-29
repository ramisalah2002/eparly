package com.eparly.api.savings.action.repository;

import com.eparly.api.savings.action.domain.SavingsActionHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SavingsActionHistoryRepository extends JpaRepository<SavingsActionHistory, UUID> {

    List<SavingsActionHistory> findAllByActionIdOrderByCreatedAtAsc(UUID actionId);
}
