# CallGuard — Never go over your call limit.

This document is the permanent record of the audit → investigation →
decision → implementation cycle for the transition from "Call Timer" to
production-direction "CallGuard". Read this before changing anything.

I still cannot compile or run this myself — no Android SDK, emulator, or
phone in this sandbox. Everything below is written from a correct, current
understanding of the Android APIs and Play policy involved (with sources),
verified against the actual code, but you are the one running it.

---

## 1. Audit of the pre-CallGuard codebase (what was actually there)

- **Versions:** AGP 8.5.2, Kotlin 1.9.24, Gradle 8.7, compileSdk/targetSdk 35, minSdk 26.
- **Architecture:** single module, Views + ViewBinding, one singleton state
  machine (`CallTimerEngine`) everything feeds into.
- **Cellular detection:** `TelephonyCallback`/`PhoneStateListener` on
  `IDLE/RINGING/OFFHOOK`. No dialer role. Incoming-call timing is exact;
  outgoing-call timing starts at dial-time (see section 2).
- **Timer/alerts:** single fixed 1-minute warning, limit alert with 5
  selectable styles (default sound, custom ringtone, siren, warble, spoken
  announcement via TTS), independent vibrate toggle.
- **Background:** foreground service (`specialUse` type), runs continuously
  while enabled, not just during a call.
- **WhatsApp:** implemented via Accessibility, detect-and-alert only (no
  control), off by default, needs two separate opt-ins.
