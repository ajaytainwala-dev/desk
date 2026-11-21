/** GitHub repo that hosts Desk release artifacts. */
export const GITHUB_REPO = "ajaytainwala-dev/desk";
export const GITHUB_URL = `https://github.com/${GITHUB_REPO}`;

export type PlatformId = "windows" | "macos" | "linux";

export type DownloadKind =
  | "windows-gui"
  | "windows-cli"
  | "portable-zip"
  | "cli-setup"
  | "native"
  | "tui";

export interface ReleaseAsset {
  name: string;
  url: string;
  size: number;
}

/** Per-platform map of download kinds → release assets (from sync workflow / API). */
export type PlatformDownloads = Partial<Record<DownloadKind, ReleaseAsset>>;

export interface LatestRelease {
  tag: string;
  name: string;
  publishedAt: string | null;
  htmlUrl: string;
  assets: ReleaseAsset[];
  /** Pre-resolved download links by platform — preferred over pattern matching. */
  downloads: Record<PlatformId, PlatformDownloads>;
}

export interface DownloadOption {
  kind: DownloadKind;
  title: string;
  description: string;
  platforms: PlatformId[];
}

/** Product downloads we advertise when a matching release asset exists. */
export const DOWNLOAD_OPTIONS: DownloadOption[] = [
  {
    kind: "windows-gui",
    title: "Windows installer",
    description: "Guided GUI setup with Start Menu shortcuts and PATH option.",
    platforms: ["windows"],
  },
  {
    kind: "windows-cli",
    title: "Windows CLI setup",
    description: "PowerShell installer for terminals and scripts.",
    platforms: ["windows"],
  },
  {
    kind: "cli-setup",
    title: "CLI setup script",
    description: "One-liner install for macOS and Linux shells.",
    platforms: ["macos", "linux"],
  },
  {
    kind: "portable-zip",
    title: "Portable zip",
    description: "jlink runtime + Desk — unzip and run, no install required.",
    platforms: ["windows", "linux"],
  },
  {
    kind: "tui",
    title: "Desk TUI editor",
    description: "Standalone Rust Desk TUI editor binary (Ratatui).",
    platforms: ["windows", "macos", "linux"],
  },
  {
    kind: "native",
    title: "Native binary",
    description: "Standalone GraalVM native image for your OS.",
    platforms: ["windows", "macos", "linux"],
  },
];

function pick(
  assets: ReleaseAsset[],
  patterns: RegExp[],
): ReleaseAsset | null {
  for (const pattern of patterns) {
    const match = assets.find(
      (a) => pattern.test(a.name) && !a.name.endsWith(".sha256"),
    );
    if (match) return match;
  }
  return null;
}

/** Build the same platform→kind map the Sync release links workflow writes. */
export function buildDownloadsMap(
  assets: ReleaseAsset[],
): Record<PlatformId, PlatformDownloads> {
  const downloads: Record<PlatformId, PlatformDownloads> = {
    windows: {
      "windows-gui": pick(assets, [/^Desk-Setup-.*\.exe$/i]) ?? undefined,
      "windows-cli":
        pick(assets, [/^desk-cli-setup-windows\.ps1$/i]) ?? undefined,
      "portable-zip":
        pick(assets, [/^desk-portable-windows\.zip$/i]) ?? undefined,
      native: pick(assets, [/^desk-native-windows(\.exe)?$/i]) ?? undefined,
      tui: pick(assets, [/^desk-tui-windows(\.exe)?$/i]) ?? undefined,
    },
    macos: {
      "cli-setup": pick(assets, [/^desk-cli-setup\.sh$/i]) ?? undefined,
      // Only a real macOS zip — never fall back to the Linux portable alias
      "portable-zip":
        pick(assets, [/^desk-portable-macos\.zip$/i]) ?? undefined,
      native: pick(assets, [/^desk-native-macos$/i]) ?? undefined,
      tui: pick(assets, [/^desk-tui-macos$/i]) ?? undefined,
    },
    linux: {
      "cli-setup": pick(assets, [/^desk-cli-setup\.sh$/i]) ?? undefined,
      "portable-zip":
        pick(assets, [
          /^desk-portable-linux\.zip$/i,
          /^desk-portable\.zip$/i,
        ]) ?? undefined,
      native: pick(assets, [/^desk-native-linux$/i]) ?? undefined,
      tui: pick(assets, [/^desk-tui-linux$/i]) ?? undefined,
    },
  };

  for (const platform of Object.keys(downloads) as PlatformId[]) {
    for (const kind of Object.keys(downloads[platform]) as DownloadKind[]) {
      if (!downloads[platform][kind]) delete downloads[platform][kind];
    }
  }
  return downloads;
}

export function detectPlatform(): PlatformId {
  const ua = navigator.userAgent.toLowerCase();
  if (ua.includes("win")) return "windows";
  if (ua.includes("mac")) return "macos";
  return "linux";
}

