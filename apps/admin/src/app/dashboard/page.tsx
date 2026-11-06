"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import {
  LayoutDashboard,
  ShieldCheck,
  ShieldAlert,
  ShieldCheck as ShieldCheckIcon,
  Clock,
  Archive,
  Folder,
  Users,
  Building2,
  RefreshCw,
  ChevronRight,
} from "lucide-react";
import { apiRequest } from "@/lib/api";
import { Button } from "@/components/ui/button";

interface SummaryData {
  totalOffers: number;
  draftOffers: number;
  publishedOffers: number;
  archivedOffers: number;
  staleOffers: number;
  expiredOffers: number;
  providersCount: number;
  pendingEvidences: number;
  needsReview: number;
}

interface StatCardProps {
  title: string;
  value: number | string;
  icon: React.ComponentType<{ size?: number }>;
  color: "blue" | "green" | "amber" | "red" | "zinc";
  href?: string;
}

function StatCard({ title, value, icon: Icon, color, href }: StatCardProps) {
  const colorClasses: Record<string, string> = {
    blue: "bg-blue-50 text-blue-700 border-blue-200",
    green: "bg-green-50 text-green-700 border-green-200",
    amber: "bg-amber-50 text-amber-700 border-amber-200",
    red: "bg-red-50 text-red-700 border-red-200",
    zinc: "bg-zinc-50 text-zinc-700 border-zinc-200",
  };

  return (
    <div
      className={`rounded-lg border p-6 ${colorClasses[color]}`}
    >
      <div className="flex items-center justify-between">
        <div>
          <p className="text-sm text-zinc-500">{title}</p>
          <p className="text-2xl font-bold">{value}</p>
        </div>
        <div className={`p-3 rounded-lg ${colorClasses[color].replace("bg-", "bg-").replace("text-", "text-")}`}>
          <Icon size={20} />
        </div>
      </div>
      {href && (
        <Link
          href={href}
          className="mt-4 block text-xs font-medium text-zinc-500 hover:text-zinc-700 underline underline-offset-4"
        >
          Voir les détails →
        </Link>
      )}
    </div>
  );
}

