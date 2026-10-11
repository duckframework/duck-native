"""
Loads and validates a Duck Native project's native/config.py.
"""
import os
import re
import importlib.util

from pathlib import Path
from dataclasses import dataclass
from types import ModuleType
from typing import Any, NamedTuple, Optional


class Limits:
    """
    Fixed values that Duck Native itself depends on.

    Attributes:
        MIN_SDK_FLOOR: Lowest Android API level Duck Bridge supports.
        PLAY_TARGET_SDK: API level Google Play requires for new apps and updates.
        COMPILE_SDK: Default API level the Android project compiles against.
        MIN_JAVA_VERSION: Oldest Java language level accepted.
        ORIENTATIONS: Screen orientations the app can request.
        PLATFORMS: Platforms that can be built.
        ARCHITECTURES: Android ABIs that can be built.
        PLACEHOLDER_PACKAGE: Package prefix that stores reject.
    """

    MIN_SDK_FLOOR = 23
    PLAY_TARGET_SDK = 36
    COMPILE_SDK = 36
    MIN_JAVA_VERSION = 8
    ORIENTATIONS = ("portrait", "landscape", "unspecified")
    PLATFORMS = ("android",)
    ARCHITECTURES = ("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
    PLACEHOLDER_PACKAGE = "com.example"


class Names:
    """
    Names and patterns shared by the loader.

    Attributes:
        CONFIG_PATH: Location of the user's file inside a project.
        BASE_PERMISSIONS: Permissions Duck Bridge always needs.
        PERMISSION_PREFIX: Namespace added to short permission names.
        SIGNING_ENV_VARS: Environment variables holding the signing passwords.
        PACKAGE: Valid Android application id.
        COLOR: Valid #RRGGBB or #AARRGGBB colour.
        SETTING: Looks like a setting name, as opposed to a helper or import.
        PATH_SETTINGS: Settings holding file paths.
    """

    CONFIG_PATH = Path("native") / "config.py"
    PERMISSION_PREFIX = "android.permission."
    BASE_PERMISSIONS = (
        PERMISSION_PREFIX + "INTERNET",
        PERMISSION_PREFIX + "ACCESS_NETWORK_STATE",
    )
    SIGNING_ENV_VARS = ("DUCK_KEYSTORE_PASSWORD", "DUCK_KEY_PASSWORD")
    PACKAGE = re.compile(r"^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$")
    COLOR = re.compile(r"^#(?:[0-9A-F]{6}|[0-9A-F]{8})$")
    SETTING = re.compile(r"^[A-Z][A-Z0-9_]*$")
    PATH_SETTINGS = ("icon", "splash_image", "build_dir", "signing_keystore")


class Encoding:
    """
    Constants for writing properties files.

    Attributes:
        ASCII_LIMIT: Code points below this are plain ASCII.
        UTF16_UNIT_BYTES: Bytes in one UTF-16 code unit.
    """

    ASCII_LIMIT = 128
    UTF16_UNIT_BYTES = 2


class Setting(NamedTuple):
    """
    Describes one setting a project's config.py may define.

    Attributes:
        default: Value used when the file omits it, or REQUIRED.
        types: Accepted Python types.
        label: Plain description used in error messages.
    """

    default: Any
    types: tuple[type, ...]
    label: str


class ConfigError(Exception):
    """
    Raised when a project's config.py cannot be loaded or is invalid.

    Args:
        path: The config file that was read.
        problems: Every problem found, so users can fix them in one pass.
    """

    def __init__(self, path: Path, problems: list[str]) -> None:
        lines = "\n\n".join(f"  - {problem}" for problem in problems)
        super().__init__(f"Invalid settings in {path}:\n\n{lines}")


@dataclass(frozen=True)
class ProjectConfig:
    """
    Validated settings for one Duck Native project.

    Paths are absolute and permissions are full Android names.

    Attributes:
        app_name: Name shown under the launcher icon.
        package_name: Android application id.
        version: User-facing version string.
        version_code: Integer that must increase with every release.
        permissions: Full permission names, including Duck Bridge's own.
        icon: Launcher icon source image, if any.
        splash_image: Splash screen image, if any.
        splash_background: Splash screen colour as #RRGGBB or #AARRGGBB.
        orientation: Preferred screen orientation.
        fullscreen: Whether to hide the system bars.
        keep_screen_on: Whether to keep the screen awake while the app is open.
        min_sdk: Lowest supported Android API level.
        target_sdk: Android API level the app targets.
        compile_sdk: Android API level the project compiles against.
        platform: Target platform.
        architecture: Android ABI to build for.
        debug: True for development builds, False for release builds.
        build_dir: Folder for generated build files.
        java_version: Java language level for Java and Kotlin.
        signing_keystore: Keystore used to sign release builds, if any.
        signing_key_alias: Key alias inside the keystore, if any.
    """

    app_name: str
    package_name: str
    version: str
    version_code: int
    permissions: tuple[str, ...]
    icon: Optional[Path]
    splash_image: Optional[Path]
    splash_background: str
    orientation: str
    fullscreen: bool
    keep_screen_on: bool
    min_sdk: int
    target_sdk: int
    compile_sdk: int
    platform: str
    architecture: str
    debug: bool
    build_dir: Path
    java_version: int
    signing_keystore: Optional[Path]
    signing_key_alias: Optional[str]

    @classmethod
    def from_values(
        cls,
        values: dict[str, Any],
        project_dir: Path,
    ) -> "ProjectConfig":
        """
        Build a config from raw settings, resolving paths and permissions.

        Args:
            values: Settings read from config.py, keyed by upper-case name.
            project_dir: Project folder that relative paths belong to.

        Returns:
            ProjectConfig: The normalized, not yet validated, config.
        """
        fields = {name.lower(): value for name, value in values.items()}

        # Put Duck Bridge's own permissions first and drop duplicates.
        extras = (normalize_permission(name) for name in fields["permissions"])
        merged = (*Names.BASE_PERMISSIONS, *extras)
        fields["permissions"] = tuple(dict.fromkeys(merged))

        # Compile against at least the target level unless told otherwise.
        if fields["compile_sdk"] is None:
            fields["compile_sdk"] = max(fields["target_sdk"], Limits.COMPILE_SDK)

        # Resolve every path against the project folder.
        for name in Names.PATH_SETTINGS:
            fields[name] = resolve_path(project_dir, fields[name])

        # Colours and orientations compare case-insensitively.
        fields["splash_background"] = fields["splash_background"].upper()
        fields["orientation"] = fields["orientation"].lower()
        
        # Return the final config
        return cls(**fields)

    def validate(self) -> list[str]:
        """
        Check the config for problems.

        Returns:
            list[str]: One message per problem, empty when the config is valid.
        """
        errors: list[str] = []

        # Identity.
        if not self.app_name.strip():
            errors.append("APP_NAME must not be empty.")
        
        if not Names.PACKAGE.match(self.package_name):
            errors.append("PACKAGE_NAME must look like com.company.app.")
        
        if self.version_code < 1:
            errors.append("VERSION_CODE must be 1 or higher.")

        # Android levels.
        if self.min_sdk < Limits.MIN_SDK_FLOOR:
            errors.append(f"MIN_SDK must be at least {Limits.MIN_SDK_FLOOR}.")
        
        if self.min_sdk > self.target_sdk:
            errors.append("MIN_SDK must not be higher than TARGET_SDK.")

        # Appearance.
        if not Names.COLOR.match(self.splash_background):
            errors.append("SPLASH_BACKGROUND must look like #FFFFFF or #80FFFFFF.")
        
        if self.orientation not in Limits.ORIENTATIONS:
            options = ", ".join(Limits.ORIENTATIONS)
            errors.append(f"ORIENTATION must be one of {options}.")
        
        for name, path in (("ICON", self.icon), ("SPLASH_IMAGE", self.splash_image)):
            if path is not None and not path.is_file():
                errors.append(f"{name} file not found: {path}")

        # Build.
        if self.platform not in Limits.PLATFORMS:
            errors.append(f"PLATFORM must be one of {', '.join(Limits.PLATFORMS)}.")
        
        if self.architecture not in Limits.ARCHITECTURES:
            options = ", ".join(Limits.ARCHITECTURES)
            errors.append(f"ARCHITECTURE must be one of {options}.")
        
        if self.java_version < Limits.MIN_JAVA_VERSION:
            errors.append(f"JAVA_VERSION must be at least {Limits.MIN_JAVA_VERSION}.")

        # Release builds have extra requirements.
        if not self.debug:
            errors.extend(self.release_errors())
        
        return errors

    def release_errors(self) -> list[str]:
        """
        Check the extra requirements of a release build.

        Returns:
            list[str]: One message per problem.
        """
        errors: list[str] = []

        # Stores reject placeholder identities.
        if self.package_name.startswith(Limits.PLACEHOLDER_PACKAGE):
            errors.append("PACKAGE_NAME still uses the com.example placeholder.")

        # Release builds must be signed.
        if self.signing_keystore is None or self.signing_key_alias is None:
            errors.append("Release builds need SIGNING_KEYSTORE and SIGNING_KEY_ALIAS.")
        
        elif not self.signing_keystore.is_file():
            errors.append(f"SIGNING_KEYSTORE file not found: {self.signing_keystore}")

        # Passwords come from the environment, never from config.py.
        missing = [name for name in Names.SIGNING_ENV_VARS if not os.environ.get(name)]
        
        if missing:
            errors.append(f"Set these environment variables: {', '.join(missing)}.")
        
        return errors

    def warnings(self) -> list[str]:
        """
        List problems that should not stop a build.

        Returns:
            list[str]: One message per warning.
        """
        # Google Play rejects new apps and updates targeting older API levels.
        if not self.debug and self.target_sdk < Limits.PLAY_TARGET_SDK:
            return [
                f"TARGET_SDK {self.target_sdk} is below {Limits.PLAY_TARGET_SDK}, "
                "which Google Play requires for new apps and updates."
            ]
        return []

    def gradle_values(self) -> dict[str, str]:
        """
        Collect every value the Gradle build needs.

        Signing passwords are left out. Gradle reads them from the environment.

        Returns:
            dict[str, str]: Gradle property names mapped to plain text values.
        """
        values = {
            "duck.appName": self.app_name,
            "duck.packageName": self.package_name,
            "duck.versionName": self.version,
            "duck.versionCode": self.version_code,
            "duck.permissions": ",".join(self.permissions),
            "duck.icon": path_text(self.icon),
            "duck.splashImage": path_text(self.splash_image),
            "duck.splashBackground": self.splash_background,
            "duck.orientation": self.orientation,
            "duck.fullscreen": str(self.fullscreen).lower(),
            "duck.keepScreenOn": str(self.keep_screen_on).lower(),
            "duck.minSdk": self.min_sdk,
            "duck.targetSdk": self.target_sdk,
            "duck.compileSdk": self.compile_sdk,
            "duck.platform": self.platform,
            "duck.abi": self.architecture,
            "duck.debug": str(self.debug).lower(),
            "duck.buildDir": path_text(self.build_dir),
            "duck.javaVersion": self.java_version,
            "duck.keystore": path_text(self.signing_keystore),
            "duck.keyAlias": self.signing_key_alias or "",
        }
        return {key: str(value) for key, value in values.items()}

    def to_gradle_properties(self) -> str:
        """
        Render the config as a gradle.properties file.

        Returns:
            str: Newline separated key=value lines, escaped for Java properties.
        """
        return "\n".join(
            f"{key}={escape_property(value)}"
            for key, value in self.gradle_values().items()
        )


def normalize_permission(name: str) -> str:
    """
    Expand a short permission name into a full Android permission name.

    Names containing a dot, including third-party ones, pass through as given.
    Short names are upper-cased and placed under android.permission, so new
    Android permissions work without any change here.

    Args:
        name: A short name like "camera" or a full permission name.

    Returns:
        str: The full permission name.
    """
    # Full names contain a dot and are used as given.
    if "." in name:
        return name

    # Short names map into the Android namespace.
    return Names.PERMISSION_PREFIX + name.upper()


def resolve_path(project_dir: Path, value: Optional[str]) -> Optional[Path]:
    """
    Resolve a configured path against the project folder.

    Args:
        project_dir: Project folder that relative paths belong to.
        value: Path text from config.py, or None.

    Returns:
        Optional[Path]: An absolute path, or None when no path was set.
    """
    if value is None:
        return None

    # Absolute paths and ~ are kept, anything else is project-relative.
    path = Path(value).expanduser()
    return path if path.is_absolute() else project_dir / path


def path_text(path: Optional[Path]) -> str:
    """
    Format a path for Gradle, using forward slashes on every platform.

    Args:
        path: The path, or None.

    Returns:
        str: The path text, or an empty string for None.
    """
    return "" if path is None else path.as_posix()


def escape_property(value: str) -> str:
    """
    Escape text for a Java properties file.

    Gradle reads properties as Latin-1, so everything beyond ASCII is written
    as \\uXXXX escapes. Emoji become surrogate pairs.

    Args:
        value: Raw text.

    Returns:
        str: Text that is safe to place after the = sign.
    """
    # Backslashes are the escape character in properties files.
    escaped = value.replace("\\", "\\\\")

    # Spell out every non-ASCII character as UTF-16 code units.
    parts: list[str] = []
    
    for char in escaped:
        if ord(char) < Encoding.ASCII_LIMIT:
            parts.append(char)
            continue
        
        data = char.encode("utf-16-be")
        size = Encoding.UTF16_UNIT_BYTES
        
        for index in range(0, len(data), size):
            unit = int.from_bytes(data[index:index + size], "big")
            parts.append(f"\\u{unit:04x}")
    
    return "".join(parts)


def has_type(value: Any, types: tuple[type, ...]) -> bool:
    """
    Check a value against accepted types, without letting bool pass as int.

    Args:
        value: The value to check.
        types: Accepted types.

    Returns:
        bool: True when the value is acceptable.
    """
    if isinstance(value, bool) and bool not in types:
        return False
    return isinstance(value, types)


def load_module(path: Path) -> ModuleType:
    """
    Run a config.py file and return it as a module.

    Args:
        path: The file to run.

    Returns:
        ModuleType: The executed module.

    Raises:
        ConfigError: If the file is missing or fails to run.
    """
    if not path.is_file():
        raise ConfigError(path, ["File not found. Run duck-native init to create it."])

    # Import by path so the project does not need to be on sys.path.
    spec = importlib.util.spec_from_file_location("duck_native_project_config", path)
    module = importlib.util.module_from_spec(spec)
    
    try:
        spec.loader.exec_module(module)
    except Exception as error:
        raise ConfigError(path, [f"Could not run the file: {error!r}"]) from error
    
    return module


def read_settings(module: ModuleType) -> tuple[dict[str, Any], list[str]]:
    """
    Read every known setting from a config module.

    Args:
        module: The executed config.py.

    Returns:
        tuple[dict[str, Any], list[str]]: Settings with defaults applied, and
            any problems found while reading them.
    """
    values: dict[str, Any] = {}
    errors: list[str] = []

    # Catch misspelled setting names.
    for name in vars(module):
        if Names.SETTING.match(name) and name not in SCHEMA:
            errors.append(f"Unknown setting {name}.")

    # Read each known setting, applying defaults and checking types.
    for name, setting in SCHEMA.items():
        value = getattr(module, name, setting.default)
        
        if value is REQUIRED:
            errors.append(f"{name} is required.")
        
        elif not has_type(value, setting.types):
            errors.append(f"{name} must be {setting.label}.")
        
        else:
            values[name] = value

    # Permissions must be plain strings before they are normalized.
    if not all(isinstance(item, str) for item in values.get("PERMISSIONS", ())):
        errors.append("PERMISSIONS must only contain strings.")
    
    # Return values and errors
    return values, errors


def load_config(project_dir: Path) -> ProjectConfig:
    """
    Load, normalize and validate a project's native/config.py.

    Args:
        project_dir: Folder that contains the project's native/ directory.

    Returns:
        ProjectConfig: The validated settings.

    Raises:
        ConfigError: If the file is missing, fails to run, or has any problem.
    """
    project_dir = project_dir.resolve()
    path = project_dir / Names.CONFIG_PATH

    # Run the user's file and read its settings.
    values, errors = read_settings(load_module(path))
    
    if errors:
        raise ConfigError(path, errors)

    # Resolve paths and permissions, then check the result.
    config = ProjectConfig.from_values(values, project_dir)
    errors = config.validate()
    
    if errors:
        raise ConfigError(path, errors)
    
    # Return final config
    return config


REQUIRED = object()

SCHEMA: dict[str, Setting] = {
    "APP_NAME": Setting(REQUIRED, (str,), "a string"),
    "PACKAGE_NAME": Setting(REQUIRED, (str,), "a string"),
    "VERSION": Setting("1.0.0", (str,), "a string"),
    "VERSION_CODE": Setting(1, (int,), "an integer"),
    "PERMISSIONS": Setting((), (list, tuple), "a list of strings"),
    "ICON": Setting(None, (str, type(None)), "a path or None"),
    "SPLASH_IMAGE": Setting(None, (str, type(None)), "a path or None"),
    "SPLASH_BACKGROUND": Setting("#FFFFFF", (str,), "a colour like #FFFFFF"),
    "ORIENTATION": Setting("unspecified", (str,), "a string"),
    "FULLSCREEN": Setting(False, (bool,), "True or False"),
    "KEEP_SCREEN_ON": Setting(False, (bool,), "True or False"),
    "MIN_SDK": Setting(Limits.MIN_SDK_FLOOR, (int,), "an integer"),
    "TARGET_SDK": Setting(Limits.PLAY_TARGET_SDK, (int,), "an integer"),
    "COMPILE_SDK": Setting(None, (int, type(None)), "an integer or None"),
    "PLATFORM": Setting("android", (str,), "a string"),
    "ARCHITECTURE": Setting("arm64-v8a", (str,), "a string"),
    "DEBUG": Setting(True, (bool,), "True or False"),
    "BUILD_DIR": Setting("build", (str,), "a path"),
    "JAVA_VERSION": Setting(17, (int,), "an integer"),
    "SIGNING_KEYSTORE": Setting(None, (str, type(None)), "a path or None"),
    "SIGNING_KEY_ALIAS": Setting(None, (str, type(None)), "a string or None"),
}
