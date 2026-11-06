import { notFound } from "next/navigation";
import { Suspense } from "react";
import { ApiProblem } from "@/components/api-problem";
import { Card, PageHeader, Skeleton, TextLink } from "@/components/ui";
import { countProviderOffers, getProvider } from "@/lib/server/catalog";
import { ProviderForm } from "../../provider-form";

export const metadata = { title: "Modifier le fournisseur — Eparly Admin" };

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export default function EditProviderPage({ params }: PageProps<"/catalog/providers/[providerId]/edit">) {
  return (
    <Suspense fallback={<Skeleton rows={6} />}>
      <EditProviderContent params={params} />
    </Suspense>
  );
}

async function EditProviderContent({ params }: { params: PageProps<"/catalog/providers/[providerId]/edit">["params"] }) {
  const { providerId } = await params;
  if (!UUID_RE.test(providerId)) notFound();

  const [providerResult, offersResult, publishedResult] = await Promise.all([
    getProvider(providerId),
    countProviderOffers(providerId),
    countProviderOffers(providerId, "VERIFIED"),
  ]);
  if (!providerResult.ok) {
    if (providerResult.status === 404) notFound();
    return <ApiProblem result={providerResult} context="chargement du fournisseur" />;
  }
  const provider = providerResult.data;

  return (
    <>
      <p className="mb-2">
        <TextLink href={`/catalog/providers/${provider.id}`}>← Retour à la fiche</TextLink>
      </p>
      <PageHeader
        title={`Modifier ${provider.name}`}
        description="Seuls les champs modifiés sont envoyés. Si quelqu'un a modifié ce fournisseur entre-temps, l'enregistrement est refusé."
      />
      <Card className="max-w-2xl">
        <ProviderForm
          provider={provider}
          offerCount={offersResult.ok ? offersResult.data : null}
          publishedCount={publishedResult.ok ? publishedResult.data : null}
        />
      </Card>
    </>
  );
}
