import { Activity, BarChart3, CalendarClock, ClipboardCheck, History, Layers, ShieldCheck, Tags } from "lucide-react";
import Link from "next/link";
import { Suspense, type ReactNode } from "react";
import { ApiProblem } from "@/components/api-problem";
import { buttonClass } from "@/components/button-class";
import { ColumnChart, HorizontalBars, Legend, ProgressRing, type Series } from "@/components/charts";
import { SectorIcon } from "@/components/sector-icon";
import { Badge, Card, Notice, OfferStatusBadge, Skeleton, TextLink } from "@/components/ui";
import { blockingCount, publicationChecklist } from "@/lib/checklist";
import {
  monthKey,
  monthlyActivity,
  recentByDay,
  sectorProgress,
  statusDistribution,
  type SectorState,
} from "@/lib/dashboard-data";
import { formatDateTime, OFFER_STATUS_LABELS, SECTOR_LABELS } from "@/lib/format";
import { shortPrice } from "@/lib/pricing";
import { listAllOffers, listProviders, listSyncRuns } from "@/lib/server/catalog";

export const metadata = { title: "Tableau de bord — Eparly Admin" };

const ACTIVITY_SERIES: Series[] = [
  { key: "offersAdded", label: "Offres ajoutées", colorClass: "bg-chart-1" },
  { key: "evidenceVerified", label: "Preuves vérifiées", colorClass: "bg-chart-2" },
];

const SECTOR_STATE: Record<SectorState, { label: string; tone: "ok" | "warn" | "danger" | "neutral" }> = {
  complete: { label: "À jour", tone: "ok" },
  "in-review": { label: "Revue en cours", tone: "warn" },
  missing: { label: "Preuves manquantes", tone: "danger" },
  empty: { label: "Aucune offre", tone: "neutral" },
};

const timeFormatter = new Intl.DateTimeFormat("fr-FR", { timeStyle: "short", timeZone: "Europe/Paris" });

export default function DashboardPage() {
  return (
    <Suspense fallback={<Skeleton rows={10} />}>
      <DashboardContent />
    </Suspense>
  );
}

