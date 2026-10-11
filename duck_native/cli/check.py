"""
The check command.
"""

from pathlib import Path

import click

from duck_native.cli.common import load_project, project_argument


@click.command(help="Validate a project's config and print its Gradle properties.")
@project_argument
def check(project: Path) -> None:
    """
    Validate a project and print its Gradle properties.

    Args:
        project: Folder that contains the project's native/ directory.
    """
    click.echo(load_project(project).to_gradle_properties())
