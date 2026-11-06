import { notFound } from "next/navigation";
import { Suspense, type ReactNode } from "react";
import { ApiProblem } from "@/components/api-problem";
import { AuditCard } from "@/components/audit-card";
import {
  Badge,
  Card,
  CheckBadge,
  DefinitionList,
  EmptyState,
  EvidenceStatusBadge,
  Notice,
  OfferStatusBadge,
  PageHeader,
  Skeleton,
  TextLink,
} from "@/components/ui";
import { parseCharacteristics } from "@/lib/characteristics";
import { blockingCount, publicationChecklist } from "@/lib/checklist";
import {
  EVIDENCE_SCOPE_LABELS,
  formatDate,
  formatDateTime,
  formatMinor,
  PROVIDER_STATUS_LABELS,
  SECTOR_LABELS,
  UNKNOWN_LABEL,
} from "@/lib/format";
import { describePricing } from "@/lib/pricing";
import { getOffer, getOfferAudit, getOfferEvidences, getOfferPriceHistory } from "@/lib/server/catalog";
import { publicationEnabled } from "@/lib/server/config";
import type { AdminEvidence, CommercialOffer, OfferPriceHistory } from "@/lib/types";
import { EvidenceReview } from "./evidence-review";
import { OfferActions } from "./offer-actions";

export const metadata = { title: "Fiche offre — Eparly Admin" };

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export default function OfferPage({ params }: PageProps<"/catalog/offers/[offerId]">) {
  return (
    <>
      <p className="mb-2">
        <TextLink href="/catalog/offers">← Retour aux offres</TextLink>
      </p>
      <Suspense fallback={<Skeleton rows={12} />}>
        <OfferContent params={params} />
      </Suspense>
    </>
  );
}

function orUnknown(value: string | null | undefined) {
  return value?.trim() ? value : <span className="text-muted">{UNKNOWN_LABEL}</span>;
}

async function OfferContent({ params }: { params: PageProps<"/catalog/offers/[offerId]">["params"] }) {
  const { offerId } = await params;
  if (!UUID_RE.test(offerId)) notFound();

  const [offerResult, evidencesResult, historyResult, auditResult] = await Promise.all([
    getOffer(offerId),
    getOfferEvidences(offerId),
    getOfferPriceHistory(offerId),
    getOfferAudit(offerId),
  ]);
  if (!offerResult.ok) {
    if (offerResult.status === 404) notFound();
    return <ApiProblem result={offerResult} context="chargement de l'offre" />;
  }

  const offer = offerResult.data;
  const evidences = evidencesResult.ok ? evidencesResult.data : (offer.evidence ?? []);
  const checklist = publicationChecklist(offer, evidences);
  const blocks = blockingCount(checklist);

  return (
    <div className="space-y-6">
      <PageHeader
        title={offer.commercialName}
        description={
          <span className="flex flex-wrap items-center gap-2">
            <span>{offer.provider.name}</span>
            <span aria-hidden>·</span>
            <span>{SECTOR_LABELS[offer.sector]}</span>
            <OfferStatusBadge status={offer.publicationStatus} />
            {blocks > 0 && <Badge tone="danger">{blocks} blocage{blocks > 1 ? "s" : ""}</Badge>}
          </span>
        }
      />

      <div className="grid gap-6 xl:grid-cols-3">
        <div className="space-y-6 xl:col-span-2">
          <SummaryCard offer={offer} />
          <PricingCard offer={offer} />
          <ConditionsCard offer={offer} />
          <EvidenceCard offer={offer} evidences={evidencesResult.ok ? evidencesResult.data : null} />
          <HistoryCard history={historyResult.ok ? historyResult.data : null} offer={offer} />
          <AuditCard title="Journal d'audit de l'offre" audit={auditResult.ok ? auditResult.data : null} />
        </div>

        <div className="space-y-6">
          <Card title="Contrôle de publication (indicatif)">
            <ul className="space-y-3">
              {checklist.map((item) => (
                <li key={item.id}>
                  <div className="flex items-center justify-between gap-2">
                    <span className="font-medium">{item.label}</span>
                    <CheckBadge state={item.state} />
                  </div>
                  <p className="mt-0.5 text-xs text-muted">{item.detail}</p>
                </li>
              ))}
            </ul>
            <p className="mt-4 text-xs text-muted">
              « Vérifiée », « publiable » et « publiée » sont trois états distincts. Seule la réponse du backend fait foi.
            </p>
          </Card>

          <Card title="Droits de réutilisation">
            <Notice title="Pas encore affichés dans la console">
              L&apos;API expose désormais les droits de réutilisation, mais la console ne les lit pas encore. Aucun droit
              n&apos;est considéré comme établi ici : vérifiez-les avant toute publication. Une preuve vérifiée ne vaut pas
              autorisation de réutilisation.
            </Notice>
          </Card>

          <Card title="Actions">
            <OfferActions
              offerId={offer.id}
              status={offer.publicationStatus}
              blockingItems={blocks}
              publicationEnabled={publicationEnabled()}
            />
          </Card>
        </div>
      </div>
    </div>
  );
}

