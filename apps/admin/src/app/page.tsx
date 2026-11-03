"use client";

export const dynamic = "force-dynamic";

import { useEffect } from "react";
import { useRouter } from "next/navigation";

export default function RootPage() {
  const router = useRouter();

  useEffect(() => {
    const token = localStorage.getItem("eparly_admin_access_token");
    if (token) {
      router.replace("/catalog/offers");
    } else {
      router.replace("/login");
    }
  }, [router]);

  return null;
}
