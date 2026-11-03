import Link from "next/link";
import { Suspense } from "react";
import { ApiProblem } from "@/components/api-problem";
import { buttonClass } from "@/components/button-class";
import { Badge, EmptyState, Notice, OfferStatusBadge, PageHeader, Skeleton, TextLink } from "@/components/ui";
import { blockingCount, publicationChecklist } from "@/lib/checklist";
import { formatDateTime, OFFER_STATUS_LABELS, SECTOR_LABELS } from "@/lib/format";
import { shortPrice } from "@/lib/pricing";
import { listAllOffers, listOffers, listProviders } from "@/lib/server/catalog";
import { OFFER_STATUSES, SECTORS, type CommercialOffer, type OfferStatus, type Sector } from "@/lib/types";

export const metadata = { title: "Offres — Eparly Admin" };

const PAGE_SIZE = 20;
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

interface Filters {
  status?: OfferStatus;
  sector?: Sector;
  providerId?: string;
  blocked: boolean;
  page: number;
}

function first(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value;
}

/** Les paramètres d'URL sont validés avant d'être transmis à l'API. */
function parseFilters(params: Record<string, string | string[] | undefined>): Filters {
  const status = first(params.status);
  const sector = first(params.sector);
  const providerId = first(params.providerId);
  const page = Number.parseInt(first(params.page) ?? "0", 10);
  return {
    status: OFFER_STATUSES.includes(status as OfferStatus) ? (status as OfferStatus) : undefined,
    sector: SECTORS.includes(sector as Sector) ? (sector as Sector) : undefined,
    providerId: providerId && UUID_RE.test(providerId) ? providerId : undefined,
    blocked: first(params.blocked) === "1",
    page: Number.isFinite(page) && page >= 0 ? page : 0,
  };
}

function hrefFor(filters: Filters, page: number): string {
  const query = new URLSearchParams();
  if (filters.status) query.set("status", filters.status);
  if (filters.sector) query.set("sector", filters.sector);
  if (filters.providerId) query.set("providerId", filters.providerId);
  if (filters.blocked) query.set("blocked", "1");
  if (page > 0) query.set("page", String(page));
  const qs = query.toString();
  return qs ? `/catalog/offers?${qs}` : "/catalog/offers";
}

export default function OffersPage({ searchParams }: PageProps<"/catalog/offers">) {
  return (
    <>
      <PageHeader
        title="Offres"
        description="Catalogue commercial complet, tous statuts confondus. Les blocages affichés sont indicatifs : seul le backend décide d'une publication."
      />
      <Suspense fallback={<Skeleton rows={10} />}>
        <OffersContent searchParams={searchParams} />
      </Suspense>
    </>
  );
}

