"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";

/* ============================================================
 * Grand Horizon RP — SSO (launcher WebView)
 *
 * Architecture (matches the original launcher contract):
 *   WebView = AUTHENTICATION ONLY (login / register / recovery / guest).
 *   Server select, character select, world = NATIVE engine.
 *
 * Flow: Sign in / Sign up (email -> code -> password), Recovery,
 *   Guest -> Success -> START PLAY -> Android.initToken(payload).
 * Bridge name: "Android" (AuthController injects it).
 *
 * Session state machine (this page):
 *   main -> signin | signup-email | recovery-email | guest-wait -> success
 *   success -> [launcher?] handoff (initToken) : info note
 * Persistence itself lives on the device (launcher SessionStore) —
 * this page only hands the fresh identity to the native side.
 * ============================================================ */

type Screen =
  | "main"
  | "signin"
  | "signup-email"
  | "signup-code"
  | "signup-password"
  | "recovery-email"
  | "recovery-code"
  | "recovery-newpassword"
  | "recovery-done"
  | "guest-wait"
  | "success";

interface ApiResult<T = Record<string, unknown>> {
  success: boolean;
  error?: string;
  data?: T;
}

const ERRORS: Record<string, string> = {
  ErrorArgsMissing: "Please fill in all the fields.",
  ErrorInvalidEmail: "Invalid e-mail address.",
  ErrorEmailTaken: "This e-mail is already registered. Try signing in.",
  ErrorInvalidCode: "Invalid verification code.",
  ErrorInvalidSession: "Your session has expired. Please start again.",
  ErrorInvalidCredentials: "Wrong e-mail or password.",
  ErrorUserNotFound: "Account not found.",
  ErrorPasswordTooShort: "The password must be at least 8 characters long.",
  ErrorPasswordTooLong: "The password must be no longer than 32 characters.",
  ErrorPasswordSpaces: "The password must not contain spaces.",
  ErrorTokenNotFound: "Your session has expired. Please log in again.",
  ErrorNetwork: "Network error. Check your connection and try again.",
  ErrorInternalServerError: "Server error. Please try again later.",
  ErrorUnhandled: "Something went wrong. Please try again.",
};

function errText(e?: string): string {
  if (!e) return ERRORS.ErrorUnhandled;
  return ERRORS[e] || ERRORS[e.replace(/^api\./, "")] || "Something went wrong. Please try again.";
}

/* ---------- Android native bridge ---------- */

function android(): any | null {
  const a = (window as any).Android;
  return a && typeof a === "object" ? a : null;
}

function callAndroid(method: string, arg?: string): boolean {
  const a = android();
  if (!a || typeof a[method] !== "function") return false;
  try {
    if (arg === undefined) (a[method] as () => void)();
    else (a[method] as (s: string) => void)(arg);
    return true;
  } catch {
    return false;
  }
}

export interface HandoffPayload {
  front_token: string;
  guest_secret?: string;
  account?: { name?: string; email?: string; kind?: string };
}

/** Hand the full identity to the native launcher (AuthController bridge). */
function initTokenNative(p: HandoffPayload): boolean {
  return callAndroid("initToken", JSON.stringify(p));
}

/* ---------- API client (retry contract as before) ---------- */

async function api(path: string, body?: object, method: "POST" | "GET" = "POST"): Promise<ApiResult> {
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), 15000);
  const attempt = () =>
    fetch(`/api/v2${path}`, {
      method,
      headers: { "Content-Type": "application/json" },
      body: method === "POST" ? JSON.stringify(body ?? {}) : undefined,
      signal: ctrl.signal,
    });
  let res: Response | null = null;
  for (let i = 0; i < 3; i++) {
    try {
      res = await attempt();
      if (res.status < 500) break;
    } catch {
      /* retry */
    }
    await new Promise((r) => setTimeout(r, 1000));
  }
  clearTimeout(timer);
  if (!res) return { success: false, error: "ErrorNetwork" };
  let data: any = {};
  try {
    data = await res.json();
  } catch {}
  if (!res.ok) {
    const err = typeof data?.error === "string" ? data.error : res.status >= 500 ? "ErrorInternalServerError" : "ErrorUnhandled";
    return { success: false, error: err };
  }
  return { success: true, data };
}

