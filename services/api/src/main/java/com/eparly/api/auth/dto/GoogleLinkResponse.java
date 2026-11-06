package com.eparly.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resultat de la liaison")
public record GoogleLinkResponse(
        @Schema(example = "GOOGLE") String provider,
        @Schema(example = "alex.martin@gmail.com", description = "Email confirme par Google lors de la liaison") String email,
        @Schema(description = "Vrai si ce compte Google etait deja lie a ce compte Eparly") boolean alreadyLinked
) {
}
