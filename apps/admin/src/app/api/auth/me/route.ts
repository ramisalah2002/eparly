import { NextResponse } from "next/server";
import { getCurrentAdmin } from "@/lib/server/identity";

export async function GET() {
  const identity = await getCurrentAdmin();
  if (!identity.ok) {
    return NextResponse.json({ status: identity.status }, { status: identity.status || 502 });
  }
  return NextResponse.json(identity.data);
}
