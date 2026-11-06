import Link from "next/link";
import { ChevronRight } from "lucide-react";
import { ProviderForm } from "@/components/providers/ProviderForm";

export const dynamic = "force-dynamic";

export default function NewProviderPage() {
  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <nav aria-label="Fil d’Ariane" className="flex items-center gap-2 text-sm text-zinc-500">
        <Link href="/catalog/providers" className="hover:text-zinc-900">Fournisseurs</Link>
        <ChevronRight size={15} />
        <span className="font-medium text-zinc-900">Nouveau fournisseur</span>
      </nav>
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-zinc-900">Nouveau fournisseur</h1>
        <p className="mt-1 text-sm text-zinc-500">
          Le fournisseur sera créé actif dans le catalogue par l’API d’administration.
        </p>
      </div>
      <ProviderForm />
    </div>
  );
}
