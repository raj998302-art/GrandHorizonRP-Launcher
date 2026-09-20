import { NextResponse } from "next/server";
import { getPool } from "../../../lib/db";

export const dynamic = "force-dynamic";

export async function GET() {
  try {
    const conn = await getPool().getConnection();
    await conn.query("SELECT 1");
    conn.release();
    return NextResponse.json({ ok: true, service: "ghrp-auth", db: "up" });
  } catch (e) {
    return NextResponse.json({ ok: false, service: "ghrp-auth", db: "down" }, { status: 500 });
  }
}
