from uuid import UUID

from app.db.supabase import get_supabase

SESSION_ARTIFACTS_TABLE = "session_artifacts"
PHOTO_ARTIFACT_TYPE = "photo"


def insert_session_artifact(
    *,
    session_id: UUID,
    artifact_type: str,
    storage_url: str,
    mime_type: str | None = None,
    linked_question_no: int | None = None,
    summary: str | None = None,
) -> dict:
    payload = {
        "session_id": str(session_id),
        "artifact_type": artifact_type,
        "storage_url": storage_url,
    }
    if mime_type:
        payload["mime_type"] = mime_type
    if linked_question_no is not None:
        payload["linked_question_no"] = linked_question_no
    if summary:
        payload["summary"] = summary

    result = get_supabase().table(SESSION_ARTIFACTS_TABLE).insert(payload).execute()
    if not result.data:
        raise RuntimeError("세션 자료 저장에 실패했습니다.")
    return result.data[0]


def list_session_artifacts(
    *,
    session_id: UUID,
    artifact_type: str | None = None,
    linked_question_no: int | None = None,
    limit: int | None = None,
) -> list[dict]:
    query = (
        get_supabase()
        .table(SESSION_ARTIFACTS_TABLE)
        .select("*")
        .eq("session_id", str(session_id))
        .order("created_at", desc=True)
    )
    if artifact_type:
        query = query.eq("artifact_type", artifact_type)
    if linked_question_no is not None:
        query = query.eq("linked_question_no", linked_question_no)
    if limit is not None:
        query = query.limit(limit)
    result = query.execute()
    return result.data or []


def get_latest_session_artifact(
    *,
    session_id: UUID,
    artifact_type: str | None = None,
    linked_question_no: int | None = None,
) -> dict | None:
    rows = list_session_artifacts(
        session_id=session_id,
        artifact_type=artifact_type,
        linked_question_no=linked_question_no,
        limit=1,
    )
    return rows[0] if rows else None
