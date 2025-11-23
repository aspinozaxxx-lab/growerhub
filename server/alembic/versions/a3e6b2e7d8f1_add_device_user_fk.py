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
    with op.batch_alter_table("devices") as batch_op:
        batch_op.add_column(sa.Column("user_id", sa.Integer(), nullable=True))
        batch_op.create_foreign_key(
            "fk_devices_user_id_users",
            "users",
            ["user_id"],
            ["id"],
            ondelete="SET NULL",
        )


def downgrade():
    with op.batch_alter_table("devices") as batch_op:
        batch_op.drop_constraint(
            "fk_devices_user_id_users", table_name="devices", type_="foreignkey"
        )
        batch_op.drop_column("user_id")
