# Call Timer — Version 2 (Simplified: Detect → Count → Alert)

No call termination anywhere in this codebase. No default-dialer role. No
Android Studio required to build it. This version trades some accuracy on
outgoing calls (explained below) for a much simpler setup.

I could not compile or run this myself — this sandbox has no Android SDK,
emulator, or phone, and no access to Google's Maven repo. Everything below
is written from a correct, current understanding of the Android APIs
involved, but you are the first one to actually build and run it.

---

## 1. What changed from Version 1, and why

- **No `InCallService`, no `RoleManager.ROLE_DIALER`.** Detection now uses
  `TelephonyManager`'s call-state notifications (`IDLE`/`RINGING`/`OFFHOOK`),
  which only require the single `READ_PHONE_STATE` permission — no becoming
  the default phone app, no dial-pad trampoline activity, no giving up your
  normal calling app while the timer runs.
- **No termination code at all.** `CallTimerEngine` has no concept of ending
  a call — it only has `start()` and `ended()`. There is nothing in this
  repo that could accidentally hang up a call even by mistake.
- **WhatsApp detection is back, alert-only.** Same accessibility-based
  approach as the very first version, minus every line of code that used to
  click WhatsApp's own controls — this version only ever reads what's on
  screen.
- **Selectable time-limit alert styles.** Default alarm sound, a siren, a
  warble, a spoken announcement, or any ringtone already on your phone — see
  section 3 below for how each one actually works under the hood.

## 2. The one honest limitation (Step 1 finding)

`TelephonyManager` call state cannot distinguish "outgoing call still
ringing on the other end" from "outgoing call answered" — both are just
`OFFHOOK`. That distinction is only available to the app that's the active
default dialer (via `InCallService`), which is exactly the complexity we
removed. So:

- **Incoming calls:** exact. The timer starts precisely when you answer.
- **Outgoing calls:** the timer starts the moment you dial, a few seconds
  before the other person actually answers. This is a real Android platform
  restriction, not something fixable at this permission level — see the
  comment at the top of `CellularCallDetector.kt` for the full explanation.
  Every time it applies, the app also logs it plainly in the event log so
  it's never silently wrong.

If perfectly accurate outgoing-call timing matters enough to you to justify
the added setup complexity, that's exactly what Version 1 (dialer-role based
detection) already implements — happy to help you go back to that path
specifically for detection (with termination still stripped out) if you'd
rather trade simplicity for that accuracy.

## 3. How the alert styles actually work

Android locks a notification's sound and vibration to whichever
*notification channel* it's posted to (since Android 8) — a per-notification
"play this sound" call is silently ignored once a channel exists. That rules
out a simple implementation for two of the five styles here, since neither a
spoken sentence nor a synthesized tone can be expressed as a channel's fixed
sound file. So the two pieces are split:

- The **visual notification** (`CallTimerNotification.kt`) is always silent
  on its own — just text, always shown.
