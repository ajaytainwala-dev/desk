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
