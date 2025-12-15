"""Add user refresh tokens table."""

from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = "f9a8b7c6d5e4"
down_revision: Union[str, None] = "f3c1c4b6f1a2"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_table(
        "user_refresh_tokens",
        sa.Column("id", sa.Integer(), nullable=False),
        sa.Column("user_id", sa.Integer(), nullable=False),
        sa.Column("token_hash", sa.String(length=64), nullable=False),
        sa.Column("created_at", sa.DateTime(), nullable=False),
        sa.Column("expires_at", sa.DateTime(), nullable=False),
        sa.Column("revoked_at", sa.DateTime(), nullable=True),
        sa.Column("user_agent", sa.Text(), nullable=True),
        sa.Column("ip", sa.String(), nullable=True),
        sa.ForeignKeyConstraint(["user_id"], ["users.id"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("token_hash", name="uq_user_refresh_tokens_token_hash"),
    )
    op.create_index("ix_user_refresh_tokens_user_id", "user_refresh_tokens", ["user_id"])
    op.create_index("ix_user_refresh_tokens_expires_at", "user_refresh_tokens", ["expires_at"])


def downgrade() -> None:
    op.drop_index("ix_user_refresh_tokens_expires_at", table_name="user_refresh_tokens")
    op.drop_index("ix_user_refresh_tokens_user_id", table_name="user_refresh_tokens")
    op.drop_table("user_refresh_tokens")

