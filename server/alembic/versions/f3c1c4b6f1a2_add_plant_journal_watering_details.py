"""Add plant journal watering details table."""

from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = "f3c1c4b6f1a2"
down_revision: Union[str, None] = "e1b9d3f7c2b4"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_table(
        "plant_journal_watering_details",
        sa.Column("id", sa.Integer(), nullable=False),
        sa.Column("journal_entry_id", sa.Integer(), nullable=False),
        sa.Column("water_volume_l", sa.Float(), nullable=False),
        sa.Column("duration_s", sa.Integer(), nullable=False),
        sa.Column("ph", sa.Float(), nullable=True),
        sa.Column("fertilizers_per_liter", sa.Text(), nullable=True),
        sa.ForeignKeyConstraint(
            ["journal_entry_id"],
            ["plant_journal_entries.id"],
            ondelete="CASCADE",
        ),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("journal_entry_id"),
    )


def downgrade() -> None:
    op.drop_table("plant_journal_watering_details")