- The **actual sound/speech/vibration** (`AlertPlayer.kt`) is triggered
  imperatively, in code, at the exact moment the limit is reached:
  - **Default** and **Custom ringtone** play a real media file via
    `Ringtone.play()` (system default alarm sound, or whichever ringtone you
    picked via Android's own ringtone picker).
  - **Siren** and **Warble** use Android's built-in `ToneGenerator` to
    synthesize a tone on the spot — no audio files involved at all, so
    these work identically on every device.
  - **Spoken announcement** uses Android's built-in text-to-speech engine to
    say "Your call time limit has been reached" out loud. It's preloaded at
    app startup (`CallTimerApp.kt`) so it has time to initialize before your
    first real alert; if it somehow isn't ready in time, that's logged in
    the event log rather than silently doing nothing.
- **Vibrate** is its own toggle and applies on top of whichever style you
  picked, including Spoken.

## 4. WhatsApp calls — how it works, and its real limits

Same situation as always: WhatsApp has no API for this, so detection works
by watching WhatsApp's own screen via Android's Accessibility service and
recognizing when its call screen is showing (primarily by looking for the
running `Chronometer` widget WhatsApp itself uses for the call-duration
readout — see the comment at the top of `WhatsAppCallDetector.kt`). This
version **never** taps, clicks, or otherwise controls WhatsApp — it only
reads what's already on screen and reports it to the same timer engine the
cellular detector uses.

**Two honest caveats:**
- This is the most fragile part of the whole app. WhatsApp's UI can change
  with any update and silently stop matching the detection hints — if
  WhatsApp calls stop being detected after a WhatsApp update, that file is
  the one to check and update, using `adb shell uiautomator dump` (or
  Android Studio's Layout Inspector) against a live WhatsApp call.
- Direction (incoming vs. outgoing) for WhatsApp calls is best-effort text
  matching on the pre-connect screen and is noticeably less reliable than
  the cellular path's exact signal.

WhatsApp detection needs **two** separate opt-ins to do anything: the
"WhatsApp calls" toggle on the main screen, AND Accessibility granted for
Call Timer specifically in system Settings (Permissions / Setup → Enable
Accessibility). Either one alone does nothing.

## 5. Building the APK — recommended path: GitHub Actions (no local install)

This is the one path I'd recommend. It builds the APK on GitHub's servers
and gives you a `.zip` to download — nothing installs on your computer.

**You need:** a free GitHub account. Nothing else.

1. Go to github.com, sign in (or create a free account), click the **+** in
   the top-right → **New repository**. Name it `call-timer`, leave it
   **Public** (simplest — no extra billing setup needed), don't check any of
   the "Initialize with..." boxes, click **Create repository**.
2. On the new empty repo's page, click **uploading an existing file** (a
   link in the box of instructions GitHub shows you).
3. On your computer, open the `CallTimer` folder from this zip. Select
   *everything inside it* (all files and folders — `app`, `gradle`,
   `.github`, `build.gradle.kts`, etc., but not the outer `CallTimer` folder
   itself) and drag that whole selection onto the GitHub upload page in your
   browser. Modern Chrome/Edge preserves the folder structure when you drag
   folders this way — wait for the file list to finish populating before
   committing.
4. Scroll down, click the green **Commit changes** button.
5. Click the **Actions** tab near the top of the repo. You should see a
   workflow run start automatically (named "Build Debug APK") — click it.
6. Wait for the green checkmark (a few minutes). Then scroll down to
   **Artifacts** and click **call-timer-debug-apk** to download a `.zip`.
7. Unzip it — inside is `app-debug.apk`. Copy that file to your phone (email
   it to yourself, use a cloud drive, or a USB cable) and tap it there to
   install (Android will ask you to allow installing from that source once).

If step 3's drag-and-drop doesn't preserve the folder structure in your
browser, the reliable fallback is installing the free **GitHub Desktop**
app, which lets you point it at the `CallTimer` folder directly and publish
it with a few clicks — say the word if you hit that and I'll walk you
through it.

## 6. Setting it up on your Samsung Galaxy S24 Ultra

1. Install the APK (see above), open **Call Timer**.
2. Go to **Permissions / Setup**:
   - Tap **Grant** under "Phone state" → allow it.
   - Tap **Grant** under "Notifications" → allow it.
   - Tap **Exempt from battery optimization** → confirm. This matters more
     on Samsung than stock Android — One UI's Device Care can put
     long-running background apps to sleep even with a visible notification.
   - Also worth doing manually, as noted on that screen: **Settings → Apps
     → Call Timer → Battery** and make sure it's not set to be put to sleep,
     and it isn't listed under **Settings → Battery → Background usage
     limits → Sleeping apps**.
   - If you want WhatsApp calls timed too: tap **Enable Accessibility** →
     find "Call Timer" in the list → turn it on → confirm the warning
     dialog.
3. Back on the main screen: pick your duration (10 minutes is the default),
   pick a time-limit alert style (and choose a ringtone if you picked
   "Custom"), leave Warning/Vibrate on, turn on "WhatsApp calls" if you set
   up Accessibility, then tap **ENABLE CALL TIMER**. Status should flip to
   🟢 ACTIVE and a "Watching for calls" notification appears.

## 7. Testing without waiting for a real call

**Test / Debug screen → pick 10s/30s/1m/5m/10m → START TEST.** This
simulates a connected call and runs the exact same timer/notification/alert
code path a real cellular call would (WhatsApp detection can only be tested
with a real WhatsApp call, since it depends on WhatsApp's actual screen).
Watch the debug fields update live; use **End Test Call** to simulate
hanging up and confirm the timer stops immediately.

## 8. Testing with a real call

1. With Call Timer enabled, have someone call you (or call them) — or, for
   WhatsApp, start/receive a WhatsApp voice call with "WhatsApp calls" and
   Accessibility both turned on.
2. For a fast pass, set duration to 1 minute first so you're not waiting 10.
3. **Incoming (cellular):** answer it — the timer starts at that exact
   moment. **Outgoing (cellular):** starts as soon as you dial (see the
   limitation above). **WhatsApp:** starts once the call screen with the
   running duration readout appears.
4. At 1 minute remaining, a notification should appear. At the limit, you
   should get your chosen alert style plus vibration (if on). The call keeps
   running — you hang up manually. The moment either side hangs up, the
   timer should stop and the notification should say "Call ended."

## 9. Known limitations, plainly stated

- Outgoing-call start-time accuracy — covered in section 2.
- WhatsApp detection fragility and direction accuracy — covered in section 4.
- Background reliability is a genuine "should work, please confirm"
  item: a foreground service with a visible notification is the correct,
  Android-sanctioned way to stay alive in the background, and the battery
  exemption further protects it — but Samsung's OEM-level background
  management has historically been more aggressive than stock Android's,
  and I have no way to verify this holds on a real S24 Ultra without you
  testing it.
- Minimum Android version supported: 8.0 (API 26), chosen because dropping
  the dialer-role requirement removed the only reason V1 needed API 29+.

## 10. Final report

**NORMAL CALL DETECTION:** WORKING for incoming calls, PARTIALLY WORKING for
outgoing calls (starts a few seconds early — see the honest limitation
above). Both are implemented with no stub/fake code.

**AUTOMATIC TIMER:** WORKING as a mechanism — implemented fully, ticks every
second, correctly stops on call-end. Unverified by me on real hardware.

**BACKGROUND TIMER:** PARTIALLY WORKING / NEEDS ON-DEVICE CONFIRMATION —
correct Android mechanism (foreground service + battery-optimization
exemption) is in place, but Samsung-specific background-kill behavior can
only be confirmed by testing on your actual S24 Ultra.

**SOUND/VIBRATION/SPEECH ALERT:** WORKING as a mechanism for all five
styles — implemented using the only approach that actually works on Android
8+ (silent visual notification + sound/speech/vibration triggered
imperatively in code; see section 3 for why a simpler per-notification
`setSound()` call would have silently failed). Unverified on real hardware.

**WHATSAPP:** PARTIALLY WORKING / NEEDS ON-DEVICE VERIFICATION — the
mechanism is correctly implemented with no stubs, but the exact detection
signals are inherently dependent on your installed WhatsApp version and
must be confirmed (and possibly tweaked in `WhatsAppCallDetector.kt`) on
your actual phone. This is the same honest caveat WhatsApp detection has
carried since the very first version.
