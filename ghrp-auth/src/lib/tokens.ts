import crypto from "crypto";
import { sign, verify } from "./state";

/**
 * front_token — the token handed to the native launcher through
 * Android.initToken({front_token}). The native engine later exchanges it
 * against characterService (POST /api/v2/auth/token) for an access_token.
 *
 * Format: v4.local-style PASETO-equivalent: base64url payload + HMAC-SHA256
 * signature with TOKEN_SECRET. Self-contained (account_uuid, name, email).
 * TTL 12 hours.
 */

export interface FrontTokenClaims {
  account_uuid: string;
  name: string;
  email: string;
  kind: "user" | "guest";
  [k: string]: unknown;
}

export function issueFrontToken(claims: FrontTokenClaims): string {
  return sign({ ...claims, typ: "front_token" }, 12 * 3600);
}

export function verifyFrontToken(token: string): FrontTokenClaims | null {
  const body = verify<FrontTokenClaims & { typ?: string }>(token);
  if (!body || body.typ !== "front_token") return null;
  return body;
}

export function issueAccessToken(claims: FrontTokenClaims): string {
  return sign({ ...claims, typ: "access_token" }, 3600);
}

export function verifyAccessToken(token: string): FrontTokenClaims | null {
  const body = verify<FrontTokenClaims & { typ?: string }>(token);
  if (!body || body.typ !== "access_token") return null;
  return body;
}

export function uuid(): string {
  return crypto.randomUUID();
}
