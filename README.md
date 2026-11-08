# DanMemoRe Launcher APK (untested, best effort)

A thin app: it starts the Termux launcher and shows it full screen. The server itself still runs in Termux
(embedding Python in an APK breaks the server's worker processes and openssl calls).

## Build (no PC tools needed)
1. Create a GitHub repo, upload this folder's contents (keep `.github/workflows/build.yml`).
2. Actions tab -> "Build APK" -> Run workflow. Download `DanMemoRe-launcher-apk` and install app-debug.apk.

## One-time phone setup
1. Install Termux from F-Droid, unzip danmemo-android.zip, run `sh install.sh`.
2. In Termux: `mkdir -p ~/.termux && echo "allow-external-apps=true" >> ~/.termux/termux.properties`, then restart Termux.
3. Android Settings -> Apps -> DanMemoRe -> Permissions -> allow "Run commands in Termux environment".
4. Open the DanMemoRe app. Termux needs "Display over other apps" allowed for background start on newer Android.

