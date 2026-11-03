export function buttonClass(variant: "primary" | "secondary" | "danger"): string {
  const base =
    "inline-flex items-center justify-center rounded-xl px-3 py-1.5 text-sm font-medium transition-colors disabled:cursor-not-allowed disabled:opacity-50";
  switch (variant) {
    case "primary":
      return `${base} bg-lavender-strong text-white hover:bg-lavender-strong/90`;
    case "danger":
      return `${base} bg-danger text-white hover:bg-danger/90`;
    default:
      return `${base} border border-border bg-surface text-foreground hover:bg-background`;
  }
}
