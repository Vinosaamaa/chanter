import { createPublicKey } from 'node:crypto';
import { constants } from 'node:fs';
import { copyFile, lstat, mkdir, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { CompanionError } from './codex-app-server.mjs';
import { NativeState, ownCreatedEntry } from './native-state.mjs';

const sources = ['codex-account.mjs', 'codex-app-server.mjs', 'codex-launch.mjs', 'native-application.mjs',
  'native-console.mjs', 'native-install.mjs', 'native-provider.mjs', 'native-request.mjs', 'native-retention.mjs',
  'native-server.mjs', 'native-state.mjs', 'protect-state.ps1'];

export function validateConfiguration(value) {
  try {
    if (!value || Object.keys(value).sort().join(',') !== 'build,codexPath,distribution,origin,port,publicKey,version'
        || value.version !== 1 || value.build !== '0.1.0-dev' || value.distribution !== 'unsigned-developer' || typeof value.publicKey !== 'string'
        || value.publicKey.length > 4096 || !value.publicKey.startsWith('-----BEGIN PUBLIC KEY-----')
        || typeof value.codexPath !== 'string' || !path.isAbsolute(value.codexPath)
        || !Number.isInteger(value.port) || value.port < 1024 || value.port > 65535) throw new Error();
    const origin = new URL(value.origin);
    if (origin.protocol !== 'https:' || origin.origin !== value.origin) throw new Error();
    const publicKey = createPublicKey(value.publicKey);
    if (publicKey.asymmetricKeyType !== 'ed25519') throw new Error();
    return Object.freeze({ ...value });
  } catch { throw new CompanionError('INVALID_NATIVE_CONFIGURATION'); }
}

export async function readConfiguration(directory) {
  try {
    const target = path.join(directory, 'configuration.json');
    const stat = await lstat(target);
    if (!stat.isFile() || stat.isSymbolicLink() || stat.nlink !== 1 || stat.size > 8192
        || (process.platform !== 'win32' && (stat.mode & 0o077) !== 0)) throw new Error();
    return validateConfiguration(JSON.parse(await readFile(target, 'utf8')));
  } catch (error) { throw error instanceof CompanionError ? error : new CompanionError('NATIVE_NOT_CONFIGURED'); }
}

/** Source install only. The CLI verifies the operator-selected binary before calling this function. */
export async function installCompanion({ directory, origin, publicKey, codexPath, port = 43160 }) {
  const config = validateConfiguration({ version: 1, build: '0.1.0-dev', distribution: 'unsigned-developer', origin, publicKey, codexPath, port });
  const state = await NativeState.open(directory);
  try {
    const app = path.join(directory, 'app');
    try { await mkdir(app, { mode: 0o700 }); }
    catch { throw new CompanionError('NATIVE_ALREADY_INSTALLED'); }
    await writeFile(path.join(directory, '.gitignore'), '*\n', { flag: 'wx', mode: 0o600 });
    await mkdir(path.join(app, 'src'), { mode: 0o700 });
    const source = path.dirname(fileURLToPath(import.meta.url));
    for (const name of sources) await copyFile(path.join(source, name), path.join(app, 'src', name), constants.COPYFILE_EXCL);
    await copyFile(path.join(source, '..', 'cli.mjs'), path.join(app, 'cli.mjs'), constants.COPYFILE_EXCL);
    await copyFile(path.join(source, '..', 'README.md'), path.join(directory, 'README.md'), constants.COPYFILE_EXCL);
    await writeFile(path.join(directory, 'configuration.json'), JSON.stringify(config, null, 2) + '\n', { flag: 'wx', mode: 0o600 });
    const psQuote = (value) => "'" + value.replaceAll("'", "''") + "'";
    await writeFile(path.join(directory, 'start.ps1'), `# Unsigned Chanter developer installation.\n& ${psQuote(process.execPath)} (Join-Path $PSScriptRoot 'app/cli.mjs') start --directory $PSScriptRoot\nexit $LASTEXITCODE\n`, { flag: 'wx', mode: 0o600 });
    for (const entry of ['app', 'configuration.json', 'start.ps1']) await ownCreatedEntry(directory, entry);
    return { installationId: state.installationId, build: config.build, distribution: config.distribution, configured: true };
  } catch (error) { throw error instanceof CompanionError ? error : new CompanionError('NATIVE_INSTALL_INCOMPLETE'); }
  finally { state.close(); }
}
