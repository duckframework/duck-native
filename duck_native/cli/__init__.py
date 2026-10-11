"""
Command line interface for Duck Native.

Usage: python -m duck_native [init|build|check|apk] [PROJECT]
"""

import click

from duck_native.cli.apk import apk
from duck_native.cli.init import init
from duck_native.cli.build import build
from duck_native.cli.check import check


@click.group(help="Build and validate Duck Native projects.")
def cli() -> None:
    """
    Root command group. Subcommands are registered below.
    """


cli.add_command(init)
cli.add_command(build)
cli.add_command(check)
cli.add_command(apk)
