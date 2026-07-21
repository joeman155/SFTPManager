# 3.6 SFTP Provisioning (ProFTPD integration)

See `DOCUMENTATION.md` §3.6 and `PROFTPD-SETUP.md` for full server setup.

```mermaid
flowchart TD
    A["Portal or Admin: create/edit Service Account"] --> B["Enter username, auth type,<br/>password or public key, permissions"]
    B --> C{Username already used anywhere in the system?}
    C -->|Yes| D["409 Conflict — rejected<br/>(usernames are GLOBALLY unique)"]
    C -->|No| E[SftpCredentialService.applyCredentials]
    E --> F{Auth type}
    F -->|Password| G["Bcrypt-hash the password<br/>hash stored — plaintext never kept,<br/>never returned by the API"]
    F -->|Public Key| H["Store OpenSSH-format key AS-IS<br/>+ auto-generate an RFC 4716 copy"]
    G --> I[Save SftpServiceAccount row]
    H --> I

    I --> J["App startup: DataInitialiser<br/>(re)creates 3 Postgres views + re-grants access"]
    J --> K["proftpd_users:<br/>login table, filtered to ENABLED accounts<br/>of owners who are NOT deactivated/locked/closed"]
    J --> L[proftpd_allowed_ips: IP whitelist per username]
    J --> M["proftpd_groups: sftpread / sftpwrite / sftpdelete<br/>synthetic groups derived from the permissions column"]

    N[Customer connects via SFTP client] --> O["ProFTPD (mod_sql_postgres) queries proftpd_users"]
    O --> P{Row returned?}
    P -->|No| Q["Login refused —<br/>disabled account OR owner deactivated/locked/closed"]
    P -->|Yes| R{Auth method offered}
    R -->|Password| S[crypt/bcrypt verify against passwd column]
    R -->|Public key| T[Match against ssh_key RFC4716 column]
    S --> U[Authenticated]
    T --> U

    U --> V["Chrooted into /srv/sftp/svc-id-<br/>SHARED per SFTP Service — every account<br/>under one service sees the same files"]
    V --> W[mod_sql group lookup against proftpd_groups]
    W --> X["&lt;Limit&gt; blocks enforce READ/WRITE/DELETE<br/>per operation — filesystem stays uniform uid/gid,<br/>permissions enforced at the protocol layer"]
```

**Key points**
- Every admin kill-switch (lock, deactivate, close) applies to **real SFTP
  logins automatically** — no separate wiring, because `proftpd_users`
  filters on those same flags.
- Permissions are enforced by ProFTPD `<Limit>` blocks reading synthetic SQL
  groups, **not** by Linux file ownership — every account runs as the same
  uid/gid so files are freely shared within one service.
- View grants are **re-applied on every app restart** (the dev schema is
  rebuilt via `ddl-auto=create`, which would otherwise silently drop them —
  this was a real incident during setup; see `PROFTPD-SETUP.md`
  troubleshooting table).
