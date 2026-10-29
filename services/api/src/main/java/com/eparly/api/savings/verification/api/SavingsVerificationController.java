package com.eparly.api.savings.verification.api;

import com.eparly.api.savings.verification.domain.EvidenceRole;
import com.eparly.api.savings.verification.dto.AddEvidenceRequest;
import com.eparly.api.savings.verification.dto.EvaluateVerificationRequest;
import com.eparly.api.savings.verification.dto.EvidenceResponse;
import com.eparly.api.savings.verification.dto.VerificationCandidatesResponse;
import com.eparly.api.savings.verification.dto.VerificationDetailsResponse;
import com.eparly.api.savings.verification.service.SavingsVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/savings/actions/{id}/verification")
@Tag(name = "Vérification des économies", description = "Rapprochement et vérification financière des économies à partir des transactions bancaires (étape 11B.1)")
@SecurityRequirement(name = "bearerAuth")
public class SavingsVerificationController {

    private static final int MAX_CANDIDATE_PAGE_SIZE = 100;

    private final SavingsVerificationService service;

    public SavingsVerificationController(SavingsVerificationService service) {
        this.service = service;
    }

    @GetMapping("/candidates")
    @Operation(summary = "Lister les transactions candidates",
            description = "Retourne des transactions bancaires de l'utilisateur pouvant correspondre à l'ancien ou au nouveau fournisseur, avec score et justifications heuristiques.")
    @ApiResponse(responseCode = "200", description = "Suggestions candidates retournées", content = @Content(
            schema = @Schema(implementation = VerificationCandidatesResponse.class)))
    @ApiResponse(responseCode = "401", description = "JWT absent ou invalide")
    @ApiResponse(responseCode = "404", description = "Action introuvable")
    public VerificationCandidatesResponse getCandidates(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("id") UUID actionId,
            @RequestParam(name = "role", required = false) EvidenceRole role,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        return service.getCandidates(jwt, actionId, role,
                PageRequest.of(Math.max(0, page), Math.min(MAX_CANDIDATE_PAGE_SIZE, Math.max(1, size))));
    }

    @PostMapping("/evidence")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Associer une transaction comme preuve",
            description = "Permet à l'utilisateur de confirmer le rattachement d'une transaction bancaire réelle à l'action d'économie (provenance USER_CONFIRMED).")
    @ApiResponse(responseCode = "201", description = "Preuve associée avec succès", content = @Content(
            schema = @Schema(implementation = EvidenceResponse.class)))
    @ApiResponse(responseCode = "400", description = "Transaction non éligible (virement, remboursement, devise incompatible)")
    @ApiResponse(responseCode = "401", description = "JWT absent ou invalide")
    @ApiResponse(responseCode = "404", description = "Action ou transaction introuvable")
    @ApiResponse(responseCode = "409", description = "Transaction déjà associée à une preuve financière")
    public EvidenceResponse addEvidence(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("id") UUID actionId,
            @Valid @RequestBody AddEvidenceRequest request
    ) {
        return service.addEvidence(jwt, actionId, request);
    }

    @DeleteMapping("/evidence/{evidenceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Retirer une preuve d'association",
            description = "Supprime le lien entre la transaction bancaire et l'action d'économie sans supprimer la transaction bancaire source.")
    @ApiResponse(responseCode = "204", description = "Association supprimée")
    @ApiResponse(responseCode = "401", description = "JWT absent ou invalide")
    @ApiResponse(responseCode = "404", description = "Action ou preuve introuvable")
    public void removeEvidence(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("id") UUID actionId,
            @PathVariable("evidenceId") UUID evidenceId
    ) {
        service.removeEvidence(jwt, actionId, evidenceId);
    }

    @GetMapping
    @Operation(summary = "Obtenir le détail de la vérification financière",
            description = "Retourne le statut de vérification, les transactions observées avant et après, les montants réels, les informations manquantes et contradictions éventuelles.")
    @ApiResponse(responseCode = "200", description = "Détail de la vérification retourné", content = @Content(
            schema = @Schema(implementation = VerificationDetailsResponse.class)))
    @ApiResponse(responseCode = "401", description = "JWT absent ou invalide")
    @ApiResponse(responseCode = "404", description = "Action introuvable")
    public VerificationDetailsResponse getVerificationDetails(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("id") UUID actionId
    ) {
        return service.getVerificationDetails(jwt, actionId);
    }

    @PostMapping("/evaluate")
    @Operation(summary = "Réévaluer le niveau de preuve",
            description = "Recalcule de manière idempotente le niveau de vérification financière de l'action à partir des preuves et données bancaires disponibles.")
    @ApiResponse(responseCode = "200", description = "Réévaluation effectuée", content = @Content(
            schema = @Schema(implementation = VerificationDetailsResponse.class)))
    @ApiResponse(responseCode = "401", description = "JWT absent ou invalide")
    @ApiResponse(responseCode = "404", description = "Action introuvable")
    public VerificationDetailsResponse evaluate(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("id") UUID actionId,
            @RequestBody(required = false) EvaluateVerificationRequest request
    ) {
        return service.evaluate(jwt, actionId, request);
    }
}
