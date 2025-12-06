from __future__ import annotations

from datetime import datetime, timedelta
from typing import Optional

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from app.core.database import get_db
from app.core.security import get_current_admin, get_current_user
from app.fastapi.routers.devices import _device_to_out
from app.models.database_models import (
    DeviceDB,
    PlantDB,
    PlantDeviceDB,
    PlantGroupDB,
    PlantJournalEntryDB,
    PlantJournalPhotoDB,
    UserDB,
)
from app.models.device_schemas import AdminDeviceOut, DeviceOut
from app.models.plant_schemas import (
    AdminPlantOut,
    PlantCreate,
    PlantGroupCreate,
    PlantGroupOut,
    PlantJournalEntryCreate,
    PlantJournalEntryOut,
    PlantOut,
    PlantUpdate,
)
from pydantic import BaseModel
from app.repositories.state_repo import DeviceStateLastRepository

router = APIRouter()


class PlantJournalEntryUpdate(BaseModel):
    """Translitem: obnovlenie zapisi zhurnala rastenija (type/text)."""

    type: Optional[str] = None
    text: Optional[str] = None


@router.get("/api/plant-groups", response_model=list[PlantGroupOut])
async def list_plant_groups(
    db: Session = Depends(get_db), current_user: UserDB = Depends(get_current_user)
):
    groups = db.query(PlantGroupDB).filter(PlantGroupDB.user_id == current_user.id).all()
    return [
        PlantGroupOut(id=group.id, name=group.name, user_id=group.user_id)
        for group in groups
    ]


@router.post("/api/plant-groups", response_model=PlantGroupOut)
async def create_plant_group(
    payload: PlantGroupCreate,
    db: Session = Depends(get_db),
    current_user: UserDB = Depends(get_current_user),
):
    group = PlantGroupDB(name=payload.name, user_id=current_user.id)
    db.add(group)
    db.commit()
    db.refresh(group)
    return PlantGroupOut(id=group.id, name=group.name, user_id=group.user_id)


@router.delete("/api/plant-groups/{group_id}")
async def delete_plant_group(
    group_id: int,
    db: Session = Depends(get_db),
    current_user: UserDB = Depends(get_current_user),
):
    group = (
        db.query(PlantGroupDB)
        .filter(PlantGroupDB.id == group_id, PlantGroupDB.user_id == current_user.id)
        .first()
    )
    if not group:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="gruppa ne najdena")

    db.query(PlantDB).filter(
        PlantDB.user_id == current_user.id, PlantDB.plant_group_id == group_id
    ).update({PlantDB.plant_group_id: None})
    db.delete(group)
    db.commit()
    return {"message": "group deleted"}


@router.get("/api/plants", response_model=list[PlantOut])
async def list_plants(
    db: Session = Depends(get_db), current_user: UserDB = Depends(get_current_user)
):
    plants = db.query(PlantDB).filter(PlantDB.user_id == current_user.id).all()
    state_repo = DeviceStateLastRepository()
    current_time = datetime.utcnow()
    online_window = timedelta(minutes=3)
    return [
        _build_plant_out(plant, db, state_repo, current_time, online_window, current_user)
        for plant in plants
    ]


@router.post("/api/plants", response_model=PlantOut)
async def create_plant(
    payload: PlantCreate,
    db: Session = Depends(get_db),
    current_user: UserDB = Depends(get_current_user),
):
    planted_at = payload.planted_at or datetime.utcnow()
    plant = PlantDB(
        name=payload.name,
        planted_at=planted_at,
        user_id=current_user.id,
        plant_group_id=payload.plant_group_id,
    )
    db.add(plant)
    db.commit()
    db.refresh(plant)

    state_repo = DeviceStateLastRepository()
    current_time = datetime.utcnow()
    online_window = timedelta(minutes=3)
    return _build_plant_out(plant, db, state_repo, current_time, online_window, current_user)


@router.get("/api/plants/{plant_id}", response_model=PlantOut)
async def get_plant(
    plant_id: int,
    db: Session = Depends(get_db),
    current_user: UserDB = Depends(get_current_user),
):
    plant = _get_user_plant(db, plant_id, current_user)
    state_repo = DeviceStateLastRepository()
    current_time = datetime.utcnow()
    online_window = timedelta(minutes=3)
    return _build_plant_out(plant, db, state_repo, current_time, online_window, current_user)


