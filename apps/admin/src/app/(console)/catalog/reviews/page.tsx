import { Suspense } from "react";
import { ApiProblem } from "@/components/api-problem";
import { Badge, EmptyState, Notice, PageHeader, Skeleton, TextLink } from "@/components/ui";
import { EVIDENCE_SCOPE_LABELS, formatDateTime, SECTOR_LABELS } from "@/lib/format";
import { reviewQueue } from "@/lib/review-queue";
import { listAllOffers } from "@/lib/server/catalog";

export const metadata = { title: "Revue des preuves — Eparly Admin" };

export default function ReviewsPage() {
  return (
    <>
      <PageHeader
        title="Revue des preuves"
        description="Preuves en attente de décision humaine. La décision se prend depuis la fiche de l'offre, après consultation de la source."
      />
      <Suspense fallback={<Skeleton rows={8} />}>
        <ReviewsContent />
      </Suspense>
    </>
  );
}

async function ReviewsContent() {
  const result = await listAllOffers();
  if (!result.ok) return <ApiProblem result={result} context="chargement de la file de revue" />;
  const queue = reviewQueue(result.data.offers);

  return (
    <div className="space-y-4">
      {result.data.truncated && (
        <Notice title="File partielle">
          Seules les {result.data.offers.length} premières offres sont parcourues. Un endpoint de file de revue côté
          backend est nécessaire.
        </Notice>
      )}
      <p className="text-muted">
        {queue.length} preuve{queue.length > 1 ? "s" : ""} à vérifier, de la plus prioritaire à la moins prioritaire.
      </p>
      {queue.length === 0 ? (
        <EmptyState title="Aucune preuve en attente">Toutes les preuves enregistrées ont reçu une décision.</EmptyState>
      ) : (
        <div className="overflow-x-auto rounded-lg border border-border bg-surface">
          <table className="w-full min-w-[56rem] text-left">
            <thead className="border-b border-border bg-background text-xs uppercase tracking-wide text-muted">
              <tr>
                <th scope="col" className="px-3 py-2 font-medium">Offre</th>
                <th scope="col" className="px-3 py-2 font-medium">Type</th>
                <th scope="col" className="px-3 py-2 font-medium">Source</th>
                <th scope="col" className="px-3 py-2 font-medium">Consultée</th>
                <th scope="col" className="px-3 py-2 font-medium">Points d&apos;attention</th>
                <th scope="col" className="px-3 py-2 font-medium"><span className="sr-only">Action</span></th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {queue.map(({ offer, evidence, flags }) => (
                <tr key={evidence.id} className="align-top">
                  <td className="px-3 py-2">
                    <p className="font-medium">{offer.commercialName}</p>
                    <p className="text-xs text-muted">
                      {offer.provider.name} · {SECTOR_LABELS[offer.sector]}
                    </p>
                  </td>
                  <td className="px-3 py-2">
                    {evidence.evidenceScope ? <Badge tone="lavender">{EVIDENCE_SCOPE_LABELS[evidence.evidenceScope]}</Badge> : "—"}
                  </td>
                  <td className="max-w-72 px-3 py-2">
                    {evidence.officialUrl ? (
                      <a href={evidence.officialUrl} target="_blank" rel="noopener noreferrer" className="break-all text-lavender-strong underline">
                        {evidence.officialUrl}
                      </a>
                    ) : (
                      <span className="text-muted">Aucune URL</span>
                    )}
                    {evidence.provenance && <p className="mt-0.5 text-xs text-muted">{evidence.provenance}</p>}
                  </td>
                  <td className="whitespace-nowrap px-3 py-2">{evidence.consultedAt ? formatDateTime(evidence.consultedAt) : "Inconnue"}</td>
                  <td className="px-3 py-2">
                    {flags.length === 0 ? (
                      <span className="text-muted">—</span>
                    ) : (
                      <ul className="space-y-1">
                        {flags.map((flag) => (
                          <li key={flag}>
                            <Badge tone="warn">{flag}</Badge>
                          </li>
                        ))}
                      </ul>
                    )}
                  </td>
                  <td className="whitespace-nowrap px-3 py-2">
                    <TextLink href={`/catalog/offers/${offer.id}#preuves`}>Examiner</TextLink>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