async function DashboardContent() {
  const [offersResult, providersResult] = await Promise.all([listAllOffers(), listProviders(0, 1)]);
  const now = new Date();

  const header = (
    <div className="mb-6 flex flex-wrap items-center justify-between gap-3">
      <h1 className="text-2xl font-semibold tracking-tight">Tableau de bord</h1>
      <div className="flex flex-wrap items-center gap-2">
        <span className="inline-flex items-center gap-2 rounded-2xl border border-border bg-surface px-3 py-2 text-muted shadow-card">
          <CalendarClock aria-hidden className="size-4" />
          Données au {formatDateTime(now.toISOString())}
        </span>
        <Link href="/catalog/reviews" className={`${buttonClass("primary")} gap-2 py-2`}>
          <ClipboardCheck aria-hidden className="size-4" />
          Revue des preuves
        </Link>
      </div>
    </div>
  );

  if (!offersResult.ok) {
    return (
      <>
        {header}
        <ApiProblem result={offersResult} context="chargement du catalogue" />
      </>
    );
  }

  const { offers, totalElements, truncated } = offersResult.data;
  const published = offers.filter((o) => o.publicationStatus === "VERIFIED").length;
  const pendingEvidence = offers.reduce(
    (sum, o) => sum + (o.evidence ?? []).filter((e) => e.verificationStatus === "PENDING").length,
    0,
  );
  const blocked = offers.filter(
    (o) => o.publicationStatus !== "ARCHIVED" && blockingCount(publicationChecklist(o, o.evidence ?? [], now)) > 0,
  ).length;
  const currentMonth = monthKey(now);
  const activity = monthlyActivity(offers, now).map((m) => ({
    key: m.key,
    label: m.label,
    fullLabel: m.fullLabel,
    values: { offersAdded: m.offersAdded, evidenceVerified: m.evidenceVerified },
    highlight: m.key === currentMonth,
  }));
  const sectors = sectorProgress(offers);
  const statuses = statusDistribution(offers);
  const groups = recentByDay(offers, now);

  return (
    <>
      {header}
      <div className="space-y-6">
        {truncated && (
          <Notice title="Vue partielle">
            Seules les {offers.length} premières offres sur {totalElements} sont agrégées. Un endpoint d&apos;agrégats côté
            backend est nécessaire pour un décompte exact.
          </Notice>
        )}

        <div className="grid gap-6 xl:grid-cols-2">
          <Card title="Catalogue" icon={<Tags className="size-4" />}>
            <p className="text-5xl font-semibold tracking-tight">{totalElements}</p>
            <p className="mt-1 text-muted">
              offre{totalElements > 1 ? "s" : ""} au catalogue · {published} publiée{published > 1 ? "s" : ""} ·{" "}
              {providersResult.ok ? `${providersResult.data.totalElements} fournisseurs` : "fournisseurs non disponibles"}
            </p>
            <div className="mt-5 grid gap-3 sm:grid-cols-2">
              <Tile
                label="Preuves à vérifier"
                value={pendingEvidence}
                badge={pendingEvidence > 0 ? <Badge tone="warn">À traiter</Badge> : <Badge tone="ok">À jour</Badge>}
                footer={<TextLink href="/catalog/reviews">Ouvrir la file</TextLink>}
              />
              <Tile
                label="Offres avec blocage"
                value={blocked}
                badge={blocked > 0 ? <Badge tone="danger">Bloquées</Badge> : <Badge tone="ok">Aucun</Badge>}
                footer={<TextLink href="/catalog/offers?blocked=1">Voir les offres</TextLink>}
              />
            </div>
            {published === 0 && totalElements > 0 && (
              <p className="mt-4 text-xs text-muted">
                Aucune offre publiée. Droits de réutilisation : non exposés par l&apos;API, à documenter hors back-office.
              </p>
            )}
          </Card>

          <Card title="Activité du catalogue" icon={<BarChart3 className="size-4" />} actions={<Legend series={ACTIVITY_SERIES} />}>
            <ColumnChart
              data={activity}
              series={ACTIVITY_SERIES}
              caption="Offres ajoutées et preuves vérifiées par mois, sur les 6 derniers mois"
            />
          </Card>
        </div>

        <div className="grid items-start gap-6 xl:grid-cols-[2fr_3fr]">
          <div className="space-y-6">
          <Card title="Vérification par secteur" icon={<ShieldCheck className="size-4" />}>
            <ul className="divide-y divide-border">
              {sectors.map((s) => (
                <li key={s.sector} className="flex items-center gap-4 py-3 first:pt-0 last:pb-0">
                  <ProgressRing
                    percent={s.percent}
                    label={
                      s.percent === null
                        ? `${SECTOR_LABELS[s.sector]} : aucune offre`
                        : `${SECTOR_LABELS[s.sector]} : ${s.percent} % des offres ont une preuve vérifiée`
                    }
                  />
                  <div className="min-w-0 flex-1">
                    <TextLink href={`/catalog/offers?sector=${s.sector}`}>{SECTOR_LABELS[s.sector]}</TextLink>
                    <p className="text-xs text-muted">
                      {s.withVerifiedProof}/{s.total} offre{s.total > 1 ? "s" : ""} avec preuve vérifiée
                      {s.pendingEvidence > 0 && ` · ${s.pendingEvidence} en attente`}
                    </p>
                  </div>
                  <Badge tone={SECTOR_STATE[s.state].tone}>{SECTOR_STATE[s.state].label}</Badge>
                </li>
              ))}
            </ul>
          </Card>

          <Card title="Offres par statut" icon={<Layers className="size-4" />}>
            <HorizontalBars
              caption="Nombre d'offres par statut de publication"
              items={statuses.map((s) => ({ key: s.status, label: OFFER_STATUS_LABELS[s.status], value: s.count }))}
            />
          </Card>

          <Suspense fallback={<Card title="Collecte du catalogue" icon={<Activity className="size-4" />}><Skeleton rows={3} /></Card>}>
            <SyncCard />
          </Suspense>
          </div>

          <Card title="Dernières modifications" icon={<History className="size-4" />} actions={<TextLink href="/catalog/offers">Tout voir</TextLink>}>
            {groups.length === 0 ? (
              <p className="text-muted">Aucune offre dans le catalogue.</p>
            ) : (
              <div className="space-y-4">
                {groups.map((group) => (
                  <section key={group.key} aria-label={group.label}>
                    <div className="flex items-center justify-between rounded-xl bg-background px-3 py-2 text-muted">
                      <span>{group.label}</span>
                      <span>
                        {group.offers.length} modification{group.offers.length > 1 ? "s" : ""}
                      </span>
                    </div>
                    <ul className="divide-y divide-border">
                      {group.offers.map((offer) => (
                        <li key={offer.id} className="flex flex-wrap items-center gap-3 px-1 py-3 sm:flex-nowrap">
                          <SectorIcon sector={offer.sector} />
                          <div className="min-w-0 flex-1">
                            <Link href={`/catalog/offers/${offer.id}`} className="block truncate font-medium hover:underline">
                              {offer.commercialName}
                            </Link>
                            <p className="truncate text-xs text-muted">{offer.provider.name}</p>
                          </div>
                          <span className="hidden max-w-56 truncate text-muted lg:block">{shortPrice(offer.pricing)}</span>
                          <span className="w-12 text-right tabular-nums text-muted">{timeFormatter.format(new Date(offer.updatedAt))}</span>
                          <OfferStatusBadge status={offer.publicationStatus} />
                        </li>
                      ))}
                    </ul>
                  </section>
                ))}
              </div>
            )}
          </Card>
        </div>
      </div>
    </>
  );
}

