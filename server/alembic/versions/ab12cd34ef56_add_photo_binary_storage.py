"""Add binary storage fields for plant journal photos.

Revision ID: ab12cd34ef56
Revises: f3c1c4b6f1a2
Create Date: 2026-01-01 00:00:00.000000
"""

from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = "ab12cd34ef56"
down_revision: Union[str, None] = "f3c1c4b6f1a2"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column("plant_journal_photos", sa.Column("data", sa.LargeBinary(), nullable=True))
    op.add_column("plant_journal_photos", sa.Column("content_type", sa.String(), nullable=True))


def downgrade() -> None:
    op.drop_column("plant_journal_photos", "content_type")
    op.drop_column("plant_journal_photos", "data")
