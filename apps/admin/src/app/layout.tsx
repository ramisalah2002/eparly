import type { Metadata } from "next";
import { Spline_Sans } from "next/font/google";
import "./globals.css";

// Même police que l'application mobile (apps/mobile/src/global.css, --font-display).
const splineSans = Spline_Sans({
  variable: "--font-spline",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "Eparly Admin",
  description: "Console d'administration du catalogue Eparly",
  robots: { index: false, follow: false },
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="fr" className={`${splineSans.variable} h-full antialiased`}>
      <body className="min-h-full flex flex-col font-sans text-sm">{children}</body>
    </html>
  );
}