function Tile({ label, value, badge, footer }: { label: string; value: number; badge: ReactNode; footer: ReactNode }) {
  return (
    <div className="rounded-2xl border border-border bg-surface p-4">
      <p className="font-medium">{label}</p>
      <div className="mt-3 flex items-end justify-between gap-2">
        <span className="text-3xl font-semibold">{value}</span>
        {badge}
      </div>
      <div className="mt-2 text-xs">{footer}</div>
    </div>
  );
}

// Valeurs de la contrainte chk_catalog_sync_run_status (migration V20).
const SYNC_STATUS: Record<string, { label: string; tone: "ok" | "warn" | "danger" | "neutral" }> = {
  RUNNING: { label: "En cours", tone: "warn" },
  SUCCEEDED: { label: "Réussie", tone: "ok" },
  PARTIAL_FAILURE: { label: "Échec partiel", tone: "warn" },
  FAILED: { label: "Échec", tone: "danger" },
  SKIPPED: { label: "Ignorée", tone: "neutral" },
};

async function SyncCard() {
  const runs = await listSyncRuns(10);
  const icon = <Activity className="size-4" />;
  if (!runs.ok) {
    return (
      <Card title="Collecte du catalogue" icon={icon}>
        <p className="text-muted">État du batch non disponible (HTTP {runs.status || "réseau"}).</p>
      </Card>
    );
  }
  const latest = runs.data[0];
  const lastSuccess = runs.data.find((run) => run.status === "SUCCEEDED");
  const status = latest?.status ? SYNC_STATUS[latest.status] : undefined;
  return (
    <Card title="Collecte du catalogue" icon={icon} actions={status && <Badge tone={status.tone}>{status.label}</Badge>}>
      {!latest ? (
        <p className="text-muted">Aucune exécution enregistrée.</p>
      ) : (
        <dl className="grid grid-cols-2 gap-3">
          {[
            ["Dernière exécution", formatDateTime(latest.startedAt)],
            ["Dernière réussite", lastSuccess ? formatDateTime(lastSuccess.finishedAt) : "Aucune récente"],
            ["Offres découvertes / mises à jour", `${latest.offersDiscovered} / ${latest.offersUpdated}`],
            ["Erreurs", String(latest.errorsCount)],
          ].map(([term, value]) => (
            <div key={term} className="rounded-2xl bg-background px-3 py-2">
              <dt className="text-xs text-muted">{term}</dt>
              <dd className="mt-0.5 font-semibold">{value}</dd>
            </div>
          ))}
        </dl>
      )}
      <p className="mt-3 text-xs text-muted">
        Prochaine échéance et état du scheduler : non exposés par l&apos;API. Une exécution passée ne prouve pas que le
        scheduler tourne actuellement.
      </p>
    </Card>
  );
}
