"""Add plant type, strain, and growth stage fields to plants.

Revision ID: c2a5f6b7d8e9
Revises: ab12cd34ef56
Create Date: 2026-01-10 00:00:00.000000
"""

from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = "c2a5f6b7d8e9"
down_revision: Union[str, None] = "ab12cd34ef56"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    # Translitem: dobavlyaem tip rastenija (kategoriya/vid, svobodnaya stroka).
    op.add_column("plants", sa.Column("plant_type", sa.String(length=255), nullable=True))
    # Translitem: dobavlyaem sort/strain rastenija.
    op.add_column("plants", sa.Column("strain", sa.String(length=255), nullable=True))
    # Translitem: sohranyaem tekushchuyu stadiyu rosta.
    op.add_column("plants", sa.Column("growth_stage", sa.String(length=255), nullable=True))


def downgrade() -> None:
    # Translitem: udalyaem tekushchuyu stadiyu rosta.
    op.drop_column("plants", "growth_stage")
    # Translitem: udalyaem sort/strain rastenija.
    op.drop_column("plants", "strain")
    # Translitem: udalyaem tip rastenija.
    op.drop_column("plants", "plant_type")
