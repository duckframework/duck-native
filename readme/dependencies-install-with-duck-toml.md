# 📦 Installing dependencies with `duck.toml`

Duck Native needs a few system tools to build your app, like Gradle. Instead of
installing each one by hand, list them in `duck.toml` and run one command:

```bash
duck sync
```

Duck Sync comes with Duck Framework and uses your system's package manager
(`apt`, `brew`, `pacman`, `pkg` on Termux and more), so you don't need to
install anything extra.

## 🚀 Quick start

1. Put a `duck.toml` in your project root and commit it.
2. List the tools Duck Native needs:

```toml
[dependencies]
# Needed to build the app.
system = ["gradle"]

[development]
# Only installed with --dev or --with-dev.
system = ["kotlin"]
```

3. Install them:

```bash
duck sync --with-dev
```

## 🧰 Commands

| Command | What it installs |
| --- | --- |
| `duck sync` | `[dependencies]` only |
| `duck sync --dev` | `[development]` only |
| `duck sync --with-dev` | Both |
| `duck sync --dry-run` | Nothing. Shows what it would install. |

> 💡 Run `duck sync --dry-run` first to see what will be installed.

## ✏️ Add your own tools

Add a name to the list it belongs in. Python packages go in `python`, system
tools go in `system`:

```toml
[dependencies]
python = ["requests"]
system = ["gradle", "my-build-tool"]
```

Use generic names. Duck Sync turns them into the right package name for your
system. If a name doesn't match, rename it for one package manager with
`[overrides]`:

```toml
[overrides.mypackage]
apt    = "libmypackage-dev"
brew   = "mypackage"
pacman = "mypackage"
```

## 💡 Good to know

- Duck Native itself installs with `pip install duck-native`.
- Gradle and Kotlin are the only system tools listed above. Install JDK 17 and
  the Android SDK yourself (see
  [What you need](https://github.com/duckframework/duck-native#-what-you-need)),
  or on a phone follow the
  [Termux guide](https://github.com/duckframework/duck-native/blob/main/readme/termux-notes.md).
- Pick a package manager for one run with `--backend`, for example
  `duck sync --backend apt`.
- Inside a container where you're already root, use `duck sync --no-sudo`.
