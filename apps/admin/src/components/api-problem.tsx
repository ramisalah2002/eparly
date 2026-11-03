import { redirect } from "next/navigation";
import { describeApiError } from "@/lib/format";
import type { ApiErrorBody } from "@/lib/types";
import { Notice } from "./ui";

/**
 * Affiche un échec d'appel API en distinguant réseau, accès refusé, absence et erreur serveur.
 * Un 401 (session expirée malgré le refresh du proxy) renvoie vers la connexion.
 */
export function ApiProblem({
  result,
  context,
}: {
  result: { status: number; error: ApiErrorBody | null };
  context: string;
}) {
  if (result.status === 401) redirect("/login");
  const tone = result.status === 0 || result.status >= 500 ? "danger" : "warn";
  const title =
    result.status === 0
      ? "API injoignable"
      : result.status === 403
        ? "Accès refusé"
        : result.status === 404
          ? "Introuvable"
          : result.status >= 500
            ? "Erreur serveur"
            : "Requête refusée";
  return (
    <Notice tone={tone} title={`${title} — ${context}`}>
      {describeApiError(result.status, result.error?.message)}
    </Notice>
  );
}
