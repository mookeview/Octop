import { createServer, type Server } from "node:http";
import {
  act,
  fireEvent,
  render,
  renderHook,
  screen,
  waitFor,
} from "@testing-library/react";
import { App } from "antd";
import { MemoryRouter } from "react-router-dom";
import { useState } from "react";
import {
  afterAll,
  beforeAll,
  beforeEach,
  describe,
  expect,
  it,
  vi,
} from "vitest";
import { message } from "@/utils/antdMessage";
import { useChatAttachments } from "./useChatAttachments";
import ChatInput from "../components/ChatInput";
import {
  useChatMessageQueue,
  type ChatQueueFlushHandler,
} from "./useChatMessageQueue";

const endpoint = vi.hoisted(() => ({ origin: "" }));
// The composer runs effects keyed by t; keep the test translator stable.
const translation = vi.hoisted(() => ({
  t: (
    key: string,
    fallback?: string | Record<string, unknown>,
    options?: Record<string, unknown>,
  ) => {
    const text = typeof fallback === "string" ? fallback : key;
    return text.replace(/\{\{(\w+)\}\}/g, (match, name: string) =>
      String(options?.[name] ?? match),
    );
  },
  i18n: { language: "en", changeLanguage: () => Promise.resolve() },
}));

vi.mock("react-i18next", async (importOriginal) => ({
  ...(await importOriginal<typeof import("react-i18next")>()),
  useTranslation: () => translation,
}));

vi.mock("../../../api/config", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../../api/config")>()),
  getApiUrl: (path: string) => `${endpoint.origin}/api${path}`,
}));
vi.mock("../../../hooks/useServerUploadLimit", () => ({
  useServerUploadLimit: () => ({ maxUploadBytes: 1024, maxUploadMb: 1 }),
}));
vi.mock("@/utils/antdMessage", () => ({
  message: { error: vi.fn(), warning: vi.fn() },
}));

