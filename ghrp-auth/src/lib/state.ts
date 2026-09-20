import crypto from "crypto";

/**
 * Stateless, HMAC-signed flow state.
 *
 * The original SSO keeps the in-progress registration/recovery session on the
 * server between steps (email -> code -> password). On serverless we cannot use
 * in-memory state, so the state travels with the client, signed with
 * TOKEN_SECRET. It binds: flow, email, the verification code hash and expiry.
 */

const SECRET = () => process.env.TOKEN_SECRET || "dev-secret";

function b64url(buf: Buffer): string {
  return buf.toString("base64").replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function fromB64url(s: string): Buffer {
  s = s.replace(/-/g, "+").replace(/_/g, "/");
  while (s.length % 4) s += "=";
  return Buffer.from(s, "base64");
}

export function sign(payload: object, ttlSec: number): string {
  const body = { ...payload, exp: Date.now() + ttlSec * 1000 };
  const data = b64url(Buffer.from(JSON.stringify(body), "utf8"));
  const sig = b64url(crypto.createHmac("sha256", SECRET()).update(data).digest());
  return `${data}.${sig}`;
}

export function verify<T = Record<string, unknown>>(token: string | undefined | null): T | null {
  if (!token || typeof token !== "string" || !token.includes(".")) return null;
  const [data, sig] = token.split(".");
  if (!data || !sig) return null;
  const expected = b64url(crypto.createHmac("sha256", SECRET()).update(data).digest());
  const a = Buffer.from(sig);
  const b = Buffer.from(expected);
  if (a.length !== b.length || !crypto.timingSafeEqual(a, b)) return null;
  try {
    const body = JSON.parse(fromB64url(data).toString("utf8"));
    if (typeof body.exp !== "number" || body.exp < Date.now()) return null;
    return body as T;
  } catch {
    return null;
  }
}

export function hashCode(email: string, code: string): string {
  return crypto.createHmac("sha256", SECRET()).update(`${email.toLowerCase()}|${code}`).digest("hex");
}

export function genCode(): string {
  return String(crypto.randomInt(100000, 1000000));
}

export function genSalt(): string {
  // 10 printable ASCII chars, matching the gamemode's salt column (varchar(10))
  const chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789:[]<>?@!";
  let out = "";
  for (let i = 0; i < 10; i++) out += chars[crypto.randomInt(chars.length)];
  return out;
}

export function passHash(password: string, salt: string): string {
  // The gamemode uses SHA256_PassHash(input, salt, hash, 65): sha256(password + salt), uppercase hex
  return crypto.createHash("sha256").update(password + salt, "utf8").digest("hex").toUpperCase();
}
