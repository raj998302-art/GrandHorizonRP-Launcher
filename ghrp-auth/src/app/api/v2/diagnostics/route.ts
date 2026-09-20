import { withDb } from "../../../../lib/db";
import { readJson, fail, ok } from "../../../../lib/api";

export const dynamic = "force-dynamic";

/**
 * POST /api/v2/diagnostics — automatic launcher/engine diagnostics.
 *
 * The launcher app reports device-side engine evidence (renderer info, asset
 * scan results, scene load status, load errors) so production issues can be
 * diagnosed from real devices without requiring the user to run adb.
 *
 * Payload: { kind: string, device?: {...}, app_version?: number, payload: {...} }
 * Stored additively in `launcher_diagnostics` (created on first use; the
 * gamemode ignores this table).
 */
export async function POST(req: Request) {
  const body = await readJson(req);
  const kind = typeof body.kind === "string" ? body.kind.slice(0, 64) : "";
  if (!kind) return fail("ErrorArgsMissing");
  let device = {};
  if (body.device && typeof body.device === "object") device = body.device;
  const app_version = typeof body.app_version === "number" ? body.app_version : 0;
  let payload = {};
  if (body.payload && typeof body.payload === "object") payload = body.payload;

  try {
    return await withDb(async (conn) => {
      await conn.query(
        `CREATE TABLE IF NOT EXISTS launcher_diagnostics (
           id INT AUTO_INCREMENT PRIMARY KEY,
           created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
           kind VARCHAR(64) NOT NULL,
           app_version INT NOT NULL DEFAULT 0,
           device VARCHAR(255) NOT NULL DEFAULT '',
           payload TEXT NOT NULL
         ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4`
      );
      const deviceStr = JSON.stringify(device).slice(0, 255);
      const payloadStr = JSON.stringify(payload).slice(0, 8000);
      await conn.query(
        "INSERT INTO launcher_diagnostics (kind, app_version, device, payload) VALUES (?, ?, ?, ?)",
        [kind, app_version, deviceStr, payloadStr]
      );
      return ok({ recorded: true });
    });
  } catch (e) {
    console.error("diagnostics POST error", e);
    return fail("ErrorInternalServerError", 500);
  }
}

/** GET /api/v2/diagnostics?limit=50&kind=engine — recent diagnostic records. */
export async function GET(req: Request) {
  const url = new URL(req.url);
  const limit = Math.min(parseInt(url.searchParams.get("limit") || "50", 10) || 50, 200);
  const kind = (url.searchParams.get("kind") || "").slice(0, 64);
  try {
    return await withDb(async (conn) => {
      const [rows] = await conn.query(
        kind
          ? "SELECT id, created_at, kind, app_version, device, payload FROM launcher_diagnostics WHERE kind = ? ORDER BY id DESC LIMIT ?"
          : "SELECT id, created_at, kind, app_version, device, payload FROM launcher_diagnostics ORDER BY id DESC LIMIT ?",
        kind ? [kind, limit] : [limit]
      );
      return ok({ records: rows });
    });
  } catch (e) {
    console.error("diagnostics GET error", e);
    return fail("ErrorInternalServerError", 500);
  }
}
