# ProFTPD + PostgreSQL — SFTP host setup (Option 2)

This connects a Linux SFTP host to the SFTP Manager database so customer
accounts authenticate with **password AND public key**, straight from the
`sftp_service_account` table. ProFTPD's `mod_sftp` speaks the SSH/SFTP
protocol itself (OpenSSH is not involved) and `mod_sql_postgres` does the
lookups.

## What the app now provides (already implemented)

- **Bcrypt password hashes** — SFTP account passwords are stored bcrypt-hashed
  (`$2a$...`), which `crypt(3)` verifies on any modern distro (libxcrypt:
  Ubuntu 20.04+, Debian 10+, RHEL 8+). Plaintext is never stored, and hashes
  are never returned by the app's APIs.
- **RFC 4716 public keys** — `public_key_rfc4716` column, auto-converted from
  the OpenSSH-format key the user pastes (mod_sftp requires RFC 4716).
- **Globally unique usernames** — enforced at creation/edit (one Linux host
  serves every service, so `backup` can only exist once).
- **`proftpd_users` view** — one row per *loginable* account: enabled, and the
  owning customer is not deactivated / locked / closed. Every kill-switch in
  the admin screen applies to SFTP logins on the next connection.
  Columns: `userid, passwd, uid, gid, homedir, shell, ssh_key, permissions`.
  Home dirs are `/srv/sftp/svc<serviceId>` — **shared per service**, so every
  account under the same SFTP Service sees the same files (they all run as
  uid/gid 2001, so there are no ownership conflicts between them).
- **`proftpd_allowed_ips` view** — `(name, allowed)` pairs from the IP
  whitelist, per username.

Note: existing plaintext passwords in old rows won't work — accounts need
their password (re)saved once so it gets hashed. With `ddl-auto=create`
wiping data on restart this is moot in dev.

## Steps on the Linux SFTP host

### 1. Install

```bash
sudo apt-get install proftpd-core proftpd-mod-pgsql proftpd-mod-crypto
# (proftpd-mod-crypto provides mod_sftp on Debian/Ubuntu)
```

### 1b. Enable the required modules — IMPORTANT

Installing the packages is not enough: the modules must be loaded in
`/etc/proftpd/modules.conf`. Make sure these four lines exist and are
**uncommented**:

```apacheconf
LoadModule mod_sql.c
LoadModule mod_sql_postgres.c
LoadModule mod_sftp.c
LoadModule mod_sftp_sql.c
```

(`mod_sftp_sql` is what makes `SFTPAuthorizedUserKeys sql:/...` work.)

This step matters because the whole vhost below is wrapped in
`<IfModule mod_sftp.c>` — if the module isn't loaded, ProFTPD starts fine
but **silently ignores the entire block**, and nothing listens on 2222.

### 2. Create the shared system user and directory root

All virtual users map to one unprivileged system account:

```bash
sudo groupadd -g 2001 sftpusers
sudo useradd  -u 2001 -g 2001 -d /srv/sftp -s /usr/sbin/nologin sftpuser
sudo mkdir -p /srv/sftp && sudo chown sftpuser:sftpusers /srv/sftp
```

### 3. Generate host keys

```bash
sudo ssh-keygen -t rsa     -b 4096 -N '' -f /etc/proftpd/sftp_host_rsa_key
sudo ssh-keygen -t ed25519 -N ''         -f /etc/proftpd/sftp_host_ed25519_key
```

### 4. A read-only database role

On the Postgres server (least privilege — ProFTPD only ever reads):

```sql
CREATE ROLE proftpd LOGIN PASSWORD 'choose-a-strong-password';
GRANT CONNECT ON DATABASE sftpmanager TO proftpd;
```

The `SELECT` grants on the three views are applied **automatically by the app
at startup** (and re-applied every restart). This matters because the app's
`ddl-auto=create` rebuilds the schema on every start, which would otherwise
destroy manually-issued grants — the classic symptom being ProFTPD suddenly
logging `permission denied for view proftpd_users` after an app restart.
The role must be named exactly `proftpd` for the auto-grant to find it.

Allow the SFTP host's IP in `pg_hba.conf` if it's a separate machine.

### 5. ProFTPD configuration

`/etc/proftpd/conf.d/sftp-sql.conf`:

