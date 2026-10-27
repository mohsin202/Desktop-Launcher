# Desktop Launcher (Android)

Android source project for `com.android.desktoplauncher`.

## Build on GitHub

1. Upload/push the contents of this folder to a GitHub repository.
2. Open **Actions** and run **Build APK** (or push a commit).
3. Download the `DesktopLauncher` artifact from the completed workflow.

The workflow currently builds a debug APK using Android SDK 35, which is a conservative
build baseline. The app's `targetSdk` and `compileSdk` can be overridden with Gradle
properties `desktop.targetSdk` and `desktop.compileSdk`; changing them to 37 requires
an installed Android 17 SDK platform and a compatible Android Gradle Plugin/toolchain.

## Important platform limitations

- The overlay taskbar requires the user to grant **Display over other apps** permission.
- Back/Home/Recents overlay buttons require the user to enable the app's accessibility
  service. Android/OEM policy can still limit overlay behavior.
- A third-party app cannot replace BassOS/Launcher3's privileged navigation-bar taskbar
  integration. This implementation uses an overlay as a best-effort alternative and
  may not be identical to the built-in BassOS taskbar.
- Freeform/windowed launch is OEM/system dependent. The source now attempts to request
  freeform mode reflectively when the Android build exposes that API; otherwise it falls
  back to a normal app launch.
- The `attachments` folder from the supplied workspace was empty and is not included.
