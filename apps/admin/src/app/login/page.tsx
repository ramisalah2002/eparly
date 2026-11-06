import { Suspense } from "react";
import { EparlyLogo } from "@/components/eparly-logo";
import { LoginForm } from "./login-form";

export const metadata = { title: "Connexion — Eparly Admin" };

export default function LoginPage() {
  return (
    <main className="flex flex-1 items-center justify-center bg-[radial-gradient(ellipse_at_top,var(--lavender-soft),transparent_60%)] px-4 py-16">
      <div className="w-full max-w-sm">
        <div className="mb-6 flex flex-col items-center text-center">
          <EparlyLogo width={150} />
          <h1 className="mt-3 text-xl font-semibold tracking-tight">Console d&apos;administration</h1>
          <p className="mt-2 text-muted">Accès réservé aux comptes disposant de la permission CATALOG_ADMIN.</p>
        </div>
        <div className="rounded-3xl border border-border bg-surface p-6 shadow-card">
          <Suspense>
            <LoginForm />
          </Suspense>
        </div>
      </div>
    </main>
  );
}
