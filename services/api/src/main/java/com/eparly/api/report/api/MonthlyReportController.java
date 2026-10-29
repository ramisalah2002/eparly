package com.eparly.api.report.api;

import com.eparly.api.auth.service.JwtUserId;
import com.eparly.api.report.dto.MonthlyReportResponse;
import com.eparly.api.report.service.MonthlyReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.UUID;

@RestController
@RequestMapping("/v1/reports")
@Tag(name = "Rapports financiers", description = "Synthèses mensuelles des données enregistrées")
@SecurityRequirement(name = "bearerAuth")
public class MonthlyReportController {

    private final MonthlyReportService service;

    public MonthlyReportController(MonthlyReportService service) {
        this.service = service;
    }

    @GetMapping("/monthly")
    @Operation(summary = "Rapport financier mensuel",
            description = "Synthèse des transactions, abonnements et actions enregistrés. "
                    + "Ne constitue ni un solde bancaire ni une preuve de couverture complète des imports.")
    @ApiResponse(responseCode = "200", description = "Rapport mensuel")
    @ApiResponse(responseCode = "400", description = "Mois invalide (format YYYY-MM)")
    @ApiResponse(responseCode = "401", description = "JWT absent ou invalide")
    @ApiResponse(responseCode = "404", description = "Compte introuvable ou appartenant à un autre utilisateur")
    public MonthlyReportResponse monthlyReport(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
            @RequestParam(required = false) UUID accountId
    ) {
        return service.getMonthlyReport(JwtUserId.from(jwt), month, accountId);
    }
}
