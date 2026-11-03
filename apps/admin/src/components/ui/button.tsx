"use client";

import * as React from "react";
import { cn } from "@/lib/utils";

export interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: "default" | "destructive" | "outline" | "secondary" | "ghost" | "link";
  size?: "default" | "sm" | "lg";
}

export const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant, size, ...props }, ref) => {
    return (
      <button
        ref={ref}
        className={cn(
          "inline-flex items-center justify-center rounded-md text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50",
          variant === "default" &&
            "bg-zinc-900 text-white hover:bg-zinc-800",
          variant === "destructive" &&
            "bg-red-600 text-white hover:bg-red-700",
          variant === "outline" &&
            "border border-zinc-200 bg-white hover:bg-zinc-100",
          variant === "secondary" &&
            "bg-zinc-100 text-zinc-900 hover:bg-zinc-200",
          variant === "ghost" && "hover:bg-zinc-100 hover:text-zinc-900",
          variant === "link" && "text-primary underline-offset-4 hover:underline",
          size === "default" && "h-10 px-4 py-2",
          size === "sm" && "h-9 px-3",
          size === "lg" && "h-11 px-8",
          className,
        )}
        {...props}
      />
    );
  },
);
Button.displayName = "Button";
