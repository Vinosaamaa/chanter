import fs from 'node:fs/promises';
import path from 'node:path';
import { createHash } from 'node:crypto';

const directory = path.resolve(process.argv[2] ?? '.cache/models/minilm');
const revision = '1110a243fdf4706b3f48f1d95db1a4f5529b4d41';
const files = [
  ['onnx/model.onnx', 'model.onnx', '6fd5d72fe4589f189f8ebc006442dbb529bb7ce38f8082112682524616046452', 100_000_000],
  ['tokenizer.json', 'tokenizer.json', 'be50c3628f2bf5bb5e3a7f17b1f74611b2561a3a27eeab05e5aa30f411572037', 1_000_000],
];
await fs.mkdir(directory, { recursive: true });
for (const [remote, local, checksum, maximum] of files) {
  const file = path.join(directory, local);
  const existing = await fs.readFile(file).catch(() => null);
  if (existing && createHash('sha256').update(existing).digest('hex') === checksum) continue;
  let completed = false;
  for (let attempt = 0; attempt < 3 && !completed; attempt++) {
    try {
      const response = await fetch(`https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/${revision}/${remote}`, { signal: AbortSignal.timeout(180_000) });
      if (!response.ok) throw new Error('Model asset download failed');
      const chunks = []; let size = 0;
      for await (const chunk of response.body) {
        size += chunk.length;
        if (size > maximum) throw new Error('Model asset exceeds expected size');
        chunks.push(chunk);
      }
      const bytes = Buffer.concat(chunks);
      if (createHash('sha256').update(bytes).digest('hex') !== checksum) throw new Error('Model asset checksum mismatch');
      await fs.writeFile(file, bytes);
      completed = true;
    } catch (failure) { if (attempt === 2) throw failure; }
  }
}
console.log('Pinned semantic model assets verified.');