/* ---------- Brand emblem (same geometry family as the launcher splash) ---------- */

function Emblem({ size = 52 }: { size?: number }) {
  return (
    <svg width={size} height={size * 1.08} viewBox="0 0 120 130" aria-hidden="true" className="ghrp-emblem">
      <defs>
        <linearGradient id="ghrpSun" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#FFD86B" />
          <stop offset="0.5" stopColor="#F0A93B" />
          <stop offset="1" stopColor="#C97A1E" />
        </linearGradient>
        <filter id="ghrpGlow" x="-40%" y="-40%" width="180%" height="180%">
          <feGaussianBlur stdDeviation="3.2" result="b" />
          <feMerge>
            <feMergeNode in="b" />
            <feMergeNode in="SourceGraphic" />
          </feMerge>
        </filter>
      </defs>
      <g filter="url(#ghrpGlow)">
        <path d="M25,52 A35,35 0 0 1 95,52 L95,56 L25,56 Z" fill="url(#ghrpSun)" />
        <rect x="12" y="72" width="96" height="7" rx="1.5" fill="#FFFFFF" opacity="0.94" />
        <rect x="22" y="88" width="76" height="6" rx="1.5" fill="#C6CBD4" opacity="0.8" />
        <rect x="32" y="103" width="56" height="5" rx="1.5" fill="#8A9099" opacity="0.66" />
      </g>
    </svg>
  );
}

function Brand() {
  return (
    <div className="ghrp-brand">
      <Emblem />
      <div className="ghrp-brand-text">
        <span className="t1">GRAND</span>
        <span className="t2">HORIZON</span>
      </div>
    </div>
  );
}

/* ---------- Icons (inline SVG — no unicode placeholders) ---------- */

const iconProps = { width: 18, height: 18, viewBox: "0 0 24 24", fill: "none" } as const;

