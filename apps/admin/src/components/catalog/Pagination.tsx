"use client";

import { ChevronLeft, ChevronRight } from "lucide-react";

interface PaginationProps {
  page: number;
  totalPages: number;
  totalElements: number;
  size: number;
  onPageChange: (page: number) => void;
}

export function Pagination({
  page,
  totalPages,
  totalElements,
  size,
  onPageChange,
}: PaginationProps) {
  if (totalPages <= 1) return null;

  const start = Math.min(page * size + 1, totalElements);
  const end = Math.min((page + 1) * size, totalElements);

  return (
    <nav className="flex items-center justify-between border-t border-zinc-200 bg-zinc-50 px-4 py-3">
      <p className="text-xs text-zinc-500">
        Affichage de <span className="font-medium">{start}</span> à{" "}
        <span className="font-medium">{end}</span> sur{" "}
        <span className="font-medium">{totalElements}</span> résultat{totalElements > 1 ? "s" : ""}
      </p>
      <div className="flex items-center gap-1">
        <button
          type="button"
          onClick={() => onPageChange(Math.max(0, page - 1))}
          disabled={page === 0}
          className="px-3 py-1.5 text-sm rounded-lg border disabled:opacity-50 disabled:pointer-events-none transition-colors"
        >
          <ChevronLeft size={16} /> Précédent
        </button>
        <span className="px-3 py-1.5 text-sm font-medium text-zinc-700 border border-zinc-200 rounded-lg">
          Page {page + 1} sur {totalPages}
        </span>
        <button
          type="button"
          onClick={() => onPageChange(Math.min(totalPages - 1, page + 1))}
          disabled={page >= totalPages - 1}
          className="px-3 py-1.5 text-sm rounded-lg border disabled:opacity-50 disabled:pointer-events-none transition-colors"
        >
          Suivant <ChevronRight size={16} />
        </button>
      </div>
    </nav>
  );
}
