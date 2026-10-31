"""Tests for BinaryReadGuardMiddleware."""

from __future__ import annotations

from pathlib import Path
from unittest.mock import AsyncMock

import pytest
from langchain_core.messages import HumanMessage, ToolMessage
from langgraph.prebuilt.tool_node import ToolCallRequest

from octop.infra.agents.middleware.binary_read_guard import (
    BinaryReadGuardMiddleware,
    read_file_block_reason,
)


def test_blocks_inbound_pdf() -> None:
    reason = read_file_block_reason("inbound/01KWB9MG2Z570P7QB367KDJ75R.pdf")
    assert reason is not None
    assert "pdf" in reason.lower()
    assert "execute_shell_command" in reason


def test_allows_inbound_txt() -> None:
    assert read_file_block_reason("inbound/01JTEST.txt") is None


def test_allows_inbound_log() -> None:
    assert read_file_block_reason("inbound/1793071556_octop.log") is None


def test_allows_unlisted_text_suffix() -> None:
    assert read_file_block_reason("inbound/1793071556_app.conf") is None


def test_allows_text_suffix_labeled_octet_stream() -> None:
    assert (
        read_file_block_reason(
            "inbound/1793071556_app.conf",
            media_type="application/octet-stream",
        )
        is None
    )


def test_blocks_unlabeled_unknown_inbound_suffix() -> None:
    reason = read_file_block_reason(
        "inbound/payload.unknown",
        media_type="application/octet-stream",
    )
    assert reason is not None
    assert "inbound attachment" in reason


def test_blocks_inbound_without_suffix() -> None:
    assert read_file_block_reason("inbound/payload") is not None


def test_allows_unknown_inbound_suffix_with_text_media() -> None:
    assert read_file_block_reason("inbound/notes.go", media_type="text/x-go") is None


def test_allows_workspace_file_with_unknown_suffix() -> None:
    assert (
        read_file_block_reason(
            "src/payload.unknown",
            media_type="application/octet-stream",
        )
        is None
    )


def test_blocks_inbound_image() -> None:
    reason = read_file_block_reason("inbound/1793071556_shot.png")
    assert reason is not None
    assert "binary file" in reason


def test_blocks_binary_mime_with_an_unknown_extension() -> None:
    reason = read_file_block_reason(
        "inbound/1793071556_upload.dat",
        media_type="application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    )
    assert reason is not None
    assert "binary attachment" in reason


def test_blocks_pdf_anywhere() -> None:
    assert read_file_block_reason("docs/report.pdf") is not None


@pytest.mark.asyncio
@pytest.mark.parametrize("path_kind", ["relative", "virtual", "absolute", "backslash"])
@pytest.mark.parametrize(
    ("filename", "media_type", "blocked"),
    [
        ("attachment.txt", "text/plain", False),
        ("attachment.go", "text/x-go", False),
        ("attachment.dat", "application/pdf", True),
    ],
)
async def test_read_file_matches_attachment_metadata_with_workspace_paths(
    tmp_path: Path, path_kind: str, filename: str, media_type: str, blocked: bool
) -> None:
    workspace_path = f"inbound/{filename}"
    tool_path = {
        "relative": workspace_path,
        "virtual": f"/{workspace_path}",
        "absolute": str(tmp_path / "inbound" / filename),
        "backslash": workspace_path.replace("/", "\\"),
    }[path_kind]
    request = ToolCallRequest(
        tool_call={"name": "read_file", "args": {"file_path": tool_path}, "id": "call-1"},
        tool=None,
        state={
            "messages": [
                HumanMessage(
                    content="Read the attachment.",
                    additional_kwargs={
                        "octop_inbound_attachments": [
                            {"workspace_path": workspace_path, "media_type": media_type}
                        ]
                    },
                )
            ]
        },
        runtime=None,  # type: ignore[arg-type]
    )
    response = ToolMessage(content="ATTACHMENT_READ_OK", tool_call_id="call-1")
    handler = AsyncMock(return_value=response)

    result = await BinaryReadGuardMiddleware().awrap_tool_call(request, handler)

    if blocked:
        handler.assert_not_awaited()
        assert isinstance(result, ToolMessage)
        assert result.status == "error"
        assert "binary attachment" in result.content
    else:
        handler.assert_awaited_once_with(request)
        assert result is response
