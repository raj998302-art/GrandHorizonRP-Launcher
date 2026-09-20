import { withDb } from "../../../../../lib/db";
import { fail, ok, clientMeta } from "../../../../../lib/api";
import { createAccount } from "../../../../../lib/accounts";
import { issueFrontToken } from "../../../../../lib/tokens";
import crypto from "crypto";

export const dynamic = "force-dynamic";

/**
 * POST /api/v2/registration/guest  {}
 * Guest registration (allow_guest_auth): creates a guest game account
 * with a generated name and a random password, returns front_token.
 */
export async function POST(req: Request) {
  try {
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
      // guest_secret returned once to the creating device (re-auth contract).
      return ok({
        front_token,
        guest_secret: guestPass,
        account: { name: acc.name, email: acc.email },
        message: "GuestCreated",
      });
    });
  } catch (e) {
    console.error("registration/guest error", e);
    return fail("ErrorInternalServerError", 500);
  }
}
