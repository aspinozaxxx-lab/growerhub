from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from app.models.database_models import Base

SQLALCHEMY_DATABASE_URL = "postgresql://watering_user:watering_pass@localhost/watering_db"

engine = create_engine(SQLALCHEMY_DATABASE_URL)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

def create_tables():
    Base.metadata.create_all(bind=engine)

def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