@router.patch("/api/plants/{plant_id}", response_model=PlantOut)
async def update_plant(
    plant_id: int,
    payload: PlantUpdate,
    db: Session = Depends(get_db),
    current_user: UserDB = Depends(get_current_user),
):
    plant = _get_user_plant(db, plant_id, current_user)
    update_data = payload.model_dump(exclude_unset=True)
    if "name" in update_data:
        plant.name = update_data["name"]
    if "planted_at" in update_data:
        plant.planted_at = update_data["planted_at"]
    if "plant_group_id" in update_data:
        plant.plant_group_id = update_data["plant_group_id"]

    db.commit()
    db.refresh(plant)

    state_repo = DeviceStateLastRepository()
    current_time = datetime.utcnow()
    online_window = timedelta(minutes=3)
    return _build_plant_out(plant, db, state_repo, current_time, online_window, current_user)


@router.delete("/api/plants/{plant_id}")
async def delete_plant(
    plant_id: int,
    db: Session = Depends(get_db),
    current_user: UserDB = Depends(get_current_user),
):
    plant = _get_user_plant(db, plant_id, current_user)

    db.query(PlantDeviceDB).filter(PlantDeviceDB.plant_id == plant.id).delete()
    db.query(PlantJournalEntryDB).filter(PlantJournalEntryDB.plant_id == plant.id).delete()
    db.delete(plant)
    db.commit()
    return {"message": "plant deleted"}


@router.post("/api/plants/{plant_id}/devices/{device_id}", response_model=PlantOut)
async def attach_device_to_plant(
    plant_id: int,
    device_id: int,
    db: Session = Depends(get_db),
    current_user: UserDB = Depends(get_current_user),
):
    plant = _get_user_plant(db, plant_id, current_user)
    device = (
        db.query(DeviceDB)
        .filter(DeviceDB.id == device_id, DeviceDB.user_id == current_user.id)
        .first()
    )
    if not device:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="ustrojstvo ne najdeno")

    existing = (
        db.query(PlantDeviceDB)
        .filter(PlantDeviceDB.plant_id == plant.id, PlantDeviceDB.device_id == device.id)
        .first()
    )
    if not existing:
        link = PlantDeviceDB(plant_id=plant.id, device_id=device.id)
        db.add(link)
        db.commit()

    state_repo = DeviceStateLastRepository()
    current_time = datetime.utcnow()
    online_window = timedelta(minutes=3)
    return _build_plant_out(plant, db, state_repo, current_time, online_window, current_user)


@router.delete("/api/plants/{plant_id}/devices/{device_id}", status_code=status.HTTP_204_NO_CONTENT)
async def detach_device_from_plant(
    plant_id: int,
    device_id: int,
    db: Session = Depends(get_db),
    current_user: UserDB = Depends(get_current_user),
):
    _get_user_plant(db, plant_id, current_user)
    device = (
        db.query(DeviceDB)
        .filter(DeviceDB.id == device_id, DeviceDB.user_id == current_user.id)
        .first()
    )
    if not device:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="ustrojstvo ne najdeno")

    db.query(PlantDeviceDB).filter(
        PlantDeviceDB.plant_id == plant_id, PlantDeviceDB.device_id == device_id
    ).delete()
    db.commit()
    return None


@router.get("/api/plants/{plant_id}/journal", response_model=list[PlantJournalEntryOut])
async def list_plant_journal(
    plant_id: int,
    db: Session = Depends(get_db),
    current_user: UserDB = Depends(get_current_user),
):
    _get_user_plant(db, plant_id, current_user)
    entries = (
        db.query(PlantJournalEntryDB)
        .filter(PlantJournalEntryDB.plant_id == plant_id)
        .order_by(PlantJournalEntryDB.event_at.desc())
        .all()
    )
    return [_build_journal_out(entry, db) for entry in entries]


@router.post("/api/plants/{plant_id}/journal", response_model=PlantJournalEntryOut)
async def create_journal_entry(
    plant_id: int,
    payload: PlantJournalEntryCreate,
    db: Session = Depends(get_db),
    current_user: UserDB = Depends(get_current_user),
):
    _get_user_plant(db, plant_id, current_user)
    event_at = payload.event_at or datetime.utcnow()
    entry = PlantJournalEntryDB(
        plant_id=plant_id,
        user_id=current_user.id,
        type=payload.type,
        text=payload.text,
        event_at=event_at,
    )
    db.add(entry)
    db.commit()
    db.refresh(entry)

    photo_urls = payload.photo_urls or []
    for url in photo_urls:
        photo = PlantJournalPhotoDB(journal_entry_id=entry.id, url=url)
        db.add(photo)
    if photo_urls:
        db.commit()

    return _build_journal_out(entry, db)


