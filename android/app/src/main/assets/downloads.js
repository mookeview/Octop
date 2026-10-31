(() => {
  const bridge = window.OctopDownloads;
  if (!bridge || window.octopAndroidDownloads) return;
  const pending = new Map();
  let sequence = 0;

  bridge.onmessage = (event) => {
    const reply = JSON.parse(event.data);
    const waiter = pending.get(reply.id);
    if (!waiter) return;
    pending.delete(reply.id);
    if (reply.type === "error") waiter.reject(new Error(reply.message));
    else waiter.resolve(reply);
  };

  function request(message) {
    return new Promise((resolve, reject) => {
      pending.set(message.id, { resolve, reject });
      bridge.postMessage(JSON.stringify(message));
    });
  }

  function base64(blob) {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(reader.result.split(",", 2)[1]);
      reader.onerror = () => reject(reader.error);
      reader.readAsDataURL(blob);
    });
  }

  async function save(blob, filename) {
    const id = `${Date.now()}-${++sequence}`;
    try {
      const start = await request({
        type: "begin", id, name: filename, mime: blob.type, size: blob.size,
      });
      if (start.type === "cancel") return;
      for (let offset = 0; offset < blob.size; offset += 65536) {
        const data = await base64(blob.slice(offset, offset + 65536));
        await request({ type: "chunk", id, data });
      }
      await request({ type: "end", id });
    } catch (error) {
      bridge.postMessage(JSON.stringify({ type: "abort", id }));
      throw error;
    }
  }

  window.octopAndroidDownloads = {
    save,
    async downloadUrl(url, filename) {
      try {
        const response = await fetch(url, { credentials: "same-origin" });
        if (!response.ok) throw new Error();
        await save(await response.blob(), filename);
      } catch {
        bridge.postMessage(JSON.stringify({ type: "urlError" }));
      }
    },
  };
})();
