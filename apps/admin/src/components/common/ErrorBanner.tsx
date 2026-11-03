"use client";

export interface ErrorBannerProps {
  title: string;
  message?: string;
  status?: number;
  onDismiss?: () => void;
  retry?: () => void;
  retryLabel?: string;
}

export function ErrorBanner({
  title,
  message,
  status,
  onDismiss,
  retry,
  retryLabel,
}: ErrorBannerProps) {
  return (
    <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-red-800">
      <div className="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-3">
        <div className="flex gap-2">
          <span className="font-semibold">{title}</span>
          {status != null && (
            <span className="text-xs font-medium bg-red-100 px-2 py-0.5 rounded border border-red-200">
              {status}
            </span>
          )}
        </div>
        {message && <p className="text-sm">{message}</p>}
      </div>
      <div className="mt-3 flex items-center gap-2">
        {retry && retryLabel && (
          <button
            type="button"
            onClick={retry}
            className="text-sm underline underline-offset-4 hover:text-red-700"
          >
            {retryLabel}
          </button>
        )}
        {onDismiss && (
          <button
            type="button"
            onClick={onDismiss}
            className="text-sm underline underline-offset-4 hover:text-red-700"
          >
            Fermer
          </button>
        )}
      </div>
    </div>
  );
}
