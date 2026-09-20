import { NextResponse } from "next/server";

/** Shared helpers for /api/v2 routes. */

export function ok(data: Record<string, unknown>): NextResponse {
  return NextResponse.json({ success: true, ...data });
}

export function fail(error: string, status = 400): NextResponse {
  return NextResponse.json({ error }, { status });
}

export async function readJson(req: Request): Promise<Record<string, unknown>> {
  try {
    const body = await req.json();
    if (body && typeof body === "object") return body as Record<string, unknown>;
  } catch {}
  return {};
}

export function str(v: unknown): string {
  return typeof v === "string" ? v.trim() : "";
}

export function validEmail(v: string): boolean {
  return /^[^\s@]{1,64}@[^\s@]{1,255}\.[^\s@]{2,24}$/.test(v);
}

export function validPassword(v: string): string | null {
  if (v.length < 8) return "ErrorPasswordTooShort";
  if (v.length > 32) return "ErrorPasswordTooLong";
  if (/\s/.test(v)) return "ErrorPasswordSpaces";
  return null;
}

export function clientMeta(req: Request): Record<string, string> {
  const ip =
    req.headers.get("x-real-ip") ||
    req.headers.get("x-forwarded-for")?.split(",")[0]?.trim() ||
    "0.0.0.0";
  return { ip };
}
