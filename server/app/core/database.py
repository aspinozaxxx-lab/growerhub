import os
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from app.models.database_models import Base

# Берём строку подключения из переменной окружения DATABASE_URL.
# Если её нет (локальная разработка) — используем SQLite файл.
DATABASE_URL = os.getenv(
    "DATABASE_URL",
    "sqlite:///./local.db"
)

if DATABASE_URL.startswith("sqlite"):
    engine = create_engine(
        DATABASE_URL,
        connect_args={"check_same_thread": False}
    )
else:
    engine = create_engine(DATABASE_URL)

SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

def create_tables():
    Base.metadata.create_all(bind=engine)

def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()

