# Desk website

Single-page marketing site for Desk. Download buttons load assets from the
**latest GitHub release** (`/repos/.../releases/latest`) on every visit, so
publishing a new tag updates the links without redeploying this site.

## Develop

```bash
cd website
npm install
npm run dev
```

## Build

```bash
npm run build
```

Output: `website/dist`.

## GitHub Pages

The **Deploy website (GitHub Pages)** workflow (`.github/workflows/pages.yml`) builds and deploys on pushes to `main` that touch `website/**`, or via manual dispatch.

Enable once: repo **Settings → Pages → Source: GitHub Actions**.
