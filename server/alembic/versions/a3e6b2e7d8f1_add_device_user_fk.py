"""add device user fk

Revision ID: a3e6b2e7d8f1
Revises: c5d1b1b5d0ea
Create Date: 2025-02-03 00:00:00.000000
"""

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision = "a3e6b2e7d8f1"
down_revision = "c5d1b1b5d0ea"
branch_labels = None
depends_on = None


def upgrade():
    op.add_column(
        "devices",
        sa.Column("user_id", sa.Integer(), nullable=True),
    )
    op.create_foreign_key(
        "fk_devices_user_id_users",
        "devices",
        "users",
        ["user_id"],
        ["id"],
        ondelete="SET NULL",
    )


def downgrade():
    op.drop_constraint("fk_devices_user_id_users", "devices", type_="foreignkey")
    op.drop_column("devices", "user_id")
