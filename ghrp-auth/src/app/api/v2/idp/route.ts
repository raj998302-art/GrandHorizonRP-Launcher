import { ok } from "../../../../lib/api";

export const dynamic = "force-dynamic";

/**
 * GET /api/v2/idp/
 * Identity providers list. The original offered Google/Telegram/Apple IDP.
 * We keep the endpoint + contract but expose no providers yet
 * (each would need its own OAuth app credentials).
 */
export async function GET() {
  return ok({ providers: [], enabled: false });
}

export async function POST() {
  return ok({ providers: [], enabled: false });
}
