# Design Diagrams

Companion to `../../DOCUMENTATION.md` (pages, forms, field lists). This
folder holds the visual diagrams: one data model, one diagram per business
process.

Each diagram is available two ways in its `.md` file:
- **A static SVG** (`images/*.svg`), rendered ahead of time — vector, so it
  stays crisp at any zoom level (unlike a raster PNG). Displays everywhere:
  GitHub, plain Markdown viewers, Word/PDF export, no plugin needed.
- **The [Mermaid](https://mermaid.js.org/) source**, in a collapsed
  `<details>` block underneath — edit it and paste into
  [mermaid.live](https://mermaid.live) to tweak and re-export, or let it
  render live in tools that support Mermaid natively (GitHub, GitLab, VS
  Code with the "Markdown Preview Mermaid Support" extension).

To regenerate an SVG after editing its source, base64-encode the Mermaid
block and fetch it from the free rendering service:
```bash
B64=$(base64 -w0 diagram.mmd | tr '+/' '-_' | tr -d '=')
curl -s "https://mermaid.ink/svg/${B64}?backgroundColor=white" -o images/diagram.svg
```

## Contents

- [`data-model.md`](data-model.md) — entity-relationship diagram of every table
- [`process-signup-onboarding.md`](process-signup-onboarding.md)
- [`process-trial-management.md`](process-trial-management.md)
- [`process-billing-subscription.md`](process-billing-subscription.md)
- [`process-plan-changes.md`](process-plan-changes.md)
- [`process-account-lifecycle.md`](process-account-lifecycle.md)
- [`process-sftp-provisioning.md`](process-sftp-provisioning.md)
- [`process-signup-abuse-prevention.md`](process-signup-abuse-prevention.md)
- [`process-admin-operations.md`](process-admin-operations.md)

Each process file numbering matches §3 of `DOCUMENTATION.md` (3.1–3.8), so
the prose description and the diagram can be read side by side.
