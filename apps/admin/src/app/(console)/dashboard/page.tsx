import { Suspense } from "react";
import { ApiProblem } from "@/components/api-problem";
import { Card, Notice, OfferStatusBadge, PageHeader, Skeleton, TextLink } from "@/components/ui";
import { blockingCount, publicationChecklist } from "@/lib/checklist";
import { formatDateTime, OFFER_STATUS_LABELS, SECTOR_LABELS } from "@/lib/format";
import { listAllOffers, listProviders, listSyncRuns } from "@/lib/server/catalog";
import { OFFER_STATUSES, SECTORS, type CommercialOffer } from "@/lib/types";

export const metadata = { title: "Tableau de bord — Eparly Admin" };

export default function DashboardPage() {
  return (
    <>
      <PageHeader
        title="Tableau de bord"
        description="État du catalogue et opérations prioritaires, calculés à partir des données réelles de l'API."
      />
      <Suspense fallback={<Skeleton rows={8} />}>
        <DashboardContent />
      </Suspense>
    </>
  );
}

function Stat({ label, value, hint }: { label: string; value: string | number; hint?: string }) {
  return (
    <div className="rounded-lg border border-border bg-surface px-4 py-3">
      <p className="text-muted">{label}</p>
      <p className="mt-1 text-2xl font-semibold tabular-nums">{value}</p>
      {hint && <p className="mt-0.5 text-xs text-muted">{hint}</p>}
    </div>
  );
}

function pendingEvidenceCount(offers: CommercialOffer[]): number {
  return offers.reduce(
    (sum, offer) => sum + (offer.evidence ?? []).filter((e) => e.verificationStatus === "PENDING").length,
    0,
  );
}

async function DashboardContent() {
  const [offersResult, providersResult] = await Promise.all([listAllOffers(), listProviders(0, 1)]);
  if (!offersResult.ok) return <ApiProblem result={offersResult} context="chargement du catalogue" />;

  const { offers, totalElements, truncated } = offersResult.data;
  const byStatus = Object.fromEntries(OFFER_STATUSES.map((s) => [s, offers.filter((o) => o.publicationStatus === s).length]));
  const bySector = SECTORS.map((sector) => [sector, offers.filter((o) => o.sector === sector).length] as const);
  const pendingEvidence = pendingEvidenceCount(offers);
  const active = offers.filter((o) => o.publicationStatus !== "ARCHIVED");
  const blocked = active.filter((o) => blockingCount(publicationChecklist(o, o.evidence ?? [])) > 0);
  const recent = [...offers].sort((a, b) => b.updatedAt.localeCompare(a.updatedAt)).slice(0, 8);

  return (
    <div className="space-y-6">
      {truncated && (
        <Notice title="Vue partielle">
          Seules les {offers.length} premières offres sur {totalElements} sont agrégées. Un endpoint d&apos;agrégats côté
          backend est nécessaire pour un décompte exact.
        </Notice>
      )}
      {byStatus.VERIFIED === 0 && totalElements > 0 && (
        <Notice tone="lavender" title="Aucune offre publiée">
          Toutes les offres sont hors publication. Les blocages sont détaillés sur chaque fiche.
        </Notice>
      )}

      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4 xl:grid-cols-7">
        <Stat label="Offres" value={totalElements} />
        {OFFER_STATUSES.map((status) => (
          <Stat key={status} label={OFFER_STATUS_LABELS[status]} value={byStatus[status]} />
        ))}
        <Stat
          label="Fournisseurs"
          value={providersResult.ok ? providersResult.data.totalElements : "Non disponible"}
        />
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        <Card title="À traiter">
          <ul className="space-y-3">
            <li className="flex items-center justify-between gap-2">
              <span>Preuves à vérifier</span>
              <span className="flex items-center gap-3">
                <span className="font-semibold tabular-nums">{pendingEvidence}</span>
                <TextLink href="/catalog/reviews">Ouvrir la file</TextLink>
              </span>
            </li>
            <li className="flex items-center justify-between gap-2">
              <span>Offres non archivées avec blocage</span>
              <span className="flex items-center gap-3">
                <span className="font-semibold tabular-nums">{blocked.length}</span>
                <TextLink href="/catalog/offers?blocked=1">Voir</TextLink>
              </span>
            </li>
            <li className="flex items-center justify-between gap-2">
              <span>Droits de réutilisation</span>
              <span className="text-right text-muted">Non exposés par l&apos;API</span>
            </li>
          </ul>
        </Card>

        <Card title="Répartition par secteur">
          <ul className="space-y-2">
            {bySector.map(([sector, count]) => (
              <li key={sector} className="flex items-center justify-between">
                <TextLink href={`/catalog/offers?sector=${sector}`}>{SECTOR_LABELS[sector]}</TextLink>
                <span className="font-semibold tabular-nums">{count}</span>
              </li>
            ))}
          </ul>
        </Card>

        <Suspense fallback={<Card title="Collecte du catalogue"><Skeleton rows={3} /></Card>}>
          <SyncCard />
        </Suspense>
      </div>

      <Card title="Dernières modifications">
        {recent.length === 0 ? (
          <p className="text-muted">Aucune offre dans le catalogue.</p>
        ) : (
          <ul className="divide-y divide-border">
            {recent.map((offer) => (
              <li key={offer.id} className="flex flex-wrap items-center justify-between gap-2 py-2">
                <span className="min-w-0">
                  <TextLink href={`/catalog/offers/${offer.id}`}>{offer.commercialName}</TextLink>
                  <span className="ml-2 text-muted">{offer.provider.name}</span>
                </span>
                <span className="flex items-center gap-3">
                  <OfferStatusBadge status={offer.publicationStatus} />
                  <span className="text-muted tabular-nums">{formatDateTime(offer.updatedAt)}</span>
                </span>
              </li>
            ))}
          </ul>
        )}
      </Card>
    </div>
  );
}

