"""
The build command.
"""

import shlex

from pathlib import Path
from typing import Any, Optional

import click

from duck.logging import console

from duck_native import builder
from duck_native.cli.common import load_project, project_argument


@click.command(help="Validate a project, generate its resources and run Gradle.")
@project_argument
@click.option("--apk", is_flag=True, help="Build a release APK instead of a bundle.")
@click.option(
    "--server-url",
    default=None,
    help="Server the app opens, overriding SERVER_URL in native/config.py.",
)
@click.option(
    "--test-release",
    is_flag=True,
    help="Build an installable release APK signed with the debug key.",
)
@click.option(
    "-g",
    "--gradle-args",
    "gradle_args",
    multiple=True,
    help='Flags forwarded to Gradle, e.g. -g "--debug --stacktrace". Repeatable.',
)
def build(
    project: Path,
    apk: bool,
    server_url: Optional[str],
    test_release: bool,
    gradle_args: tuple[str, ...],
) -> None:
    """
    Build a project.

    Args:
        project: Folder that contains the project's native/ directory.
        apk: Build a release APK instead of a bundle.
        server_url: Server the app opens, overriding the config.
        test_release: Build a release APK signed with the debug key.
        gradle_args: Flags forwarded to Gradle, each value may hold several flags.

    Raises:
        click.ClickException: If the build cannot start.
        click.exceptions.Exit: With Gradle's exit code when the build fails.
    """
    # Flags replace the config file's values. A test release is never a debug build.
    overrides: dict[str, Any] = {}
    
    if server_url:
        overrides["SERVER_URL"] = server_url
    
    if test_release:
        overrides.update(DEBUG=False, TEST_RELEASE=True)
    
    config = load_project(project, overrides=overrides)
    
    if config.warnings():
        console.log_raw("")
        
    # Log starter message
    console.log(f"Building {config.app_name} ({config.package_name})\n")
    
    if apk and config.debug:
        console.log("Building release APK ignored, config's DEBUG=True\n", level=console.WARNING)
        
    # Generate resources and run Gradle.
    try:
        extra = [flag for value in gradle_args for flag in shlex.split(value)]
        code = builder.build(config, apk=apk, gradle_args=extra)
    
    except builder.BuildError as error:
        raise click.ClickException(str(error)) from error
    
    if code != 0:
        raise click.exceptions.Exit(code)
