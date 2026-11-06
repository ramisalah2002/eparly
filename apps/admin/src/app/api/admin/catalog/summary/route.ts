import { NextResponse } from "next/server";
import {
  serverApiRequest,
  refreshSession,
} from "@/lib/server-api";
import { verifyRequestCsrf } from "@/lib/proxy-handler";

const SEGMENT_PREFIX = "/v1/admin/catalog";

interface OfferEvidence {
  verificationStatus: string;
}

interface OfferSummary {
  publicationStatus: string;
  evidence?: OfferEvidence[];
}

interface SummaryData {
  totalOffers: number;
  draftOffers: number;
  publishedOffers: number;
  archivedOffers: number;
  staleOffers: number;
  expiredOffers: number;
  providersCount: number;
  pendingEvidences: number;
  needsReview: number;
}

/**
 * GET /api/admin/catalog/summary
 * Agrège les compteurs du catalogue depuis Spring Boot pour le dashboard.
 */
export async function GET(request: Request) {
  if (!verifyRequestCsrf(request)) {
    return NextResponse.json(
      { error: "Jeton CSRF invalide ou manquant" },
      { status: 403 },
    );
  }

  try {
    // Récupérer toutes les offres avec pagination (taille max 100 par page)
    const allOffers: OfferSummary[] = [];
    let page = 0;
    let hasMore = true;

    while (hasMore) {
      const springResponse = await serverApiRequest(
        `${SEGMENT_PREFIX}/offers?page=${page}&size=100`,
        {
          method: "GET",
          headers: {
            "content-type": "application/json",
          },
        },
      );

      if (!springResponse.ok) {
        if (springResponse.status === 401) {
          const refreshed = await refreshSession();
          if (refreshed) {
            const retryResponse = await serverApiRequest(
              `${SEGMENT_PREFIX}/offers?page=${page}&size=100`,
              {
                method: "GET",
                headers: {
                  Authorization: `Bearer ${refreshed.accessToken}`,
                  "content-type": "application/json",
                },
              },
            );
            if (!retryResponse.ok) {
              return NextResponse.json(
                { error: "Impossible de récupérer les données du catalogue" },
                { status: retryResponse.status },
              );
            }
            const data = await retryResponse.json();
            allOffers.push(...(data.content || data.items || []));
            hasMore = data.page < data.totalPages - 1;
            page++;
            continue;
          }
          return NextResponse.json(
            { error: "Session expirée" },
            { status: 401 },
          );
        }
        return NextResponse.json(
          { error: "Impossible de récupérer les données du catalogue" },
          { status: springResponse.status },
        );
      }

      const data = await springResponse.json();
      const content = data.content || data.items || [];
      allOffers.push(...content);
      hasMore = data.page < data.totalPages - 1;
      page++;
    }

    // Récupérer les fournisseurs
    const providersResponse = await serverApiRequest(
      `${SEGMENT_PREFIX}/providers?page=0&size=100`,
      {
        method: "GET",
        headers: { "content-type": "application/json" },
      },
    );

    let providersCount = 0;
    if (providersResponse.ok) {
      const providersData = await providersResponse.json();
      const providersContent =
        providersData.content || providersData.items || providersData || [];
      providersCount = providersContent.length;
    }

    // Calculer les compteurs
    const totalOffers = allOffers.length;
    const draftOffers = allOffers.filter(
      (o) => o.publicationStatus === "DRAFT",
    ).length;
    const publishedOffers = allOffers.filter(
      (o) => o.publicationStatus === "VERIFIED",
    ).length;
    const archivedOffers = allOffers.filter(
      (o) => o.publicationStatus === "ARCHIVED",
    ).length;
    const staleOffers = allOffers.filter(
      (o) => o.publicationStatus === "STALE",
    ).length;
    const expiredOffers = allOffers.filter(
      (o) => o.publicationStatus === "EXPIRED",
    ).length;

    // Compter les preuves PENDING
    const pendingEvidences = allOffers.filter(
      (o) =>
        o.evidence &&
        o.evidence.some((e: OfferEvidence) => e.verificationStatus === "PENDING"),
    ).length;

    // Offres nécessitant une revue
    const needsReview = allOffers.filter(
      (o) =>
        o.publicationStatus === "DRAFT" ||
        o.publicationStatus === "STALE" ||
        o.publicationStatus === "EXPIRED" ||
        o.evidence?.some((e: OfferEvidence) => e.verificationStatus === "PENDING"),
    ).length;

    const summary: SummaryData = {
      totalOffers,
      draftOffers,
      publishedOffers,
      archivedOffers,
      staleOffers,
      expiredOffers,
      providersCount,
      pendingEvidences,
      needsReview,
    };

    return NextResponse.json(summary);
  } catch {
    return NextResponse.json(
      { error: "Erreur interne lors de la récupération des indicateurs" },
      { status: 500 },
    );
  }
}
