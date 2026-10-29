package com.eparly.api.report.service;

import com.eparly.api.dashboard.dto.DashboardSummaryResponse;
import com.eparly.api.dashboard.dto.SpendingByCategoryResponse;
import com.eparly.api.dashboard.repository.DashboardRepository;
import com.eparly.api.dashboard.service.DashboardService;
import com.eparly.api.auth.exception.ApiException;
import com.eparly.api.report.dto.MonthlyReportResponse;
import com.eparly.api.savings.action.domain.SavingsAction;
import com.eparly.api.savings.action.repository.SavingsActionRepository;
import com.eparly.api.savings.verification.domain.SavingsActionEvidence;
import com.eparly.api.savings.verification.domain.SavingsActionVerification;
import com.eparly.api.savings.verification.repository.SavingsActionEvidenceRepository;
import com.eparly.api.savings.verification.repository.SavingsActionVerificationRepository;
import com.eparly.api.subscription.dto.SubscriptionResponse;
import com.eparly.api.subscription.service.SubscriptionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class MonthlyReportService {

    private final DashboardService dashboardService;
    private final DashboardRepository dashboardRepository;
    private final SubscriptionService subscriptionService;
    private final SavingsActionRepository savingsActionRepository;
    private final SavingsActionVerificationRepository verificationRepository;
    private final SavingsActionEvidenceRepository evidenceRepository;
    private final MonthlyReportAssembler assembler;
    private final Clock clock;

    public MonthlyReportService(
            DashboardService dashboardService,
            DashboardRepository dashboardRepository,
            SubscriptionService subscriptionService,
            SavingsActionRepository savingsActionRepository,
            SavingsActionVerificationRepository verificationRepository,
            SavingsActionEvidenceRepository evidenceRepository,
            MonthlyReportAssembler assembler,
            @Autowired(required = false) Clock clock
    ) {
        this.dashboardService = dashboardService;
        this.dashboardRepository = dashboardRepository;
        this.subscriptionService = subscriptionService;
        this.savingsActionRepository = savingsActionRepository;
        this.verificationRepository = verificationRepository;
        this.evidenceRepository = evidenceRepository;
        this.assembler = assembler;
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    @Transactional(readOnly = true)
    public MonthlyReportResponse getMonthlyReport(UUID userId, YearMonth month, UUID accountId) {
        if (month == null) {
            throw new ApiException(BAD_REQUEST, "month is required");
        }

        LocalDate startDate = month.atDay(1);
        LocalDate endDate = month.atEndOfMonth();
        DashboardSummaryResponse current = dashboardService.getSummary(userId, startDate, endDate, accountId);
        List<SpendingByCategoryResponse> categories =
                dashboardService.getSpendingByCategory(userId, startDate, endDate, accountId);
        DashboardSummaryResponse previous = dashboardService.getSummary(
                userId,
                month.minusMonths(1).atDay(1),
                month.minusMonths(1).atEndOfMonth(),
                accountId
        );
        Map<String, Object> coverage = dashboardRepository.findReportCoverage(userId, startDate, endDate, accountId);

        List<SubscriptionResponse> subscriptions = subscriptionService.list(userId, accountId, null);
        List<SavingsAction> actions = savingsActionRepository.findAllForMonthlyReport(userId, accountId);
        List<SavingsActionVerification> verifications = List.of();
        List<SavingsActionEvidence> evidences = List.of();
        if (!actions.isEmpty()) {
            List<UUID> actionIds = actions.stream().map(SavingsAction::getId).toList();
            verifications = verificationRepository.findAllForMonthlyReport(userId, actionIds);
            evidences = evidenceRepository.findAllForMonthlyReport(userId, actionIds);
        }

        return assembler.assemble(
                month,
                current,
                categories,
                previous,
                coverage,
                subscriptions,
                actions,
                verifications,
                evidences,
                clock.instant()
        );
    }
}
