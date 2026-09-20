import { withDb } from "../../../../../lib/db";
import { readJson, str, fail, ok, validEmail, clientMeta } from "../../../../../lib/api";
import { findByEmail } from "../../../../../lib/accounts";
import { sign, genCode, hashCode } from "../../../../../lib/state";

export const dynamic = "force-dynamic";

/**
 * POST /api/v2/registration/start  { email }
 * Starts registration: checks the email is free and issues the verification code.
 * The state (email + code hash) travels signed in `state`.
 *
 * Email delivery: no SMTP provider is wired (serverless), so the code is
 * returned in `dev_code` and shown in the UI with a clear notice.
 */
export async function POST(req: Request) {
  const body = await readJson(req);
  const email = str(body.email);
  if (!email) return fail("ErrorArgsMissing");
  if (!validEmail(email)) return fail("ErrorInvalidEmail");

  try {
    const exists = await withDb(async (conn) => findByEmail(conn, email));
    if (exists) return fail("ErrorEmailTaken");

    const code = genCode();
    const state = sign(
      { flow: "registration", email: email.toLowerCase(), code_hash: hashCode(email, code), step: "code" },
      15 * 60
    );
    return ok({ state, dev_code: code, message: "CodeSent" });
  } catch (e) {
    console.error("registration/start error", e);
    return fail("ErrorInternalServerError", 500);
  }
}
