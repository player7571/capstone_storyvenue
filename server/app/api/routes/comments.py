from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Response, status

from app.api.dependencies.auth import get_current_user_id
from app.api.schemas.comments import CommentCreateRequest, CommentResponse
from app.db.supabase import get_supabase
from app.services.safety import check_content_safety

router = APIRouter(tags=["comments"])


# ── GET /feed/{post_id}/comments ─────────────────
@router.get("/feed/{post_id}/comments", response_model=list[CommentResponse])
async def list_comments(
    post_id: UUID,
    user_id: str = Depends(get_current_user_id),
):
    """해당 게시물의 댓글 목록을 시간순으로 반환한다."""
    sb = get_supabase()
    result = (
        sb.table("feed_comments")
        .select("*")
        .eq("post_id", str(post_id))
        .order("created_at", desc=False)
        .execute()
    )
    rows = result.data or []
    unique_ids = list({row.get("user_id") for row in rows if row.get("user_id")})
    profiles_map: dict[str, dict] = {}
    if unique_ids:
        profiles_result = (
            sb.table("profiles")
            .select("id, name, avatar_url")
            .in_("id", unique_ids)
            .execute()
        )
        profiles_map = {str(p["id"]): p for p in (profiles_result.data or [])}

    comments = []
    for row in rows:
        profile = profiles_map.get(str(row.get("user_id"))) or {}
        merged = {**row}
        merged.pop("author_name", None)
        merged.pop("author_avatar_url", None)
        comments.append(
            CommentResponse(
                **merged,
                author_name=profile.get("name") or row.get("author_name"),
                author_avatar_url=profile.get("avatar_url"),
            )
        )
    return comments


# ── POST /feed/{post_id}/comments ────────────────
@router.post(
    "/feed/{post_id}/comments",
    response_model=CommentResponse,
    status_code=status.HTTP_201_CREATED,
)
async def create_comment(
    post_id: UUID,
    body: CommentCreateRequest,
    user_id: str = Depends(get_current_user_id),
):
    """댓글을 작성하고, 게시물 작성자에게 알림을 생성한다."""
    sb = get_supabase()
    post_id_str = str(post_id)

    # 게시물 존재 확인
    post = (
        sb.table("feed_posts")
        .select("id, user_id")
        .eq("id", post_id_str)
        .maybe_single()
        .execute()
    )
    post_data = getattr(post, "data", None) if post else None
    if not post_data:
        raise HTTPException(status_code=404, detail="게시물을 찾을 수 없습니다.")

    # 안전 검사
    safety = check_content_safety(body.content)
    if not safety["safe"]:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"부적절한 콘텐츠가 감지되었습니다: {safety['reason']}",
        )

    # 작성자 이름/아바타 조회 (insert 전에 먼저 — author_name 컬럼이 NOT NULL)
    profile = (
        sb.table("profiles")
        .select("name, avatar_url")
        .eq("id", user_id)
        .maybe_single()
        .execute()
    )
    profile_data = getattr(profile, "data", None) if profile else None
    author_name = (profile_data or {}).get("name") or "익명"
    author_avatar_url = (profile_data or {}).get("avatar_url")

    # 댓글 저장
    row = (
        sb.table("feed_comments")
        .insert(
            {
                "post_id": post_id_str,
                "user_id": user_id,
                "content": body.content,
                "author_name": author_name,
            }
        )
        .execute()
    )
    comment_data = row.data[0]

    # 알림 생성 (본인 글에 본인이 댓글 달면 제외)
    # notifications 테이블이 없거나 실패해도 댓글 자체는 성공 처리
    post_author_id = post_data["user_id"]
    if post_author_id != user_id:
        try:
            sb.table("notifications").insert(
                {
                    "user_id": post_author_id,
                    "type": "comment",
                    "actor_id": user_id,
                    "post_id": post_id_str,
                    "comment_id": comment_data["id"],
                    "message": "회원님의 글에 댓글을 남겼습니다",
                    "is_read": False,
                }
            ).execute()
        except Exception:
            pass

    merged_comment = {**comment_data}
    merged_comment.pop("author_name", None)
    merged_comment.pop("author_avatar_url", None)
    return CommentResponse(
        **merged_comment,
        author_name=author_name,
        author_avatar_url=author_avatar_url,
    )


# ── DELETE /comments/{comment_id} ────────────────
@router.delete("/comments/{comment_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_comment(
    comment_id: UUID,
    user_id: str = Depends(get_current_user_id),
):
    """본인이 작성한 댓글만 삭제할 수 있다."""
    sb = get_supabase()
    comment_id_str = str(comment_id)

    # 댓글 존재 확인
    comment = (
        sb.table("feed_comments")
        .select("id, user_id")
        .eq("id", comment_id_str)
        .maybe_single()
        .execute()
    )
    if not comment.data:
        raise HTTPException(status_code=404, detail="댓글을 찾을 수 없습니다.")

    # 본인 확인
    if comment.data["user_id"] != user_id:
        raise HTTPException(status_code=403, detail="본인이 작성한 댓글이 아닙니다.")

    sb.table("feed_comments").delete().eq("id", comment_id_str).execute()
    return Response(status_code=status.HTTP_204_NO_CONTENT)
