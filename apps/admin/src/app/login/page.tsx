import { Suspense } from "react";
import { LoginForm } from "./login-form";

export const metadata = { title: "Connexion — Eparly Admin" };

export default function LoginPage() {
  return (
    <main className="flex flex-1 items-center justify-center px-4 py-16">
      <div className="w-full max-w-sm">
        <div className="mb-6 text-center">
          <p className="text-xs font-semibold uppercase tracking-widest text-lavender-strong">Eparly</p>
          <h1 className="mt-1 text-xl font-semibold">Console d&apos;administration</h1>
          <p className="mt-2 text-muted">Accès réservé aux comptes disposant de la permission CATALOG_ADMIN.</p>
        </div>
        <div className="rounded-lg border border-border bg-surface p-6">
          <Suspense>
            <LoginForm />
          </Suspense>
        </div>
      </div>
    </main>
  );
}
