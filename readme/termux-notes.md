# 🤖 Building on Termux

Duck Native can build Android apps right on your phone with Termux. It needs a
few extra steps because Google's Android SDK doesn't run on ARM64.

> ⚡ **Just want to test quickly?** `duck native apk` needs no SDK, Java or
> Gradle, so it works on Termux as is. Follow this guide when you need
> `duck native build` (custom name, icon, permissions or actions).

## 1. Install the tools

```bash
pkg install openjdk-17 gradle aapt2
```

Termux's own `aapt2` is required. The one inside the custom SDK below fails with
`unexpected e_type`. Duck Native finds Termux's `aapt2` automatically. To use a
different one, set `DUCK_AAPT2`.

## 2. Get an ARM64 Android SDK

1. Download `android-sdk-aarch64-linux-android.tar.xz` from
   [HomuHomu833/android-sdk-custom](https://github.com/HomuHomu833/android-sdk-custom/releases).
2. Extract it into Termux's home folder, not shared storage:

```bash
mkdir -p ~/android-sdk
tar -xf android-sdk-aarch64-linux-android.tar.xz -C ~/android-sdk
```

3. If it extracted into `~/android-sdk/android-sdk`, move the contents up one level.
4. Tell Duck Native where it is. Add this line to `~/.bashrc`, then restart Termux:

```bash
export ANDROID_HOME=$HOME/android-sdk
```

## 3. Accept the SDK licenses

Gradle downloads the platform it needs, but only after the licenses are accepted:

```bash
mkdir -p ~/android-sdk/licenses
printf "\n24333f8a63b6825ea9c5514f83c2829b004d1fee\n" > ~/android-sdk/licenses/android-sdk-license
```

## 4. Lower the compile SDK

Termux's `aapt2` is built from Android 13 tools and can't read newer
`android.jar` files. Add this to `native/config.py`:

```python
COMPILE_SDK = 34
```

If you still get the error below, try `33`:

```
AAPT: error: failed to load include path .../platforms/android-36/android.jar
```

Leave `TARGET_SDK` as it is. Gradle downloads the matching platform on the next build.

## 5. Build

```bash
duck native build
```

## ⚠️ Keep your project in `~/`

Put your project inside Termux's home folder, for example `~/my-app`. Projects in
`/storage/emulated/0` can't run Gradle (`Permission denied`).

## 🩹 Quick fixes

| Problem | Fix |
| --- | --- |
| `SDK location not found` | Set `ANDROID_HOME` (step 2) and restart Termux. |
| `failed to load include path ... android.jar` | Lower `COMPILE_SDK` (step 4). |
| `unexpected e_type` | Install Termux's `aapt2`: `pkg install aapt2`. |
| Gradle can't download the platform | Accept the licenses (step 3). |
| `Permission denied` | Move the project to `~/`. |
