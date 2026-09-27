# Walllock

The iPhone's full-screen album cover, on the Android lock screen. While music plays, turning the
screen off puts a now-playing screen on top of the lock screen, and tapping the cover makes it big.

## What it does

- **Cover wallpaper.** The album cover blurred across the whole screen, with the big clock and date
  on top.
- **Player card.** Cover thumbnail, song and artist, a progress bar you can drag to scrub, and
  previous / play-pause / next.
- **Tap the cover** and it springs up to fill the top of the screen, melting into its own colour
  below. Tap it again to shrink it back. It remembers which you left it on.
- **Open the app.** Tap the song title to unlock and jump to the music app.
- **Swipe up** to unlock as usual (PIN, fingerprint or face). Unlocking with fingerprint or face
  straight away also closes it.
- **After pausing** it keeps showing for 10 minutes, like the iPhone (can be turned off).

Android doesn't let apps replace the real lock screen, so Walllock sits on top of it the way alarm
and call screens do.

## Install

On your phone, download
**[Walllock.apk](https://github.com/FRENCHIIIFRIES/walllock/releases/latest/download/Walllock.apk)**
(always the newest build) and open it to install. Or build it yourself:

```sh
./gradlew assembleRelease   # app/build/outputs/apk/release/app-release.apk
```

Then open Walllock and allow:

1. **Notification access**, so it can see what's playing. On Android 13+ this may be greyed out for
   sideloaded apps: go to *Settings → Apps → Walllock → ⋮ → Allow restricted settings*, then try again.
2. **Notifications** (Android 13+).
3. **Full-screen alerts** (Android 14+), which is how it appears over the lock screen on newer
   Android.
4. **Display over other apps** (Android 14 and older), which opens it instantly there.

Play some music and tap **Preview** to see it without locking the phone.

Requires Android 8.0 (API 26) or newer.

## How it works

- `WalllockListener` is a notification listener: that's what lets an app read the phone's media
  sessions (song, artist, cover, position) and control them. It also listens for the screen turning
  off.
- When the screen turns off with music playing, it opens `LockActivity`, which is allowed to show
  over the lock screen. Up to Android 14 it's started directly; from Android 15 it's opened through
  a silent full-screen notification.
- `LockView` draws everything by hand, so the cover can grow from the thumbnail to full size on a
  spring, iPhone style.
