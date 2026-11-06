import {
  proxiedRequest,
  ALLOWED_METHODS,
} from "@/lib/proxy-handler";

const SEGMENT_PREFIX = "/v1/offers";

async function getPath(params: Promise<{ path: string[] }>): Promise<string> {
  const { path } = await params;
  return `${SEGMENT_PREFIX}/${path.join("/")}`;
}

export async function GET(
  request: Request,
  context: { params: Promise<{ path: string[] }> },
) {
  const path = await getPath(context.params);
  return proxiedRequest(request, path, ALLOWED_METHODS["/api/offers"]);
}
