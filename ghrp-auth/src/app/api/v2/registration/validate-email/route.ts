import { readJson, str, fail, ok } from "../../../../../lib/api";
import { verify, sign, hashCode } from "../../../../../lib/state";

export const dynamic = "force-dynamic";

/**
 * POST /api/v2/registration/validate-email  { code, state }
 * Verifies the 6-digit code against the signed state; advances to the password step.
 */
export async function POST(req: Request) {
  const body = await readJson(req);
  const code = str(body.code);
  const stateTok = str(body.state);
  if (!code || !stateTok) return fail("ErrorArgsMissing");
  if (!/^\d{6}$/.test(code)) return fail("ErrorInvalidCode");

  const st = verify<{ flow?: string; email?: string; code_hash?: string; step?: string }>(stateTok);
  if (!st || st.flow !== "registration" || !st.email || !st.code_hash) return fail("ErrorInvalidSession");

  if (hashCode(st.email, code) !== st.code_hash) return fail("ErrorInvalidCode");

  const next = sign({ flow: "registration", email: st.email, step: "password", verified: true }, 15 * 60);
  return ok({ state: next });
}
