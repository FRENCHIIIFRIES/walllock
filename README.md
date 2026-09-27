# Walllock

The iPhone's big album cover, on the Android lock screen. While music plays, your lock screen shows
the album cover as a large rounded box, over a soft blur of its own colours. Your phone's own clock,
notifications and media player stay exactly where they are, on top of it. When the music stops,
your wallpaper comes back.

## What it does

- **Big cover.** The whole cover, exactly as it is (never cropped or changed), as a big rounded box
  with a soft shadow, like the iPhone.
- **Background.** A blur of the cover's colours, or plain black.
- **Position.** High, middle or low, to sit clear of your phone's clock and notifications.
- **Follows the music.** Changes with every song, waits a moment while you skip through, and keeps
  the cover for 10 minutes after you pause (can be turned off).
- **Puts your wallpaper back.** When music stops, the lock screen goes back to your home screen
  wallpaper, or to an image you pick (for lock screens that had their own wallpaper).
- **Quick Settings tile** to switch it on and off, even from the lock screen.
- **Live preview** in the app, which is styled in the Nothing look.

Walllock only changes the lock screen wallpaper; it never draws over the lock screen or the home
screen.

## Install

On your phone, download
**[Walllock.apk](https://github.com/FRENCHIIIFRIES/walllock/releases/latest/download/Walllock.apk)**
(always the newest build) and open it to install. Or build it yourself:

```sh
./gradlew assembleRelease   # app/build/outputs/apk/release/app-release.apk
```

Then open Walllock and allow **Notification access**, so it can see what's playing. On Android 13+
this may be greyed out for sideloaded apps: go to *Settings → Apps → Walllock → ⋮ → Allow restricted
settings*, then try again.

If your lock screen has its own wallpaper (different from the home screen), tap **Pick** under
*When music stops* and choose it, so Walllock can put it back.

Requires Android 8.0 (API 26) or newer.

### Updating

Open Walllock and scroll to **Updates**, then tap **Check**. It never checks by itself. If a newer
build is out, the button becomes **Update**, which downloads and installs it in place (the first
time, Android asks you to allow Walllock to install apps).

## How it works

- `WalllockListener` is a notification listener: that's what lets an app read the phone's media
  sessions (song, artist, cover, play state).
- `LockWall` decides when the cover should be up, draws it with `CoverArt` at the screen's size on
  a background thread, and sets it with `WallpaperManager` as the lock screen wallpaper only
  (`FLAG_LOCK`). Putting yours back clears the lock screen wallpaper so it follows the home screen
  again, or sets the image you picked.

## Fonts

[Doto](https://fonts.google.com/specimen/Doto) (dot-matrix) and
[Space Mono](https://fonts.google.com/specimen/Space+Mono), both under the SIL Open Font License
(see `licenses/`). Walllock is not affiliated with Nothing Technology.
