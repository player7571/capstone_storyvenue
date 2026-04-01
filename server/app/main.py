from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.routes import auth, chapters, feed, health, memory, messages, sessions, voice

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
app.include_router(chapters.router)
app.include_router(messages.router)
app.include_router(sessions.router)
app.include_router(voice.router)
app.include_router(feed.router)
app.include_router(memory.router)
