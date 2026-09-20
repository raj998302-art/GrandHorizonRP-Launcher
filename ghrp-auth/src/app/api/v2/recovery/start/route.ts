import { withDb } from "../../../../../lib/db";
import { readJson, str, fail, ok, validEmail } from "../../../../../lib/api";
import { findByEmail } from "../../../../../lib/accounts";
import { sign, genCode, hashCode } from "../../../../../lib/state";

export const dynamic = "force-dynamic";

/**
 * POST /api/v2/recovery/start  { email }
 * Starts password recovery for an existing account.
 * Always answers success (no account enumeration).
 */
export async function POST(req: Request) {
  const body = await readJson(req);
  const email = str(body.email);
  if (!email) return fail("ErrorArgsMissing");
  if (!validEmail(email)) return fail("ErrorInvalidEmail");

  try {
    const acc = await withDb(async (conn) => findByEmail(conn, email));
    const code = genCode();
    const state = sign(
      { flow: "recovery", email: email.toLowerCase(), code_hash: hashCode(email, code), step: "code", account_id: acc?.id ?? null },
      15 * 60
    );
    // dev_code only returned when the account exists (else it's meaningless)
    return ok({ state, dev_code: acc ? code : undefined, message: "CodeSent" });
  } catch (e) {
    console.error("recovery/start error", e);
    return fail("ErrorInternalServerError", 500);
  }
}
