"""
Suggests fixes for common Gradle build failures.
"""

# Each rule is (phrases to look for, suggestion). Phrases are lowercase.
RULES = [
    (
        ("failed to load include path", "android.jar"),
        "aapt2 cannot read this Android platform (common on Termux). "
        "Set COMPILE_SDK = 34 in native/config.py, or 33 if that still fails.",
    ),
    (
        ("sdk location not found", "android_home"),
        "The Android SDK was not found. Set the ANDROID_HOME environment variable "
        "to your SDK folder.",
    ),
    (
        ("failed to install the following android sdk packages", "licen"),
        "Accept the SDK licenses: sdkmanager --licenses",
    ),
    (
        ("build-tools", "failed to find build tools"),
        "A build-tools version is missing. Install one: sdkmanager \"build-tools;34.0.0\"",
    ),
    (
        ("unsupported class file major version", "java_home", "requires java", "invalid source release"),
        "Java problem. Use JDK 17 for Gradle and check JAVA_VERSION in native/config.py.",
    ),
    (
        ("unknownhostexception", "could not resolve", "connect timed out", "connection refused"),
        "Gradle could not download something. Check your internet connection and try again.",
    ),
    (
        ("keystore", "failed to read key", "cannot recover key"),
        "Signing failed. Check SIGNING_KEYSTORE and SIGNING_KEY_ALIAS in native/config.py, "
        "and set DUCK_KEYSTORE_PASSWORD and DUCK_KEY_PASSWORD.",
    ),
    (
        ("outofmemoryerror", "java heap space", "daemon disappeared"),
        "Gradle ran out of memory. Close other apps and try again.",
    ),
    (
        ("aapt2",),
        "aapt2 failed. Set DUCK_AAPT2 to a working aapt2 binary and try again.",
    ),
]


def suggest(output: str) -> list[str]:
    """
    Find suggestions that match a failed build's output.

    Args:
        output: Everything Gradle printed.

    Returns:
        list[str]: Matching suggestions, empty when nothing is recognised.
    """
    text = output.lower()
    
    return [
        hint
        for phrases, hint in RULES
        if any(phrase in text for phrase in phrases)
    ]
