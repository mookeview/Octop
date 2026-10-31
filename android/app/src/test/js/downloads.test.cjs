const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const vm = require("node:vm");

const script = fs.readFileSync(path.join(__dirname, "../../main/assets/downloads.js"), "utf8");

function page(onMessage) {
  const messages = [];
  const bridge = {
    postMessage(raw) {
      const message = JSON.parse(raw);
      messages.push(message);
      const reply = onMessage(message);
      if (reply) setImmediate(() => bridge.onmessage({ data: JSON.stringify({ id: message.id, ...reply }) }));
    },
  };
  class FileReader {
    readAsDataURL(blob) {
      blob.arrayBuffer().then((bytes) => {
        this.result = `data:application/octet-stream;base64,${Buffer.from(bytes).toString("base64")}`;
        this.onload();
      });
    }
  }
  const window = { OctopDownloads: bridge };
  vm.runInNewContext(script, { window, FileReader, fetch });
  return { api: window.octopAndroidDownloads, messages };
}

test("transfers binary files in bounded, acknowledged chunks without changing bytes", async () => {
  const bytes = Buffer.alloc(65536 * 3 + 11);
  for (let index = 0; index < bytes.length; index++) bytes[index] = index % 256;
  const received = [];
  const { api, messages } = page((message) => {
    if (message.type === "chunk") received.push(Buffer.from(message.data, "base64"));
    return { type: message.type === "begin" ? "ready" : message.type === "end" ? "saved" : "chunk" };
  });
  await api.save(new Blob([bytes], { type: "image/png" }), "中文 图片.png");
  assert.deepEqual(Buffer.concat(received), bytes);
  assert.equal(received.length, 4);
  assert.ok(received.every((chunk) => chunk.length <= 65536));
  assert.equal(messages[0].name, "中文 图片.png");
  assert.equal(messages[0].mime, "image/png");
  assert.equal(messages[0].size, bytes.length);
  assert.equal(messages.at(-1).type, "end");
});

test("canceling the picker sends no file data and allows another save", async () => {
  let canceled = true;
  const { api, messages } = page((message) => ({
    type: message.type === "begin" ? (canceled ? "cancel" : "ready") : "saved",
  }));
  await api.save(new Blob(["private text"]), "cancel.txt");
  assert.deepEqual(messages.map((message) => message.type), ["begin"]);
  canceled = false;
  await api.save(new Blob([]), "empty.txt");
  assert.deepEqual(messages.map((message) => message.type), ["begin", "begin", "end"]);
});

test("a disk error stops transmission and aborts the partial document", async () => {
  const { api, messages } = page((message) => {
    if (message.type === "begin") return { type: "ready" };
    if (message.type === "chunk") return { type: "error", message: "文件保存失败，请重试。" };
  });
  await assert.rejects(api.save(new Blob([Buffer.alloc(65537)]), "disk.bin"), /文件保存失败/);
  assert.deepEqual(messages.map((message) => message.type), ["begin", "chunk", "abort"]);
});

test("ordinary pages do not receive an Android download adapter", () => {
  const window = {};
  vm.runInNewContext(script, { window });
  assert.equal(window.octopAndroidDownloads, undefined);
});
