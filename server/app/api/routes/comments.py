from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Response, status

from app.api.dependencies.auth import get_current_user_id
from app.api.schemas.comments import CommentCreateRequest, CommentResponse
from app.db.supabase import get_supabase

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
        .select("*, profiles(display_name)")
        .eq("post_id", str(post_id))
        .order("created_at", desc=False)
        .execute()
    )
    comments = []
    for row in result.data:
        profile = row.pop("profiles", None)
        comments.append(
            CommentResponse(
                **row,
                author_name=profile.get("display_name") if profile else None,
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
    if not post.data:
        raise HTTPException(status_code=404, detail="게시물을 찾을 수 없습니다.")

    # 댓글 저장
    row = (
        sb.table("feed_comments")
        .insert(
            {
                "post_id": post_id_str,
                "user_id": user_id,
                "content": body.content,
            }
        )
        .execute()
    )
    comment_data = row.data[0]

    # 작성자 이름 조회
    profile = (
        sb.table("profiles")
        .select("display_name")
        .eq("id", user_id)
        .maybe_single()
        .execute()
    )
    author_name = profile.data.get("display_name") if profile.data else None

    # 알림 생성 (본인 글에 본인이 댓글 달면 제외)
    post_author_id = post.data["user_id"]
    if post_author_id != user_id:
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

    return CommentResponse(**comment_data, author_name=author_name)


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
