import type { ReactNode } from "react";
import { niceCeiling } from "@/lib/dashboard-data";

// Graphes en HTML/SVG, rendus côté serveur. Spécifications : barres ≤ 24 px, extrémité
// arrondie de 4 px et base carrée, écart de 2 px entre barres voisines, grille en filet
// 1 px, textes en couleurs de texte (jamais la couleur de la série), légende dès deux
// séries, info-bulle au survol / focus, et vue tableau pour chaque graphe.

export interface Series {
  key: string;
  label: string;
  /** Classe Tailwind de fond de la série (bg-chart-1, bg-chart-2). */
  colorClass: string;
}

export interface ColumnDatum {
  key: string;
  label: string;
  fullLabel: string;
  values: Record<string, number>;
  highlight?: boolean;
}

export function Legend({ series }: { series: Series[] }) {
  return (
    <ul className="flex flex-wrap items-center gap-4 text-xs text-muted">
      {series.map((s) => (
        <li key={s.key} className="flex items-center gap-1.5">
          <span aria-hidden className={`size-2 rounded-full ${s.colorClass}`} />
          {s.label}
        </li>
      ))}
    </ul>
  );
}

export function DataTable({ caption, columns, rows }: { caption: string; columns: string[]; rows: ReactNode[][] }) {
  return (
    <details className="mt-3 text-xs">
      <summary className="cursor-pointer text-muted hover:text-foreground">Voir les données</summary>
      <table className="mt-2 w-full text-left">
        <caption className="sr-only">{caption}</caption>
        <thead className="text-muted">
          <tr>
            {columns.map((c) => (
              <th key={c} scope="col" className="py-1 pr-3 font-medium">
                {c}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="tabular-nums">
          {rows.map((row, i) => (
            <tr key={i} className="border-t border-border">
              {row.map((cell, j) => (
                <td key={j} className="py-1 pr-3">
                  {cell}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </details>
  );
}

const PLOT_HEIGHT = 168;

/** Histogramme groupé (une seule échelle verticale, partagée par toutes les séries). */
export function ColumnChart({ data, series, caption }: { data: ColumnDatum[]; series: Series[]; caption: string }) {
  const max = Math.max(0, ...data.flatMap((d) => series.map((s) => d.values[s.key] ?? 0)));
  const top = niceCeiling(max);
  const ticks = [top, top / 2, 0];

  return (
    <figure>
      <div className="flex gap-2">
        <div className="relative w-6 shrink-0 text-right text-[11px] tabular-nums text-muted" style={{ height: PLOT_HEIGHT }} aria-hidden>
          {ticks.map((t, i) => (
            <span key={t} className="absolute right-0 -translate-y-1/2" style={{ top: `${(i / (ticks.length - 1)) * 100}%` }}>
              {Number.isInteger(t) ? t : ""}
            </span>
          ))}
        </div>
        <div className="relative min-w-0 flex-1">
          <div className="pointer-events-none absolute inset-x-0 top-0" style={{ height: PLOT_HEIGHT }} aria-hidden>
            {ticks.map((t, i) => (
              <div key={t} className="absolute inset-x-0 border-t border-border" style={{ top: `${(i / (ticks.length - 1)) * 100}%` }} />
            ))}
          </div>
          <ol className="relative flex" aria-label={caption}>
            {data.map((d, index) => {
              const summary = series.map((s) => `${s.label} : ${d.values[s.key] ?? 0}`).join(", ");
              return (
                <li key={d.key} className="group relative flex flex-1 flex-col items-center">
                  <div
                    tabIndex={0}
                    aria-label={`${d.fullLabel} — ${summary}`}
                    className={`relative flex w-full items-end justify-center gap-0.5 rounded-t-xl outline-none ${
                      d.highlight ? "bg-lavender-soft/70" : "group-hover:bg-lavender-soft/40"
                    }`}
                    style={{ height: PLOT_HEIGHT }}
                  >
                    {series.map((s) => {
                      const value = d.values[s.key] ?? 0;
                      if (value === 0) return <span key={s.key} className="w-3.5" aria-hidden />;
                      return (
                        <span
                          key={s.key}
                          aria-hidden
                          className={`w-3.5 max-w-6 rounded-t-[4px] ${s.colorClass}`}
                          style={{ height: `${(value / top) * 100}%` }}
                        />
                      );
                    })}
                    <span
                      role="tooltip"
                      className={`pointer-events-none absolute top-2 z-10 hidden w-max rounded-xl border border-border bg-surface px-3 py-2 text-xs shadow-card group-hover:block group-focus-within:block ${
                        // Ancrée vers l'intérieur du graphe pour ne jamais déborder de la carte.
                        index < 2 ? "left-0" : index >= data.length - 2 ? "right-0" : "left-1/2 -translate-x-1/2"
                      }`}
                    >
                      <span className="block font-semibold capitalize">{d.fullLabel}</span>
                      {series.map((s) => (
                        <span key={s.key} className="mt-0.5 flex items-center gap-1.5 text-muted">
                          <span aria-hidden className={`size-2 rounded-full ${s.colorClass}`} />
                          {s.label}
                          <span className="ml-auto pl-3 font-semibold tabular-nums text-foreground">{d.values[s.key] ?? 0}</span>
                        </span>
                      ))}
                    </span>
                  </div>
                  <span
                    className={`mt-2 rounded-full px-2 py-0.5 text-xs capitalize ${
                      d.highlight ? "border border-border bg-surface font-semibold text-foreground shadow-card" : "text-muted"
                    }`}
                  >
                    {d.label}
                  </span>
                </li>
              );
            })}
          </ol>
        </div>
      </div>
      <DataTable
        caption={caption}
        columns={["Mois", ...series.map((s) => s.label)]}
        rows={data.map((d) => [<span key="m" className="capitalize">{d.fullLabel}</span>, ...series.map((s) => d.values[s.key] ?? 0)])}
      />
    </figure>
  );
}

/** Jauge circulaire : la piste est un cran plus clair de la même teinte que le remplissage. */
export function ProgressRing({ percent, label, size = 56 }: { percent: number | null; label: string; size?: number }) {
  const stroke = 5;
  const radius = (size - stroke) / 2;
  const circumference = 2 * Math.PI * radius;
  const value = percent ?? 0;
  return (
    <div className="relative shrink-0" style={{ width: size, height: size }} role="img" aria-label={label}>
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} className="-rotate-90" aria-hidden>
        <circle cx={size / 2} cy={size / 2} r={radius} fill="none" stroke="var(--lavender-soft)" strokeWidth={stroke} />
        {value > 0 && (
          <circle
            cx={size / 2}
            cy={size / 2}
            r={radius}
            fill="none"
            stroke="var(--chart-1)"
            strokeWidth={stroke}
            strokeLinecap="round"
            strokeDasharray={`${(value / 100) * circumference} ${circumference}`}
          />
        )}
      </svg>
      <span className="absolute inset-0 flex items-center justify-center text-xs font-semibold">
        {percent === null ? "—" : `${percent}%`}
      </span>
    </div>
  );
}

/** Barres horizontales, une seule série : pas de légende, le titre de la carte la nomme. */
export function HorizontalBars({ items, caption }: { items: { key: string; label: string; value: number }[]; caption: string }) {
  const max = Math.max(1, ...items.map((i) => i.value));
  return (
    <figure>
      <ul className="space-y-3" aria-label={caption}>
        {items.map((item) => (
          <li key={item.key} className="grid grid-cols-[7rem_1fr_2rem] items-center gap-3">
            <span className="text-muted">{item.label}</span>
            <span className="h-2 rounded-full bg-lavender-soft" aria-hidden>
              {item.value > 0 && (
                <span className="block h-2 rounded-full bg-chart-1" style={{ width: `${(item.value / max) * 100}%` }} />
              )}
            </span>
            <span className="text-right font-semibold tabular-nums">{item.value}</span>
          </li>
        ))}
      </ul>
    </figure>
  );
}