describe("chat attachments over HTTP", () => {
  let server: Server;
  let status: number;
  let body: string;
  let requests: string[];

  beforeAll(async () => {
    server = createServer(async (request, response) => {
      response.setHeader("Access-Control-Allow-Origin", "*");
      response.setHeader("Access-Control-Allow-Headers", "*");
      if (request.method === "OPTIONS") {
        response.writeHead(204).end();
        return;
      }
      if (request.method === "GET") {
        response.writeHead(200, { "Content-Type": "application/json" });
        response.end(
          JSON.stringify({ commands: [], stt: "browser", tts: "browser" }),
        );
        return;
      }
      const chunks: Buffer[] = [];
      for await (const chunk of request) chunks.push(Buffer.from(chunk));
      requests.push(Buffer.concat(chunks).toString());
      response.writeHead(status, { "Content-Type": "application/json" });
      response.end(body);
    });
    await new Promise<void>((resolve) =>
      server.listen(0, "127.0.0.1", resolve),
    );
    const address = server.address();
    if (!address || typeof address === "string")
      throw new Error("No HTTP port");
    endpoint.origin = `http://127.0.0.1:${address.port}`;
  });

  afterAll(async () => {
    await new Promise<void>((resolve, reject) => {
      server.close((error) => (error ? reject(error) : resolve()));
    });
  });

  beforeEach(() => {
    requests = [];
    vi.mocked(message.error).mockClear();
  });

  it.each([
    [
      413,
      JSON.stringify({ detail: "File exceeds server upload limit" }),
      "File exceeds server upload limit",
    ],
    [
      500,
      JSON.stringify({
        error: {
          code: "INTERNAL_ERROR",
          message: "Storage temporarily unavailable",
        },
      }),
      "Storage temporarily unavailable",
    ],
    [502, "Bad Gateway", "Bad Gateway"],
    [
      503,
      JSON.stringify({
        error: { code: "SERVICE_UNAVAILABLE", message: "Please retry upload" },
      }),
      "Please retry upload",
    ],
  ])(
    "recovers from HTTP %i when selecting the same file again",
    async (errorStatus, errorBody, expectedMessage) => {
      status = errorStatus;
      body = errorBody;
      const { result } = renderHook(() => useChatAttachments("agent-1"));
      const file = new File(["HTTP_RECOVERY_7316"], "note.txt", {
        type: "text/plain",
      });
      const input = { files: [file], value: "note.txt" };

      await act(async () => {
        result.current.handleFileChange({
          target: input,
        } as unknown as Parameters<typeof result.current.handleFileChange>[0]);
      });
      expect(input.value).toBe("");
      await waitFor(() =>
        expect(message.error).toHaveBeenCalledWith(expectedMessage),
      );
      expect(result.current.uploading).toBe(false);
      expect(result.current.attachments).toEqual([]);
      expect(requests).toHaveLength(1);
      expect(requests[0]).toContain("HTTP_RECOVERY_7316");

      status = 200;
      body = JSON.stringify({
        path: "inbound/note.txt",
        filename: "note.txt",
        media_type: "text/plain",
        access_url:
          "/api/agents/agent-1/workspace/download?path=inbound/note.txt",
      });
      input.value = "note.txt";
      await act(async () => {
        result.current.handleFileChange({
          target: input,
        } as unknown as Parameters<typeof result.current.handleFileChange>[0]);
      });
      await waitFor(() => expect(result.current.attachments).toHaveLength(1));
      expect(result.current.attachments[0].filename).toBe("note.txt");
      expect(result.current.uploading).toBe(false);
      expect(input.value).toBe("");
      expect(requests).toHaveLength(2);
      expect(message.error).toHaveBeenCalledTimes(1);
    },
  );

  it("keeps the real composer's draft and existing attachment after an HTTP error", async () => {
    const onSend = vi.fn();
    const { container } = render(
      <MemoryRouter>
        <App>
          <ChatInput
            agentId="agent-1"
            threadId="http-upload-draft"
            isStreaming={false}
            onSend={onSend}
            onCancel={() => {}}
            onNewChat={() => {}}
          />
        </App>
      </MemoryRouter>,
    );
    const textarea = screen.getByRole("textbox");
    const input =
      container.querySelector<HTMLInputElement>('input[type="file"]');
    if (!input) throw new Error("No composer file input");
    fireEvent.change(textarea, { target: { value: "KEEP_DRAFT_5824" } });

    status = 200;
    body = JSON.stringify({
      path: "inbound/kept.txt",
      filename: "kept.txt",
      media_type: "text/plain",
      access_url: "/files/kept.txt",
    });
    fireEvent.change(input, {
      target: { files: [new File(["kept"], "kept.txt")] },
    });
    await screen.findByText("kept.txt");

    status = 500;
    body = JSON.stringify({
      error: { code: "INTERNAL_ERROR", message: "Upload storage failed" },
    });
    const retry = new File(["retry"], "retry.txt");
    fireEvent.change(input, { target: { files: [retry] } });
    await waitFor(() =>
      expect(message.error).toHaveBeenCalledWith("Upload storage failed"),
    );
    expect(textarea).toHaveValue("KEEP_DRAFT_5824");
    expect(screen.getByText("kept.txt")).toBeInTheDocument();
    expect(screen.queryByText("retry.txt")).not.toBeInTheDocument();

    status = 200;
    body = JSON.stringify({
      path: "inbound/retry.txt",
      filename: "retry.txt",
      media_type: "text/plain",
      access_url: "/files/retry.txt",
    });
    fireEvent.change(input, { target: { files: [retry] } });
    await screen.findByText("retry.txt");
    expect(screen.getByText("kept.txt")).toBeInTheDocument();
    expect(textarea).toHaveValue("KEEP_DRAFT_5824");
    expect(input.value).toBe("");
    expect(requests).toHaveLength(3);
    expect(onSend).not.toHaveBeenCalled();
  });

  it("queues an addition in the real composer and flushes it once after streaming", async () => {
    const onSend = vi.fn();
    const onFlush = vi.fn<ChatQueueFlushHandler>(() => true);
    function StreamingComposer() {
      const [streaming, setStreaming] = useState(true);
      const queue = useChatMessageQueue({
        agentId: "agent-1",
        threadId: "composer-addition",
        isStreaming: streaming,
        onFlush,
        subscribeStreamEnd: () => () => {},
        isThreadStreaming: () => streaming,
      });
      return (
        <>
          <button onClick={() => setStreaming(false)}>Finish stream</button>
          <output data-testid="queue-size">{queue.items.length}</output>
          <ChatInput
            agentId="agent-1"
            threadId="composer-addition"
            isStreaming={streaming}
            onSend={onSend}
            onCancel={() => {}}
            onNewChat={() => {}}
            onQueue={queue.enqueue}
            queuedItems={queue.items}
          />
        </>
      );
    }
    render(
      <MemoryRouter>
        <App>
          <StreamingComposer />
        </App>
      </MemoryRouter>,
    );
    const textarea = screen.getByRole("textbox");
    fireEvent.change(textarea, { target: { value: "ADDITION_4289" } });
    fireEvent.keyDown(textarea, { key: "Enter", code: "Enter" });
    await waitFor(() =>
      expect(screen.getByTestId("queue-size")).toHaveTextContent("1"),
    );
    expect(textarea).toHaveValue("");
    expect(onSend).not.toHaveBeenCalled();
    expect(onFlush).not.toHaveBeenCalled();
    fireEvent.click(screen.getByText("Finish stream"));
    await waitFor(() => expect(onFlush).toHaveBeenCalledTimes(1));
    expect(onFlush.mock.calls[0][0]).toMatchObject({ text: "ADDITION_4289" });
    expect(screen.getByTestId("queue-size")).toHaveTextContent("0");
    expect(onSend).not.toHaveBeenCalled();
  });
});
