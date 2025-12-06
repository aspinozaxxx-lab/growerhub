"""Add watering_speed_lph to devices."""

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision = "e1b9d3f7c2b4"
down_revision = "d2e4a1c8c1f0"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column(
        "devices",
        sa.Column("watering_speed_lph", sa.Float(), nullable=True),
    )


def downgrade() -> None:
    op.drop_column("devices", "watering_speed_lph")

