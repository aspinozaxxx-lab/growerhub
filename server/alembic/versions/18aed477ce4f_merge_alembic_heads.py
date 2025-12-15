"""Translitem: merge alembic heads

Revision ID: 18aed477ce4f
Revises: c2a5f6b7d8e9, f9a8b7c6d5e4
Create Date: 2025-12-15 20:37:57.675782

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = '18aed477ce4f'
down_revision: Union[str, None] = ('c2a5f6b7d8e9', 'f9a8b7c6d5e4')
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    # Translitem: eto merge-reviziya, izmenenij shemy net.
    pass


def downgrade() -> None:
    # Translitem: eto merge-reviziya, izmenenij shemy net.
    pass
