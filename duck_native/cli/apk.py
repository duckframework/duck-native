"""
The apk command.
"""

import shutil
import subprocess

from pathlib import Path
from typing import Optional

import click

from duck.logging import console

from duck_native.cli.common import load_project, project_argument
from duck_native.config import Names, ProjectConfig


PREBUILT_APK = Path(__file__).resolve().parent.parent / "etc" / "app-debug.apk"

# What the prebuilt APK was built with.
PREBUILT_APP_NAME = "Duck Native Bridge"
PREBUILT_PACKAGE = "com.duckframework.bridge"
PREBUILT_PERMISSIONS = (*Names.BASE_PERMISSIONS, Names.PERMISSION_PREFIX + "VIBRATE")


def ignored_settings(config: ProjectConfig) -> list[str]:
    """
    List settings that differ from the prebuilt APK and so would be ignored.

    Args:
        config: The validated project config.

    Returns:
        list[str]: Setting names, empty when the prebuilt APK matches the config.
    """
    checks = {
        "APP_NAME": config.app_name != PREBUILT_APP_NAME,
        "PACKAGE_NAME": config.package_name != PREBUILT_PACKAGE,
        "PERMISSIONS": not set(config.permissions) <= set(PREBUILT_PERMISSIONS),
        "ICON": config.icon is not None,
        "SPLASH_IMAGE": config.splash_image is not None,
        "ORIENTATION": config.orientation != "unspecified",
        "FULLSCREEN": config.fullscreen,
        "KEEP_SCREEN_ON": config.keep_screen_on,
    }
    return [name for name, changed in checks.items() if changed]


def install(apk: Path) -> None:
    """
    Install an APK on the connected device with adb.

    Args:
        apk: The APK to install.

    Raises:
        click.ClickException: If adb is missing or the install fails.
    """
    adb = shutil.which("adb")
    if adb is None:
        raise click.ClickException("adb was not found. Install Android platform-tools.")

    console.log(f"Installing {apk}", level=console.DEBUG)
    if subprocess.run([adb, "install", "-r", str(apk)]).returncode != 0:
        raise click.ClickException("adb could not install the APK.")


@click.command(help="Get the prebuilt debug APK, without needing Gradle or the Android SDK.")
@project_argument
@click.option("--output", type=click.Path(dir_okay=False, path_type=Path), help="Where to write the APK.")
@click.option("--install", "install_apk", is_flag=True, help="Install it on the connected device with adb.")
def apk(project: Path, output: Optional[Path], install_apk: bool) -> None:
    """
    Copy the prebuilt debug APK and optionally install it.

    Args:
        project: Folder that contains the project's native/ directory.
        output: Destination file, the project's build folder by default.
        install_apk: Install the APK on the connected device.

    Raises:
        click.ClickException: If the prebuilt APK is missing or cannot be installed.
    """
    config = load_project(project)

    if not PREBUILT_APK.is_file():
        raise click.ClickException(f"Prebuilt APK not found: {PREBUILT_APK}")

    # The prebuilt APK cannot follow settings that are baked in at build time.
    ignored = ignored_settings(config)
    if ignored:
        console.log(
            f"The prebuilt APK ignores {', '.join(ignored)}. "
            "Run duck-native build to apply them.",
            level=console.WARNING,
        )

    # Copy it next to the other build output.
    target = output or config.build_dir / "app-debug.apk"
    target.parent.mkdir(parents=True, exist_ok=True)
    console.log(f"Copying prebuilt APK to {target}", level=console.DEBUG)
    shutil.copyfile(PREBUILT_APK, target)

    if install_apk:
        install(target)

    console.log(f"Debug APK: {target}", level=console.SUCCESS)
