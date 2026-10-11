<div align="center">

# Duck Native

**Turn your Duck Framework web app into a real Android app.**

[![PyPI version](https://img.shields.io/pypi/v/duck-native?color=blue&logo=pypi&logoColor=white)](https://pypi.org/project/duck-native/)
[![Python versions](https://img.shields.io/pypi/pyversions/duck-native?logo=python&logoColor=white)](https://pypi.org/project/duck-native/)
[![License](https://img.shields.io/badge/license-BSD--3--Clause-green)](LICENSE)
[![Status](https://img.shields.io/badge/status-beta-orange)](https://github.com/duckframework/duck-native)
[![Platform](https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white)](https://github.com/duckframework/duck-native)
[![GitHub stars](https://img.shields.io/github/stars/duckframework/duck-native?style=social)](https://github.com/duckframework/duck-native)

[Website](https://duckframework.com) · [Documentation](https://docs.duckframework.com) · [Report a bug](https://github.com/duckframework/duck-native/issues)

</div>

---

## 📖 What is Duck Native?

You already have a web app built with [Duck Framework](https://duckframework.com).
Duck Native wraps it in an Android app, so people can install it from an APK or
the Play Store like any other app.

You do **not** need to write Kotlin or Java. You edit one Python file
(`native/config.py`) and run one command.

## 📋 Table of contents

1. [What you need](#-what-you-need)
2. [Install](#-install)
3. [Quick start](#-quick-start)
4. [Commands](#-commands)
5. [Settings](#%EF%B8%8F-settings)
6. [Where is my app?](#-where-is-my-app)
7. [Troubleshooting](#-troubleshooting)
8. [Contributing](#-contributing)

## ✅ What you need

| Need | Why | Needed for `apk` command? |
| --- | --- | --- |
| Python 3.10 or newer | Runs Duck Native | Yes |
| A Duck Framework project | The app you want to wrap | Yes |
| JDK 17 | Runs Gradle | No |
| Android SDK | Builds the app | No |
| Gradle 8.9 or newer | Builds the app | No |

> 💡 Just want to try your app on a phone quickly? Skip the last three rows and
> use [`duck native apk`](#-get-a-ready-made-debug-apk).

## 📦 Install

```bash
pip install duck-native
```

## 🚀 Quick start

Run these inside your Duck project folder.

**1. Create the config**

```bash
duck native init
```

This creates `native/config.py`.

**2. Edit it**

Open `native/config.py` and change at least these two lines:

```python
APP_NAME = "My Duck App"
PACKAGE_NAME = "com.yourname.myapp"
```

**3. Build**

```bash
duck native build
```

**4. Install the app**

Copy `build/app-debug.apk` to your phone and open it, or plug your phone in and
run `adb install -r build/app-debug.apk`.

That's it! 🎉

## 🧰 Commands

> **`duck native` and `duck-native` are the same command.** We suggest
> `duck native` as the base command, and the examples here use it. Use
> `duck-native` if you prefer one word, or `python -m duck_native`.

| Command | What it does |
| --- | --- |
| `duck native init` | Creates `native/config.py` in your project. Add `--force` to overwrite. |
| `duck native check` | Checks your config and shows any problems. Builds nothing. |
| `duck native build` | Builds your app. Debug builds make an APK. |
| `duck native build --apk` | Builds a release APK instead of a bundle (when `DEBUG = False`). |
| `duck native apk` | Gets a ready-made debug APK, with no Gradle or SDK needed. |

Every command takes an optional project folder, for example
`duck native build ../my-app`.

### ⚡ Get a ready-made debug APK

```bash
duck native apk              # copies app-debug.apk into your build folder
duck native apk --install    # also installs it on a connected phone
duck native apk --output ~/Desktop/test.apk
```

This APK is the same for everyone, so settings like the app name, icon and
permissions are **not** applied. Use `duck native build` when you need them.

### 🔨 What `build` does

You will see each step in the terminal:

1. **Prepare**: copies the Android project into `build/android`.
2. **Generate**: creates your icons, splash screen and manifest.
3. **Build**: runs Gradle on the copy.
4. **Collect**: copies the finished APK or AAB into `build/`.

If the build fails, Duck Native prints suggestions based on the error.

## ⚙️ Settings

All settings live in `native/config.py`. The common ones:

| Setting | Example | What it does |
| --- | --- | --- |
| `APP_NAME` | `"My Duck App"` | Name shown under the app icon. |
| `PACKAGE_NAME` | `"com.yourname.myapp"` | Unique app ID. Pick it once and keep it. |
| `VERSION` | `"1.0.0"` | Version people see. |
| `VERSION_CODE` | `1` | Whole number. Raise it for every update. |
| `PERMISSIONS` | `["android.permission.INTERNET"]` | Only list what you need. |
| `ICON` | `"assets/icon.png"` | App icon. Use a square PNG. |
| `SPLASH_IMAGE` | `"assets/splash.png"` | Image shown while the app starts. |
| `ORIENTATION` | `"portrait"` | `portrait`, `landscape` or `unspecified`. |
| `FULLSCREEN` | `False` | Hide the status and navigation bars. |
| `DEBUG` | `True` | `True` builds a debug APK. `False` builds a release. |
| `COMPILE_SDK` | `36` | Lower this if the build can't load `android.jar`. |
| `SIGNING_KEYSTORE` | `"my.keystore"` | Needed to sign release builds. |

The generated file has a comment above every setting explaining it.

## 📁 Where is my app?

Everything ends up in the `build/` folder of your project:

```
build/
├── app-debug.apk      ← debug build
├── app-release.aab    ← release bundle (Play Store)
├── android/           ← working copy of the Android project
└── generated/         ← icons, splash and manifest made for you
```

## 🩹 Troubleshooting

| Problem | Fix |
| --- | --- |
| `Gradle was not found` | Install Gradle 8.9 or newer. |
| `SDK location not found` | Set the `ANDROID_HOME` environment variable to your SDK folder. |
| `failed to load include path ... android.jar` | Add `COMPILE_SDK = 34` to `native/config.py`. Common on Termux. |
| `Failed to install the following Android SDK packages` | Run `sdkmanager --licenses` and accept everything. |
| `Pillow is required` | Run `pip install pillow`. |
| Release APK says `unsigned` | Set `SIGNING_KEYSTORE` and `SIGNING_KEY_ALIAS`, plus the `DUCK_KEYSTORE_PASSWORD` and `DUCK_KEY_PASSWORD` environment variables. |

Still stuck? [Open an issue](https://github.com/duckframework/duck-native/issues)
and paste the output of the failed command.

## 🤝 Contributing

Contributions are welcome! See the
[contribution guide](https://duckframework.com/contribute) or open an issue.

## 📄 License

Released under the [BSD 3-Clause License](LICENSE).
