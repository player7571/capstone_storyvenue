import logging
from pathlib import Path

from fastapi import FastAPI

logging.basicConfig(level=logging.INFO)
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles

from app.api.routes import auth, book, chat, chapters, comments, feed, health, memory, messages, notifications, safety, sessions, users, voice

app = FastAPI(title="StoryVenue API", version="0.1.0")
AUDIO_CACHE_DIR = Path(__file__).resolve().parents[1] / ".generated-audio"
AUDIO_CACHE_DIR.mkdir(parents=True, exist_ok=True)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)
app.mount(
    "/generated-audio",
    StaticFiles(directory=AUDIO_CACHE_DIR),
    name="generated-audio",
)

app.include_router(auth.router)
app.include_router(health.router)
app.include_router(chapters.router)
app.include_router(messages.router)
app.include_router(sessions.router)
app.include_router(voice.router)
app.include_router(feed.router)
app.include_router(comments.router)
app.include_router(notifications.router)
app.include_router(memory.router)
app.include_router(chat.router)
app.include_router(safety.router)
app.include_router(book.router)
app.include_router(users.router)
