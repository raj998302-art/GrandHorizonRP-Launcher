"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";

/* ============================================================
 * Grand Horizon RP — SSO (launcher WebView)
 *
 * Reproduces the original launcher authentication architecture:
 *   Sign in / Sign up (email -> verification code -> password),
 *   Recovery, Guest, Success -> START PLAY -> Android.initToken.
 * JS bridge name: "Android" (WebViewAuthFragment adds
 * addJavascriptInterface(WebAppInterface, "Android")).
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

/** Hand the front_token to the native engine (AuthViewModel.saveTokenAndCloseWebView). */
function initTokenNative(frontToken: string): boolean {
  return callAndroid("initToken", JSON.stringify({ front_token: frontToken }));
}

/* ---------- API client (mirrors the original retry contract) ---------- */

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

/* ---------- Brand emblem (same geometry as the launcher splash) ---------- */

function Emblem({ size = 56 }: { size?: number }) {
  return (
    <svg width={size} height={size * 1.08} viewBox="0 0 120 130" aria-hidden="true">
      <defs>
        <linearGradient id="ghrpSun" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#FF6318" />
          <stop offset="1" stopColor="#C60000" />
        </linearGradient>
      </defs>
      <path d="M25,52 A35,35 0 0 1 95,52 L95,56 L25,56 Z" fill="url(#ghrpSun)" />
      <rect x="12" y="72" width="96" height="7" rx="1" fill="#FFFFFF" />
      <rect x="22" y="88" width="76" height="6" rx="1" fill="#B9BEC7" />
      <rect x="32" y="103" width="56" height="5" rx="1" fill="#7A8089" />
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

/* ---------- Small pieces ---------- */

function ErrorBox({ msg }: { msg: string | null }) {
  if (!msg) return null;
  return (
    <div className="ghrp-error" role="alert">
      <span aria-hidden="true">⚠</span>
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
    <div style={{ display: "flex", gap: 10, marginTop: 6, flexWrap: "wrap" }}>
      {items.map((it) => (
        <span key={it.label} style={{ fontSize: 10.5, fontWeight: 700, color: it.ok ? "var(--ghrp-ok)" : "var(--ghrp-text-faint)" }}>
          {it.ok ? "●" : "○"} {it.label}
        </span>
      ))}
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
  const [accountName, setAccountName] = useState<string>("");
  const [resent, setResent] = useState(false);
  const [seconds, setSeconds] = useState(0);
  const [nativeHandedOff, setNativeHandedOff] = useState(false);
  const [inLauncher, setInLauncher] = useState(false);

  useEffect(() => {
    setInLauncher(android() !== null);
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
    setAccountName(String((r.data?.account as any)?.name || ""));
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
    setAccountName(String((r.data?.account as any)?.name || ""));
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
    setAccountName(String((r.data?.account as any)?.name || ""));
    go("success");
  }, [busy]);

  /* ---------- Start play (native handoff) ---------- */
  const doStartPlay = useCallback(() => {
    if (!frontToken) return;
    const ok = initTokenNative(frontToken);
    setNativeHandedOff(ok);
  }, [frontToken]);


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
          Sign in with email
        </button>
        <div className="ghrp-or">or</div>
        <div className="ghrp-social">
          <button className="ghrp-social-btn" type="button" onClick={() => doGuest()} disabled={busy}>
            <span aria-hidden="true">👤</span> Continue as guest
          </button>
          <button className="ghrp-social-btn" type="button" onClick={() => go("signup-email")}>
            <span aria-hidden="true">✉</span> Create account
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
        <div className="ghrp-topbar">
          <button className="ghrp-back" onClick={() => go("main")}>
            ← Back
          </button>
        </div>
        <div className="ghrp-h1">Sign In</div>
        <p className="ghrp-sub">Enter your account e-mail and password</p>
        <div className="ghrp-field">
          <label className="ghrp-label" htmlFor="si-email">E-mail</label>
          <input
            id="si-email"
            className="ghrp-input"
            type="email"
            autoComplete="email"
            placeholder="Enter your e-mail address"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && doSignIn()}
          />
        </div>
        <div className="ghrp-field">
          <label className="ghrp-label" htmlFor="si-pass">Password</label>
          <input
            id="si-pass"
            className="ghrp-input"
            type="password"
            autoComplete="current-password"
            placeholder="Enter your password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && doSignIn()}
          />
        </div>
        <ErrorBox msg={error} />
        <button className="ghrp-btn" onClick={doSignIn} disabled={busy}>
          {busy ? "Signing in…" : "Sign In"}
        </button>
        <div style={{ display: "flex", justifyContent: "center", gap: 18, marginTop: 10 }}>
          <button className="ghrp-link" onClick={() => go("recovery-email")}>
            Forgot password?
          </button>
          <button className="ghrp-link" onClick={() => go("signup-email")}>
            Sign Up
          </button>
        </div>
      </>
    );
  }

  if (screen === "signup-email") {
    content = (
      <>
        <div className="ghrp-topbar">
          <button className="ghrp-back" onClick={() => go("main")}>
            ← Back
          </button>
        </div>
        <div className="ghrp-h1">Sign Up</div>
        <p className="ghrp-sub">Enter your e-mail — we will send a 6-digit verification code</p>
        <div className="ghrp-field">
          <label className="ghrp-label" htmlFor="su-email">E-mail</label>
          <input
            id="su-email"
            className="ghrp-input"
            type="email"
            autoComplete="email"
            placeholder="Enter your e-mail address"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && doRegStart()}
          />
        </div>
        <ErrorBox msg={error} />
        <button className="ghrp-btn" onClick={doRegStart} disabled={busy}>
          {busy ? "Sending code…" : "Continue"}
        </button>
        <p className="ghrp-footnote">Already have an account? Sign in instead.</p>
      </>
    );
  }

  if (screen === "signup-code") {
    content = (
      <>
        <div className="ghrp-topbar">
          <button className="ghrp-back" onClick={() => go("signup-email")}>
            ← Back
          </button>
        </div>
        <div className="ghrp-h1">Verify E-mail</div>
        <p className="ghrp-sub">Enter the 6-digit code sent to {email}</p>
        <CodeBoxes value={code} onChange={setCode} disabled={busy} />
        {devCode && (
          <div className="ghrp-note">
            Demo delivery (no mail server connected): your code is <b>{devCode}</b>
          </div>
        )}
        <ErrorBox msg={error} />
        <button className="ghrp-btn" onClick={doRegValidate} disabled={busy || code.length !== 6}>
          {busy ? "Verifying…" : "Verify"}
        </button>
        <div className="ghrp-timer">
          {resent && <div style={{ marginBottom: 4, color: "var(--ghrp-ok)" }}>The code has been sent again.</div>}
          {seconds > 0 ? (
            <>Resend available in {seconds}s</>
          ) : (
            <button className="ghrp-link" onClick={doResend} disabled={busy}>
              Resend code
            </button>
          )}
        </div>
      </>
    );
  }

  if (screen === "signup-password") {
    content = (
      <>
        <div className="ghrp-topbar">
          <button className="ghrp-back" onClick={() => go("signup-code")}>
            ← Back
          </button>
        </div>
        <div className="ghrp-h1">Create a Password</div>
        <p className="ghrp-sub">Your account {email} is verified — set a password to finish</p>
        <div className="ghrp-field">
          <label className="ghrp-label" htmlFor="su-pass">Password</label>
          <input
            id="su-pass"
            className="ghrp-input"
            type="password"
            autoComplete="new-password"
            placeholder="Create a password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
          <PasswordChecks pw={password} />
        </div>
        <div className="ghrp-field">
          <label className="ghrp-label" htmlFor="su-pass2">Repeat password</label>
          <input
            id="su-pass2"
            className="ghrp-input"
            type="password"
            autoComplete="new-password"
            placeholder="Enter the password again"
            value={password2}
            onChange={(e) => setPassword2(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && doRegPassword()}
          />
        </div>
        <ErrorBox msg={error} />
        <button className="ghrp-btn" onClick={doRegPassword} disabled={busy || password.length < 8 || password !== password2}>
          {busy ? "Creating account…" : "Create Account"}
        </button>
      </>
    );
  }

  if (screen === "recovery-email") {
    content = (
      <>
        <div className="ghrp-topbar">
          <button className="ghrp-back" onClick={() => go("signin")}>
            ← Back
          </button>
        </div>
        <div className="ghrp-h1">Password Recovery</div>
        <p className="ghrp-sub">Enter the e-mail of your account — we will send a verification code</p>
        <div className="ghrp-field">
          <label className="ghrp-label" htmlFor="rec-email">E-mail</label>
          <input
            id="rec-email"
            className="ghrp-input"
            type="email"
            placeholder="Enter your e-mail address"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && doRecStart()}
          />
        </div>
        <ErrorBox msg={error} />
        <button className="ghrp-btn" onClick={doRecStart} disabled={busy}>
          {busy ? "Sending…" : "Continue"}
        </button>
      </>
    );
  }

  if (screen === "recovery-code") {
    content = (
      <>
        <div className="ghrp-topbar">
          <button className="ghrp-back" onClick={() => go("recovery-email")}>
            ← Back
          </button>
        </div>
        <div className="ghrp-h1">Verify E-mail</div>
        <p className="ghrp-sub">Enter the 6-digit code sent to {email}</p>
        <CodeBoxes value={code} onChange={setCode} disabled={busy} />
        {devCode && (
          <div className="ghrp-note">
            Demo delivery (no mail server connected): your code is <b>{devCode}</b>
          </div>
        )}
        <ErrorBox msg={error} />
        <button className="ghrp-btn" onClick={doRecValidate} disabled={busy || code.length !== 6}>
          {busy ? "Verifying…" : "Verify"}
        </button>
        <div className="ghrp-timer">
          {seconds > 0 ? <>Resend available in {seconds}s</> : <button className="ghrp-link" onClick={doRecStart}>Resend code</button>}
        </div>
      </>
    );
  }

  if (screen === "recovery-newpassword") {
    content = (
      <>
        <div className="ghrp-topbar">
          <button className="ghrp-back" onClick={() => go("recovery-code")}>
            ← Back
          </button>
        </div>
        <div className="ghrp-h1">Set a New Password</div>
        <p className="ghrp-sub">Choose a new password for {email}</p>
        <div className="ghrp-field">
          <label className="ghrp-label" htmlFor="rec-pass">New password</label>
          <input
            id="rec-pass"
            className="ghrp-input"
            type="password"
            autoComplete="new-password"
            placeholder="Enter a new password"
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
          />
          <PasswordChecks pw={newPassword} />
        </div>
        <div className="ghrp-field">
          <label className="ghrp-label" htmlFor="rec-pass2">Repeat new password</label>
          <input
            id="rec-pass2"
            className="ghrp-input"
            type="password"
            autoComplete="new-password"
            placeholder="Enter the password again"
            value={newPassword2}
            onChange={(e) => setNewPassword2(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && doRecPassword()}
          />
        </div>
        <ErrorBox msg={error} />
        <button className="ghrp-btn" onClick={doRecPassword} disabled={busy || newPassword.length < 8 || newPassword !== newPassword2}>
          {busy ? "Saving…" : "Save Password"}
        </button>
      </>
    );
  }

  if (screen === "recovery-done") {
    content = (
      <>
        <div className="ghrp-success-ring" aria-hidden="true">
          <svg width="40" height="40" viewBox="0 0 24 24" fill="none">
            <path d="M4 12.5l5 5L20 6.5" stroke="#35c26b" strokeWidth="2.6" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        </div>
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
        <div className="ghrp-spin" aria-hidden="true" />
        <div className="ghrp-h1">Creating Guest Account</div>
        <p className="ghrp-sub">Setting up your guest character…</p>
      </>
    );
  }

  if (screen === "success") {
    content = (
      <>
        <Brand />
        <div className="ghrp-success-ring" aria-hidden="true">
          <svg width="40" height="40" viewBox="0 0 24 24" fill="none">
            <path d="M4 12.5l5 5L20 6.5" stroke="#35c26b" strokeWidth="2.6" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        </div>
        <div className="ghrp-h1">Success</div>
        <p className="ghrp-sub">You have successfully completed registration!</p>
        {accountName && (
          <div className="ghrp-account-chip">
            <span className="k">Character</span>
            <span className="v">{accountName}</span>
          </div>
        )}
        <ErrorBox msg={error} />
        <button className="ghrp-btn" onClick={doStartPlay}>
          Start Playing
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
      <div className="ghrp-backdrop" aria-hidden="true" />
      <div className="ghrp-card">{content}</div>
    </main>
  );
}
