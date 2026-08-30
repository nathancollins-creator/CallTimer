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
- **No WhatsApp code in this version.** `CallSource.CELLULAR` and
  `CallSource.TEST` are implemented; `WHATSAPP` is left as a documented gap
  in the enum for a future version to slot into the same engine, but no
  WhatsApp files exist yet — nothing to maintain or debug for V1.

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

## 3. Building the APK — recommended path: GitHub Actions (no local install)

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

## 4. Setting it up on your Samsung Galaxy S24 Ultra

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
3. Back on the main screen, pick your duration (10 minutes is the default),
   leave Warning/Sound/Vibrate on, tap **ENABLE CALL TIMER**. The status
   should flip to 🟢 ACTIVE and a "Watching for calls" notification appears.

## 5. Testing without waiting for a real call

**Test / Debug screen → pick 10s/30s/1m/5m/10m → START TEST.** This
simulates a connected call and runs the exact same timer/notification code
path a real call would. Watch the debug fields update live; use **End Test
Call** to simulate hanging up and confirm the timer stops immediately.

## 6. Testing with a real call

1. With Call Timer enabled, have someone call you (or call them).
2. For a fast pass, set duration to 1 minute first so you're not waiting 10.
3. **Incoming:** answer it — the timer should start at that exact moment.
   **Outgoing:** the timer starts as soon as you dial (see the limitation
   above) — the far end doesn't need to answer for the countdown to show up.
4. At 1 minute remaining, a notification should appear (and the status
   notification's text should change). At the limit, you should get the
   stronger alert with sound/vibration per your toggles. The call keeps
   running — you hang up manually. The moment either side hangs up, the
   timer should stop and the notification should say "Call ended."

## 7. Known limitations, plainly stated

- Outgoing-call start-time accuracy — covered above.
- Background reliability is a genuine "should work, please confirm"
  item: a foreground service with a visible notification is the correct,
  Android-sanctioned way to stay alive in the background, and the battery
  exemption further protects it — but Samsung's OEM-level background
  management has historically been more aggressive than stock Android's,
  and I have no way to verify this holds on a real S24 Ultra without you
  testing it.
- Minimum Android version supported: 8.0 (API 26), chosen because dropping
  the dialer-role requirement removed the only reason V1 needed API 29+.

## 8. Final report

**NORMAL CALL DETECTION:** WORKING for incoming calls, PARTIALLY WORKING for
outgoing calls (starts a few seconds early — see the honest limitation
above). Both are implemented with no stub/fake code.

**AUTOMATIC TIMER:** WORKING as a mechanism — implemented fully, ticks every
second, correctly stops on call-end. Unverified by me on real hardware.

**BACKGROUND TIMER:** PARTIALLY WORKING / NEEDS ON-DEVICE CONFIRMATION —
correct Android mechanism (foreground service + battery-optimization
exemption) is in place, but Samsung-specific background-kill behavior can
only be confirmed by testing on your actual S24 Ultra.

**SOUND/VIBRATION ALERT:** WORKING as a mechanism — implemented using the
only approach that actually respects per-alert toggles on Android 8+
(separate notification channels per sound/vibrate combination; see the
comment at the top of `CallTimerNotification.kt` for why a simpler
per-notification `setSound()` call would have silently failed). Unverified
on real hardware.

**WHATSAPP:** NOT YET IMPLEMENTED — deliberately deferred per the brief.
