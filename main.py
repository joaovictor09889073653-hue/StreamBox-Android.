import re
from urllib.parse import urlparse
from fastapi import FastAPI, Depends, HTTPException, status, Body, Query
from sqlalchemy.orm import Session
from sqlalchemy.exc import IntegrityError
from .database import Base, engine, get_db
from . import models
from .schemas import Credentials, TokenOut, UserOut, PlaylistOut, ChannelOut
from .security import hash_password, verify_password, create_access_token
from .dependencies import get_current_user

app = FastAPI(title="StreamBox API", version="0.4.0")

@app.on_event("startup")
def create_tables():
    Base.metadata.create_all(bind=engine)

@app.get("/health")
def health():
    return {"status": "ok"}

@app.post("/auth/register", response_model=TokenOut, status_code=status.HTTP_201_CREATED)
def register(data: Credentials, db: Session = Depends(get_db)):
    email = str(data.email).lower()
    if db.query(models.User).filter(models.User.email == email).first():
        raise HTTPException(status_code=409, detail="Este e-mail já está cadastrado")
    user = models.User(email=email, password_hash=hash_password(data.password))
    db.add(user)
    try:
        db.commit()
        db.refresh(user)
    except IntegrityError:
        db.rollback()
        raise HTTPException(status_code=409, detail="Este e-mail já está cadastrado")
    return TokenOut(access_token=create_access_token(user.email))

@app.post("/auth/login", response_model=TokenOut)
def login(data: Credentials, db: Session = Depends(get_db)):
    email = str(data.email).lower()
    user = db.query(models.User).filter(models.User.email == email).first()
    if not user or not verify_password(data.password, user.password_hash):
        raise HTTPException(status_code=401, detail="E-mail ou senha incorretos")
    return TokenOut(access_token=create_access_token(user.email))

@app.get("/me", response_model=UserOut)
def me(current_user: models.User = Depends(get_current_user)):
    return current_user

def parse_m3u(content: str):
    if len(content.encode("utf-8")) > 5_000_000:
        raise HTTPException(status_code=413, detail="Lista maior que 5 MB")
    lines = [line.strip() for line in content.lstrip("\\ufeff").splitlines() if line.strip()]
    if not lines or not lines[0].upper().startswith("#EXTM3U"):
        raise HTTPException(status_code=422, detail="Cabeçalho #EXTM3U não encontrado")
    channels, metadata = [], None
    for line in lines[1:]:
        if line.startswith("#EXTINF:"):
            attrs = dict(re.findall(r'([\\w-]+)="([^"]*)"', line))
            title = line.split(",", 1)[1].strip() if "," in line else attrs.get("tvg-name", "Canal")
            metadata = {
                "name": (attrs.get("tvg-name") or title or "Canal")[:255],
                "logo_url": attrs.get("tvg-logo"),
                "group_title": attrs.get("group-title"),
                "tvg_id": attrs.get("tvg-id"),
            }
        elif not line.startswith("#") and metadata:
            parsed = urlparse(line)
            if parsed.scheme in ("http", "https") and parsed.netloc:
                channels.append({**metadata, "stream_url": line[:2048]})
            metadata = None
            if len(channels) > 10000:
                raise HTTPException(status_code=413, detail="Limite de 10.000 canais excedido")
    if not channels:
        raise HTTPException(status_code=422, detail="Nenhum canal válido encontrado")
    return channels

@app.post("/playlists/import", response_model=PlaylistOut, status_code=201)
def import_playlist(
    content: str = Body(..., media_type="text/plain"),
    name: str = Query(default="Minha lista", min_length=1, max_length=120),
    current_user: models.User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    channels = parse_m3u(content)
    playlist = models.Playlist(user_id=current_user.id, name=name.strip())
    db.add(playlist)
    db.flush()
    for item in channels:
        db.add(models.Channel(playlist_id=playlist.id, **item))
    db.commit()
    db.refresh(playlist)
    return PlaylistOut(id=playlist.id, name=playlist.name, channel_count=len(channels), created_at=playlist.created_at)

@app.get("/playlists", response_model=list[PlaylistOut])
def list_playlists(current_user: models.User = Depends(get_current_user), db: Session = Depends(get_db)):
    rows = db.query(models.Playlist).filter_by(user_id=current_user.id).order_by(models.Playlist.id.desc()).all()
    return [PlaylistOut(id=p.id, name=p.name, channel_count=db.query(models.Channel).filter_by(playlist_id=p.id).count(), created_at=p.created_at) for p in rows]

@app.get("/playlists/{playlist_id}/channels", response_model=list[ChannelOut])
def list_channels(playlist_id: int, current_user: models.User = Depends(get_current_user), db: Session = Depends(get_db)):
    playlist = db.query(models.Playlist).filter_by(id=playlist_id, user_id=current_user.id).first()
    if not playlist:
        raise HTTPException(status_code=404, detail="Lista não encontrada")
    return db.query(models.Channel).filter_by(playlist_id=playlist_id).order_by(models.Channel.group_title, models.Channel.name).all()

@app.delete("/playlists/{playlist_id}", status_code=204)
def delete_playlist(playlist_id: int, current_user: models.User = Depends(get_current_user), db: Session = Depends(get_db)):
    playlist = db.query(models.Playlist).filter_by(id=playlist_id, user_id=current_user.id).first()
    if not playlist:
        raise HTTPException(status_code=404, detail="Lista não encontrada")
    db.query(models.Channel).filter_by(playlist_id=playlist_id).delete()
    db.delete(playlist)
    db.commit()
