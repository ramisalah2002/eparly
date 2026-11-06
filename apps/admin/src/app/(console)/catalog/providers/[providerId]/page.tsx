import Link from "next/link";
import { notFound } from "next/navigation";
import { Suspense } from "react";
import { ApiProblem } from "@/components/api-problem";
import { AuditCard } from "@/components/audit-card";
import { buttonClass } from "@/components/button-class";
import { Badge, Card, DefinitionList, PageHeader, Skeleton, TextLink } from "@/components/ui";
import { formatDateTime, PROVIDER_STATUS_LABELS, SECTOR_LABELS, UNKNOWN_LABEL } from "@/lib/format";
import { countProviderOffers, getProvider, getProviderAudit } from "@/lib/server/catalog";
import { ProviderStatusAction } from "./provider-status-action";

export const metadata = { title: "Fiche fournisseur — Eparly Admin" };

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export default function ProviderPage({ params }: PageProps<"/catalog/providers/[providerId]">) {
  return (
    <>
      <p className="mb-2">
        <TextLink href="/catalog/providers">← Retour aux fournisseurs</TextLink>
      </p>
      <Suspense fallback={<Skeleton rows={8} />}>
        <ProviderContent params={params} />
      </Suspense>
    </>
  );
}

async function ProviderContent({ params }: { params: PageProps<"/catalog/providers/[providerId]">["params"] }) {
  const { providerId } = await params;
  if (!UUID_RE.test(providerId)) notFound();

  const [providerResult, auditResult, offersResult, publishedResult] = await Promise.all([
    getProvider(providerId),
    getProviderAudit(providerId),
    countProviderOffers(providerId),
    countProviderOffers(providerId, "VERIFIED"),
  ]);
  if (!providerResult.ok) {
    if (providerResult.status === 404) notFound();
    return <ApiProblem result={providerResult} context="chargement du fournisseur" />;
  }

  const provider = providerResult.data;
  const offerCount = offersResult.ok ? offersResult.data : null;
  const publishedCount = publishedResult.ok ? publishedResult.data : null;

  return (
    <div className="space-y-6">
      <PageHeader
        title={provider.name}
        description={
          <span className="flex flex-wrap items-center gap-2">
            <span>{SECTOR_LABELS[provider.sector]}</span>
            <Badge tone={provider.status === "ACTIVE" ? "ok" : "neutral"}>{PROVIDER_STATUS_LABELS[provider.status]}</Badge>
          </span>
        }
        actions={
          <div className="flex flex-wrap gap-2">
            <Link href={`/catalog/providers/${provider.id}/edit`} className={buttonClass("secondary")}>
              Modifier
            </Link>
            <ProviderStatusAction
              providerId={provider.id}
              status={provider.status}
              version={provider.version}
              publishedCount={publishedCount}
            />
          </div>
        }
      />

      <div className="grid items-start gap-6 xl:grid-cols-[3fr_2fr]">
        <Card title="Informations">
          <DefinitionList
            items={[
              ["Nom", provider.name],
              ["Secteur", SECTOR_LABELS[provider.sector]],
              ["Pays", provider.country ?? <span className="text-muted">{UNKNOWN_LABEL}</span>],
              [
                "Site officiel",
                provider.officialWebsite ? (
                  <a href={provider.officialWebsite} target="_blank" rel="noopener noreferrer" className="break-all text-lavender-strong underline">
                    {provider.officialWebsite}
                  </a>
                ) : (
                  <span className="text-muted">{UNKNOWN_LABEL}</span>
                ),
              ],
              ["Créé le", formatDateTime(provider.createdAt)],
              ["Modifié le", formatDateTime(provider.updatedAt)],
              ["Version", <span key="v" className="tabular-nums">{provider.version}</span>],
            ]}
          />
        </Card>

        <Card title="Offres rattachées">
          {offerCount === null ? (
            <p className="text-muted">Décompte non disponible.</p>
          ) : (
            <>
              <p className="text-3xl font-semibold tabular-nums">{offerCount}</p>
              <p className="text-muted">
                offre{offerCount > 1 ? "s" : ""}
                {publishedCount !== null && ` · ${publishedCount} publiée${publishedCount > 1 ? "s" : ""}`}
              </p>
              {offerCount > 0 && (
                <p className="mt-3">
                  <TextLink href={`/catalog/offers?providerId=${provider.id}`}>Voir les offres</TextLink>
                </p>
              )}
            </>
          )}
        </Card>
      </div>

      <AuditCard title="Journal d'audit du fournisseur" audit={auditResult.ok ? auditResult.data : null} />
    </div>
  );
}