export function formatBytes(bytes: number): string {
  if (!bytes || bytes <= 0) return "";
  const units = ["B", "KB", "MB", "GB"];
  let n = bytes;
  let i = 0;
  while (n >= 1024 && i < units.length - 1) {
    n /= 1024;
    i += 1;
  }
  return `${n.toFixed(i === 0 ? 0 : 1)} ${units[i]}`;
}

export function resolveDownload(
  release: LatestRelease | null,
  kind: DownloadKind,
  platform: PlatformId,
): ReleaseAsset | null {
  if (!release) return null;
  return release.downloads[platform]?.[kind] ?? null;
}

/** Options that have a real GitHub release asset for this platform. */
export function availableDownloads(
  release: LatestRelease | null,
  platform: PlatformId,
): DownloadOption[] {
  return DOWNLOAD_OPTIONS.filter((o) => {
    if (!o.platforms.includes(platform)) return false;
    if (!release) return o.kind !== "native"; // hide empty native while loading
    return Boolean(resolveDownload(release, o.kind, platform));
  });
}

export function latestAssetUrl(assetName: string): string {
  return `${GITHUB_URL}/releases/latest/download/${assetName}`;
}

interface GhAsset {
  name: string;
  browser_download_url: string;
  size: number;
}

interface GhRelease {
  tag_name: string;
  name: string | null;
  published_at: string | null;
  html_url: string;
  assets: GhAsset[];
}

interface BundledRelease {
  tag: string | null;
  name: string | null;
  publishedAt: string | null;
  htmlUrl: string;
  assets: ReleaseAsset[];
  downloads?: Record<string, PlatformDownloads>;
}

function normalizeRelease(
  tag: string,
  name: string,
  publishedAt: string | null,
  htmlUrl: string,
  assets: ReleaseAsset[],
  downloads?: Record<string, PlatformDownloads>,
): LatestRelease {
  const clean = assets.filter((a) => !a.name.endsWith(".sha256"));
  const map =
    downloads && Object.keys(downloads).length > 0
      ? {
          windows: downloads.windows ?? {},
          macos: downloads.macos ?? {},
          linux: downloads.linux ?? {},
        }
      : buildDownloadsMap(clean);
  // Re-build macos portable if bundled map incorrectly pointed at linux zip
  const rebuilt = buildDownloadsMap(clean);
  return {
    tag,
    name,
    publishedAt,
    htmlUrl,
    assets: clean,
    downloads: {
      windows: { ...map.windows, ...rebuilt.windows },
      macos: rebuilt.macos, // always use strict macos rules
      linux: { ...map.linux, ...rebuilt.linux },
    },
  };
}

/** Prefer the Actions-synced snapshot when the live API is unavailable. */
async function fetchBundledRelease(
  signal?: AbortSignal,
): Promise<LatestRelease | null> {
  try {
    const res = await fetch(`${import.meta.env.BASE_URL}latest-release.json`, {
      signal,
      headers: { Accept: "application/json" },
    });
    if (!res.ok) return null;
    const data = (await res.json()) as BundledRelease;
    if (!data.tag || !data.assets?.length) return null;
    return normalizeRelease(
      data.tag,
      data.name || data.tag,
      data.publishedAt,
      data.htmlUrl,
      data.assets,
      data.downloads,
    );
  } catch {
    return null;
  }
}

/**
 * Fetch the newest published GitHub release. Falls back to
 * `public/latest-release.json` when the API is rate-limited or offline.
 */
export async function fetchLatestRelease(
  signal?: AbortSignal,
): Promise<LatestRelease> {
  const url = `https://api.github.com/repos/${GITHUB_REPO}/releases/latest`;
  try {
    const res = await fetch(url, {
      signal,
      headers: {
        Accept: "application/vnd.github+json",
      },
    });

    if (res.status === 404) {
      const bundled = await fetchBundledRelease(signal);
      if (bundled) return bundled;
      throw new Error("No published release yet");
    }
    if (!res.ok) {
      throw new Error(`GitHub API ${res.status}`);
    }

    const data = (await res.json()) as GhRelease;
    const assets = (data.assets ?? []).map((a) => ({
      name: a.name,
      url: a.browser_download_url,
      size: a.size,
    }));
    return normalizeRelease(
      data.tag_name,
      data.name || data.tag_name,
      data.published_at,
      data.html_url,
      assets,
    );
  } catch (err) {
    if (signal?.aborted) throw err;
    const bundled = await fetchBundledRelease(signal);
    if (bundled) return bundled;
    throw err instanceof Error ? err : new Error("Could not load releases");
  }
}

export function releasesPageUrl(): string {
  return `${GITHUB_URL}/releases`;
}

export function latestReleasePageUrl(tag?: string): string {
  if (tag) return `${GITHUB_URL}/releases/tag/${tag}`;
  return releasesPageUrl();
}
