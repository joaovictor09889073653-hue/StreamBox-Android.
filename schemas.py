from pydantic import BaseModel, EmailStr, Field, ConfigDict
from datetime import datetime

class Credentials(BaseModel):
    email: EmailStr
    password: str = Field(min_length=8, max_length=128)

class UserOut(BaseModel):
    id: int
    email: EmailStr
    created_at: datetime
    model_config = ConfigDict(from_attributes=True)

class TokenOut(BaseModel):
    access_token: str
    token_type: str = "bearer"


class PlaylistOut(BaseModel):
    id: int
    name: str
    channel_count: int
    created_at: datetime


class ChannelOut(BaseModel):
    id: int
    name: str
    stream_url: str
    logo_url: str | None = None
    group_title: str | None = None
    tvg_id: str | None = None
