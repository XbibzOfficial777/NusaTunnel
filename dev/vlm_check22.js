// Verifikasi visual screenshot NusaTunnel 2.2 (React + liquid glass)
// via z-ai-web-dev-sdk vision. Argumen: file1 file2 ... prompt
const fs = require("fs");

async function ask(zai, file, prompt) {
  const b64 = fs.readFileSync(file).toString("base64");
  const r = await zai.chat.completions.createVision({
    messages: [{
      role: "user",
      content: [
        { type: "text", text: prompt },
        { type: "image_url", image_url: { url: `data:image/png;base64,${b64}` } },
      ],
    }],
    thinking: { type: "disabled" },
  });
  console.log("=== " + file + " ===");
  console.log(r.choices[0]?.message?.content);
  console.log();
}

(async () => {
  const files = process.argv.slice(2, -1);
  const prompt = process.argv[process.argv.length - 1];
  const ZAI = (await import("z-ai-web-dev-sdk")).default;
  const zai = await ZAI.create();
  for (const f of files) await ask(zai, f, prompt);
})().catch(e => { console.error("ERR:", e.message); process.exit(1); });