- **Permissions already in place:** `READ_PHONE_STATE`, `POST_NOTIFICATIONS`,
  `FOREGROUND_SERVICE(+SPECIAL_USE)`, `VIBRATE`,
  `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. Nothing else. No `CALL_PHONE`, no
  `ANSWER_PHONE_CALLS`, no `CALL_LOG`, no `INTERNET`.
- **Auto call termination:** not implemented anywhere.
- **Weaknesses identified before the CallGuard changes:** single non-configurable
  warning point; main screen didn't match the CALL LIMIT/WARNING/WHEN LIMIT
  REACHED structure; WhatsApp sat in the main flow as if it were a first-class
  feature; no compatibility matrix; branding still said "Call Timer".

## 2. Outgoing-call accuracy (unchanged conclusion, re-verified)

`TelephonyManager`'s `OFFHOOK` state does not distinguish "dialing/ringing
out" from "answered" for outgoing calls - that distinction is only visible to
whichever app holds `RoleManager.ROLE_DIALER`. Incoming calls remain exact
(`RINGING->OFFHOOK` is the real answer moment). This has not changed and isn't
fixable without the full dialer commitment discussed in section 3.

## 3. Automatic call termination - full investigation (Step 4-8 of the brief)

**Conclusion up front: not implemented, on purpose.** Reliable auto-end
requires CallGuard to become the phone's complete default dialer app - dial
pad, incoming-call screen, ongoing-call screen, all of it. There is no
lighter-weight path. This applies uniformly across every current Android
version and every manufacturer; it is not a "some devices support it" case,
so it doesn't fit "Path B" - it's squarely "Path C", which the product
direction explicitly says not to force.

| Question | Finding | Source |
|---|---|---|
| Can a normal 3rd-party app end a call? | No. Only `InCallService.disconnect()`/`TelecomManager.endCall()`, exclusively bound to the `ROLE_DIALER` holder. | Android Developers: Build a default phone app (developer.android.com/develop/connectivity/telecom/dialer-app) |
| What's required to hold `ROLE_DIALER`? | Must handle `ACTION_DIAL` with a real dial-pad UI, AND fully implement `InCallService` with both incoming-call UI and ongoing-call UI. A minimal/UI-less registration doesn't qualify. | Same source |
| Works without becoming the default dialer? | No path exists. | Same source |
| Samsung / Pixel / Xiaomi / Motorola? | The role requirement is enforced by the AOSP Telecom framework itself, not an OEM decision - applies identically everywhere. (OEM background-kill aggressiveness is a separate, already-handled concern.) | Platform-level, not OEM-level |
| Dual-SIM? | No change - binding is per-role, not per-SIM. | |
| Incoming vs outgoing? | Same requirement for both. | |
| Would Google Play allow it? | Conditionally yes - but only once the app is the registered default Phone handler; restricted call-related permissions are gated on that. | Play Console: "Permissions and APIs that Access Sensitive Information"; Android Developers: "Permissions used only in default handlers" (developer.android.com/guide/topics/permissions/default-handlers) |
| Would the permissions be invasive? | The permission itself isn't the concern - the UX commitment (replacing the user's everyday phone app) is. | |
| Requires a full dialer buildout? | Yes, unavoidably. | |

**Decision:** Alert-only for this version. `CallTimerEngine` has no
termination code path - `start()` and `ended()` are its entire public
surface for call lifecycle. The main screen's "WHEN LIMIT IS REACHED"
section states this plainly rather than hiding it or offering a toggle that
can't do what it says.

## 4. What changed for CallGuard (this pass)

- **Rebrand:** app name, tagline, all user-facing notification/UI text now
  says CallGuard. The Kotlin package and `applicationId` were deliberately
  left as `com.calltimer.app` rather than renamed - a package rename this
  late is a mechanical, error-prone change across every file for a cosmetic
  benefit at this dev stage. If you're getting close to a Play Store
  submission, say so and this is worth doing properly then (it affects the
  Play listing identity, not the running app).
- **Main screen restructured** to match the brief exactly: large CALL LIMIT
  display, WARNING section with two independently-toggleable points (5 min /
  1 min - default duration is now 20:00, matching the new default), WHEN
  LIMIT IS REACHED section that states plainly that only "Alert only" is
  offered and why.
- **Multi-point warnings implemented properly**, not hardcoded to one value:
  `AppSettings.warningPointsSeconds` is a set, `CallTimerEngine` fires each
  configured point independently and tracks which have already fired per
  call (`firedWarningSeconds`), so both "5 minutes before" and "1 minute
  before" can be on at once without double-firing or interfering with each
  other. Structured so adding 30s/10s later (as the brief allows for) is a
  UI-only change.
- **WhatsApp moved out of the main flow entirely.** It now lives in
  Permissions/Setup under a clearly-labeled "EXPERIMENTAL - WhatsApp calls"
  section, off by default, with an explicit note that it's the kind of
  feature that invites Play Store review scrutiny and should stay off for a
  build you intend to publish. The underlying code is unchanged (still
  detect-and-alert only, still needs two separate opt-ins) - only its
  prominence changed, per the brief's instruction to evaluate whether it
  should "remain disabled / remain experimental / be removed."

## 5. First-run permission explanations (now in-app)

The Permissions/Setup screen now opens with the exact framing requested:
"CallGuard needs access to phone-call state so it can detect when a call
starts and ends and measure its duration. It does not need your contacts,
location, microphone, messages, or call recordings - and there is no account
of any kind." Each permission section below it explains specifically what
that permission does and doesn't grant, before the Grant button, never after.

## 6. Compatibility matrix

Documented honestly as claims, not verified results - I have no way to run
this on real hardware. Confidence is based on which Android layer each piece
depends on.

| Feature | Mechanism | Expected compatibility |
|---|---|---|
| Incoming-call detection | AOSP `TelephonyManager` state | High confidence, all manufacturers - core platform API, not OEM-modifiable |
| Outgoing-call detection | Same, with the documented early-start caveat | Same confidence, same caveat everywhere |
| Foreground service / background survival | AOSP foreground service + optional battery-optimization exemption | Stock Android: high confidence. Samsung/Xiaomi (aggressive OEM battery managers): needs the battery exemption and, for Xiaomi specifically, is known in the wider Android developer community to sometimes need additional manual "Autostart" allowance - not yet built into this app, worth adding if Xiaomi testing shows it's needed |
| Notifications, sound, vibration, TTS | Core Android APIs | High confidence, all manufacturers |
| WhatsApp detection | Accessibility, screen-reading heuristics | Low confidence by nature - depends on the installed WhatsApp build, not the phone manufacturer; expect to revisit `WhatsAppCallDetector.kt` periodically |
| Dual-SIM per-SIM limits | Not implemented (see section 7) | N/A |

This table should be replaced with real results as you test on the devices
listed in the brief (Samsung, Pixel, Motorola, Xiaomi/Redmi, Tecno/Infinix).

## 7. Dual-SIM - investigated, not implemented

Android exposes per-SIM information via `SubscriptionManager` /
`TelephonyManager.createForSubscriptionId()`, and `PhoneStateListener`/
`TelephonyCallback` can, on many devices, be registered per-subscription.
However, reliably mapping an in-progress call to a specific SIM slot without
holding the dialer role is inconsistent across OEMs in practice - some
expose it cleanly, others don't expose it at all to a non-dialer app. Per
the brief's own instruction ("reliability is more important than having the
checkbox"), this is left undone and documented rather than shipped as a
checkbox that might silently misattribute a call to the wrong SIM. Worth a
dedicated investigation pass on 2-3 real dual-SIM devices before building it.

## 8. Monetization architecture - documented, not built

No payment code exists yet, per instruction. The scaffolding decision:
`AppSettings` and `AlertSoundMode` are already structured so a future "Pro"
gate is a matter of checking one flag before allowing a setting to be
changed, not a rearchitecture - e.g. multiple warning points, custom
ringtone, and (eventually) per-profile/per-SIM limits are all natural Pro
candidates already living in isolated, swappable settings rather than
scattered through the UI code.

## 9. Building the APK - GitHub Actions (unchanged process)

Same as before: push/upload this project to a GitHub repo (public, free),
the `.github/workflows/build-apk.yml` workflow builds the debug APK on
GitHub's servers, download the artifact from the Actions run, install on
your phone. If you're picking this up mid-flow, you already know the steps.

## 10. Testing

Test/Debug screen -> simulated calls exercises the full timer/alert pipeline
(now correctly reporting multiple fired warning points) without a real call.
Real calls: incoming timing is exact; outgoing starts at dial-time per
section 2; WhatsApp requires both the experimental toggle and Accessibility
grant in Setup.

## 11. Known limitations, plainly stated

- Outgoing-call start-time accuracy (section 2) - unchanged, unfixable
  without the full-dialer commitment this product deliberately avoids.
- No automatic call termination (section 3) - deliberate product decision,
  not a gap waiting to be filled; would require rebuilding CallGuard as a
  complete dialer app.
- WhatsApp detection fragility (section 4) - inherent to the
  accessibility-service approach, now clearly scoped as experimental.
- Dual-SIM per-limit configuration - investigated, not implemented (section 7).
- Background reliability on aggressive OEM battery managers - should work
  via the existing foreground-service + battery-exemption approach; Xiaomi's
  additional Autostart permission is a known gap not yet addressed.
- Compatibility matrix (section 6) is a set of engineering claims, not
  verified results - needs real-device testing across the listed
  manufacturers.
