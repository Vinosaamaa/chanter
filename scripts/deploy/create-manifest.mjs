import fs from 'node:fs';
import { execFileSync } from 'node:child_process';
import { imageNames, validateRelease } from './release.mjs';

const [output, architecture] = process.argv.slice(2);
if (!output || !['arm64', 'amd64'].includes(architecture)) throw new Error('Expected output file and architecture');
const commit = execFileSync('git', ['rev-parse', 'HEAD'], { encoding: 'utf8' }).trim();
const policy = JSON.parse(fs.readFileSync('infra/production/release-policy.json', 'utf8'));
const images = Object.fromEntries(imageNames.map(name => [name,
  execFileSync('docker', ['image', 'inspect', '--format', '{{.Id}}', `chanter-${name}:${commit}`], { encoding: 'utf8' }).trim()]));
fs.writeFileSync(output, JSON.stringify(validateRelease({ version: 1, commit, architecture, schemaEpoch: policy.schemaEpoch, images }), null, 2) + '\n');
