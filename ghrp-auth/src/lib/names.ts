/**
 * Character name generator for new accounts.
 * SA-MP convention: Firstname_Lastname (letters only, <= 24 chars in DB).
 */

const FIRST = [
  "Max", "Ethan", "Marcus", "Dominic", "Victor", "Owen", "Caleb", "Nathan",
  "Adrian", "Miles", "Leo", "Grant", "Dean", "Cole", "Troy", "Dane",
  "Asher", "Bryce", "Chase", "Derek", "Eli", "Floyd", "Gage", "Harvey",
  "Ian", "Jasper", "Keith", "Lance", "Mason", "Noel", "Omar", "Preston",
  "Quinn", "Reid", "Shane", "Trevor", "Vaughn", "Wade", "Xander", "Yuri",
];

const LAST = [
  "Ward", "Steel", "Kane", "Cross", "Banks", "Reyes", "Walker", "Hayes",
  "Mercer", "Vance", "Brooks", "Grant", "Ford", "Dane", "Ross", "Blake",
  "Cole", "Rhodes", "Sutton", "Wolf", "Barrett", "Carter", "Dalton", "Edge",
  "Fisher", "Gibbs", "Holt", "Irwin", "Jones", "Knox", "Lane", "Morse",
  "Nash", "Otto", "Porter", "Quill", "Reed", "Slade", "Turner", "Voss",
];

export function genName(): string {
  const f = FIRST[Math.floor(Math.random() * FIRST.length)];
  const l = LAST[Math.floor(Math.random() * LAST.length)];
  return `${f}_${l}`;
}

/** Returns a unique name (checks the accounts table). */
export async function uniqueName(conn: { query: (sql: string, params?: unknown[]) => Promise<[unknown[], unknown]> }): Promise<string> {
  for (let i = 0; i < 25; i++) {
    const name = genName();
    const [rows] = await conn.query("SELECT id FROM accounts WHERE name = ? LIMIT 1", [name]);
    if ((rows as unknown[]).length === 0) return name;
  }
  // Extremely unlikely fallback
  return `Guest_${Math.floor(Math.random() * 100000)}`;
}
