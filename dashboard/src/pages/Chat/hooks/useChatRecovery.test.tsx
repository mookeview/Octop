import { act, cleanup, renderHook, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { octopThreadsApi } from "../../../api/modules/octopThreads";
import { octopAgentsApi } from "../../../api/modules/octopAgents";
import * as chatStore from "./chatStore";
import { useChat } from "./useChat";
import { useChatNavigation } from "./useChatNavigation";
import { clearInflightTail } from "../utils/inflightTranscriptCache";
import { clearPendingThread, markPendingThread } from "./useSessions";

vi.mock("../../../api/modules/octopAgents", () => ({
  octopAgentsApi: { markRead: vi.fn().mockResolvedValue(undefined) },
}));

const thread = "background-recovery";
const complete = "001: start\n120: complete\nBACKGROUND_DONE";

class FakeSocket {
  static OPEN = 1;
  static CONNECTING = 0;
  static instances: FakeSocket[] = [];
  readyState = FakeSocket.CONNECTING;
  onopen: (() => void) | null = null;
  onmessage: ((event: { data: string }) => void) | null = null;
  onclose: (() => void) | null = null;
  onerror: (() => void) | null = null;

  constructor() {
    FakeSocket.instances.push(this);
    queueMicrotask(() => {
      this.readyState = FakeSocket.OPEN;
      this.onopen?.();
    });
  }

  send() {}

  close() {
    if (this.readyState === 3) return;
    this.readyState = 3;
    this.onclose?.();
  }

  frame(data: Record<string, unknown>) {
    this.onmessage?.({ data: JSON.stringify(data) });
  }
}

function wrapper({ children }: { children: ReactNode }) {
  return <MemoryRouter>{children}</MemoryRouter>;
}

function renderChat(pending = false) {
  const prefillInputRef = { current: "" };
  const sessions = [
    {
      id: thread,
      name: "Recovery test",
      threadId: thread,
      updatedAt: null,
      channelType: "dashboard",
      hasActivity: true,
    },
  ];
  const ensureThreadInList = vi.fn().mockResolvedValue("found");
  const fetchSessions = vi.fn().mockResolvedValue(sessions);
  const refreshAgents = vi.fn().mockResolvedValue(undefined);
  const rendered = renderHook(
    () => {
      const chat = useChat(thread, "agent");
      useChatNavigation({
        routeAgentId: "agent",
        threadId: thread,
        resolvedAgentId: "agent",
        activeThreadId: thread,
        sessions: pending ? [] : sessions,
        sessionsLoading: false,
        prefillInputRef,
        loadHistory: chat.loadHistory,
        clearMessages: chat.clearMessages,
        ensureThreadInList,
        fetchSessions,
        refreshAgents,
      });
      return chat;
    },
    { wrapper },
  );
  return { ...rendered, fetchSessions };
}

function resumePage() {
  const visibility = vi.spyOn(document, "visibilityState", "get");
  act(() => {
    visibility.mockReturnValue("hidden");
    document.dispatchEvent(new Event("visibilitychange"));
    visibility.mockReturnValue("visible");
    document.dispatchEvent(new Event("visibilitychange"));
  });
}

beforeEach(() => {
  vi.mocked(octopAgentsApi.markRead).mockResolvedValue(undefined);
  FakeSocket.instances = [];
  vi.stubGlobal("WebSocket", FakeSocket);
  chatStore.setHistoryPage(
    thread,
    [
      { id: "user", role: "user", content: "test", timestamp: 1_000 },
      {
        id: "assistant",
        role: "assistant",
        content: "001: start\n096: partial",
        status: "done",
        timestamp: 2_000,
      },
    ],
    { hasMore: false, nextOffset: 2 },
  );
  vi.spyOn(octopThreadsApi, "history").mockResolvedValue({
    thread_id: thread,
    messages: [
      { id: "user", role: "user", content: "test", timestamp: 1 },
      { id: "assistant", role: "assistant", content: complete, timestamp: 2 },
    ],
    turn_active: false,
    has_more: false,
  });
});

afterEach(() => {
  cleanup();
  chatStore.removeSession(thread);
  clearInflightTail(thread);
  clearPendingThread(thread);
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

describe("chat history after a background stream", () => {
  it("replaces the incomplete local reply when the page becomes visible", async () => {
    const { result } = renderChat();
    expect(octopThreadsApi.history).not.toHaveBeenCalled();
    resumePage();

    await waitFor(() => {
      expect(result.current.messages.at(-1)?.content).toBe(complete);
    });
    expect(
      result.current.messages.filter((m) => m.role === "user"),
    ).toHaveLength(1);
  });

  it.each([true, false])(
    "loads the full reply after reconnect (server active: %s)",
    async (active) => {
      const { result } = renderChat();
      await waitFor(() => expect(FakeSocket.instances).toHaveLength(1));
      act(() => {
        FakeSocket.instances[0].frame({ type: "turn_status", active: true });
        FakeSocket.instances[0].close();
      });
      await waitFor(() => expect(FakeSocket.instances).toHaveLength(2));
      act(() => {
        FakeSocket.instances[1].frame({ type: "turn_status", active });
        if (active) FakeSocket.instances[1].frame({ type: "done" });
      });

      await waitFor(() => {
        expect(result.current.messages.at(-1)?.content).toBe(complete);
      });
      expect(result.current.isStreaming).toBe(false);
      expect(
        result.current.messages.filter((m) => m.role === "user"),
      ).toHaveLength(1);
    },
  );

  it("waits for the live reply to finish before reconciling on foreground", async () => {
    const { result } = renderChat();
    await waitFor(() => expect(FakeSocket.instances).toHaveLength(1));
    act(() => {
      FakeSocket.instances[0].frame({ type: "turn_status", active: true });
    });
    resumePage();
    expect(result.current.isStreaming).toBe(true);
    expect(octopThreadsApi.history).not.toHaveBeenCalled();
    act(() => FakeSocket.instances[0].frame({ type: "done" }));
    await waitFor(() => {
      expect(result.current.messages.at(-1)?.content).toBe(complete);
    });
    expect(octopThreadsApi.history).toHaveBeenCalledOnce();
  });

  it("preserves a pending approval when returning to the foreground", async () => {
    const card = {
      id: "approval",
      role: "assistant" as const,
      content: "",
      timestamp: 3_000,
      status: "done" as const,
      hitlData: {
        pending_id: "pending-write",
        action_requests: [{ name: "write_file", args: { path: "test.txt" } }],
        status: "pending" as const,
      },
    };
    chatStore.setMessages(thread, [
      ...chatStore.getSnapshot(thread).messages,
      card,
    ]);
    const { result } = renderChat();
    resumePage();
    expect(result.current.messages.at(-1)).toEqual(card);
    expect(octopThreadsApi.history).not.toHaveBeenCalled();
  });

  it("does not reload history at an uninterrupted stream end", async () => {
    renderChat();
    await waitFor(() => expect(FakeSocket.instances).toHaveLength(1));
    act(() => {
      FakeSocket.instances[0].frame({ type: "turn_status", active: true });
      FakeSocket.instances[0].frame({ type: "done" });
    });
    expect(octopThreadsApi.history).not.toHaveBeenCalled();
  });

  it("still refreshes the session list after the first turn of a new thread", async () => {
    markPendingThread(thread);
    const { fetchSessions } = renderChat(true);
    await act(async () => {
      await chatStore.attachThread(thread, "agent", thread);
    });
    act(() => {
      FakeSocket.instances[0].frame({ type: "turn_status", active: true });
      FakeSocket.instances[0].frame({ type: "done" });
    });
    expect(fetchSessions).toHaveBeenCalledWith(thread);
  });
});
