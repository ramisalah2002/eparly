import Link from "next/link";

export default function NotFound() {
  return (
    <main className="flex flex-1 flex-col items-center justify-center gap-3 px-4 py-16 text-center">
      <h1 className="text-xl font-semibold">Page ou ressource introuvable</h1>
      <p className="text-muted">Elle n&apos;existe pas, ou vous n&apos;y avez pas accès.</p>
      <Link href="/dashboard" className="font-medium text-lavender-strong underline">
        Retour au tableau de bord
      </Link>
    </main>
  );
}