const Icon = {
  mail: (p: { className?: string }) => (
    <svg {...iconProps} className={p.className} aria-hidden="true">
      <rect x="3" y="5" width="18" height="14" rx="2.5" stroke="currentColor" strokeWidth="1.7" />
      <path d="M4 7.5l8 5.5 8-5.5" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  ),
  user: (p: { className?: string }) => (
    <svg {...iconProps} className={p.className} aria-hidden="true">
      <circle cx="12" cy="8" r="3.6" stroke="currentColor" strokeWidth="1.7" />
      <path d="M4.5 20c1.4-3.4 4.2-5 7.5-5s6.1 1.6 7.5 5" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" />
    </svg>
  ),
  key: (p: { className?: string }) => (
    <svg {...iconProps} className={p.className} aria-hidden="true">
      <circle cx="8" cy="12" r="3.5" stroke="currentColor" strokeWidth="1.7" />
      <path d="M11.5 12H21m-3.5 0v3m-2.5-3v2.2" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" />
    </svg>
  ),
  arrowLeft: (p: { className?: string }) => (
    <svg {...iconProps} className={p.className} aria-hidden="true">
      <path d="M19 12H5m0 0 6-6m-6 6 6 6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  ),
  eye: (p: { className?: string }) => (
    <svg {...iconProps} className={p.className} aria-hidden="true">
      <path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12Z" stroke="currentColor" strokeWidth="1.6" strokeLinejoin="round" />
      <circle cx="12" cy="12" r="2.8" stroke="currentColor" strokeWidth="1.6" />
    </svg>
  ),
  eyeOff: (p: { className?: string }) => (
    <svg {...iconProps} className={p.className} aria-hidden="true">
      <path d="M4 4l16 16M9.9 5.9A9.9 9.9 0 0 1 12 5.5c6 0 9.5 6.5 9.5 6.5a17 17 0 0 1-3.2 3.9M6.3 8.2A16.6 16.6 0 0 0 2.5 12S6 18.5 12 18.5c1 0 1.9-.15 2.7-.4"
        stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
      <path d="M9.9 9.9a3 3 0 0 0 4.2 4.2" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
    </svg>
  ),
  check: (p: { className?: string }) => (
    <svg width={40} height={40} viewBox="0 0 24 24" fill="none" className={p.className} aria-hidden="true">
      <path d="M4 12.5l5 5L20 6.5" stroke="#39D98A" strokeWidth="2.6" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  ),
  shield: (p: { className?: string }) => (
    <svg {...iconProps} className={p.className} aria-hidden="true">
      <path d="M12 3l7.5 2.8v5.4c0 4.6-3.1 8.2-7.5 9.8-4.4-1.6-7.5-5.2-7.5-9.8V5.8L12 3Z" stroke="currentColor" strokeWidth="1.6" strokeLinejoin="round" />
      <path d="M8.8 12.2l2.2 2.2 4.2-4.4" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  ),
  chevron: (p: { className?: string }) => (
    <svg {...iconProps} className={p.className} aria-hidden="true">
      <path d="M9 6l6 6-6 6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  ),
};

/* ---------- Small pieces ---------- */

function ErrorBox({ msg }: { msg: string | null }) {
  if (!msg) return null;
  return (
    <div className="ghrp-error" role="alert">
      <svg width={15} height={15} viewBox="0 0 24 24" fill="none" aria-hidden="true">
        <circle cx="12" cy="12" r="9.5" stroke="currentColor" strokeWidth="1.8" />
        <path d="M12 7.5v5.5M12 16.4v.4" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" />
      </svg>
      <span>{msg}</span>
    </div>
  );
}

function CodeBoxes({ value, onChange, disabled }: { value: string; onChange: (v: string) => void; disabled?: boolean }) {
  const refs = useRef<(HTMLInputElement | null)[]>([]);
  const chars = useMemo(() => value.split(""), [value]);

  const setChar = (i: number, ch: string) => {
    const next = value.split("");
    next[i] = ch;
    const joined = next.join("").replace(/\D/g, "");
    onChange(joined);
    if (ch && i < 5) refs.current[i + 1]?.focus();
  };

  return (
    <div className="ghrp-code" role="group" aria-label="Verification code">
      {Array.from({ length: 6 }, (_, i) => (
        <input
          key={i}
          ref={(el) => {
            refs.current[i] = el;
          }}
          inputMode="numeric"
          autoComplete={i === 0 ? "one-time-code" : "off"}
          maxLength={1}
          disabled={disabled}
          value={chars[i] || ""}
          onChange={(e) => setChar(i, e.target.value.replace(/\D/g, "").slice(-1))}
          onKeyDown={(e) => {
            if (e.key === "Backspace" && !chars[i] && i > 0) {
              const next = value.split("");
              next[i - 1] = "";
              onChange(next.join("").replace(/\D/g, ""));
              refs.current[i - 1]?.focus();
            }
          }}
          onPaste={(e) => {
            e.preventDefault();
            const pasted = e.clipboardData.getData("text").replace(/\D/g, "").slice(0, 6);
            if (pasted) onChange(pasted);
          }}
        />
      ))}
    </div>
  );
}

function PasswordChecks({ pw }: { pw: string }) {
  const items = [
    { ok: pw.length >= 8, label: "8+ characters" },
    { ok: pw.length <= 32, label: "max 32" },
    { ok: pw.length > 0 && !/\s/.test(pw), label: "no spaces" },
  ];
  return (
    <div className="ghrp-pwchecks">
      {items.map((it) => (
        <span key={it.label} className={it.ok ? "ok" : ""}>
          {it.ok ? "\u25CF" : "\u25CB"} {it.label}
        </span>
      ))}
    </div>
  );
}

/** Password input with a visibility toggle (reference interaction). */
function PasswordField({
  id, label, placeholder, value, onChange, onEnter, autoComplete,
}: {
  id: string; label: string; placeholder: string; value: string;
  onChange: (v: string) => void; onEnter?: () => void; autoComplete?: string;
}) {
  const [show, setShow] = useState(false);
  return (
    <div className="ghrp-field">
      <label className="ghrp-label" htmlFor={id}>{label}</label>
      <div className="ghrp-inputwrap">
        <input
          id={id}
          className="ghrp-input has-trailing"
          type={show ? "text" : "password"}
          autoComplete={autoComplete || "new-password"}
          placeholder={placeholder}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && onEnter?.()}
        />
        <button type="button" className="ghrp-input-icon" aria-label={show ? "Hide password" : "Show password"}
          onClick={() => setShow((s) => !s)}>
          {show ? <Icon.eyeOff /> : <Icon.eye />}
        </button>
      </div>
    </div>
  );
}

function BackBar({ onBack }: { onBack: () => void }) {
  return (
    <div className="ghrp-topbar">
      <button className="ghrp-back" onClick={onBack} aria-label="Back">
        <Icon.arrowLeft />
        <span>Back</span>
      </button>
    </div>
  );
}

/* ============================================================ */

export default function Page() {
  const [screen, setScreen] = useState<Screen>("main");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [password2, setPassword2] = useState("");
  const [code, setCode] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [newPassword2, setNewPassword2] = useState("");
  const [stateTok, setStateTok] = useState<string | null>(null);
  const [devCode, setDevCode] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [frontToken, setFrontToken] = useState<string | null>(null);
  const [guestSecret, setGuestSecret] = useState<string | null>(null);
  const [accountName, setAccountName] = useState<string>("");
  const [accountKind, setAccountKind] = useState<string>("user");
  const [resent, setResent] = useState(false);
  const [seconds, setSeconds] = useState(0);
  const [nativeHandedOff, setNativeHandedOff] = useState(false);
  const [inLauncher, setInLauncher] = useState(false);

  useEffect(() => {
    setInLauncher(android() !== null);
    // E-mail prefill from the launcher (restore/re-login case).
    const q = new URLSearchParams(window.location.search);
    const e = q.get("email");
    if (e && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(e)) setEmail(e);
  }, []);

  /* resend timer */
  useEffect(() => {
    if (seconds <= 0) return;
    const t = setInterval(() => setSeconds((s) => s - 1), 1000);
    return () => clearInterval(t);
  }, [seconds]);

  const go = (s: Screen) => {
    setError(null);
    setScreen(s);
  };

  /* ---------- Sign in ---------- */
  const doSignIn = useCallback(async () => {
    if (busy) return;
    if (!email.trim() || !password) return setError(ERRORS.ErrorArgsMissing);
    setBusy(true);
    setError(null);
    const r = await api("/auth/token", { username: email.trim(), password, grant_type: "password" });
    setBusy(false);
    if (!r.success) return setError(errText(r.error));
    setFrontToken(String(r.data?.front_token || ""));
    setGuestSecret(null);
    setAccountName(String((r.data?.account as any)?.name || ""));
    setAccountKind("user");
    go("success");
  }, [busy, email, password]);

  /* ---------- Registration step 1: email ---------- */
  const doRegStart = useCallback(async () => {
    if (busy) return;
    if (!email.trim()) return setError(ERRORS.ErrorArgsMissing);
    setBusy(true);
    setError(null);
    const r = await api("/registration/start", { email: email.trim() });
    setBusy(false);
    if (!r.success) return setError(errText(r.error));
    setStateTok(String(r.data?.state || ""));
    setDevCode(r.data?.dev_code ? String(r.data.dev_code) : null);
    setCode("");
    setResent(false);
    setSeconds(60);
    go("signup-code");
  }, [busy, email]);

  /* ---------- Registration step 2: code ---------- */
  const doRegValidate = useCallback(async () => {
    if (busy) return;
    if (code.length !== 6) return setError(ERRORS.ErrorInvalidCode);
    setBusy(true);
    setError(null);
    const r = await api("/registration/validate-email", { code, state: stateTok });
    setBusy(false);
    if (!r.success) return setError(errText(r.error));
    setStateTok(String(r.data?.state || ""));
    setPassword("");
    setPassword2("");
    go("signup-password");
  }, [busy, code, stateTok]);

  /* ---------- Registration step 3: password ---------- */
  const doRegPassword = useCallback(async () => {
    if (busy) return;
    if (!password || password !== password2) {
      return setError(password !== password2 ? "Passwords do not match." : ERRORS.ErrorArgsMissing);
    }
    setBusy(true);
    setError(null);
    const r = await api("/registration/password", { password, state: stateTok });
    setBusy(false);
    if (!r.success) return setError(errText(r.error));
    setFrontToken(String(r.data?.front_token || ""));
    setGuestSecret(null);
    setAccountName(String((r.data?.account as any)?.name || ""));
    setAccountKind("user");
    go("success");
  }, [busy, password, password2, stateTok]);

  /* ---------- Resend code ---------- */
  const doResend = useCallback(async () => {
    if (busy || seconds > 0) return;
    setBusy(true);
    setError(null);
    const r = await api("/registration/start", { email: email.trim() });
    setBusy(false);
    if (!r.success) return setError(errText(r.error));
    setStateTok(String(r.data?.state || ""));
    setDevCode(r.data?.dev_code ? String(r.data.dev_code) : null);
    setResent(true);
    setSeconds(60);
  }, [busy, seconds, email]);

  /* ---------- Recovery ---------- */
  const doRecStart = useCallback(async () => {
    if (busy) return;
    if (!email.trim()) return setError(ERRORS.ErrorArgsMissing);
    setBusy(true);
    setError(null);
    const r = await api("/recovery/start", { email: email.trim() });
    setBusy(false);
    if (!r.success) return setError(errText(r.error));
    setStateTok(String(r.data?.state || ""));
    setDevCode(r.data?.dev_code ? String(r.data.dev_code) : null);
    setCode("");
    setSeconds(60);
    go("recovery-code");
  }, [busy, email]);

  const doRecValidate = useCallback(async () => {
    if (busy) return;
    if (code.length !== 6) return setError(ERRORS.ErrorInvalidCode);
    setBusy(true);
    setError(null);
    const r = await api("/recovery/validate-email", { code, state: stateTok });
    setBusy(false);
    if (!r.success) return setError(errText(r.error));
    setStateTok(String(r.data?.state || ""));
    setNewPassword("");
    setNewPassword2("");
    go("recovery-newpassword");
  }, [busy, code, stateTok]);

  const doRecPassword = useCallback(async () => {
    if (busy) return;
    if (!newPassword || newPassword !== newPassword2) {
      return setError(newPassword !== newPassword2 ? "Passwords do not match." : ERRORS.ErrorArgsMissing);
    }
    setBusy(true);
    setError(null);
    const r = await api("/recovery/new-password", { password: newPassword, state: stateTok });
    setBusy(false);
    if (!r.success) return setError(errText(r.error));
    go("recovery-done");
  }, [busy, newPassword, newPassword2, stateTok]);

  /* ---------- Guest ---------- */
  const doGuest = useCallback(async () => {
    if (busy) return;
    setBusy(true);
    setError(null);
    go("guest-wait");
    const r = await api("/registration/guest", {});
    setBusy(false);
    if (!r.success) {
      setError(errText(r.error));
      return go("main");
    }
    setFrontToken(String(r.data?.front_token || ""));
    setGuestSecret(String(r.data?.guest_secret || "") || null);
    setAccountName(String((r.data?.account as any)?.name || ""));
    // CRITICAL: guest e-mail must be captured so the native launcher can
    // re-auth this guest after restart (guest_secret password grant uses it
    // as the username). Without it the saved session is invalid on restore.
    setEmail(String((r.data?.account as any)?.email || ""));
    setAccountKind("guest");
    go("success");
  }, [busy]);

  /* ---------- Start play (native handoff) ---------- */
  const doStartPlay = useCallback(() => {
    if (!frontToken) return;
    const payload: HandoffPayload = {
      front_token: frontToken,
      account: {
        name: accountName || undefined,
        email: email || undefined,
        kind: accountKind || "user",
      },
    };
    if (guestSecret) payload.guest_secret = guestSecret;
    const okNative = initTokenNative(payload);
    setNativeHandedOff(okNative);
  }, [frontToken, guestSecret, accountName, accountKind, email]);

  /* ============================================================ */

  let content: React.ReactNode = null;

  if (screen === "main") {
    content = (
      <>
        <Brand />
        <div className="ghrp-tagline">Roleplay &middot; Horizon City</div>
        <div className="ghrp-h1">Authorization</div>
        <p className="ghrp-sub">Sign in to continue to Grand Horizon RP</p>
        <button className="ghrp-btn" onClick={() => go("signin")}>
          <Icon.mail /> Sign in with email
          <Icon.chevron className="tail" />
        </button>
        <div className="ghrp-or"><span>or</span></div>
        <div className="ghrp-social">
          <button className="ghrp-social-btn guest" type="button" onClick={() => doGuest()} disabled={busy}>
            <Icon.user /> Continue as guest
          </button>
          <button className="ghrp-social-btn" type="button" onClick={() => go("signup-email")}>
            <Icon.key /> Create account
          </button>
        </div>
        <p className="ghrp-footnote">
          By continuing you agree to the Grand Horizon RP server rules.
          <br />
          Need help? Ask in our Discord.
        </p>
      </>
    );
  }

  if (screen === "signin") {
    content = (
      <>
        <BackBar onBack={() => go("main")} />
        <div className="ghrp-h1">Sign In</div>
        <p className="ghrp-sub">Enter your account e-mail and password</p>
        <div className="ghrp-field">
          <label className="ghrp-label" htmlFor="si-email">E-mail</label>
          <div className="ghrp-inputwrap">
            <input
              id="si-email"
              className="ghrp-input has-leading"
              type="email"
              autoComplete="email"
              placeholder="Enter your e-mail address"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && doSignIn()}
            />
            <span className="ghrp-input-icon lead"><Icon.mail /></span>
          </div>
        </div>
        <PasswordField id="si-pass" label="Password" placeholder="Enter your password"
          value={password} onChange={setPassword} onEnter={doSignIn} autoComplete="current-password" />
        <ErrorBox msg={error} />
        <button className="ghrp-btn" onClick={doSignIn} disabled={busy}>
          {busy ? <><span className="ghrp-ldot" /> Signing in…</> : <>Sign In</>}
        </button>
        <div className="ghrp-rowlinks">
          <button className="ghrp-link" onClick={() => go("recovery-email")}>Forgot password?</button>
          <button className="ghrp-link" onClick={() => go("signup-email")}>Sign Up</button>
        </div>
      </>
    );
  }

  if (screen === "signup-email") {
    content = (
      <>
        <BackBar onBack={() => go("main")} />
        <div className="ghrp-h1">Sign Up</div>
        <p className="ghrp-sub">Enter your e-mail — we will send a 6-digit verification code</p>
        <div className="ghrp-field">
          <label className="ghrp-label" htmlFor="su-email">E-mail</label>
          <div className="ghrp-inputwrap">
            <input
              id="su-email"
              className="ghrp-input has-leading"
              type="email"
              autoComplete="email"
              placeholder="Enter your e-mail address"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && doRegStart()}
            />
            <span className="ghrp-input-icon lead"><Icon.mail /></span>
          </div>
        </div>
        <ErrorBox msg={error} />
        <button className="ghrp-btn" onClick={doRegStart} disabled={busy}>
          {busy ? <><span className="ghrp-ldot" /> Sending code…</> : <>Continue</>}
        </button>
        <p className="ghrp-footnote">Already have an account? Sign in instead.</p>
      </>
    );
  }

  if (screen === "signup-code") {
    content = (
      <>
        <BackBar onBack={() => go("signup-email")} />
        <div className="ghrp-h1">Verify E-mail</div>
        <p className="ghrp-sub">Enter the 6-digit code sent to <b>{email}</b></p>
        <CodeBoxes value={code} onChange={setCode} disabled={busy} />
        {devCode && (
          <div className="ghrp-note">
            Demo delivery (no mail server connected): your code is <b>{devCode}</b>
          </div>
        )}
        <ErrorBox msg={error} />
        <button className="ghrp-btn" onClick={doRegValidate} disabled={busy || code.length !== 6}>
          {busy ? <><span className="ghrp-ldot" /> Verifying…</> : <>Verify</>}
        </button>
        <div className="ghrp-timer">
          {resent && <div style={{ marginBottom: 4, color: "var(--ghrp-ok)" }}>The code has been sent again.</div>}
          {seconds > 0 ? (
            <>Resend available in {seconds}s</>
          ) : (
            <button className="ghrp-link" onClick={doResend} disabled={busy}>Resend code</button>
          )}
        </div>
      </>
    );
  }

  if (screen === "signup-password") {
    content = (
      <>
        <BackBar onBack={() => go("signup-code")} />
        <div className="ghrp-h1">Create a Password</div>
        <p className="ghrp-sub">Your account {email} is verified — set a password to finish</p>
        <PasswordField id="su-pass" label="Password" placeholder="Create a password"
          value={password} onChange={setPassword} onEnter={doRegPassword} />
        <PasswordField id="su-pass2" label="Repeat password" placeholder="Repeat the password"
          value={password2} onChange={setPassword2} onEnter={doRegPassword} />
        <PasswordChecks pw={password} />
        <ErrorBox msg={error} />
        <button className="ghrp-btn" onClick={doRegPassword} disabled={busy}>
          {busy ? <><span className="ghrp-ldot" /> Saving…</> : <>Save Password</>}
        </button>
      </>
    );
  }

  if (screen === "recovery-email") {
    content = (
      <>
        <BackBar onBack={() => go("signin")} />
        <div className="ghrp-h1">Password Recovery</div>
        <p className="ghrp-sub">Enter the e-mail linked to your account</p>
        <div className="ghrp-field">
          <label className="ghrp-label" htmlFor="rec-email">E-mail</label>
          <div className="ghrp-inputwrap">
            <input
              id="rec-email"
              className="ghrp-input has-leading"
              type="email"
              autoComplete="email"
              placeholder="Enter your e-mail address"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && doRecStart()}
            />
            <span className="ghrp-input-icon lead"><Icon.mail /></span>
          </div>
        </div>
        <ErrorBox msg={error} />
        <button className="ghrp-btn" onClick={doRecStart} disabled={busy}>
          {busy ? <><span className="ghrp-ldot" /> Sending code…</> : <>Continue</>}
        </button>
      </>
    );
  }

  if (screen === "recovery-code") {
    content = (
      <>
        <BackBar onBack={() => go("recovery-email")} />
        <div className="ghrp-h1">Enter the Code</div>
        <p className="ghrp-sub">We sent a 6-digit code to <b>{email}</b></p>
        <CodeBoxes value={code} onChange={setCode} disabled={busy} />
        {devCode && (
          <div className="ghrp-note">Demo delivery: your code is <b>{devCode}</b></div>
        )}
        <ErrorBox msg={error} />
        <button className="ghrp-btn" onClick={doRecValidate} disabled={busy || code.length !== 6}>
          {busy ? <><span className="ghrp-ldot" /> Verifying…</> : <>Verify</>}
        </button>
        <div className="ghrp-timer">
          {seconds > 0 ? <>Resend available in {seconds}s</> : (
            <button className="ghrp-link" onClick={doRecStart} disabled={busy}>Resend code</button>
          )}
        </div>
      </>
    );
  }

  if (screen === "recovery-newpassword") {
    content = (
      <>
        <BackBar onBack={() => go("recovery-code")} />
        <div className="ghrp-h1">New Password</div>
        <p className="ghrp-sub">Choose a new password for {email}</p>
        <PasswordField id="rec-pass" label="New password" placeholder="New password"
          value={newPassword} onChange={setNewPassword} onEnter={doRecPassword} />
        <PasswordField id="rec-pass2" label="Repeat new password" placeholder="Repeat the new password"
          value={newPassword2} onChange={setNewPassword2} onEnter={doRecPassword} />
        <PasswordChecks pw={newPassword} />
        <ErrorBox msg={error} />
        <button className="ghrp-btn" onClick={doRecPassword} disabled={busy}>
          {busy ? <><span className="ghrp-ldot" /> Saving…</> : <>Save Password</>}
        </button>
      </>
    );
  }

  if (screen === "recovery-done") {
    content = (
      <>
        <div className="ghrp-success-ring" aria-hidden="true"><Icon.check /></div>
        <div className="ghrp-h1">Password Changed</div>
        <p className="ghrp-sub">You have successfully changed your password. Use it to sign in next time.</p>
        <button className="ghrp-btn" onClick={() => { setPassword(""); go("signin"); }}>
          Back to Sign In
        </button>
      </>
    );
  }

  if (screen === "guest-wait") {
    content = (
      <>
        <Brand />
        <div style={{ height: 18 }} />
        <span className="ghrp-spin" aria-hidden="true" />
        <div className="ghrp-h1">Creating Guest Account</div>
        <p className="ghrp-sub">Setting up your guest character…</p>
      </>
    );
  }

  if (screen === "success") {
    content = (
      <>
        <Brand />
        <div className="ghrp-success-ring pop" aria-hidden="true"><Icon.check /></div>
        <div className="ghrp-h1">Success</div>
        <p className="ghrp-sub">
          {accountKind === "guest" ? "Your guest account is ready!" : "You have successfully signed in!"}
        </p>
        {accountName && (
          <div className="ghrp-account-chip">
            <span className="k">Character</span>
            <span className="v">{accountName}</span>
          </div>
        )}
        <ErrorBox msg={error} />
        <button className="ghrp-btn big" onClick={doStartPlay}>
          <Icon.shield /> Start Playing
        </button>
        {nativeHandedOff === false && (
          <div className="ghrp-note">
            Open this page inside the <b>Grand Horizon RP launcher</b> to start the game.
          </div>
        )}
        <p className="ghrp-footnote">
          {inLauncher ? "" : "Not in the launcher — sign-in works, but the game starts from the launcher. "}
          Your session is kept for this device.
        </p>
      </>
    );
  }

  return (
    <main className="ghrp-shell">
      <div className="ghrp-backdrop" aria-hidden="true">
        <img src="/bg.jpg" alt="" className="ghrp-bgimg" />
        <div className="ghrp-bgveil" />
      </div>
      <div className="ghrp-card" key={screen}>
        {content}
      </div>
      <div className="ghrp-footer" aria-hidden="true">
        <span>GRAND HORIZON RP</span>
        <span className="sep" />
        <span>Horizon City</span>
      </div>
    </main>
  );
}
