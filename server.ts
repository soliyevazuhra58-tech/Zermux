import crypto from 'crypto';
import express from 'express';
import fs from 'fs';
import path from 'path';
import {fileURLToPath} from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const app = express();
const PORT = 3000;

const DOWNLOADS_DIR = path.resolve(__dirname, 'public/downloads');
const ANDROID_DIR = path.resolve(__dirname, 'android');

const ARTIFACT_MANIFEST = [
  {
    id: 'release-apk',
    filename: 'app-release.apk',
    title: 'Signed Release APK',
    architecture: 'arm64-v8a',
    description: 'R8-minified, resource-shrunk, v1/v2/v3 signed release build ready for installation on Android 8.0+ ARM64 devices.',
  },
  {
    id: 'debug-apk',
    filename: 'app-debug.apk',
    title: 'Debug APK',
    architecture: 'arm64-v8a',
    description: 'Unminified debug build with full symbol tables and debuggable manifest flag for ADB inspection.',
  },
  {
    id: 'release-aab',
    filename: 'app-release.aab',
    title: 'Android App Bundle (AAB)',
    architecture: 'arm64-v8a',
    description: 'Signed release App Bundle formatted for Play Console distribution.',
  },
  {
    id: 'source-zip',
    filename: 'kestrel-android-source.zip',
    title: 'Full Android Studio Source Archive',
    architecture: 'Kotlin + Compose + C/C++ JNI',
    description: 'Complete Gradle project, CMake native PTY/utils sources, Glance widgets, Room DB, and release build configuration.',
  },
];

function computeSha256(filePath: string): string {
  const hash = crypto.createHash('sha256');
  hash.update(fs.readFileSync(filePath));
  return hash.digest('hex');
}

app.get('/api/artifacts', (_req, res) => {
  const artifacts = ARTIFACT_MANIFEST.map((item) => {
    const filePath = path.join(DOWNLOADS_DIR, item.filename);
    const exists = fs.existsSync(filePath);
    const sizeBytes = exists ? fs.statSync(filePath).size : 0;
    const sha256 = exists ? computeSha256(filePath) : '';
    return {
      ...item,
      available: exists,
      sizeBytes,
      sha256,
      downloadUrl: `/downloads/${item.filename}`,
    };
  });

  const verificationPath = path.join(DOWNLOADS_DIR, 'build-verification.json');
  let verification: Record<string, unknown> | null = null;
  if (fs.existsSync(verificationPath)) {
    try {
      verification = JSON.parse(fs.readFileSync(verificationPath, 'utf-8'));
    } catch {
      verification = null;
    }
  }
  res.json({artifacts, verification});
});

app.get('/api/source-tree', (_req, res) => {
  const files: {path: string; sizeBytes: number}[] = [];
  const skipDirs = new Set(['.gradle', 'build', '.cxx', 'keystore']);

  function walk(dir: string) {
    if (!fs.existsSync(dir)) return;
    for (const entry of fs.readdirSync(dir, {withFileTypes: true})) {
      if (skipDirs.has(entry.name)) continue;
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) walk(full);
      else files.push({path: path.relative(ANDROID_DIR, full), sizeBytes: fs.statSync(full).size});
    }
  }

  walk(ANDROID_DIR);
  files.sort((a, b) => a.path.localeCompare(b.path));
  res.json({files});
});

app.get('/api/source-file', (req, res) => {
  const relPath = String(req.query.path || '');
  const resolved = path.resolve(ANDROID_DIR, relPath);
  if (!resolved.startsWith(ANDROID_DIR + path.sep) || !fs.existsSync(resolved) || fs.statSync(resolved).isDirectory()) {
    res.status(404).json({error: 'File not found'});
    return;
  }
  res.json({path: relPath, content: fs.readFileSync(resolved, 'utf-8')});
});

app.get('/downloads/:filename', (req, res) => {
  const safeName = path.basename(req.params.filename);
  const filePath = path.join(DOWNLOADS_DIR, safeName);
  if (!fs.existsSync(filePath)) {
    res.status(404).send('Artifact not yet built');
    return;
  }
  res.download(filePath, safeName);
});

async function start() {
  const distDir = path.resolve(__dirname, 'dist');
  if (process.env.NODE_ENV === 'production' && fs.existsSync(path.join(distDir, 'index.html'))) {
    app.use(express.static(distDir));
    app.get('*', (_req, res) => {
      res.sendFile(path.join(distDir, 'index.html'));
    });
  } else {
    const {createServer: createViteServer} = await import('vite');
    const vite = await createViteServer({
      server: {middlewareMode: true},
      appType: 'spa',
    });
    app.use(vite.middlewares);
  }

  app.listen(PORT, '0.0.0.0', () => {
    console.log(`Kestrel Android Artifact Server listening on http://0.0.0.0:${PORT}`);
  });
}

start();