```apacheconf
<IfModule mod_sftp.c>
# "0.0.0.0 ::" covers IPv4 AND IPv6 — connecting to "localhost" often uses
# ::1, and an IPv4-only vhost would NOT handle that connection (the default
# server would, with mod_sql's default queries → "column userid does not
# exist" errors against the app's own users table).
<VirtualHost 0.0.0.0 ::>
    Port                 2222
    SFTPEngine           on
    SFTPLog              /var/log/proftpd/sftp.log
    SFTPHostKey          /etc/proftpd/sftp_host_rsa_key
    SFTPHostKey          /etc/proftpd/sftp_host_ed25519_key

    # ── Auth from PostgreSQL ─────────────────────────────────────────
    SQLBackend           postgres
    SQLConnectInfo       sftpmanager@db-host:5432 proftpd choose-a-strong-password
    # "users groups" — BOTH words matter. Without "groups", mod_sql never
    # looks up group membership, no session gets the sftpread/sftpwrite/
    # sftpdelete groups, and every <Limit> below denies everything.
    # There must be exactly ONE SQLAuthenticate line in this vhost.
    SQLAuthenticate      users groups
    # bcrypt hashes are verified via crypt(3) / libxcrypt:
    SQLAuthTypes         Crypt
    # TWO user queries are needed: by-name (login) AND by-uid (directory
    # listings map file-owner uid 2001 back to a name). Without the second,
    # mod_sql falls back to its default "FROM users" query -> fatal error
    # and the session is dropped right after authentication.
    #
    # IMPORTANT: each SQLNamedQuery must be ONE single line — do NOT wrap
    # them or use backslash continuations, or the next config line gets
    # swallowed into the SQL and Postgres errors out.
    SQLUserInfo          custom:/get-user/get-user-by-id
    SQLNamedQuery        get-user SELECT "userid, passwd, uid, gid, homedir, shell FROM proftpd_users WHERE userid = '%U'"
    SQLNamedQuery        get-user-by-id SELECT "userid, passwd, uid, gid, homedir, shell FROM proftpd_users WHERE uid = '%{0}' LIMIT 1"

    # Public keys (RFC 4716, from the app's converted column)
    SFTPAuthorizedUserKeys sql:/get-user-key
    SQLNamedQuery        get-user-key SELECT "ssh_key FROM proftpd_users WHERE userid = '%U' AND ssh_key IS NOT NULL"

    # Try key first, then password (both enabled)
    SFTPAuthMethods      publickey password

    # ── Virtual users: no real shell accounts needed ────────────────
    # NOTE: ProFTPD does NOT allow comments on the same line as a directive
    AuthOrder            mod_sql.c
    RequireValidShell    off
    CreateHome           on 700 dirmode 711
    # Chroot each user into their own home directory:
    DefaultRoot          ~

    # ── Enforce app permissions (READ / WRITE / DELETE) ─────────────
    # The app publishes three synthetic groups in the proftpd_groups view:
    #   sftpread   = accounts with READ    (download + list files)
    #   sftpwrite  = accounts with WRITE   (upload, mkdir, rename)
    #   sftpdelete = accounts with DELETE  (delete files, remove dirs)
    # These are SQL-only groups — nothing is added to /etc/group, and every
    # session still runs as uid/gid 2001 on the filesystem.
    # (Group lookups are enabled by the single "SQLAuthenticate users groups"
    # line in the auth section above.)
    SQLGroupInfo         proftpd_groups groupname gid members

    # Downloads and directory listings need READ
    <Limit RETR LIST NLST MLSD MLST STAT>
        AllowGroup sftpread
        DenyAll
    </Limit>
    # Uploads, new folders and renames need WRITE
    <Limit STOR STOU APPE MKD XMKD RNFR RNTO>
        AllowGroup sftpwrite
        DenyAll
    </Limit>
    # Deleting files / removing folders needs DELETE
    <Limit DELE RMD XRMD>
        AllowGroup sftpdelete
        DenyAll
    </Limit>
    # Navigation is always allowed (entering folders, printing the path)
    <Limit CWD XCWD CDUP PWD XPWD>
        AllowAll
    </Limit>
</VirtualHost>
</IfModule>
```

### 5b. Turn off plain FTP (port 21)

Out of the box, ProFTPD also runs an ordinary **unencrypted FTP server on
port 21** — that's its default personality, defined in the main config file.
You almost certainly don't want that; only the SFTP vhost on port 2222 from
the config above should be listening.

To disable it:

1. Open the main config: `sudo nano /etc/proftpd/proftpd.conf`
2. Find the line that says:
   ```
   Port    21
   ```
3. Change it to:
   ```
   Port    0
   ```
   `Port 0` is ProFTPD's official way of saying "don't listen at all" for the
   default server. The SFTP `<VirtualHost>` you created is unaffected — it
   declares its own `Port 2222`.
4. Leave `ServerType standalone` **as it is** — that line just means ProFTPD
   runs as a normal background daemon, and it must stay.

Then restart and verify only 2222 is listening:

```bash
sudo systemctl restart proftpd
sudo ss -tlnp | grep proftpd
# expect ONE line, showing :2222 — if you also see :21, step 3 didn't take
```

### 6. Optional: enforce the IP whitelist (mod_wrap2_sql)

