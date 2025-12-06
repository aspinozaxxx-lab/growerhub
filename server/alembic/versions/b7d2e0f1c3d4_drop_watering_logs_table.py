"""Drop watering_logs table."""

from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = "b7d2e0f1c3d4"
down_revision: Union[str, None] = "f3c1c4b6f1a2"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.drop_index("ix_watering_logs_id", table_name="watering_logs")
    op.drop_index("ix_watering_logs_device_id", table_name="watering_logs")
    op.drop_table("watering_logs")


def downgrade() -> None:
    op.create_table(
        "watering_logs",
        sa.Column("id", sa.Integer(), nullable=False),
        sa.Column("device_id", sa.String(), nullable=True),
        sa.Column("start_time", sa.DateTime(), nullable=True),
        sa.Column("duration", sa.Integer(), nullable=True),
        sa.Column("water_used", sa.Float(), nullable=True),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index(op.f("ix_watering_logs_device_id"), "watering_logs", ["device_id"], unique=False)
    op.create_index(op.f("ix_watering_logs_id"), "watering_logs", ["id"], unique=False)
