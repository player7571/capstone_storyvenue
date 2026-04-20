from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Query, Response, status

from app.api.dependencies.auth import get_current_user_id
from app.api.schemas.feed import (
    FeedCreateRequest,
    FeedDetailResponse,
    FeedPostResponse,
    LikeToggleResponse,
)
from app.db.supabase import get_supabase
from app.services.safety import check_content_safety

router = APIRouter(prefix="/feed", tags=["feed"])


def _fetch_profiles_map(sb, user_ids: list[str]) -> dict[str, dict]:
    unique_ids = list({uid for uid in user_ids if uid})
    if not unique_ids:
        return {}
    result = (
        sb.table("profiles")
        .select("id, name, avatar_url")
        .in_("id", unique_ids)
        .execute()
    )
    return {str(row["id"]): row for row in (result.data or [])}


def _fetch_comment_counts(sb, post_ids: list[str]) -> dict[str, int]:
    unique_ids = list({pid for pid in post_ids if pid})
    if not unique_ids:
        return {}
    result = (
        sb.table("feed_comments")
        .select("post_id")
        .in_("post_id", unique_ids)
        .execute()
    )
    counts: dict[str, int] = {}
    for row in (result.data or []):
        pid = str(row.get("post_id"))
        counts[pid] = counts.get(pid, 0) + 1
    return counts


def _attach_author(row: dict, profiles_map: dict[str, dict]) -> dict:
    profile = profiles_map.get(str(row.get("user_id"))) or {}
    return {
        **row,
        "author_name": profile.get("name"),
        "author_avatar_url": profile.get("avatar_url"),
    }


def _build_post_response(
    row: dict,
    profiles_map: dict[str, dict],
    comment_counts: dict[str, int],
) -> FeedPostResponse:
    merged = _attach_author(row, profiles_map)
    merged["comment_count"] = comment_counts.get(str(row.get("id")), 0)
    return FeedPostResponse(**merged)


# ── GET /feed ─────────────────────────────────────
@router.get("", response_model=list[FeedPostResponse])
async def list_feed(
    limit: int = Query(20, ge=1, le=100),
    offset: int = Query(0, ge=0),
    user_id: str = Depends(get_current_user_id),
):
    sb = get_supabase()
    result = (
        sb.table("feed_posts")
        .select("*")
        .order("created_at", desc=True)
        .range(offset, offset + limit - 1)
        .execute()
    )
    rows = result.data or []
    profiles_map = _fetch_profiles_map(sb, [row.get("user_id") for row in rows])
    comment_counts = _fetch_comment_counts(sb, [row.get("id") for row in rows])
    return [_build_post_response(row, profiles_map, comment_counts) for row in rows]


# ── POST /feed ────────────────────────────────────
@router.post("", response_model=FeedPostResponse, status_code=status.HTTP_201_CREATED)
async def create_feed_post(
    body: FeedCreateRequest,
    user_id: str = Depends(get_current_user_id),
):
    # 게시 전 안전 검사
    safety = check_content_safety(f"{body.title}\n{body.preview}")
    if not safety["safe"]:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"부적절한 콘텐츠가 감지되었습니다: {safety['reason']}",
        )

    sb = get_supabase()
    row = (
        sb.table("feed_posts")
        .insert(
            {
                "user_id": user_id,
                "book_id": str(body.book_id),
                "title": body.title,
                "preview": body.preview,
            }
        )
        .execute()
    )
    data = row.data[0]
    return FeedPostResponse(
        **data,
        author_name=None,
        author_avatar_url=None,
        comment_count=0,
    )


# ── GET /feed/me ─────────────────────────────────
@router.get("/me", response_model=list[FeedPostResponse])
async def list_my_feed(
    limit: int = Query(20, ge=1, le=100),
    offset: int = Query(0, ge=0),
    user_id: str = Depends(get_current_user_id),
):
    sb = get_supabase()
    result = (
        sb.table("feed_posts")
        .select("*")
        .eq("user_id", user_id)
        .order("created_at", desc=True)
        .range(offset, offset + limit - 1)
        .execute()
    )
    rows = result.data or []
    profiles_map = _fetch_profiles_map(sb, [row.get("user_id") for row in rows])
    comment_counts = _fetch_comment_counts(sb, [row.get("id") for row in rows])
    return [_build_post_response(row, profiles_map, comment_counts) for row in rows]


