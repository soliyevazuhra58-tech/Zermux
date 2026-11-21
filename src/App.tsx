import {useEffect, useMemo, useState} from 'react';
import {Download, FileCode2, FolderTree, Moon, ShieldCheck, Sun, Terminal} from 'lucide-react';

interface ArtifactItem {
  id: string;
  filename: string;
  title: string;
  architecture: string;
  description: string;
  available: boolean;
  sizeBytes: number;
  sha256: string;
  downloadUrl: string;
}

interface VerificationInfo {
  applicationId?: string;
  versionName?: string;
  versionCode?: number;
  minSdk?: number;
  targetSdk?: number;
  abi?: string;
  unitTestsPassed?: boolean;
  apksignerOutput?: string;
  aapt2BadgingSummary?: string;
  deviceTested?: boolean;
  deviceTestingNote?: string;
  builtAt?: string;
}

interface SourceFileEntry {
  path: string;
  sizeBytes: number;
}

function formatBytes(bytes: number): string {
  if (bytes <= 0) return '0 B';
  const mb = bytes / (1024 * 1024);
  if (mb >= 1) return `${mb.toFixed(2)} MB`;
  const kb = bytes / 1024;
  return `${kb.toFixed(1)} KB`;
}

export default function App() {
  const [dark, setDark] = useState(true);
  const [artifacts, setArtifacts] = useState<ArtifactItem[]>([]);
  const [verification, setVerification] = useState<VerificationInfo | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [sourceFiles, setSourceFiles] = useState<SourceFileEntry[]>([]);
  const [fileFilter, setFileFilter] = useState('');
  const [selectedPath, setSelectedPath] = useState<string>('app/build.gradle.kts');
  const [selectedContent, setSelectedContent] = useState<string>('');
  const [loadingSource, setLoadingSource] = useState(false);

  useEffect(() => {
    const root = document.documentElement;
    if (dark) root.classList.add('dark');
    else root.classList.remove('dark');
  }, [dark]);

  useEffect(() => {
    Promise.all([
      fetch('/api/artifacts').then((r) => r.json()),
      fetch('/api/source-tree').then((r) => r.json()),
    ])
      .then(([artData, treeData]) => {
        setArtifacts(artData.artifacts || []);
        setVerification(artData.verification || null);
        setSourceFiles(treeData.files || []);
        setLoading(false);
      })
      .catch((err) => {
        setError(err instanceof Error ? err.message : 'Failed to load build artifacts');
        setLoading(false);
      });
  }, []);

  useEffect(() => {
    if (!selectedPath) return;
    setLoadingSource(true);
    fetch(`/api/source-file?path=${encodeURIComponent(selectedPath)}`)
      .then((r) => r.json())
      .then((data) => {
        setSelectedContent(data.content || '');
        setLoadingSource(false);
      })
      .catch(() => {
        setSelectedContent('Unable to load source file.');
        setLoadingSource(false);
      });
  }, [selectedPath]);

  const filteredFiles = useMemo(() => {
    const q = fileFilter.trim().toLowerCase();
    if (!q) return sourceFiles;
    return sourceFiles.filter((f) => f.path.toLowerCase().includes(q));
  }, [sourceFiles, fileFilter]);

  const primaryApk = artifacts.find((a) => a.id === 'release-apk');

  return (
    <div className="min-h-screen bg-slate-50 text-slate-900 dark:bg-[#0D1117] dark:text-[#E6EDF3]">
      <header className="border-b border-slate-200 bg-white dark:border-[#242D3A] dark:bg-[#131820]">
        <div className="mx-auto flex max-w-[1280px] items-center justify-between gap-8 px-6 py-4">
          <a href="#artifacts" className="text-lg font-bold tracking-tight whitespace-nowrap shrink-0">
            Kestrel Terminal
          </a>
          <nav className="hidden md:flex items-center gap-6 text-sm font-medium text-slate-600 dark:text-slate-400">
            <a href="#artifacts" className="hover:text-slate-900 dark:hover:text-white transition-colors whitespace-nowrap shrink-0">Builds</a>
            <a href="#architecture" className="hover:text-slate-900 dark:hover:text-white transition-colors whitespace-nowrap shrink-0">Architecture</a>
            <a href="#source" className="hover:text-slate-900 dark:hover:text-white transition-colors whitespace-nowrap shrink-0">Source Code</a>
            <a href="#instructions" className="hover:text-slate-900 dark:hover:text-white transition-colors whitespace-nowrap shrink-0">Build Guide</a>
          </nav>
          <div className="flex items-center gap-3 shrink-0">
            <button
              type="button"
              onClick={() => setDark((d) => !d)}
              aria-label="Toggle color theme"
              className="rounded-lg border border-slate-200 p-2 text-slate-700 hover:bg-slate-100 dark:border-[#2A3340] dark:text-slate-300 dark:hover:bg-[#1B222D] transition-colors"
            >
              {dark ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
            </button>
            {primaryApk?.available ? (
              <a
                href={primaryApk.downloadUrl}
                download={primaryApk.filename}
                className="inline-flex items-center gap-2 rounded-lg bg-[#E5A00D] px-4 py-2 text-xs font-semibold text-[#0B0E13] hover:bg-[#D4920A] transition-colors whitespace-nowrap shrink-0"
              >
                <Download className="h-3.5 w-3.5" />
                Download Release APK
              </a>
            ) : (
              <a
                href="#artifacts"
                className="inline-flex items-center gap-2 rounded-lg bg-slate-900 px-4 py-2 text-xs font-semibold text-white dark:bg-[#E5A00D] dark:text-[#0B0E13] whitespace-nowrap shrink-0"
              >
                View Artifacts
              </a>
            )}
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-[1280px] px-6 py-10 space-y-12">
        <section id="artifacts" className="space-y-6">
          <div className="max-w-2xl space-y-2">
            <p className="text-xs font-medium text-slate-500 dark:text-slate-400">
              com.developer.terminal · Version 1.0.0 · Android 8.0+ (API 26–35) · ARM64
            </p>
            <h1 className="text-3xl font-bold tracking-tight text-balance sm:text-4xl">
              Native Android Terminal, Code Editor &amp; ARM64 Workspace
            </h1>
            <p className="text-sm leading-relaxed text-slate-600 dark:text-slate-400">
              Compiled with Kotlin, Jetpack Compose, Material 3, Room SQLite, Jetpack Glance widgets, JGit, JSch, and an NDK C/C++ POSIX pseudoterminal (PTY) engine.
            </p>
          </div>

          {error && (
            <div className="rounded-xl border border-red-300 bg-red-50 p-4 text-sm text-red-800 dark:border-red-900/50 dark:bg-red-950/30 dark:text-red-300">
              {error}
            </div>
          )}

          <div className="overflow-hidden rounded-xl border border-slate-200 bg-white dark:border-[#242D3A] dark:bg-[#131820]">
            <div className="border-b border-slate-200 px-5 py-3.5 dark:border-[#242D3A]">
              <h2 className="text-sm font-semibold">Compiled Android Deliverables</h2>
            </div>

            {loading ? (
              <div className="p-6 text-sm text-slate-500 dark:text-slate-400">Loading compiled build artifacts...</div>
            ) : (
              <div className="divide-y divide-slate-200 dark:divide-[#242D3A]">
                {artifacts.map((art) => (
                  <div key={art.id} className="flex flex-col gap-3 px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
                    <div className="space-y-1 min-w-0">
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="text-sm font-semibold">{art.title}</span>
                        <span className="text-xs text-slate-500 dark:text-slate-400">·</span>
                        <span className="font-mono text-xs text-slate-600 dark:text-slate-400">{art.filename}</span>
                        <span className="text-xs text-slate-500 dark:text-slate-400">·</span>
                        <span className="font-mono text-xs text-slate-500 dark:text-slate-400">{art.architecture}</span>
                      </div>
                      <p className="text-xs text-slate-600 dark:text-slate-400">{art.description}</p>
                      {art.available && (
                        <p className="font-mono text-[11px] text-slate-500 dark:text-slate-500 break-all">
                          SHA-256: {art.sha256}
                        </p>
                      )}
                    </div>
                    <div className="flex items-center gap-4 shrink-0">
                      <span className="font-mono text-xs text-slate-600 dark:text-slate-300 tabular-nums">
                        {art.available ? formatBytes(art.sizeBytes) : 'Pending'}
                      </span>
                      {art.available ? (
                        <a
                          href={art.downloadUrl}
                          download={art.filename}
                          className="inline-flex items-center gap-1.5 rounded-lg border border-slate-300 bg-slate-100 px-3.5 py-2 text-xs font-medium text-slate-900 hover:bg-slate-200 dark:border-[#2E3949] dark:bg-[#1B222D] dark:text-[#E6EDF3] dark:hover:bg-[#252E3C] transition-colors whitespace-nowrap shrink-0"
                        >
                          <Download className="h-3.5 w-3.5" />
                          Download
                        </a>
                      ) : (
                        <span className="text-xs text-slate-400">Not built</span>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>

          {verification && (
            <div className="rounded-xl border border-slate-200 bg-white p-5 space-y-3 dark:border-[#242D3A] dark:bg-[#131820]">
              <div className="flex items-center gap-2">
                <ShieldCheck className="h-4 w-4 text-[#E5A00D]" />
                <h3 className="text-sm font-semibold">Build &amp; Signature Verification</h3>
              </div>
              <div className="grid gap-3 text-xs sm:grid-cols-2 lg:grid-cols-4">
                <div>
                  <span className="text-slate-500 dark:text-slate-400">Unit Tests (JVM)</span>
                  <p className="font-mono font-medium mt-0.5">{verification.unitTestsPassed ? 'Passed' : 'Not run'}</p>
                </div>
                <div>
                  <span className="text-slate-500 dark:text-slate-400">Target / Min SDK</span>
                  <p className="font-mono font-medium mt-0.5">API {verification.minSdk} – {verification.targetSdk} ({verification.abi})</p>
                </div>
                <div>
                  <span className="text-slate-500 dark:text-slate-400">APK Signature</span>
                  <p className="font-mono font-medium mt-0.5">{verification.apksignerOutput ? 'Verified (v1/v2/v3)' : 'Pending'}</p>
                </div>
                <div>
                  <span className="text-slate-500 dark:text-slate-400">On-Device Execution</span>
                  <p className="font-mono font-medium mt-0.5">{verification.deviceTested ? 'Tested on device' : 'Requires physical ARM64 device'}</p>
                </div>
              </div>
              {verification.deviceTestingNote && (
                <p className="text-xs text-slate-600 dark:text-slate-400">{verification.deviceTestingNote}</p>
              )}
              {verification.aapt2BadgingSummary && (
                <pre className="overflow-x-auto rounded-lg border border-slate-200 bg-slate-50 p-3 font-mono text-[11px] leading-relaxed text-slate-700 dark:border-[#242D3A] dark:bg-[#0D1117] dark:text-slate-300">
                  {verification.aapt2BadgingSummary}
                </pre>
              )}
            </div>
          )}
        </section>

        <section id="architecture" className="space-y-4">
          <h2 className="text-lg font-semibold">Implemented Native Subsystems</h2>
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
            {[
              {title: 'JNI POSIX PTY Engine', detail: 'C++ openpty/fork/setsid/ioctl(TIOCSWINSZ) bridge with VT100/xterm-256color parser, alternate screen buffer, scrollback search, split panes, and NDK ARM64 libkestrel_utils.so.'},
              {title: 'ARM64 Package Manager', detail: 'Downloads live Alpine Linux aarch64 APKINDEX.tar.gz repositories, resolves dependencies, tracks progress, and stages packages into $PREFIX with Room install logs.'},
              {title: 'Code Editor & File Manager', detail: 'Multi-tab syntax-highlighted editor with line numbers and auto-indent, paired with internal/SAF storage browsing, recursive search, and ZIP compression/extraction.'},
              {title: 'Real Android Device APIs', detail: 'Live Build, ActivityManager RAM, StatFs storage, BatteryManager temp/voltage, CPU clocks, ConnectivityManager/WifiManager, Permissions dashboard, and opt-in su check.'},
              {title: 'Developer Utilities', detail: 'JGit (init/clone/status/commit) + GitHub API, JSch SSH client, OkHttp tester, JSON validator, SQLite browser, embedded ServerSocket HTTP server, and task scheduler.'},
              {title: '7 Jetpack Glance Widgets', detail: 'Resizable home-screen widgets for Terminal Quick Launch, CPU & RAM, Battery, Storage, Running Tasks, Quick Commands, and Network Status.'},
            ].map((mod) => (
              <div key={mod.title} className="rounded-xl border border-slate-200 bg-white p-5 space-y-2 dark:border-[#242D3A] dark:bg-[#131820]">
                <div className="flex items-center gap-2">
                  <Terminal className="h-4 w-4 text-[#E5A00D]" />
                  <h3 className="text-sm font-semibold">{mod.title}</h3>
                </div>
                <p className="text-xs leading-relaxed text-slate-600 dark:text-slate-400">{mod.detail}</p>
              </div>
            ))}
          </div>
        </section>

        <section id="source" className="space-y-4">
          <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
            <div className="flex items-center gap-2">
              <FolderTree className="h-4 w-4 text-[#E5A00D]" />
              <h2 className="text-lg font-semibold">Android Project Source Browser</h2>
            </div>
            <span className="font-mono text-xs text-slate-500 dark:text-slate-400">{sourceFiles.length} source files in android/</span>
          </div>
          <div className="grid gap-4 lg:grid-cols-12 rounded-xl border border-slate-200 bg-white overflow-hidden dark:border-[#242D3A] dark:bg-[#131820]">
            <div className="lg:col-span-4 border-b lg:border-b-0 lg:border-r border-slate-200 dark:border-[#242D3A] flex flex-col max-h-[520px]">
              <div className="p-3 border-b border-slate-200 dark:border-[#242D3A]">
                <input
                  type="text"
                  value={fileFilter}
                  onChange={(e) => setFileFilter(e.target.value)}
                  placeholder="Filter files (e.g. .kt, .cpp, xml)..."
                  className="w-full rounded-lg border border-slate-200 bg-slate-50 px-3 py-1.5 text-xs outline-none focus:border-[#E5A00D] dark:border-[#2A3340] dark:bg-[#0D1117]"
                />
              </div>
              <div className="overflow-y-auto divide-y divide-slate-100 dark:divide-[#1C232E]">
                {filteredFiles.map((f) => {
                  const active = f.path === selectedPath;
                  return (
                    <button
                      key={f.path}
                      type="button"
                      onClick={() => setSelectedPath(f.path)}
                      className={`w-full text-left px-3.5 py-2 text-xs font-mono flex items-center justify-between gap-2 transition-colors ${
                        active
                          ? 'bg-[#E5A00D]/15 text-[#E5A00D] font-medium'
                          : 'hover:bg-slate-50 dark:hover:bg-[#19202B] text-slate-700 dark:text-slate-300'
                      }`}
                    >
                      <span className="truncate">{f.path}</span>
                      <span className="text-[10px] text-slate-400 shrink-0 tabular-nums">{formatBytes(f.sizeBytes)}</span>
                    </button>
                  );
                })}
              </div>
            </div>
            <div className="lg:col-span-8 flex flex-col max-h-[520px]">
              <div className="flex items-center justify-between border-b border-slate-200 px-4 py-2.5 dark:border-[#242D3A]">
                <div className="flex items-center gap-2 min-w-0">
                  <FileCode2 className="h-3.5 w-3.5 text-slate-400 shrink-0" />
                  <span className="font-mono text-xs truncate">{selectedPath}</span>
                </div>
              </div>
              <pre className="flex-1 overflow-auto p-4 font-mono text-xs leading-relaxed text-slate-800 dark:text-slate-200 bg-slate-50 dark:bg-[#0D1117]">
                {loadingSource ? 'Loading file...' : selectedContent}
              </pre>
            </div>
          </div>
        </section>

        <section id="instructions" className="space-y-4">
          <h2 className="text-lg font-semibold">Installation &amp; Local Build Instructions</h2>
          <div className="rounded-xl border border-slate-200 bg-white p-5 space-y-4 dark:border-[#242D3A] dark:bg-[#131820]">
            <div className="space-y-1.5">
              <h3 className="text-sm font-semibold">Install Prebuilt Signed APK via ADB</h3>
              <pre className="overflow-x-auto rounded-lg border border-slate-200 bg-slate-50 p-3 font-mono text-xs dark:border-[#242D3A] dark:bg-[#0D1117]">adb install -r app-release.apk</pre>
            </div>
            <div className="space-y-1.5">
              <h3 className="text-sm font-semibold">Rebuild from Source (Android SDK 35 + NDK 27 + JDK 17)</h3>
              <pre className="overflow-x-auto rounded-lg border border-slate-200 bg-slate-50 p-3 font-mono text-xs dark:border-[#242D3A] dark:bg-[#0D1117]">
{`unzip kestrel-android-source.zip -d kestrel-android
cd kestrel-android
./gradlew testDebugUnitTest assembleDebug assembleRelease bundleRelease`}
              </pre>
            </div>
          </div>
        </section>
      </main>
    </div>
  );
}