// Valeurs de la contrainte chk_catalog_sync_run_status (migration V20).
const SYNC_STATUS_LABELS: Record<string, string> = {
  RUNNING: "En cours",
  SUCCEEDED: "Réussie",
  PARTIAL_FAILURE: "Échec partiel",
  FAILED: "Échec",
  SKIPPED: "Ignorée",
};

async function SyncCard() {
  const runs = await listSyncRuns(10);
  if (!runs.ok) {
    return (
      <Card title="Collecte du catalogue">
        <p className="text-muted">État du batch non disponible (HTTP {runs.status || "réseau"}).</p>
      </Card>
    );
  }
  const latest = runs.data[0];
  const lastSuccess = runs.data.find((run) => run.status === "SUCCEEDED");
  return (
    <Card title="Collecte du catalogue">
      {!latest ? (
        <p className="text-muted">Aucune exécution enregistrée.</p>
      ) : (
        <dl className="space-y-2">
          <div className="flex justify-between gap-2">
            <dt className="text-muted">Dernière exécution</dt>
            <dd className="text-right">{formatDateTime(latest.startedAt)}</dd>
          </div>
          <div className="flex justify-between gap-2">
            <dt className="text-muted">Résultat</dt>
            <dd className="text-right">{SYNC_STATUS_LABELS[latest.status ?? ""] ?? latest.status ?? "Inconnu"}</dd>
          </div>
          <div className="flex justify-between gap-2">
            <dt className="text-muted">Erreurs</dt>
            <dd className="text-right tabular-nums">{latest.errorsCount}</dd>
          </div>
          <div className="flex justify-between gap-2">
            <dt className="text-muted">Dernière réussite</dt>
            <dd className="text-right">{lastSuccess ? formatDateTime(lastSuccess.finishedAt) : "Aucune parmi les dernières"}</dd>
          </div>
        </dl>
      )}
      <p className="mt-3 text-xs text-muted">
        Prochaine échéance et état du scheduler : non exposés par l&apos;API. Une exécution passée ne prouve pas que le
        scheduler tourne actuellement.
      </p>
    </Card>
  );
}
