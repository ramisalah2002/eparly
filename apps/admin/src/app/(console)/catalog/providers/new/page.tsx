import { Card, PageHeader, TextLink } from "@/components/ui";
import { ProviderForm } from "../provider-form";

export const metadata = { title: "Nouveau fournisseur — Eparly Admin" };

export default function NewProviderPage() {
  return (
    <>
      <p className="mb-2">
        <TextLink href="/catalog/providers">← Retour aux fournisseurs</TextLink>
      </p>
      <PageHeader
        title="Nouveau fournisseur"
        description="Le nom doit être unique dans son secteur. La création est enregistrée dans le journal d'audit."
      />
      <Card className="max-w-2xl">
        <ProviderForm provider={null} offerCount={0} publishedCount={0} />
      </Card>
    </>
  );
}
