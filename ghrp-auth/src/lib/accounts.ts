import { genSalt, passHash } from "./state";
import { uniqueName } from "./names";
import { uuid } from "./tokens";

/**
 * Account service — the ONLY place that writes accounts rows.
 * Contract (laird.amx / reytize_fixed.sql):
 *   accounts.name     varchar(24)   character name (SA-MP login name)
 *   accounts.password varchar(65)   SHA256(password + salt), uppercase hex
 *   accounts.salt     varchar(10)   random 10 chars
 *   accounts.email    varchar(61)
 *   accounts.sex      int           0 male / 1 female (chosen later in native creation)
 *   accounts.skin     int           chosen later in native character creation
 *   reg_time / reg_ip / last_ip / last_login set at creation
 */

export interface AccountRow {
  id: number;
  name: string;
  email: string;
}

export async function findByEmail(conn: any, email: string): Promise<AccountRow | null> {
  const [rows] = await conn.query(
    "SELECT id, name, email FROM accounts WHERE LOWER(email) = ? LIMIT 1",
    [email.toLowerCase()]
  );
  const list = rows as AccountRow[];
  return list.length ? list[0] : null;
}

export async function findByName(conn: any, name: string): Promise<AccountRow | null> {
  const [rows] = await conn.query(
    "SELECT id, name, email FROM accounts WHERE name = ? LIMIT 1",
    [name]
  );
  const list = rows as AccountRow[];
  return list.length ? list[0] : null;
}

export async function verifyPassword(conn: any, email: string, password: string): Promise<AccountRow | null> {
  const [rows] = await conn.query(
    "SELECT id, name, email, password, salt FROM accounts WHERE LOWER(email) = ? LIMIT 1",
    [email.toLowerCase()]
  );
  const list = rows as (AccountRow & { password: string; salt: string })[];
  if (!list.length) return null;
  const acc = list[0];
  const hash = passHash(password, acc.salt);
  if (hash !== acc.password.toUpperCase()) return null;
  return acc;
}

export async function createAccount(
  conn: any,
  opts: { email: string; password: string; ip: string; kind: "user" | "guest" }
): Promise<AccountRow> {
  const name = await uniqueName(conn);
  const salt = genSalt();
  const password = passHash(opts.password, salt);
  const now = Math.floor(Date.now() / 1000);
  await conn.query(
    `INSERT INTO accounts
       (name, password, salt, email, confirm_email, level, exp, refer, sex, skin,
        money, bank, admin, reg_time, reg_ip, last_ip, last_login, golod)
     VALUES (?, ?, ?, ?, 1, 1, 0, 0, 0, 0, 0, 0, 0, ?, ?, ?, ?, 100)`,
    [name, password, salt, opts.email, now, opts.ip, opts.ip, now]
  );
  const [rows] = await conn.query(
    "SELECT id, name, email FROM accounts WHERE LOWER(email) = ? ORDER BY id DESC LIMIT 1",
    [opts.email.toLowerCase()]
  );
  const acc = (rows as AccountRow[])[0];
  return acc;
}

export async function updatePassword(conn: any, accountId: number, password: string): Promise<void> {
  const salt = genSalt();
  const hash = passHash(password, salt);
  await conn.query("UPDATE accounts SET password = ?, salt = ? WHERE id = ?", [hash, salt, accountId]);
}

export async function touchLogin(conn: any, accountId: number, ip: string): Promise<void> {
  const now = Math.floor(Date.now() / 1000);
  await conn.query("UPDATE accounts SET last_login = ?, last_ip = ? WHERE id = ?", [now, ip, accountId]);
}

export function accountUuid(id: number): string {
  // Stable per-account identifier for the engine's account_uuid field.
  return `ghrp-${String(id).padStart(8, "0")}-${uuid().slice(0, 8)}`;
}
