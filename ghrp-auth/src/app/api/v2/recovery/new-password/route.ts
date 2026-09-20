import { withDb } from "../../../../../lib/db";
import { readJson, str, fail, ok, validPassword } from "../../../../../lib/api";
import { updatePassword } from "../../../../../lib/accounts";
import { verify } from "../../../../../lib/state";

export const dynamic = "force-dynamic";

/**
 * POST /api/v2/recovery/new-password  { password, state }
 * Sets the new password (sha256(password+salt) with a fresh salt).
 */
export async function POST(req: Request) {
  const body = await readJson(req);
  const password = str(body.password);
  const stateTok = str(body.state);
  if (!password || !stateTok) return fail("ErrorArgsMissing");

  const pwErr = validPassword(password);
  if (pwErr) return fail(pwErr);

  const st = verify<{ flow?: string; account_id?: number; verified?: boolean }>(stateTok);
  if (!st || st.flow !== "recovery" || !st.account_id || !st.verified) return fail("ErrorInvalidSession");

  try {
    return await withDb(async (conn) => {
      await updatePassword(conn, st.account_id!, password);
      return ok({ message: "PasswordChanged" });
    });
  } catch (e) {
    console.error("recovery/new-password error", e);
    return fail("ErrorInternalServerError", 500);
  }
}
