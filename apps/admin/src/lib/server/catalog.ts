import "server-only";
import type {
  AdminEvidence,
  AdminPage,
  CatalogAudit,
  CatalogSyncReport,
  CommercialOffer,
  OfferPriceHistory,
  OfferProvider,
  OfferStatus,
  Sector,
  UserProfile,
} from "@/lib/types";
import { callBackend, type BackendResult } from "./backend";

const ADMIN = "/v1/admin/catalog";
const MAX_PAGE_SIZE = 100;
/** Plafond de pages parcourues pour les vues agrégées (100 × 20 = 2 000 offres). */
const MAX_AGGREGATE_PAGES = 20;

export interface OfferFilters {
  status?: OfferStatus;
  sector?: Sector;
  providerId?: string;
  page?: number;
  size?: number;
}

export function listOffers(filters: OfferFilters): Promise<BackendResult<AdminPage<CommercialOffer>>> {
  const query = new URLSearchParams();
  if (filters.status) query.set("status", filters.status);
  if (filters.sector) query.set("sector", filters.sector);
  if (filters.providerId) query.set("providerId", filters.providerId);
  query.set("page", String(filters.page ?? 0));
  query.set("size", String(Math.min(filters.size ?? 20, MAX_PAGE_SIZE)));
  return callBackend(`${ADMIN}/offers?${query}`);
}

export interface AllOffers {
  offers: CommercialOffer[];
  totalElements: number;
  truncated: boolean;
}

/**
 * L'API n'expose ni agrégats ni file de revue globale : le dashboard et la file des preuves
 * parcourent la liste paginée complète. À remplacer par un endpoint dédié côté backend.
 */
export async function listAllOffers(): Promise<BackendResult<AllOffers>> {
  const offers: CommercialOffer[] = [];
  let page = 0;
  let totalPages = 1;
  let totalElements = 0;
  while (page < totalPages && page < MAX_AGGREGATE_PAGES) {
    const result = await listOffers({ page, size: MAX_PAGE_SIZE });
    if (!result.ok) return result;
    offers.push(...result.data.items);
    totalPages = result.data.totalPages;
    totalElements = result.data.totalElements;
    page += 1;
  }
  return { ok: true, status: 200, data: { offers, totalElements, truncated: page < totalPages } };
}

export function getOffer(offerId: string): Promise<BackendResult<CommercialOffer>> {
  return callBackend(`${ADMIN}/offers/${encodeURIComponent(offerId)}`);
}

export function getOfferEvidences(offerId: string): Promise<BackendResult<AdminEvidence[]>> {
  return callBackend(`${ADMIN}/offers/${encodeURIComponent(offerId)}/evidences`);
}

export function getOfferPriceHistory(offerId: string): Promise<BackendResult<OfferPriceHistory[]>> {
  return callBackend(`${ADMIN}/offers/${encodeURIComponent(offerId)}/price-history`);
}

export function getOfferAudit(offerId: string): Promise<BackendResult<CatalogAudit[]>> {
  return callBackend(`${ADMIN}/offers/${encodeURIComponent(offerId)}/audit`);
}

export function listProviders(page = 0, size = MAX_PAGE_SIZE): Promise<BackendResult<AdminPage<OfferProvider>>> {
  return callBackend(`${ADMIN}/providers?page=${page}&size=${Math.min(size, MAX_PAGE_SIZE)}`);
}

export function getProvider(providerId: string): Promise<BackendResult<OfferProvider>> {
  return callBackend(`${ADMIN}/providers/${encodeURIComponent(providerId)}`);
}

export function getProviderAudit(providerId: string): Promise<BackendResult<CatalogAudit[]>> {
  return callBackend(`${ADMIN}/providers/${encodeURIComponent(providerId)}/audit`);
}

/** Nombre d'offres d'un fournisseur, éventuellement limité à un statut (lecture de totalElements). */
export async function countProviderOffers(providerId: string, status?: OfferStatus): Promise<BackendResult<number>> {
  const result = await listOffers({ providerId, status, size: 1 });
  return result.ok ? { ok: true, status: 200, data: result.data.totalElements } : result;
}

export function listSyncRuns(limit = 5): Promise<BackendResult<CatalogSyncReport[]>> {
  return callBackend(`/v1/admin/catalog-sync/runs?limit=${limit}`);
}

export function getProfile(accessToken?: string): Promise<BackendResult<UserProfile>> {
  return callBackend("/v1/me", { accessToken });
}

/** Sonde de permission : seule une réponse 200 de Spring Boot prouve CATALOG_ADMIN. */
export async function hasCatalogAdminPermission(accessToken: string): Promise<BackendResult<true>> {
  const result = await callBackend(`${ADMIN}/providers?page=0&size=1`, { accessToken });
  return result.ok ? { ok: true, status: 200, data: true } : result;
}
