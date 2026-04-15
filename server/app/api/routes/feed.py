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
        .select("*, profiles(display_name)")
        .order("created_at", desc=True)
        .range(offset, offset + limit - 1)
        .execute()
    )
    posts = []
    for row in result.data:
        profile = row.pop("profiles", None)
        posts.append(
            FeedPostResponse(
                **row,
                author_name=profile.get("display_name") if profile else None,
            )
        )
    return posts


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
    return FeedPostResponse(**data, author_name=None)


# ── GET /feed/{post_id} ──────────────────────────
@router.get("/{post_id}", response_model=FeedDetailResponse)
async def get_feed_post(
    post_id: UUID,
    user_id: str = Depends(get_current_user_id),
):
    sb = get_supabase()
    result = (
        sb.table("feed_posts")
        .select("*, profiles(display_name)")
        .eq("id", str(post_id))
        .maybe_single()
        .execute()
    )
    if not result.data:
        raise HTTPException(status_code=404, detail="게시물을 찾을 수 없습니다.")

    row = result.data
    profile = row.pop("profiles", None)

    # 내가 좋아요 했는지 확인
    like_result = (
        sb.table("feed_likes")
        .select("id")
        .eq("post_id", str(post_id))
        .eq("user_id", user_id)
        .maybe_single()
        .execute()
    )

    return FeedDetailResponse(
        **row,
        author_name=profile.get("display_name") if profile else None,
        liked_by_me=like_result.data is not None,
    )


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

    # 게시물 작성자 확인 (알림용)
    post_result = (
        sb.table("feed_posts")
        .select("user_id")
        .eq("id", post_id_str)
        .maybe_single()
        .execute()
    )
    post_author_id = post_result.data["user_id"] if post_result.data else None

    if existing.data:
        # 좋아요 취소
        sb.table("feed_likes").delete().eq("id", existing.data["id"]).execute()
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
        if post_author_id and post_author_id != user_id:
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
