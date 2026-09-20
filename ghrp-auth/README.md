# ghrp-auth — Grand Horizon RP SSO (Vercel)

Production: https://ghrp-auth.vercel.app

The launcher WebView authentication service, reproducing the original
launcher SSO architecture (screen 0x58 / Android.initToken handoff):

- Sign in / Sign up (email -> 6-digit code -> password -> START PLAY)
- Password recovery, guest accounts
- API: /api/v2 (auth/token, registration/*, recovery/*, idp)
- Accounts written to the game MySQL (sha256(password+salt), laird.amx compatible)

Deploy:
  cd ghrp-auth && npm i && vercel deploy --prod --token <token>
Env vars (already on the Vercel project): MYSQL_HOST/PORT/USER/PASSWORD/DATABASE, TOKEN_SECRET, PASETO_PRIVATE_KEY.

NOTE: no SMTP is wired on serverless; verification codes are shown in the UI
(dev_code) until an email provider is configured.
