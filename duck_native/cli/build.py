"""
The build command.
"""

from pathlib import Path

import click

from duck.logging import console

from duck_native import builder
from duck_native.cli.common import load_project, project_argument


@click.command(help="Validate a project, generate its resources and run Gradle.")
@project_argument
@click.option("--apk", is_flag=True, help="Build a release APK instead of a bundle.")
def build(project: Path, apk: bool) -> None:
    """
    Build a project.

    Args:
        project: Folder that contains the project's native/ directory.
        apk: Build a release APK instead of a bundle.

    Raises:
        click.ClickException: If the build cannot start.
        click.exceptions.Exit: With Gradle's exit code when the build fails.
    """
    config = load_project(project)
    console.log(f"Building {config.app_name} ({config.package_name})\n")
    
    if apk and config.debug:
        console.log("Building release APK ignored, config's DEBUG=True\n", level=console.WARNING)
        
    # Generate resources and run Gradle.
    try:
        code = builder.build(config, apk=apk)
    
    except builder.BuildError as error:
        raise click.ClickException(str(error)) from error
    
    if code != 0:
        raise click.exceptions.Exit(code)