async function OffersContent({ searchParams }: { searchParams: PageProps<"/catalog/offers">["searchParams"] }) {
  const filters = parseFilters(await searchParams);
  const providersPromise = listProviders();

  let offers: CommercialOffer[];
  let totalElements: number;
  let totalPages: number;

  if (filters.blocked) {
    // Pas de filtre « bloquée » côté API : on agrège puis on pagine localement.
    const all = await listAllOffers();
    if (!all.ok) return <ApiProblem result={all} context="chargement des offres" />;
    const filtered = all.data.offers.filter(
      (o) =>
        (!filters.status || o.publicationStatus === filters.status) &&
        (!filters.sector || o.sector === filters.sector) &&
        (!filters.providerId || o.provider.id === filters.providerId) &&
        o.publicationStatus !== "ARCHIVED" &&
        blockingCount(publicationChecklist(o, o.evidence ?? [])) > 0,
    );
    totalElements = filtered.length;
    totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
    offers = filtered.slice(filters.page * PAGE_SIZE, (filters.page + 1) * PAGE_SIZE);
  } else {
    const result = await listOffers({ ...filters, size: PAGE_SIZE });
    if (!result.ok) return <ApiProblem result={result} context="chargement des offres" />;
    offers = result.data.items;
    totalElements = result.data.totalElements;
    totalPages = result.data.totalPages;
  }

  const providers = await providersPromise;
  const hasFilters = !!(filters.status || filters.sector || filters.providerId || filters.blocked);

  return (
    <div className="space-y-4">
      <form method="get" className="flex flex-wrap items-end gap-3 rounded-3xl border border-border bg-surface shadow-card p-3">
        <label className="flex flex-col gap-1">
          <span className="text-xs font-medium text-muted">Statut</span>
          <select name="status" defaultValue={filters.status ?? ""} className="rounded-xl border border-border bg-surface px-2 py-1.5">
            <option value="">Tous</option>
            {OFFER_STATUSES.map((s) => (
              <option key={s} value={s}>
                {OFFER_STATUS_LABELS[s]}
              </option>
            ))}
          </select>
        </label>
        <label className="flex flex-col gap-1">
          <span className="text-xs font-medium text-muted">Secteur</span>
          <select name="sector" defaultValue={filters.sector ?? ""} className="rounded-xl border border-border bg-surface px-2 py-1.5">
            <option value="">Tous</option>
            {SECTORS.map((s) => (
              <option key={s} value={s}>
                {SECTOR_LABELS[s]}
              </option>
            ))}
          </select>
        </label>
        <label className="flex flex-col gap-1">
          <span className="text-xs font-medium text-muted">Fournisseur</span>
          <select
            name="providerId"
            defaultValue={filters.providerId ?? ""}
            className="max-w-64 rounded-xl border border-border bg-surface px-2 py-1.5"
            disabled={!providers.ok}
          >
            <option value="">{providers.ok ? "Tous" : "Liste non disponible"}</option>
            {providers.ok &&
              providers.data.items.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name} · {SECTOR_LABELS[p.sector]}
                </option>
              ))}
          </select>
        </label>
        <label className="flex items-center gap-2 pb-1.5">
          <input type="checkbox" name="blocked" value="1" defaultChecked={filters.blocked} />
          <span>Avec blocage</span>
        </label>
        <button type="submit" className={buttonClass("primary")}>
          Filtrer
        </button>
        {hasFilters && (
          <Link href="/catalog/offers" className={buttonClass("secondary")}>
            Réinitialiser
          </Link>
        )}
      </form>

      {providers.ok && providers.data.totalElements > providers.data.items.length && (
        <Notice>Seuls les {providers.data.items.length} premiers fournisseurs sont proposés dans le filtre.</Notice>
      )}

      <p className="text-muted">
        {totalElements} offre{totalElements > 1 ? "s" : ""}
        {hasFilters ? " correspondant aux filtres" : ""}. La recherche textuelle n&apos;est pas proposée par l&apos;API.
      </p>

      {offers.length === 0 ? (
        <EmptyState title={hasFilters ? "Aucune offre ne correspond à ces filtres" : "Le catalogue est vide"}>
          {!hasFilters && "Les offres apparaîtront ici après import ou création via l'API."}
        </EmptyState>
      ) : (
        <div className="overflow-x-auto rounded-3xl border border-border bg-surface shadow-card">
          <table className="w-full min-w-[60rem] text-left">
            <thead className="border-b border-border bg-background text-xs uppercase tracking-wide text-muted">
              <tr>
                <th scope="col" className="px-3 py-2 font-medium">Offre</th>
                <th scope="col" className="px-3 py-2 font-medium">Fournisseur</th>
                <th scope="col" className="px-3 py-2 font-medium">Secteur</th>
                <th scope="col" className="px-3 py-2 font-medium">Prix</th>
                <th scope="col" className="px-3 py-2 font-medium">Statut</th>
                <th scope="col" className="px-3 py-2 font-medium">Preuves</th>
                <th scope="col" className="px-3 py-2 font-medium">Blocages</th>
                <th scope="col" className="px-3 py-2 font-medium">Modifiée</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {offers.map((offer) => {
                const evidence = offer.evidence ?? [];
                const verified = evidence.filter((e) => e.verificationStatus === "VERIFIED").length;
                const pending = evidence.filter((e) => e.verificationStatus === "PENDING").length;
                const blocks = blockingCount(publicationChecklist(offer, evidence));
                return (
                  <tr key={offer.id} className="align-top hover:bg-background/60">
                    <td className="px-3 py-2">
                      <TextLink href={`/catalog/offers/${offer.id}`}>{offer.commercialName}</TextLink>
                    </td>
                    <td className="px-3 py-2">{offer.provider.name}</td>
                    <td className="px-3 py-2">{SECTOR_LABELS[offer.sector]}</td>
                    <td className="max-w-64 px-3 py-2">{shortPrice(offer.pricing)}</td>
                    <td className="px-3 py-2">
                      <OfferStatusBadge status={offer.publicationStatus} />
                    </td>
                    <td className="whitespace-nowrap px-3 py-2 tabular-nums">
                      {evidence.length === 0 ? (
                        <Badge tone="danger">Aucune</Badge>
                      ) : (
                        <span>
                          {verified} vérifiée{verified > 1 ? "s" : ""}
                          {pending > 0 && <span className="text-warn"> · {pending} à vérifier</span>}
                        </span>
                      )}
                    </td>
                    <td className="px-3 py-2">
                      {offer.publicationStatus === "ARCHIVED" ? (
                        <span className="text-muted">—</span>
                      ) : blocks > 0 ? (
                        <Badge tone="danger">{blocks}</Badge>
                      ) : (
                        <Badge tone="warn">À confirmer</Badge>
                      )}
                    </td>
                    <td className="whitespace-nowrap px-3 py-2 text-muted">{formatDateTime(offer.updatedAt)}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {totalPages > 1 && (
        <nav aria-label="Pagination" className="flex items-center justify-between">
          <span className="text-muted">
            Page {filters.page + 1} sur {totalPages}
          </span>
          <span className="flex gap-2">
            {filters.page > 0 ? (
              <Link href={hrefFor(filters, filters.page - 1)} className={buttonClass("secondary")}>
                Précédente
              </Link>
            ) : (
              <span className={`${buttonClass("secondary")} opacity-50`}>Précédente</span>
            )}
            {filters.page + 1 < totalPages ? (
              <Link href={hrefFor(filters, filters.page + 1)} className={buttonClass("secondary")}>
                Suivante
              </Link>
            ) : (
              <span className={`${buttonClass("secondary")} opacity-50`}>Suivante</span>
            )}
          </span>
        </nav>
      )}
    </div>
  );
}