# ── GET /feed/liked ──────────────────────────────
@router.get("/liked", response_model=list[FeedPostResponse])
async def list_liked_feed(
    limit: int = Query(20, ge=1, le=100),
    offset: int = Query(0, ge=0),
    user_id: str = Depends(get_current_user_id),
):
    sb = get_supabase()
    likes = (
        sb.table("feed_likes")
        .select("post_id, created_at")
        .eq("user_id", user_id)
        .order("created_at", desc=True)
        .range(offset, offset + limit - 1)
        .execute()
    )
    post_ids = [row["post_id"] for row in (likes.data or [])]
    if not post_ids:
        return []

    posts_result = (
        sb.table("feed_posts")
        .select("*")
        .in_("id", post_ids)
        .execute()
    )
    rows = posts_result.data or []
    profiles_map = _fetch_profiles_map(sb, [row.get("user_id") for row in rows])
    comment_counts = _fetch_comment_counts(sb, [row.get("id") for row in rows])
    posts_by_id = {
        str(row["id"]): _build_post_response(row, profiles_map, comment_counts)
        for row in rows
    }
    return [posts_by_id[pid] for pid in post_ids if pid in posts_by_id]


# ── GET /feed/{post_id} ──────────────────────────
@router.get("/{post_id}", response_model=FeedDetailResponse)
async def get_feed_post(
    post_id: UUID,
    user_id: str = Depends(get_current_user_id),
):
    sb = get_supabase()
    result = (
        sb.table("feed_posts")
        .select("*")
        .eq("id", str(post_id))
        .maybe_single()
        .execute()
    )
    if result is None or not getattr(result, "data", None):
        raise HTTPException(status_code=404, detail="게시물을 찾을 수 없습니다.")

    row = result.data
    profiles_map = _fetch_profiles_map(sb, [row.get("user_id")])
    comment_counts = _fetch_comment_counts(sb, [row.get("id")])

    # 내가 좋아요 했는지 확인
    like_result = (
        sb.table("feed_likes")
        .select("id")
        .eq("post_id", str(post_id))
        .eq("user_id", user_id)
        .maybe_single()
        .execute()
    )
    liked_by_me = bool(like_result and getattr(like_result, "data", None))

    merged = _attach_author(row, profiles_map)
    merged["comment_count"] = comment_counts.get(str(row.get("id")), 0)
    return FeedDetailResponse(**merged, liked_by_me=liked_by_me)


# ── POST /feed/{post_id}/like ────────────────────
@router.post("/{post_id}/like", response_model=LikeToggleResponse)
async def toggle_like(
    post_id: UUID,
    user_id: str = Depends(get_current_user_id),
):
    sb = get_supabase()
    post_id_str = str(post_id)

    # 기존 좋아요 확인
    existing = (
        sb.table("feed_likes")
        .select("id")
        .eq("post_id", post_id_str)
        .eq("user_id", user_id)
        .maybe_single()
        .execute()
    )
    existing_data = getattr(existing, "data", None) if existing else None

    # 게시물 작성자 확인 (알림용)
    post_result = (
        sb.table("feed_posts")
        .select("user_id")
        .eq("id", post_id_str)
        .maybe_single()
        .execute()
    )
    post_data = getattr(post_result, "data", None) if post_result else None
    post_author_id = post_data["user_id"] if post_data else None

    if existing_data:
        # 좋아요 취소
        sb.table("feed_likes").delete().eq("id", existing_data["id"]).execute()
        sb.table("feed_posts").update(
            {"like_count": sb.table("feed_posts").select("like_count").eq("id", post_id_str).single().execute().data["like_count"] - 1}
        ).eq("id", post_id_str).execute()
        liked = False
    else:
        # 좋아요 추가
        sb.table("feed_likes").insert(
            {"user_id": user_id, "post_id": post_id_str}
        ).execute()
        sb.table("feed_posts").update(
            {"like_count": sb.table("feed_posts").select("like_count").eq("id", post_id_str).single().execute().data["like_count"] + 1}
        ).eq("id", post_id_str).execute()
        liked = True

        # 좋아요 알림 생성 (본인 글이면 제외, 취소 시 생성 안 함)
        # notifications 테이블이 없거나 실패해도 좋아요 자체는 성공 처리
        if post_author_id and post_author_id != user_id:
            try:
                sb.table("notifications").insert(
                    {
                        "user_id": post_author_id,
                        "type": "like",
                        "actor_id": user_id,
                        "post_id": post_id_str,
                        "message": "회원님의 글을 좋아합니다",
                        "is_read": False,
                    }
                ).execute()
            except Exception:
                pass

    # 최종 like_count 반환
    post = (
        sb.table("feed_posts")
        .select("like_count")
        .eq("id", post_id_str)
        .single()
        .execute()
    )
    return LikeToggleResponse(liked=liked, like_count=post.data["like_count"])


# ── POST /feed/{post_id}/read ────────────────────
@router.post("/{post_id}/read", status_code=status.HTTP_204_NO_CONTENT)
async def mark_as_read(
    post_id: UUID,
    user_id: str = Depends(get_current_user_id),
):
    sb = get_supabase()
    # feed_reads 테이블에 upsert (중복 방지)
    sb.table("feed_reads").upsert(
        {"user_id": user_id, "post_id": str(post_id)},
        on_conflict="user_id,post_id",
    ).execute()
    return Response(status_code=status.HTTP_204_NO_CONTENT)
