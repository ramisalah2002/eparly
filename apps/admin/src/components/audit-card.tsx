import { formatDateTime } from "@/lib/format";
import type { CatalogAudit } from "@/lib/types";
import { Card } from "./ui";

/** Journal d'audit d'une ressource du catalogue, du plus récent au plus ancien. */
export function AuditCard({ title, audit }: { title: string; audit: CatalogAudit[] | null }) {
  return (
    <Card title={title}>
      {audit === null ? (
        <p className="text-muted">Journal non disponible.</p>
      ) : audit.length === 0 ? (
        <p className="text-muted">Aucun événement.</p>
      ) : (
        <ul className="divide-y divide-border">
          {[...audit]
            .sort((a, b) => b.occurredAt.localeCompare(a.occurredAt))
            .map((event) => (
              <li key={event.id} className="py-2">
                <div className="flex flex-wrap justify-between gap-2">
                  <code className="font-mono text-xs font-semibold">{event.action}</code>
                  <span className="text-muted">{formatDateTime(event.occurredAt)}</span>
                </div>
                <p className="mt-0.5 text-xs text-muted">
                  Acteur : <code className="font-mono">{event.actorUserId ?? "système"}</code>
                </p>
                {event.details && (
                  <details className="mt-1">
                    <summary className="cursor-pointer text-xs text-lavender-strong">Détails</summary>
                    <pre className="mt-1 overflow-x-auto rounded bg-background p-2 font-mono text-xs">
                      {JSON.stringify(event.details, null, 2)}
                    </pre>
                  </details>
                )}
              </li>
            ))}
        </ul>
      )}
    </Card>
  );
}
