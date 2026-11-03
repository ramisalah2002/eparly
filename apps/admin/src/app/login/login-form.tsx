"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { useState, type FormEvent } from "react";
import { buttonClass } from "@/components/button-class";
import { bffMutation } from "@/lib/client/bff";

/** N'accepte qu'un chemin interne comme destination après connexion (pas de redirection ouverte). */
function safeNext(value: string | null): string {
  if (!value || !value.startsWith("/") || value.startsWith("//") || value.startsWith("/api/")) return "/dashboard";
  return value;
}

export function LoginForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
    const form = new FormData(event.currentTarget);
    setBusy(true);
    setError(null);
    const result = await bffMutation("/api/auth/login", "POST", {
      email: String(form.get("email") ?? ""),
      password: String(form.get("password") ?? ""),
    });
    if (result.ok) {
      router.replace(safeNext(searchParams.get("next")));
      router.refresh();
      return;
    }
    setError(result.message);
    setBusy(false);
  }

  return (
    <form onSubmit={onSubmit} className="space-y-4" noValidate>
      <div>
        <label htmlFor="email" className="block font-medium">
          Email
        </label>
        <input
          id="email"
          name="email"
          type="email"
          autoComplete="username"
          required
          className="mt-1 w-full rounded-md border border-border bg-surface px-3 py-2"
        />
      </div>
      <div>
        <label htmlFor="password" className="block font-medium">
          Mot de passe
        </label>
        <input
          id="password"
          name="password"
          type="password"
          autoComplete="current-password"
          required
          className="mt-1 w-full rounded-md border border-border bg-surface px-3 py-2"
        />
      </div>
      {error && (
        <p role="alert" className="rounded-md bg-danger-soft px-3 py-2 text-danger">
          {error}
        </p>
      )}
      <button type="submit" className={`${buttonClass("primary")} w-full py-2`} disabled={busy} aria-busy={busy}>
        {busy ? "Connexion…" : "Se connecter"}
      </button>
    </form>
  );
}
