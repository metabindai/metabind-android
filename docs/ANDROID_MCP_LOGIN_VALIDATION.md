# Android MCP login and published chat validation

Validated September 18, 2026. This implementation is not released.

## Implemented

- Preserve published versus `/draft` endpoints in imported and saved links.
- Ignore legacy embedded API keys. Drafts use browser OAuth with AppAuth,
  S256 PKCE, random state, trusted discovery hosts, and project/scope checks.
- Encrypt OAuth state with Android Keystore, exclude it from backups, refresh
  credentials for MCP requests and Agent turns, and clear access on disconnect.
- Public published requests omit Authorization and use a private random guest
  session for Agent conversation isolation. Published mode does not poll drafts.
- Handle HTTPS preview intents as well as existing scanning/manual entry.

## Checks performed

| Check | Result |
| --- | --- |
| SDK unit tests | 10 passed: draft refresh, public discovery/chat/card resources, guest sessions, rejected anonymous drafts, rotating MCP/Agent tokens |
| Preview link and OAuth trust unit tests | 9 passed |
| Sample app and test APK build | Passed |
| Android 16/API 36 emulator instrumentation | 5 passed; 2 opt-in live tests skipped |
| Instrumented sign-in checks | Draft sign-in entry, activity recreation, S256/state generation, wrong-state/project rejection, encrypted session restoration, disconnect |
| Instrumented Keystore checks | Persistence, environment isolation, ciphertext, tamper rejection |
| Live draft discovery/registration/browser handoff | Passed; real Metabind login page opened |
| Live draft login and card rendering | Pending user sign-in |
| Live published Finance on a fresh app install | Opened chat without login; a subscriptions message returned the live Agent's missing-Authorization error |

Builds use a temporary Gradle init script to substitute local BindJS source while
the existing GitHub Packages cutover remains incomplete. No dependency override,
live credential, or live project identifier is committed. This is not a clean
published-package build. The public card unit test uses a local HTTP fixture;
it does not establish production guest-chat support.

The visible emulator is reserved for the user's draft login. A separate fresh
headless emulator was used for the published test without a stored OAuth session.
No physical Android device was connected. The existing physical Apple tests do
not establish Android camera behavior.

## Remaining before release

- Complete real Android draft sign-in, chat/card rendering, token refresh,
  permission removal, and physical camera testing.
- Deploy shared Agent guest-chat support and repeat published chat/card testing.
- Deploy Studio's credential-free draft/published QR controls.
- Publish the matching SDK and BindJS packages and verify a clean registry build.
- Serve valid Android Digital Asset Links at `/.well-known/assetlinks.json` for
  both `www.metabind.ai` and `dev.metabind.ai`. Both currently return the HTML app
  shell. Use package `ai.metabind.app` and the Play App Signing certificate's
  SHA-256 fingerprint, not a placeholder or a developer's upload-key fingerprint.
  Separately configure any development package/certificate association required
  for testing. Explicit adb intent launches do not validate domain verification.

The web chat transport also still unconditionally sends bearer credentials. It
needs its own guest-session/no-Authorization update to offer anonymous published
chat; Android does not depend on that web-client change.