export default function DashboardPage() {
  const [summary, setSummary] = useState<SummaryData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let mounted = true;
    async function loadSummary() {
      try {
        setLoading(true);
        setError(null);
        const data = await apiRequest<SummaryData>("/api/admin/catalog/summary");
        if (mounted) {
          setSummary(data);
        }
      } catch (err) {
        if (mounted) {
          const isAuthError =
            err instanceof Error &&
            "status" in err &&
            (err as { status: number }).status === 401;
          setError(
            isAuthError
              ? "Session expirée. Veuillez vous reconnecter."
              : "Impossible de charger les indicateurs du catalogue.",
          );
        }
      } finally {
        if (mounted) setLoading(false);
      }
    }
    loadSummary();
    return () => {
      mounted = false;
    };
  }, []);

  if (loading) {
    return (
      <div className="flex h-screen flex-col lg:flex-row overflow-hidden bg-zinc-50">
        <aside className="hidden lg:flex lg:w-64 flex-col border-r border-zinc-200 bg-white z-20">
          <div className="flex h-16 items-center justify-between border-b border-zinc-200 bg-zinc-900 px-6">
            <span className="font-bold text-white tracking-tight">Eparly Admin</span>
          </div>
        </aside>
        <div className="flex-1 flex flex-col overflow-hidden">
          <header className="flex h-16 items-center justify-between border-b border-zinc-200 bg-white px-6 lg:px-8 shrink-0">
            <h1 className="text-sm font-semibold text-zinc-900">Tableau de bord</h1>
          </header>
          <main className="flex-1 overflow-y-auto p-6 lg:p-8">
            <div className="flex h-[400px] items-center justify-center">
              <div className="flex flex-col items-center gap-3">
                <div className="h-8 w-8 rounded-full bg-zinc-200 animate-pulse" />
                <p className="text-sm text-zinc-500">Chargement des indicateurs…</p>
              </div>
            </div>
          </main>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex h-screen flex-col lg:flex-row overflow-hidden bg-zinc-50">
        <aside className="hidden lg:flex lg:w-64 flex-col border-r border-zinc-200 bg-white z-20">
          <div className="flex h-16 items-center justify-between border-b border-zinc-200 bg-zinc-900 px-6">
            <span className="font-bold text-white tracking-tight">Eparly Admin</span>
          </div>
        </aside>
        <div className="flex-1 flex flex-col overflow-hidden">
          <header className="flex h-16 items-center justify-between border-b border-zinc-200 bg-white px-6 lg:px-8 shrink-0">
            <h1 className="text-sm font-semibold text-zinc-900">Tableau de bord</h1>
          </header>
          <main className="flex-1 overflow-y-auto p-6 lg:p-8">
            <div className="rounded-lg border border-zinc-200 bg-white p-8 text-center">
              <ShieldAlert size={32} className="mx-auto text-red-400 mb-4" />
              <h3 className="text-sm font-medium text-zinc-900">{error}</h3>
              <Button
                onClick={() => window.location.reload()}
                className="mt-4"
              >
                Réessayer
              </Button>
            </div>
          </main>
        </div>
      </div>
    );
  }

  if (!summary) {
    return null;
  }

  return (
    <div className="flex h-screen flex-col lg:flex-row overflow-hidden bg-zinc-50">
      <aside className="hidden lg:flex lg:w-64 flex-col border-r border-zinc-200 bg-white z-20">
        <div className="flex h-16 items-center justify-between border-b border-zinc-200 bg-zinc-900 px-6">
          <span className="font-bold text-white tracking-tight">Eparly Admin</span>
        </div>
        <nav className="flex-1 px-4 py-6 space-y-1 overflow-y-auto">
          <Link
            href="/dashboard"
            className="block flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium bg-blue-600 text-white"
          >
            <LayoutDashboard size={18} />
            Tableau de bord
          </Link>
          <Link
            href="/catalog/offers"
            className="block flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors text-zinc-600 hover:bg-zinc-100 hover:text-zinc-900"
          >
            <Folder size={18} />
            Offres
          </Link>
          <Link
            href="/catalog/providers"
            className="block flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors text-zinc-600 hover:bg-zinc-100 hover:text-zinc-900"
          >
            <Building2 size={18} />
            Fournisseurs
          </Link>
          <Link
            href="/catalog/reviews"
            className="block flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors text-zinc-600 hover:bg-zinc-100 hover:text-zinc-900"
          >
            <ShieldCheck size={18} />
            Centre de validation
          </Link>
          <div className="mt-8 pt-4 border-t border-zinc-200">
            <button
              type="button"
              onClick={() => {
                apiRequest("/api/auth/logout", { method: "POST" }).catch(() => {});
                // eslint-disable-next-line @next/next/no-location-assign-relative-destination
                window.location.href = "/login";
              }}
              className="flex w-full items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium text-zinc-600 hover:bg-zinc-100 transition-colors"
            >
              <LogOutIcon size={18} />
              Déconnexion
            </button>
          </div>
        </nav>
      </aside>

      <div className="flex-1 flex flex-col overflow-hidden">
        <header className="flex h-16 items-center justify-between border-b border-zinc-200 bg-white px-6 lg:px-8 shrink-0">
          <div className="flex items-center gap-3">
            <ChevronRight size={16} className="text-zinc-400" />
            <h1 className="text-sm font-semibold text-zinc-900">Tableau de bord</h1>
          </div>
          <div className="flex items-center gap-2">
            <Button
              onClick={() => window.location.reload()}
              size="sm"
              variant="outline"
            >
              <RefreshCw size={16} className="mr-2" />
              Actualiser
            </Button>
          </div>
        </header>

        <main className="flex-1 overflow-y-auto p-6 lg:p-8">
          <div className="mb-6">
            <h2 className="text-lg font-bold text-zinc-900">Vue d'ensemble du catalogue</h2>
            <p className="text-sm text-zinc-500">
              Indicateurs en temps réel depuis le catalogue PostgreSQL géré par l'administration Eparly.
            </p>
          </div>

          {/* Indicateurs principaux */}
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
            <StatCard
              title="Offres totales"
              value={summary.totalOffers}
              icon={LayoutDashboard}
              color="blue"
              href="/catalog/offers"
            />
            <StatCard
              title="Brouillons"
              value={summary.draftOffers}
              icon={Folder}
              color="amber"
              href="/catalog/offers?status=DRAFT"
            />
            <StatCard
              title="Publiées"
              value={summary.publishedOffers}
              icon={ShieldCheckIcon}
              color="green"
              href="/catalog/offers?status=VERIFIED"
            />
            <StatCard
              title="Archivées"
              value={summary.archivedOffers}
              icon={Archive}
              color="zinc"
              href="/catalog/offers?status=ARCHIVED"
            />
            <StatCard
              title="Fournisseurs"
              value={summary.providersCount}
              icon={Users}
              color="blue"
            />
            <StatCard
              title="Preuves en attente"
              value={summary.pendingEvidences}
              icon={Clock}
              color="amber"
              href="/catalog/reviews"
            />
            <StatCard
              title="À examiner"
              value={summary.needsReview}
              icon={ShieldAlert}
              color="red"
              href="/catalog/reviews"
            />
            <StatCard
              title="Obsolètes"
              value={summary.staleOffers}
              icon={ShieldAlert}
              color="amber"
              href="/catalog/offers?status=STALE"
            />
          </div>

          {/* Centre d'actions prioritaires */}
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            {/* Offres à examiner */}
            <div className="rounded-lg border border-zinc-200 bg-white p-6">
              <div className="flex items-center justify-between mb-4">
                <h3 className="text-sm font-semibold text-zinc-900">
                  Offres à examiner
                </h3>
                <Link
                  href="/catalog/reviews"
                  className="text-xs font-medium text-blue-600 hover:underline"
                >
                  Voir toutes →
                </Link>
              </div>
              <div className="space-y-3">
                <div className="flex items-center justify-between p-3 rounded bg-zinc-50">
                  <div>
                    <p className="text-sm font-medium text-zinc-900">
                      {summary.needsReview} offre(s)
                    </p>
                    <p className="text-xs text-zinc-500">
                      Brouillons, obsolètes, expirées ou preuves en attente
                    </p>
                  </div>
                  <Link
                    href="/catalog/reviews"
                    className="text-xs font-medium text-blue-600 hover:underline"
                  >
                    Consulter
                  </Link>
                </div>
              </div>
            </div>

            {/* Alertes du catalogue */}
            <div className="rounded-lg border border-zinc-200 bg-white p-6">
              <h3 className="text-sm font-semibold text-zinc-900 mb-4">
                Alertes du catalogue
              </h3>
              <div className="space-y-3">
                {summary.pendingEvidences > 0 && (
                  <div className="flex items-start gap-3 p-3 rounded bg-amber-50">
                    <ShieldAlert size={16} className="mt-0.5 text-amber-600" />
                    <div>
                      <p className="text-sm font-medium text-zinc-900">
                        {summary.pendingEvidences} preuve(s) en attente
                      </p>
                      <p className="text-xs text-zinc-500">
                        Validation humaine requise avant publication
                      </p>
                    </div>
                  </div>
                )}
                {summary.draftOffers > 0 && (
                  <div className="flex items-start gap-3 p-3 rounded bg-amber-50">
                    <Folder size={16} className="mt-0.5 text-amber-600" />
                    <div>
                      <p className="text-sm font-medium text-zinc-900">
                        {summary.draftOffers} brouillon(s)
                      </p>
                      <p className="text-xs text-zinc-500">
                        Non encore publiées dans le catalogue
                      </p>
                    </div>
                  </div>
                )}
                {summary.expiredOffers > 0 && (
                  <div className="flex items-start gap-3 p-3 rounded bg-red-50">
                    <Clock size={16} className="mt-0.5 text-red-600" />
                    <div>
                      <p className="text-sm font-medium text-zinc-900">
                        {summary.expiredOffers} offre(s) expirée(s)
                      </p>
                      <p className="text-xs text-zinc-500">
                        Période de validité dépassée
                      </p>
                    </div>
                  </div>
                )}
                {summary.pendingEvidences === 0 && summary.draftOffers === 0 && summary.expiredOffers === 0 && (
                  <div className="p-3 rounded bg-green-50">
                    <ShieldCheckIcon size={16} className="mt-0.5 text-green-600" />
                    <div>
                      <p className="text-sm font-medium text-zinc-900">
                        Aucune alerte active
                      </p>
                      <p className="text-xs text-zinc-500">
                        Le catalogue est à jour
                      </p>
                    </div>
                  </div>
                )}
              </div>
            </div>

            {/* Actions rapides */}
            <div className="rounded-lg border border-zinc-200 bg-white p-6">
              <h3 className="text-sm font-semibold text-zinc-900 mb-4">
                Actions rapides
              </h3>
              <div className="space-y-2">
                <Link
                  href="/catalog/providers"
                  className="flex items-center gap-3 rounded-lg border border-zinc-200 p-3 transition-colors hover:bg-zinc-50"
                >
                  <Building2 size={16} />
                  <div>
                    <p className="text-sm font-medium text-zinc-900">Gérer les fournisseurs</p>
                    <p className="text-xs text-zinc-500">Consulter les partenaires du catalogue</p>
                  </div>
                  <ChevronRight size={14} className="text-zinc-400" />
                </Link>
                <Link
                  href="/catalog/offers"
                  className="flex items-center gap-3 p-3 rounded-lg border border-zinc-200 hover:bg-zinc-50 transition-colors"
                >
                  <Folder size={16} />
                  <div>
                    <p className="text-sm font-medium text-zinc-900">
                      Consulter toutes les offres
                    </p>
                    <p className="text-xs text-zinc-500">
                      Liste complète du catalogue PostgreSQL
                    </p>
                  </div>
                  <ChevronRight size={14} className="text-zinc-400" />
                </Link>
                <Link
                  href="/catalog/reviews"
                  className="flex items-center gap-3 p-3 rounded-lg border border-zinc-200 hover:bg-zinc-50 transition-colors"
                >
                  <ShieldCheck size={16} />
                  <div>
                    <p className="text-sm font-medium text-zinc-900">
                      Centre de validation
                    </p>
                    <p className="text-xs text-zinc-500">
                      Examiner les preuves en attente
                    </p>
                  </div>
                  <ChevronRight size={14} className="text-zinc-400" />
                </Link>
              </div>
            </div>
          </div>
        </main>
      </div>
    </div>
  );
}

function LogOutIcon({ size = 16 }: { size?: number }) {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
      <polyline points="16 17 21 12 16 7" />
      <line x1="21" x2="9" y1="12" y2="12" />
    </svg>
  );
}