```bash
sudo apt-get install proftpd-mod-wrap2 proftpd-mod-wrap2-sql
```

Add inside the vhost:

```apacheconf
<IfModule mod_wrap2_sql.c>
    WrapEngine        on
    # Per-user allow list from the app's whitelist table; a user with no
    # rows falls through to the deny query result (empty) = allowed.
    WrapUserTables    %U sql:/get-user-allow sql:/get-user-deny
    SQLNamedQuery     get-user-allow SELECT "allowed FROM proftpd_allowed_ips WHERE name = '%U'"
    SQLNamedQuery     get-user-deny  SELECT "1 WHERE false"
    WrapLog           /var/log/proftpd/wrap2.log
</IfModule>
```

Caveat: with this exact config, a user with whitelist rows is restricted to
those IPs; a user with none is unrestricted. If you want "no rows = deny all"
semantics instead, flip the deny query to `SELECT "ALL"` and rely on the allow
list — decide which semantics your product promises before enabling.

### 7. Test

```bash
# password auth
sftp -P 2222 someuser@sftp-host
# key auth
sftp -P 2222 -i ~/.ssh/id_ed25519 someuser@sftp-host
```

Watch `/var/log/proftpd/sftp.log`. Verify the kill-switches: disable the
account (or deactivate/lock/close the owning customer) in the admin screen,
reconnect → login refused, because the view no longer returns the row.

## Troubleshooting

Always start with these two commands — they distinguish "ProFTPD didn't
start" from "started but skipped the vhost":

```bash
sudo systemctl status proftpd     # running at all?
sudo proftpd -t                   # parse the config; prints the exact bad line
sudo ss -tlnp | grep proftpd      # what's actually listening
```

| Symptom | Likely cause |
|---|---|
| Nothing on 2222, proftpd NOT running | Config parse error — run `sudo proftpd -t`. Common: an inline `# comment` after a directive (not allowed — comments must be on their own line) |
| Nothing on 2222, proftpd IS running (maybe on 21) | `mod_sftp` not loaded, so the `<IfModule mod_sftp.c>` block was silently skipped — see step 1b. Also check `/etc/proftpd/proftpd.conf` still has its `Include /etc/proftpd/conf.d/` line |
| `column "userid" does not exist ... FROM users` at LOGIN time | The connection was handled by the DEFAULT server, not your vhost, so mod_sql used its built-in default query (table `users`). Usually an IPv4/IPv6 mismatch: the log shows `[::1]` but the vhost was `<VirtualHost 0.0.0.0>` (IPv4-only). Use `<VirtualHost 0.0.0.0 ::>` or connect via `127.0.0.1` |
| Auth succeeds, then "Connection closed by remote host" (same `userid` error in log) | Missing by-uid user query — directory listings look users up by uid, and with only the by-name query defined mod_sql falls back to its default `FROM users` lookup. Define `SQLUserInfo custom:/get-user/get-user-by-id` with both named queries (see step 5) |
| Login OK but EVERYTHING is "Permission denied" (`blocked by <Limit ...>`), and the SQL log has NO `sql_getgroups` lines even though `proftpd_groups` has the right members | `SQLAuthenticate` is missing the `groups` keyword (or a second `SQLAuthenticate users` line is overriding it) — mod_sql never performs group lookups, so no session joins sftpread/sftpwrite/sftpdelete. Keep exactly one line: `SQLAuthenticate users groups` |
| Password always rejected | Old plaintext row (re-save the password in the app), or distro without bcrypt in crypt(3) — check `SQLAuthTypes`; on old distros switch app hashing strategy |
| Key always rejected | Key column empty (`ssh_key IS NULL`) — the app only converts keys saved after this feature; re-save the key |
| `no such user` | Row filtered out by the view — account disabled or owner deactivated/locked/closed |
| Login OK but wrong directory | `CreateHome` missing, or `/srv/sftp` perms — homedir must be creatable by ProFTPD |
| SQL connect errors | `pg_hba.conf` / role grants / `SQLConnectInfo` credentials |
| `permission denied for view proftpd_...` appearing after an app restart | The app's schema rebuild destroyed the view grants. Newer app versions re-grant automatically at startup (role must be named `proftpd`); on older versions re-run the GRANT manually after each restart |

## Security notes

- The `proftpd` DB role can read **only the two views** — never the users,
  payments, or portal tables.
- Port 2222 keeps ProFTPD clear of the host's own OpenSSH on 22. Firewall
  everything else.
- Per-account permissions are enforced by ProFTPD at the protocol level via
  the `proftpd_groups` view and the `<Limit>` blocks — NOT by filesystem
  uid/gid. All files stay owned by uid 2001; what varies per user is which
  SFTP operations their session may perform. Changing an account's
  permissions in the app takes effect on their next connection.