@router.patch("/api/plants/{plant_id}/journal/{entry_id}", response_model=PlantJournalEntryOut)
async def update_journal_entry(
    plant_id: int,
    entry_id: int,
    payload: PlantJournalEntryUpdate,
    db: Session = Depends(get_db),
    current_user: UserDB = Depends(get_current_user),
):
    _get_user_plant(db, plant_id, current_user)
    entry = (
        db.query(PlantJournalEntryDB)
        .filter(
            PlantJournalEntryDB.id == entry_id,
            PlantJournalEntryDB.plant_id == plant_id,
            PlantJournalEntryDB.user_id == current_user.id,
        )
        .first()
    )
    if not entry:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="zapis' ne najdena")

    update_data = payload.model_dump(exclude_unset=True)
    if "type" in update_data:
        entry.type = update_data["type"]
    if "text" in update_data:
        entry.text = update_data["text"]
    entry.updated_at = datetime.utcnow()
    db.commit()
    db.refresh(entry)
    return _build_journal_out(entry, db)


@router.delete("/api/plants/{plant_id}/journal/{entry_id}")
async def delete_journal_entry(
    plant_id: int,
    entry_id: int,
    db: Session = Depends(get_db),
    current_user: UserDB = Depends(get_current_user),
):
    _get_user_plant(db, plant_id, current_user)
    entry = (
        db.query(PlantJournalEntryDB)
        .filter(
            PlantJournalEntryDB.id == entry_id,
            PlantJournalEntryDB.plant_id == plant_id,
            PlantJournalEntryDB.user_id == current_user.id,
        )
        .first()
    )
    if not entry:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="zapis' ne najdena")

    db.delete(entry)
    db.commit()
    return {"message": "entry deleted"}


@router.get("/api/admin/plants", response_model=list[AdminPlantOut])
async def admin_list_plants(
    db: Session = Depends(get_db), admin: UserDB = Depends(get_current_admin)
):
    rows = (
        db.query(PlantDB, UserDB, PlantGroupDB)
        .outerjoin(UserDB, PlantDB.user_id == UserDB.id)
        .outerjoin(PlantGroupDB, PlantDB.plant_group_id == PlantGroupDB.id)
        .all()
    )
    result: list[AdminPlantOut] = []
    for plant, owner, group in rows:
        result.append(
            AdminPlantOut(
                id=plant.id,
                name=plant.name,
                owner_email=owner.email if owner else None,
                owner_username=owner.username if owner else None,
                owner_id=owner.id if owner else None,
                group_name=group.name if group else None,
            )
        )
    return result


def _get_user_plant(db: Session, plant_id: int, current_user: UserDB) -> PlantDB:
    plant = (
        db.query(PlantDB)
        .filter(PlantDB.id == plant_id, PlantDB.user_id == current_user.id)
        .first()
    )
    if not plant:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="rastenie ne najdeno")
    return plant


def _build_journal_out(entry: PlantJournalEntryDB, db: Session) -> PlantJournalEntryOut:
    photos = (
        db.query(PlantJournalPhotoDB)
        .filter(PlantJournalPhotoDB.journal_entry_id == entry.id)
        .all()
    )
    return PlantJournalEntryOut(
        id=entry.id,
        plant_id=entry.plant_id,
        user_id=entry.user_id,
        type=entry.type,
        text=entry.text,
        event_at=entry.event_at,
        created_at=entry.created_at,
        photos=[
            {
                "id": photo.id,
                "url": photo.url,
                "caption": photo.caption,
            }
            for photo in photos
        ],
    )


def _build_plant_out(
    plant: PlantDB,
    db: Session,
    state_repo: DeviceStateLastRepository,
    current_time: datetime,
    online_window: timedelta,
    current_user: UserDB,
) -> PlantOut:
    group = None
    if plant.plant_group_id:
        group = db.query(PlantGroupDB).filter(PlantGroupDB.id == plant.plant_group_id).first()

    device_links = (
        db.query(DeviceDB)
        .join(PlantDeviceDB, PlantDeviceDB.device_id == DeviceDB.id)
        .filter(PlantDeviceDB.plant_id == plant.id, DeviceDB.user_id == current_user.id)
        .all()
    )
    devices_out: list[DeviceOut] = [
        _device_to_out(device, state_repo, current_time, online_window, db)
        for device in device_links
    ]

    return PlantOut(
        id=plant.id,
        name=plant.name,
        planted_at=plant.planted_at,
        user_id=plant.user_id,
        plant_group=PlantGroupOut(id=group.id, name=group.name, user_id=group.user_id)
        if group
        else None,
        devices=devices_out,
    )
