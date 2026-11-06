import Link from "next/link";
import { Suspense } from "react";
import { ApiProblem } from "@/components/api-problem";
import { buttonClass } from "@/components/button-class";
import { SectorIcon } from "@/components/sector-icon";
import { Badge, EmptyState, PageHeader, Skeleton, TextLink } from "@/components/ui";
import { formatDateTime, PROVIDER_STATUS_LABELS, SECTOR_LABELS } from "@/lib/format";
import { listProviders } from "@/lib/server/catalog";

export const metadata = { title: "Fournisseurs — Eparly Admin" };

const PAGE_SIZE = 20;

export default function ProvidersPage({ searchParams }: PageProps<"/catalog/providers">) {
  return (
    <>
      <PageHeader
        title="Fournisseurs"
        description="Opérateurs et services du catalogue, actifs ou non. Toute modification est journalisée et rend « Périmées » les offres publiées du fournisseur."
        actions={
          <Link href="/catalog/providers/new" className={buttonClass("primary")}>
            Nouveau fournisseur
          </Link>
        }
      />
      <Suspense fallback={<Skeleton rows={8} />}>
        <ProvidersContent searchParams={searchParams} />
      </Suspense>
    </>
  );
}

async function ProvidersContent({ searchParams }: { searchParams: PageProps<"/catalog/providers">["searchParams"] }) {
  const raw = (await searchParams).page;
  const parsed = Number.parseInt((Array.isArray(raw) ? raw[0] : raw) ?? "0", 10);
  const page = Number.isFinite(parsed) && parsed >= 0 ? parsed : 0;

  const result = await listProviders(page, PAGE_SIZE);
  if (!result.ok) return <ApiProblem result={result} context="chargement des fournisseurs" />;
  const { items, totalElements, totalPages } = result.data;

  if (totalElements === 0) {
    return (
      <EmptyState title="Aucun fournisseur">
        Créez le premier fournisseur pour pouvoir y rattacher des offres.
      </EmptyState>
    );
  }

  return (
    <div className="space-y-4">
      <p className="text-muted">
        {totalElements} fournisseur{totalElements > 1 ? "s" : ""}, triés par nom.
      </p>
      <div className="overflow-x-auto rounded-3xl border border-border bg-surface shadow-card">
        <table className="w-full min-w-[48rem] text-left">
          <thead className="border-b border-border bg-background text-xs uppercase tracking-wide text-muted">
            <tr>
              <th scope="col" className="px-3 py-2 font-medium">Fournisseur</th>
              <th scope="col" className="px-3 py-2 font-medium">Secteur</th>
              <th scope="col" className="px-3 py-2 font-medium">Pays</th>
              <th scope="col" className="px-3 py-2 font-medium">Site officiel</th>
              <th scope="col" className="px-3 py-2 font-medium">Statut</th>
              <th scope="col" className="px-3 py-2 font-medium">Modifié</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {items.map((provider) => (
              <tr key={provider.id} className="align-middle hover:bg-background/60">
                <td className="px-3 py-2">
                  <span className="flex items-center gap-3">
                    <SectorIcon sector={provider.sector} />
                    <TextLink href={`/catalog/providers/${provider.id}`}>{provider.name}</TextLink>
                  </span>
                </td>
                <td className="px-3 py-2">{SECTOR_LABELS[provider.sector]}</td>
                <td className="px-3 py-2">{provider.country ?? <span className="text-muted">—</span>}</td>
                <td className="max-w-64 truncate px-3 py-2 text-muted">{provider.officialWebsite ?? "—"}</td>
                <td className="px-3 py-2">
                  <Badge tone={provider.status === "ACTIVE" ? "ok" : "neutral"}>
                    {PROVIDER_STATUS_LABELS[provider.status]}
                  </Badge>
                </td>
                <td className="whitespace-nowrap px-3 py-2 text-muted">{formatDateTime(provider.updatedAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {totalPages > 1 && (
        <nav aria-label="Pagination" className="flex items-center justify-between">
          <span className="text-muted">
            Page {page + 1} sur {totalPages}
          </span>
          <span className="flex gap-2">
            {page > 0 ? (
              <Link href={page === 1 ? "/catalog/providers" : `/catalog/providers?page=${page - 1}`} className={buttonClass("secondary")}>
                Précédente
              </Link>
            ) : (
              <span className={`${buttonClass("secondary")} opacity-50`}>Précédente</span>
            )}
            {page + 1 < totalPages ? (
              <Link href={`/catalog/providers?page=${page + 1}`} className={buttonClass("secondary")}>
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
