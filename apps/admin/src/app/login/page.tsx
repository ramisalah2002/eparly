"use client";

export const dynamic = "force-dynamic";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { apiRequest, clearLegacyTokens } from "@/lib/api";
import { loginSchema, type LoginInput } from "@/lib/validations";
import { Button } from "@/components/ui/button";
import { Lock, Mail, AlertCircle, Eye, EyeOff } from "lucide-react";
import { HttpError } from "@/lib/api";

export default function LoginPage() {
  const router = useRouter();
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [showPassword, setShowPassword] = useState(false);

  // Nettoyer les anciens tokens localStorage (migration vers cookies HttpOnly)
  useEffect(() => {
    clearLegacyTokens();
  }, []);

  const form = useForm<LoginInput>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: "", password: "" },
  });

  const onSubmit = async (values: LoginInput) => {
    setError(null);
    setSubmitting(true);
    try {
      // Appel via le BFF Next.js (pas d'appel direct Spring Boot depuis le client)
      const res = await apiRequest("/api/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(values),
      });

      if ((res as { success?: boolean }).success) {
        // Les tokens sont dans des cookies HttpOnly (définis par le BFF)
        // Rien n'est stocké dans localStorage ni exposé au JavaScript
        router.push("/dashboard");
      } else {
        setError("Réponse d'authentification inattendue.");
        setSubmitting(false);
      }
    } catch (err) {
      setSubmitting(false);
      if (err instanceof HttpError) {
        if (err.status === 401) {
          setError("Identifiants invalides. Vérifiez votre email et votre mot de passe.");
        } else if (err.status === 403) {
          setError("Accès refusé : vous n'avez pas la permission CATALOG_ADMIN.");
        } else {
          setError(`Erreur ${err.status} à ${err.message}`);
        }
      } else {
        setError("Impossible de contacter le serveur. Vérifiez votre connexion.");
      }
    }
  };

  return (
    <div className="flex min-h-screen items-center justify-center bg-zinc-50 px-4">
      <div className="w-full max-w-sm">
        {/* En-tête de marque */}
        <div className="mx-auto mb-8">
          <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-indigo-600 text-white">
            <span className="text-lg font-bold tracking-tight">E</span>
          </div>
          <h2 className="mt-6 text-2xl font-bold text-center text-zinc-900">
            Bienvenue sur Eparly Admin
          </h2>
          <p className="mt-2 text-center text-sm text-zinc-500">
            Connectez-vous pour gérer votre plateforme.
          </p>
        </div>

        {/* Carte de connexion */}
        <div className="rounded-xl border border-zinc-200 bg-white p-8 shadow-sm">
          {error && (
            <div className="mb-6 rounded-lg bg-red-50 p-3.5 text-sm text-red-800 border border-red-200 flex items-start gap-2">
              <AlertCircle size={16} className="mt-0.5" />
              <span>{error}</span>
            </div>
          )}

          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-5">
            {/* Champ email */}
            <div className="space-y-2">
              <label htmlFor="email" className="block text-sm font-semibold text-zinc-700">
                Adresse email
              </label>
              <div className="relative">
                <Mail size={18} className="absolute left-3 top-1/2 -translate-y-1/2 text-zinc-400" />
                <input
                  id="email"
                  {...form.register("email")}
                  type="email"
                  autoComplete="username"
                  placeholder="vous@exemple.fr"
                  className="block w-full pl-10 pr-3 py-3 text-sm border border-zinc-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 outline-none transition-colors"
                />
              </div>
              {form.formState.errors.email && (
                <p className="text-xs text-red-600">{form.formState.errors.email.message}</p>
              )}
            </div>

            {/* Champ mot de passe */}
            <div className="space-y-2">
              <label htmlFor="password" className="block text-sm font-semibold text-zinc-700">
                Mot de passe
              </label>
              <div className="relative">
                <Lock size={18} className="absolute left-3 top-1/2 -translate-y-1/2 text-zinc-400" />
                <input
                  id="password"
                  {...form.register("password")}
                  type={showPassword ? "text" : "password"}
                  autoComplete="current-password"
                  placeholder="Mot de passe"
                  className="block w-full pl-10 pr-10 py-3 text-sm border border-zinc-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 outline-none transition-colors"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-zinc-400 hover:text-zinc-600 focus:outline-none"
                  aria-label={showPassword ? "Masquer le mot de passe" : "Afficher le mot de passe"}
                >
                  {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                </button>
              </div>
              {form.formState.errors.password && (
                <p className="text-xs text-red-600">{form.formState.errors.password.message}</p>
              )}
            </div>

            {/* Bouton principal */}
            <Button
              type="submit"
              className="w-full bg-indigo-600 text-white hover:bg-indigo-700 focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 disabled:opacity-70 disabled:pointer-events-none"
              disabled={submitting}
            >
              {submitting ? (
                <div className="flex items-center justify-center gap-2">
                  <svg className="animate-spin h-4 w-4 text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l-3-3a7.975 7.975 0 011.063-10.938l3 3a7.962 7.962 0 017.937 7.937z"></path>
                  </svg>
                  Connexion en cours…
                </div>
              ) : (
                "Se connecter"
              )}
            </Button>
          </form>

          {/* Message informatif */}
          <p className="mt-8 text-xs text-center text-zinc-500">
            Seuls les comptes détenant la permission{" "}
            <span className="font-medium text-zinc-700">CATALOG_ADMIN</span>{" "}
            peuvent accéder à ce module.
          </p>
        </div>
      </div>
    </div>
  );
}