function SummaryCard({ offer }: { offer: CommercialOffer }) {
  return (
    <Card title="Synthèse">
      <DefinitionList
        items={[
          ["Identifiant", <code key="id" className="font-mono text-xs">{offer.id}</code>],
          [
            "Fournisseur",
            <span key="p">
              {offer.provider.name} · {PROVIDER_STATUS_LABELS[offer.provider.status]}
              {offer.provider.officialWebsite && (
                <>
                  {" · "}
                  <a href={offer.provider.officialWebsite} target="_blank" rel="noopener noreferrer" className="text-lavender-strong underline">
                    site officiel
                  </a>
                </>
              )}
            </span>,
          ],
          ["Secteur", SECTOR_LABELS[offer.sector]],
          ["Territoire", orUnknown(offer.territory)],
          ["Validité", `${offer.validFrom ? formatDate(offer.validFrom) : "sans début"} → ${offer.validUntil ? formatDate(offer.validUntil) : "sans fin"}`],
          ["Créée / modifiée", `${formatDateTime(offer.createdAt)} / ${formatDateTime(offer.updatedAt)}`],
          ["Version", offer.version],
        ]}
      />
    </Card>
  );
}

function PricingCard({ offer }: { offer: CommercialOffer }) {
  if (!offer.pricing) {
    return (
      <Card title="Tarification">
        <EmptyState title="Aucun tarif enregistré">La publication est impossible sans tarif.</EmptyState>
      </Card>
    );
  }
  const view = describePricing(offer.pricing);
  return (
    <Card title="Tarification" actions={<span className="text-xs text-muted">Version {offer.pricing.version}</span>}>
      {view.dataUnknown && (
        <div className="mb-4">
          <Notice>Le tarif est marqué « donnée inconnue » par la source : il n&apos;est pas fiable en l&apos;état.</Notice>
        </div>
      )}
      <DefinitionList
        items={[
          ["Tarif standard", <span key="s" className="font-semibold">{view.standard}</span>],
          ...(view.promotion
            ? ([
                [
                  view.promotion.kind === "FIXED_PRICE" ? "Prix promotionnel" : "Remise promotionnelle",
                  <span key="pr">
                    {view.promotion.temporary} {view.promotion.duration}, puis tarif standard
                    {view.promotion.verified === false && <span className="ml-2"><Badge tone="warn">Promotion non vérifiée</Badge></span>}
                  </span>,
                ],
                ...(view.promotion.window ? [["Fenêtre de souscription", view.promotion.window] as [string, string]] : []),
                ...(view.promotion.inconsistent
                  ? [["Cohérence", <span key="c" className="text-danger">{view.promotion.inconsistent}</span>] as [string, ReactNode]]
                  : []),
              ] as [string, ReactNode][])
            : [["Promotion", "Aucune déclarée"] as [string, string]]),
          ["Taxes", view.taxes],
          ["Frais d'activation", view.activationFee],
          ["Autres frais", view.otherFees],
          ["Conditions de remise", view.discountConditions ?? <span className="text-muted">Aucune renseignée</span>],
        ]}
      />
    </Card>
  );
}

