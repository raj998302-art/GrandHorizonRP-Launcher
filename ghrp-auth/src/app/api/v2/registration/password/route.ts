import { withDb } from "../../../../../lib/db";
import { readJson, str, fail, ok, validPassword, clientMeta } from "../../../../../lib/api";
import { createAccount, findByEmail } from "../../../../../lib/accounts";
import { verify } from "../../../../../lib/state";
import { issueFrontToken } from "../../../../../lib/tokens";

export const dynamic = "force-dynamic";

/**
 * POST /api/v2/registration/password  { password, state }
 * Completes registration: creates the game account row
 * (name generated, sha256(password+salt), email, confirm_email=1)
 * and returns the front_token for Android.initToken.
 */
export async function POST(req: Request) {
  const body = await readJson(req);
  const password = str(body.password);
  const stateTok = str(body.state);
  if (!password || !stateTok) return fail("ErrorArgsMissing");

  const pwErr = validPassword(password);
  if (pwErr) return fail(pwErr);

  const st = verify<{ flow?: string; email?: string; step?: string; verified?: boolean }>(stateTok);
  if (!st || st.flow !== "registration" || !st.email || !st.verified) return fail("ErrorInvalidSession");

  try {
    const { ip } = clientMeta(req);
    return await withDb(async (conn) => {
      const existing = await findByEmail(conn, st.email!);
      if (existing) return fail("ErrorEmailTaken");
      const acc = await createAccount(conn, { email: st.email!, password, ip, kind: "user" });
      const front_token = issueFrontToken({
        account_uuid: `acc-${acc.id}`,
        name: acc.name,
        email: acc.email,
        kind: "user",
      });
      return ok({
        front_token,
        account: { name: acc.name, email: acc.email },
        message: "RegistrationComplete",
      });
    });
  } catch (e) {
    console.error("registration/password error", e);
    return fail("ErrorInternalServerError", 500);
  }
}
