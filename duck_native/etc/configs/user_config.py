"""
Duck Native project configuration.

This file contains application, platform, appearance, and build settings
for the project.
"""


# APPLICATION

# Human-readable name displayed by the native application.
APP_NAME = "My Duck App"


# Unique identifier used to identify the application on the target platform.
# For Android, this becomes the application's package name.
PACKAGE_NAME = "com.example.myapp"


# Application version presented to users and app stores.
VERSION = "1.0.0"


# Internal numeric version used by the platform to determine whether a newer
# build has been installed.
VERSION_CODE = 1


# PERMISSIONS

# Native platform permissions requested by the application.
#
# Only declare permissions that the application actually requires. Duck Native
# uses this list when generating the platform-specific application manifest.
PERMISSIONS = [
    "android.permission.INTERNET",
]


# APPLICATION ICON

# Path to the application's icon.
#
# Duck Native uses this asset when generating the platform-specific application
# resources and launcher icons.
# ICON = "native/assets/icon.png"


# SPLASH SCREEN

# Path to the image displayed while the native application is starting.
# SPLASH_IMAGE = "assets/splash.png"


# Background colour displayed behind the splash screen.
SPLASH_BACKGROUND = "#FFFFFF"


# DISPLAY

# Preferred screen orientation.
# Supported values: "portrait", "landscape", and "unspecified".
ORIENTATION = "portrait"


# Whether the application should hide the system status and navigation bars
# and use the entire available screen area.
FULLSCREEN = False


# Whether the application should prevent the device from entering sleep while
# the application is active.
KEEP_SCREEN_ON = False


# ANDROID

# Minimum Android API level supported by the application.
MIN_SDK = 23


# Android API level used when compiling and targeting the application.
TARGET_SDK = 36


# Android API level used to compile the application. Leave unset to compile
# against the newest supported level. Lower it (for example 34) if aapt2 fails
# to load android.jar, as happens on Termux.
# COMPILE_SDK = 34


# BUILD

# Target native platform for this project.
PLATFORM = "android"


# CPU architecture for which the native application will be built.
ARCHITECTURE = "arm64-v8a"


# Enables development-oriented build behaviour such as debugging information.
# Set this to False when producing a release build.
DEBUG = True


# Address of your Duck server, e.g. "https://example.com".
#
# Release builds open straight into this server and require it to use https.
# Override it per build with: duck native build --server-url https://example.com
# In debug builds, leave it as None to type a server address on the error screen.
SERVER_URL = None


# Signs a release build with the debug key so it installs without a keystore.
#
# Meant for testing only. Prefer the flag: duck native build --test-release
TEST_RELEASE = False


# Accepts invalid HTTPS certificates (self-signed, expired, wrong host) from
# your own server. Never leave this on for a public app.
ALLOW_INSECURE_CERTIFICATES = False


# Directory where Duck Native places generated build files and artifacts.
BUILD_DIR = "build"


# Java / Kotlin

# Java language and JVM bytecode version used to compile the native
# application. Duck Native applies this version to both Java and Kotlin.
JAVA_VERSION = 17


# SIGNING

# Path to the signing keystore used for release builds.
#
# Leave this unset or None during development. A release build must be signed
# before it can be distributed through an app store or installed as a trusted
# production application.
SIGNING_KEYSTORE = None


# Alias of the signing key inside the keystore.
SIGNING_KEY_ALIAS = None
