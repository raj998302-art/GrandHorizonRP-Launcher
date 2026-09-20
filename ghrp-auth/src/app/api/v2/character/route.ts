import { withDb } from "../../../../lib/db";
import { readJson, fail, ok } from "../../../../lib/api";
import { verifyFrontToken, verifyAccessToken } from "../../../../lib/tokens";

export const dynamic = "force-dynamic";

/**
 * Character persistence for the launcher (server-authoritative).
 *
 * Contract (accounts table, same DB the SA-MP gamemode reads):
 *   accounts.sex       0 male / 1 female
 *   accounts.skin      SA-MP skin id (0..311) as chosen in the native
 *                      character-creation preview
 *   accounts.launcher_char_created  tinyint marker (added 2026-02, additive,
 *                      ignored by the gamemode) — distinguishes "created via
 *                      launcher" from the seeded default 0/0.
 *
 * Auth: Authorization: Bearer <front_token|access_token>.
 * The account id travels inside the signed token claims (account_uuid = "acc-<id>").
 */

interface CharRow {
  name: string;
  sex: number;
  skin: number;
  launcher_char_created: number;
}

function bearerAccount(req: Request): { id: number; kind: string } | null {
  const h = req.headers.get("authorization") || "";
  const m = h.match(/^Bearer\s+(.+)$/i);
  if (!m) return null;
  const token = m[1].trim();
  let claims = verifyFrontToken(token);
  if (!claims) claims = verifyAccessToken(token);
  if (!claims) return null;
  const uuid = String(claims.account_uuid || "");
  const mm = uuid.match(/^acc-(\d+)$/);
  if (!mm) return null;
  return { id: Number(mm[1]), kind: String(claims.kind || "user") };
}

async function loadCharacter(conn: any, accountId: number): Promise<CharRow | null> {
  const [rows] = await conn.query(
    "SELECT name, sex, skin, launcher_char_created FROM accounts WHERE id = ? LIMIT 1",
    [accountId]
  );
  const list = rows as CharRow[];
  return list.length ? list[0] : null;
}

function charPayload(row: CharRow) {
  return {
    has_character: row.launcher_char_created === 1,
    character: {
      name: row.name,
      sex: row.sex === 1 ? "female" : "male",
      sex_id: row.sex,
      skin: row.skin,
    },
  };
}

export async function GET(req: Request) {
  const acc = bearerAccount(req);
  if (!acc) return fail("ErrorTokenNotFound", 401);
  try {
    return await withDb(async (conn) => {
      const row = await loadCharacter(conn, acc.id);
      if (!row) return fail("ErrorUserNotFound", 404);
      return ok({ ...charPayload(row), account: { account_uuid: `acc-${acc.id}` } });
    });
  } catch (e) {
    console.error("character GET error", e);
    return fail("ErrorInternalServerError", 500);
  }
}

export async function POST(req: Request) {
  const acc = bearerAccount(req);
  if (!acc) return fail("ErrorTokenNotFound", 401);
  const body = await readJson(req);
  const sexRaw = body.sex;
  const skinRaw = body.skin;
  const nameRaw = typeof body.name === "string" ? body.name.trim() : "";

  let sex: number;
  if (sexRaw === 0 || sexRaw === "0" || sexRaw === "male") sex = 0;
  else if (sexRaw === 1 || sexRaw === "1" || sexRaw === "female") sex = 1;
  else return fail("ErrorArgsMissing");

  const skin = typeof skinRaw === "number" ? skinRaw : parseInt(String(skinRaw ?? ""), 10);
  if (!Number.isFinite(skin) || skin < 0 || skin > 311) return fail("ErrorArgsMissing");

  // Optional character-name step (required flow: preview -> NAME -> confirm ->
  // save). SA-MP name rules: 3..24 chars, letters/digits/underscore, at least
  // one letter; "Firstname_Lastname" recommended. When omitted the generated
  // account name is kept.
  let name: string | null = null;
  if (nameRaw.length > 0) {
    if (!/^[A-Za-z0-9_]{3,24}$/.test(nameRaw) || !/[A-Za-z]/.test(nameRaw)) {
      return fail("ErrorInvalidName");
    }
    name = nameRaw;
  }

  try {
    return await withDb(async (conn) => {
      const row = await loadCharacter(conn, acc.id);
      if (!row) return fail("ErrorUserNotFound", 404);
      if (name !== null) {
        // Name uniqueness (SA-MP login names must be unique).
        const [dupe] = await conn.query(
          "SELECT id FROM accounts WHERE name = ? AND id <> ? LIMIT 1",
          [name, acc.id]
        );
        if ((dupe as unknown[]).length > 0) return fail("ErrorNameTaken", 409);
      }
      await conn.query(
        `UPDATE accounts SET sex = ?, skin = ?, launcher_char_created = 1${name !== null ? ", name = ?" : ""} WHERE id = ?`,
        name !== null ? [sex, skin, name, acc.id] : [sex, skin, acc.id]
      );
      const updated = await loadCharacter(conn, acc.id);
      return ok({ ...charPayload(updated!), saved: true });
    });
  } catch (e) {
    console.error("character POST error", e);
    return fail("ErrorInternalServerError", 500);
  }
}