function ConditionsCard({ offer }: { offer: CommercialOffer }) {
  const characteristics = parseCharacteristics(offer.characteristics);
  return (
    <Card title="Conditions et caractéristiques">
      <DefinitionList
        items={[
          ["Engagement", orUnknown(offer.commitment)],
          ["Éligibilité", orUnknown(offer.eligibilityConditions)],
        ]}
      />
      <h3 className="mt-5 mb-2 font-semibold">Caractéristiques</h3>
      {characteristics.parseError ? (
        <Notice tone="danger">Le champ « characteristics » n&apos;est pas un objet JSON valide.</Notice>
      ) : characteristics.entries.length === 0 ? (
        <p className="text-muted">Aucune caractéristique renseignée.</p>
      ) : (
        <ul className="divide-y divide-border rounded-xl border border-border">
          {characteristics.entries.map((entry) => (
            <li key={entry.key} className="px-3 py-2">
              <div className="flex flex-wrap justify-between gap-2">
                <code className="font-mono text-xs">{entry.key}</code>
                <span className={entry.warning?.startsWith("Donnée incohérente") ? "font-medium text-danger" : ""}>{entry.value}</span>
              </div>
              {entry.warning && <p className="mt-1 text-xs text-warn">{entry.warning}</p>}
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}

function EvidenceCard({ offer, evidences }: { offer: CommercialOffer; evidences: AdminEvidence[] | null }) {
  return (
    <Card id="preuves" title="Preuves">
      {evidences === null ? (
        <Notice tone="danger">Liste détaillée des preuves non disponible : aucune décision ne peut être enregistrée.</Notice>
      ) : evidences.length === 0 ? (
        <EmptyState title="Aucune preuve">Une preuve « Offre » vérifiée est requise pour publier.</EmptyState>
      ) : (
        <ul className="space-y-4">
          {evidences.map((evidence) => {
            const stale =
              evidence.verificationStatus === "VERIFIED" &&
              offer.pricing !== null &&
              (evidence.verifiedPricingVersion !== offer.pricing.version ||
                evidence.verifiedProviderVersion !== offer.provider.version);
            return (
              <li key={evidence.id} className="rounded-xl border border-border p-3">
                <div className="flex flex-wrap items-center gap-2">
                  <EvidenceStatusBadge status={evidence.verificationStatus} />
                  {evidence.evidenceScope && <Badge tone="lavender">{EVIDENCE_SCOPE_LABELS[evidence.evidenceScope]}</Badge>}
                  {stale && <Badge tone="danger">Validée sur une version antérieure</Badge>}
                </div>
                <div className="mt-3">
                  <DefinitionList
                    items={[
                      [
                        "Source",
                        evidence.officialUrl ? (
                          <a key="u" href={evidence.officialUrl} target="_blank" rel="noopener noreferrer" className="break-all text-lavender-strong underline">
                            {evidence.officialUrl}
                          </a>
                        ) : (
                          <span className="text-muted">{UNKNOWN_LABEL}</span>
                        ),
                      ],
                      ["Nature", orUnknown(evidence.nature)],
                      ["Provenance", orUnknown(evidence.provenance)],
                      ["Consultée le", evidence.consultedAt ? formatDateTime(evidence.consultedAt) : <span className="text-muted">Date inconnue</span>],
                      ["Expire le", evidence.expiresAt ? formatDateTime(evidence.expiresAt) : "—"],
                      ...(evidence.verifiedAt
                        ? ([
                            ["Décision le", formatDateTime(evidence.verifiedAt)],
                            ["Par", <code key="v" className="font-mono text-xs">{evidence.verifiedByUserId ?? "inconnu"}</code>],
                            ["Justification", orUnknown(evidence.verifiedSummary)],
                          ] as [string, ReactNode][])
                        : []),
                    ]}
                  />
                </div>
                <div className="mt-3">
                  <EvidenceReview
                    offerId={offer.id}
                    evidenceId={evidence.id}
                    expectedVersion={evidence.version}
                    status={evidence.verificationStatus}
                    officialUrl={evidence.officialUrl}
                  />
                </div>
              </li>
            );
          })}
        </ul>
      )}
    </Card>
  );
}

function HistoryCard({ history, offer }: { history: OfferPriceHistory[] | null; offer: CommercialOffer }) {
  const currency = offer.pricing?.currency ?? null;
  return (
    <Card title="Historique des prix">
      {history === null ? (
        <p className="text-muted">Historique non disponible.</p>
      ) : history.length === 0 ? (
        <p className="text-muted">Aucune révision tarifaire enregistrée.</p>
      ) : (
        <ul className="divide-y divide-border">
          {[...history]
            .sort((a, b) => b.changedAt.localeCompare(a.changedAt))
            .map((entry) => (
              <li key={entry.id} className="flex flex-wrap justify-between gap-2 py-2">
                <span>
                  {entry.oldAmountMinor === null ? "Création" : formatMinor(entry.oldAmountMinor, currency)} →{" "}
                  <span className="font-medium">{formatMinor(entry.newAmountMinor, currency)}</span>
                  {entry.reason && <span className="ml-2 text-muted">{entry.reason}</span>}
                </span>
                <span className="text-muted">{formatDateTime(entry.changedAt)}</span>
              </li>
            ))}
        </ul>
      )}
    </Card>
  );
}
