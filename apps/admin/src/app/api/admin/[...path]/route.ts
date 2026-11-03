import { NextResponse, type NextRequest } from "next/server";
import { resolveAdminRoute } from "@/lib/bff-policy";
import { callBackend } from "@/lib/server/backend";
import { publicationEnabled } from "@/lib/server/config";
import { bffError, readJsonBody, rejectUnsafeMutation } from "@/lib/server/request-guard";

async function forward(request: NextRequest, context: RouteContext<"/api/admin/[...path]">) {
  const rejected = rejectUnsafeMutation(request);
  if (rejected) return rejected;

  const { path } = await context.params;
  const decision = resolveAdminRoute(request.method, path);
  if (!decision.allowed) {
    return bffError(decision.status, "Action non disponible dans le back-office.");
  }
  if (decision.publication && !publicationEnabled()) {
    return bffError(
      403,
      "Publication désactivée sur cet environnement (EPARLY_ADMIN_ALLOW_PUBLISH). Aucune offre n'a été modifiée.",
    );
  }

  const body = await readJsonBody(request);
  if (body === undefined) {
    return bffError(400, "Corps de requête invalide.");
  }

  const result = await callBackend<unknown>(decision.backendPath, {
    method: request.method as "POST" | "PUT" | "PATCH",
    body: body ?? undefined,
  });
  if (result.ok) {
    return result.data === null ? new NextResponse(null, { status: 204 }) : NextResponse.json(result.data, { status: result.status });
  }
  const status = result.status || 502;
  return NextResponse.json(
    { status, message: result.error?.message ?? null },
    { status },
  );
}

export const POST = forward;
export const PUT = forward;
export const PATCH = forward;
