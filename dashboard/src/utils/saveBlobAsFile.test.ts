import { afterEach, describe, expect, it, vi } from "vitest";

import { message } from "@/utils/antdMessage";
import { requestUpload } from "../api/request";
import { shareDownloadLink } from "./downloadLink";
import { saveBlobAsFile } from "./saveBlobAsFile";

vi.mock("../api/request", () => ({
  requestUpload: vi.fn(),
}));

vi.mock("./downloadLink", () => ({
  shareDownloadLink: vi.fn(),
}));

vi.mock("@/utils/antdMessage", () => ({
  message: { success: vi.fn(), error: vi.fn() },
}));

type DesktopWindow = Window & {
  _wails?: { invoke?: (message: string) => void };
  octopAndroidDownloads?: {
    save: (blob: Blob, filename: string) => Promise<void>;
  };
};

afterEach(() => {
  delete (window as DesktopWindow)._wails;
  delete (window as DesktopWindow).octopAndroidDownloads;
  vi.mocked(requestUpload).mockReset();
  vi.mocked(message.success).mockReset();
  vi.mocked(shareDownloadLink).mockReset();
  vi.restoreAllMocks();
});

describe("saveBlobAsFile", () => {
  it("passes authenticated file bytes and the filename to the Android save picker", async () => {
    const save = vi.fn().mockResolvedValue(undefined);
    (window as DesktopWindow).octopAndroidDownloads = { save };
    const click = vi.spyOn(HTMLAnchorElement.prototype, "click");
    const blob = new Blob(["hello"], { type: "text/plain" });

    await saveBlobAsFile(blob, " 中文 附件.txt ");

    expect(save).toHaveBeenCalledWith(blob, "中文 附件.txt");
    expect(click).not.toHaveBeenCalled();
    expect(requestUpload).not.toHaveBeenCalled();
    expect(shareDownloadLink).not.toHaveBeenCalled();
  });

  it("reports Android save errors without falling back to another download", async () => {
    (window as DesktopWindow).octopAndroidDownloads = {
      save: vi.fn().mockRejectedValue(new Error("文件保存失败，请重试。")),
    };
    const click = vi.spyOn(HTMLAnchorElement.prototype, "click");

    await expect(
      saveBlobAsFile(new Blob(["hello"]), "note.txt"),
    ).rejects.toThrow("文件保存失败");
    expect(click).not.toHaveBeenCalled();
    expect(requestUpload).not.toHaveBeenCalled();
  });

  it("posts the blob to the host Downloads API in the desktop shell", async () => {
    (window as DesktopWindow)._wails = { invoke: () => undefined };
    const blob = new Blob(["hello"], { type: "text/plain" });
    await saveBlobAsFile(blob, "note.txt");
    expect(requestUpload).toHaveBeenCalledTimes(1);
    const [path, body] = vi.mocked(requestUpload).mock.calls[0]!;
    expect(path).toBe("/downloads/local");
    expect(body).toBeInstanceOf(FormData);
    expect((body as FormData).get("file")).toBeInstanceOf(Blob);
    expect(message.success).toHaveBeenCalled();
  });

  it("uses a download anchor in the browser", async () => {
    const click = vi.fn();
    const createElement = vi.spyOn(document, "createElement");
    createElement.mockImplementation(
      () =>
        ({
          href: "",
          download: "",
          click,
          style: {},
        }) as unknown as HTMLElement,
    );
    vi.spyOn(document.body, "appendChild").mockImplementation((node) => node);
    vi.spyOn(document.body, "removeChild").mockImplementation((node) => node);
    URL.createObjectURL = vi.fn(() => "blob:save");
    URL.revokeObjectURL = vi.fn();

    await saveBlobAsFile(new Blob(["x"]), "file.bin");
    expect(requestUpload).not.toHaveBeenCalled();
    expect(click).toHaveBeenCalled();
    createElement.mockRestore();
  });

  it("hands in-app browsers a download link instead of a blob", async () => {
    vi.spyOn(navigator, "userAgent", "get").mockReturnValue(
      "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) MicroMessenger/8.0.50",
    );
    const click = vi.spyOn(HTMLAnchorElement.prototype, "click");
    const blob = new Blob(["x"]);

    await saveBlobAsFile(blob, "report.docx");
    expect(shareDownloadLink).toHaveBeenCalledWith(blob, "report.docx");
    expect(click).not.toHaveBeenCalled();
  });
});
