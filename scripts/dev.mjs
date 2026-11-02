import { spawn, execFile } from 'node:child_process';
import { readdir, stat } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { promisify } from 'node:util';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const windows = process.platform === 'win32';
const execFileAsync = promisify(execFile);
let server;
let stopping = false;
let polling = false;
let restartAt = 0;
let timer;

async function snapshot(relativePath) {
  const absolutePath = path.join(root, relativePath);
  let info;
  try {
    info = await stat(absolutePath, { bigint: true });
  } catch (error) {
    if (error.code === 'ENOENT') return `${relativePath}:missing`;
    throw error;
  }
  if (!info.isDirectory()) {
    return `${relativePath}:${info.mtimeNs}:${info.size}`;
  }
  const entries = await readdir(absolutePath);
  entries.sort();
  const parts = [];
  for (const entry of entries) {
    parts.push(await snapshot(path.join(relativePath, entry)));
  }
  return parts.join('\n');
}

async function sourceSnapshot() {
  const parts = [];
  for (const target of ['src/main', 'pom.xml', '.env']) {
    parts.push(await snapshot(target));
  }
  return parts.join('\n');
}

function startServer() {
  const command = windows ? 'cmd.exe' : './scripts/mvnw';
  const args = windows
    ? ['/d', '/c', 'scripts\\mvnw.cmd', '-B', 'spring-boot:run']
    : ['-B', 'spring-boot:run'];
  const child = spawn(command, args, {
    cwd: root,
    stdio: 'inherit',
    detached: !windows,
  });
  server = child;
  child.on('error', (error) => {
    console.error('[dev] 서버 실행 실패:', error.message);
    void shutdown(1);
  });
  child.on('exit', (code, signal) => {
    if (!stopping && server === child && !signal && code !== 0) {
      console.error(`[dev] 서버가 종료되었습니다 (${code}). 수정 후 저장하면 다시 실행합니다.`);
    }
  });
}

async function stopServer() {
  const child = server;
  server = undefined;
  if (!child?.pid || child.exitCode !== null || child.signalCode !== null) return;
  const exited = new Promise((resolve) => child.once('exit', resolve));
  if (windows) {
    await execFileAsync('taskkill', ['/pid', String(child.pid), '/t', '/f']);
    await exited;
    return;
  }
  process.kill(-child.pid, 'SIGTERM');
  const timeout = setTimeout(() => {
    // Maven과 JVM을 함께 종료해 이전 서버가 포트를 점유하지 않게 한다.
    try {
      process.kill(-child.pid, 'SIGKILL');
    } catch (error) {
      if (error.code !== 'ESRCH') console.error('[dev] 서버 강제 종료 실패:', error.message);
    }
  }, 5000);
  try {
    await exited;
  } finally {
    clearTimeout(timeout);
  }
}

async function shutdown(code) {
  if (stopping) return;
  stopping = true;
  clearInterval(timer);
  try {
    await stopServer();
  } catch (error) {
    console.error('[dev] 서버 종료 실패:', error.message);
    process.exitCode = 1;
  }
  process.exit(process.exitCode ?? code);
}

process.on('SIGINT', () => void shutdown(0));
process.on('SIGTERM', () => void shutdown(0));

try {
  let previous = await sourceSnapshot();
  console.log('[dev] Java·resources·pom.xml·.env 변경 감지 중 (Ctrl+C로 종료)');
  startServer();
  timer = setInterval(async () => {
    if (stopping || polling) return;
    polling = true;
    try {
      const current = await sourceSnapshot();
      if (stopping) return;
      if (current !== previous) {
        previous = current;
        restartAt = Date.now() + 300;
      }
      if (restartAt && Date.now() >= restartAt) {
        restartAt = 0;
        console.log('[dev] 변경 감지: 서버를 다시 컴파일하고 시작합니다.');
        await stopServer();
        if (!stopping) startServer();
      }
    } catch (error) {
      console.error('[dev] 변경 감지 또는 재시작 실패:', error.message);
      await shutdown(1);
    } finally {
      polling = false;
    }
  }, 250);
} catch (error) {
  console.error('[dev] 개발 서버 준비 실패:', error.message);
  await shutdown(1);
}
