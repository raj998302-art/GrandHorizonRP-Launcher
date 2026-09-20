import { readJson, str, fail, ok } from "../../../../../lib/api";
import { verify, sign, hashCode } from "../../../../../lib/state";

export const dynamic = "force-dynamic";

/**
 * POST /api/v2/recovery/validate-email  { code, state }
 */
export async function POST(req: Request) {
  const body = await readJson(req);
  const code = str(body.code);
  const stateTok = str(body.state);
  if (!code || !stateTok) return fail("ErrorArgsMissing");
  if (!/^\d{6}$/.test(code)) return fail("ErrorInvalidCode");

  const st = verify<{ flow?: string; email?: string; code_hash?: string; account_id?: number | null }>(stateTok);
  if (!st || st.flow !== "recovery" || !st.email || !st.code_hash) return fail("ErrorInvalidSession");
  if (hashCode(st.email, code) !== st.code_hash) return fail("ErrorInvalidCode");
  if (!st.account_id) return fail("ErrorUserNotFound", 404);

  const next = sign(
    { flow: "recovery", email: st.email, account_id: st.account_id, verified: true },
    15 * 60
  );
  return ok({ state: next });
}
