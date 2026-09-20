# WEBVIEW UI REFERENCE AUDIT + VISUAL IMPLEMENTATION

## BEFORE (problems)

- Generic HTML-form look: flat dark panel, system-default widgets, unicode
  glyph icons (👤 ✉ ⚠ ←), no background art, no motion, no glass/depth.
- AuthController wrapped the page in a grey Java top bar ("GRAND HORIZON RP —
  SIGN IN", 46dp) — duplicated branding, wasted space, felt like a browser
  frame rather than a product.
- Guest flow handed only `{front_token}` to the bridge — not enough to persist
  a restorable session (root cause partner of the guest bug).
- No session awareness: the page always started at "main" even for a returning
  device (no email prefill).

## REFERENCE (observed)

From the original launcher forensics + the authorized start-screen art:
- full-bleed background imagery (start-screen artwork shipped in the game data),
  dark veil with horizon glow at the bottom;
- strong brand block (emblem + stacked wordmark, wide tracking);
- glass-style auth panel, one column, large touch targets (≥48dp);
- iconography next to actions (mail/user/key), not unicode glyphs;
- loading = spinner + status text; success = check ring + account chip +
  "Start Playing" CTA;
- motion: screen transitions (fade/slide), button press feedback, focus glow
  on inputs, error shake.

## IMPLEMENTED (this session)

`ghrp-auth` (Vercel, live):

- **Design system** (`globals.css` rewrite): navy+gold palette
  (`--ghrp-gold #e8b24b`), glass card `backdrop-filter: blur(18px)` on the
  authorized `bg.jpg` (start-screen art from the gamedata release, downscaled
  1280x720, 110 KB progressive JPEG — Grand Horizon identity, no Black Russia
  branding), horizon glow veil, safe-area padding, `100dvh`, no-zoom viewport.
- **Icons**: inline SVG set (mail, user, key, back arrow, eye/eye-off for
  password visibility, shield on Start Playing, check ring, warning) — zero
  unicode placeholders, zero third-party icon packs.
- **Screens** (logic preserved, visuals rebuilt): main (brand + tagline +
  email/guest/create actions), sign-in (icon inputs, password eye, error
  shake), sign-up email -> 6-digit OTP boxes (auto-advance, paste support,
  resend timer, dev-code note) -> password with live checks, recovery flow
  (email -> code -> new password -> done), guest-wait spinner, success
  (account chip, Start Playing).
- **Motion**: card enter (fade+slide, cubic-bezier overshoot), background
  slow-in, button press scale, input focus glow ring, error shake, success
  pop, reduced-motion media query, 60fps-friendly (transform/opacity only).
- **Responsive**: card `min(420px, 100vw-32px)`, internal scroll with styled
  scrollbar, code boxes shrink under 380px, short-screen paddings, safe areas.
- **State machine + bridge**: success -> `Android.initToken({front_token,
  guest_secret, account:{name,email,kind}})`; `?email=` prefill for re-login;
  outside-launcher note stays honest.
- **AuthController (Java)**: full-screen WebView (top bar removed, page owns
  chrome), overscroll off.

## PERSISTENCE / RESTORE

See GUEST_PERSISTENCE_CONTRACT.md — the page is auth-only; device persistence
is SessionStore + server character API; restore never shows creation again for
an existing character.

## TESTED

- `bun run build` (Next.js) green; deployed to Vercel prod.
- Live: `/api/health` db up; guest grant returns secret; character GET/POST
  round-trip verified; restore path verified (re-auth token sees saved
  character); page + bg.jpg served (HTTP 200).
- Real-device validation of the visual pass: pending user run of the new APK
  (all state transitions logged).
