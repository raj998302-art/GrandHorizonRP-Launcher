import { withDb } from "../../../../../lib/db";
import { readJson, str, fail, ok, clientMeta } from "../../../../../lib/api";
import { verifyPassword, createAccount, touchLogin, findByEmail } from "../../../../../lib/accounts";
import { issueFrontToken, verifyFrontToken, issueAccessToken } from "../../../../../lib/tokens";
import { genCode } from "../../../../../lib/state";
import crypto from "crypto";

export const dynamic = "force-dynamic";

/**
 * POST /api/v2/auth/token
 *
 * OAuth-style token endpoint used by BOTH the web SSO and the native engine:
 *   grant_type "password"   -> { username (email), password }  => front_token
 *   grant_type "guest"      -> {}                              => front_token (guest account)
 *   grant_type "front_token"-> { front_token }                 => access_token (engine session init)
 */
export async function POST(req: Request) {
  const body = await readJson(req);
  const grant = str(body.grant_type) || "password";

  try {
    if (grant === "password") {
      const username = str(body.username);
      const password = str(body.password);
      if (!username || !password) return fail("ErrorArgsMissing");
      const { ip } = clientMeta(req);
      return await withDb(async (conn) => {
        const acc = await verifyPassword(conn, username, password);
        if (!acc) return fail("ErrorInvalidCredentials", 401);
        await touchLogin(conn, acc.id, ip);
        const front_token = issueFrontToken({
          account_uuid: `acc-${acc.id}`,
          name: acc.name,
          email: acc.email,
          kind: "user",
        });
        return ok({ front_token, token_type: "Bearer", expires_in: 12 * 3600, account: { name: acc.name, email: acc.email } });
      });
    }

    if (grant === "guest") {
      const { ip } = clientMeta(req);
      return await withDb(async (conn) => {
        const guestPass = crypto.randomBytes(12).toString("hex");
        const email = `guest_${Date.now()}_${crypto.randomBytes(4).toString("hex")}@guest.ghrp.local`;
        const acc = await createAccount(conn, { email, password: guestPass, ip, kind: "guest" });
        const front_token = issueFrontToken({
          account_uuid: `acc-${acc.id}`,
          name: acc.name,
          email: acc.email,
          kind: "guest",
        });
        // guest_secret: the generated account password, returned ONCE to the
        // creating device so it can re-auth (grant_type=password) after the
        // front_token expires. Store only on the device, never in logs.
        return ok({
          front_token,
          guest_secret: guestPass,
          token_type: "Bearer",
          expires_in: 12 * 3600,
          account: { name: acc.name, email: acc.email },
        });
      });
    }

    if (grant === "front_token") {
      const ft = str(body.front_token);
      if (!ft) return fail("ErrorArgsMissing");
      const claims = verifyFrontToken(ft);
      if (!claims) return fail("ErrorTokenNotFound", 401);
      const access_token = issueAccessToken(claims);
      return ok({
        access_token,
        refresh_token: ft,
        token_type: "Bearer",
        expires_in: 3600,
        account: { account_uuid: claims.account_uuid, name: claims.name, email: claims.email },
      });
    }

    return fail("ErrorUnsupportedGrantType");
  } catch (e) {
    console.error("auth/token error", e);
    return fail("ErrorInternalServerError", 500);
  }
}
