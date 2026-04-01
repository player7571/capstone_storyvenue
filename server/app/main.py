from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.routes import auth, chat, health, messages, sessions, voice, feed

app = FastAPI(title="StoryVenue API", version="0.1.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(auth.router)
app.include_router(health.router)
app.include_router(messages.router)
app.include_router(sessions.router)
app.include_router(voice.router)
app.include_router(feed.router)
app.include_router(chat.router)