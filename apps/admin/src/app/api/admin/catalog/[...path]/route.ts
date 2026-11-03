import {
  proxiedRequest,
  ALLOWED_METHODS,
} from "@/lib/proxy-handler";

const SEGMENT_PREFIX = "/v1/admin/catalog";

async function getPath(params: Promise<{ path: string[] }>): Promise<string> {
  const { path } = await params;
  return `${SEGMENT_PREFIX}/${path.join("/")}`;
}

export async function GET(
  request: Request,
  context: { params: Promise<{ path: string[] }> },
) {
  const path = await getPath(context.params);
  return proxiedRequest(request, path, ALLOWED_METHODS["/api/admin/catalog"]);
}

export async function POST(
  request: Request,
  context: { params: Promise<{ path: string[] }> },
) {
  const path = await getPath(context.params);
  return proxiedRequest(request, path, ALLOWED_METHODS["/api/admin/catalog"]);
}

export async function PATCH(
  request: Request,
  context: { params: Promise<{ path: string[] }> },
) {
  const path = await getPath(context.params);
  return proxiedRequest(request, path, ALLOWED_METHODS["/api/admin/catalog"]);
}

export async function PUT(
  request: Request,
  context: { params: Promise<{ path: string[] }> },
) {
  const path = await getPath(context.params);
  return proxiedRequest(request, path, ALLOWED_METHODS["/api/admin/catalog"]);
}

export async function DELETE(
  request: Request,
  context: { params: Promise<{ path: string[] }> },
) {
  const path = await getPath(context.params);
  return proxiedRequest(request, path, ALLOWED_METHODS["/api/admin/catalog"]);
}
