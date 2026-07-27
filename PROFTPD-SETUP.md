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

### 8. Storage quotas (XFS project quotas) — kernel-enforced, per service

Storage limits come from the owner's plan (`account_controls.max_storage_mb`,
NULL = unlimited) and are enforced by the KERNEL via XFS project quotas on
the service directory — exact real disk usage, every writer counted, no
tallies to drift. ProFTPD needs no quota modules at all; when a service hits
its cap, any SFTP write simply fails with "Disk quota exceeded".

The app provides:
- `proftpd_service_quotas` view — (service_id, quota_mb) per service, read by
  the reconciler below (grants re-applied on every app start).
- `sftp_service_usage` table — the reconciler writes real usage back here;
  the portal displays it (the only DB write access this host has).

#### 8.1 Create and mount the XFS data disk (GCP)

```bash
# from anywhere with gcloud:
gcloud compute disks create sftp-data --size=500GB --type=pd-balanced --zone=<ZONE>
gcloud compute instances attach-disk <SFTP-VM-NAME> --disk=sftp-data --zone=<ZONE>

# on the SFTP host:
lsblk                                   # identify the new device, e.g. /dev/sdb
sudo mkfs.xfs -L sftpdata /dev/sdb
sudo mkdir -p /srv/sftp
echo 'LABEL=sftpdata /srv/sftp xfs defaults,prjquota 0 2' | sudo tee -a /etc/fstab
sudo mount -a
xfs_quota -x -c state /srv/sftp         # MUST show "Project quota state ... Accounting: ON, Enforcement: ON"
```

Migrating existing service data (if any):

```bash
sudo systemctl stop proftpd
sudo rsync -a /old/location/svc*/ /srv/sftp/     # or gsutil -m rsync -r gs://<bucket> /srv/sftp
sudo chown -R 2001:2001 /srv/sftp/svc*
sudo systemctl start proftpd
```

Growing later (online, no downtime):

```bash
gcloud compute disks resize sftp-data --size=1TB --zone=<ZONE>
sudo xfs_growfs /srv/sftp
```

#### 8.2 The quota reconciler

The script authenticates to Postgres as the `proftpd` role via `/root/.pgpass`
(it runs as root, so root's home). Set that up first:

```bash
# the role's password is whatever ProFTPD itself uses — find it with:
sudo grep -r "SQLConnectInfo" /etc/proftpd/

# then (fields: host:port:database:user:password — must match the DB= line
# in the script below):
sudo bash -c 'echo "127.0.0.1:5432:sftpmanager:proftpd:THE_PASSWORD" > /root/.pgpass'
sudo chmod 600 /root/.pgpass     # mandatory — psql ignores a readable .pgpass

# verify (no password prompt = working):
sudo psql "host=127.0.0.1 dbname=sftpmanager user=proftpd" -c "SELECT 1"
```

`/usr/local/sbin/sftp-quota-sync` (run as root — it drives xfs_quota):

```bash
#!/usr/bin/env bash
# Applies plan storage limits to service dirs (XFS project quotas) and
# reports real usage back to the app. Also creates the directory for any
# newly provisioned service, so new services are ready within a minute.
set -euo pipefail
FS=/srv/sftp
DB="host=127.0.0.1 dbname=sftpmanager user=proftpd"

# 1. every service: dir exists, is an XFS project, carries its plan limit
psql "$DB" -Atc "SELECT service_id, quota_mb FROM proftpd_service_quotas" |
while IFS='|' read -r id mb; do
    dir="$FS/svc$id"
    if [ ! -d "$dir" ]; then
        mkdir -p "$dir" && chown 2001:2001 "$dir" && chmod 750 "$dir"
    fi
    if ! grep -q "^$id:" /etc/projects 2>/dev/null; then
        echo "$id:$dir" >> /etc/projects
        echo "svc$id:$id" >> /etc/projid
        xfs_quota -x -c "project -s svc$id" "$FS" >/dev/null
    fi
    xfs_quota -x -c "limit -p bhard=${mb}m svc$id" "$FS"    # bhard=0m = unlimited
done

# 2. report real usage back for the portal (report is in 1K blocks)
xfs_quota -x -c 'report -p -N -b' "$FS" | while read -r proj used _rest; do
    id="${proj#svc}"
    [[ "$id" =~ ^[0-9]+$ ]] || continue
    psql "$DB" -qc "INSERT INTO sftp_service_usage (sftp_service_id, used_bytes, updated_at)
                    VALUES ($id, ${used}::bigint * 1024, now())
                    ON CONFLICT (sftp_service_id)
                    DO UPDATE SET used_bytes = EXCLUDED.used_bytes, updated_at = now()"
done
```

```bash
sudo chmod 700 /usr/local/sbin/sftp-quota-sync
```

Run it every minute via systemd:

```ini
# /etc/systemd/system/sftp-quota-sync.service
[Unit]
Description=Sync SFTP storage quotas and usage

[Service]
Type=oneshot
ExecStart=/usr/local/sbin/sftp-quota-sync
```

```ini
# /etc/systemd/system/sftp-quota-sync.timer
[Unit]
Description=Run sftp-quota-sync every minute

[Timer]
OnBootSec=30
OnUnitActiveSec=60

[Install]
WantedBy=timers.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now sftp-quota-sync.timer
```

#### 8.3 Verify

```bash
sudo /usr/local/sbin/sftp-quota-sync            # run once by hand — no errors
xfs_quota -x -c 'report -p -h' /srv/sftp        # limits + usage per svc project
psql "host=127.0.0.1 dbname=sftpmanager user=proftpd" \
  -c "SELECT * FROM sftp_service_usage ORDER BY sftp_service_id"

# end-to-end: upload past a trial plan's limit -> client shows
# "Disk quota exceeded"; portal usage bar updates within a minute
```

Notes:
- Plan changes flow automatically: admin edits max_storage_mb (or the user
  upgrades) -> the view reflects it -> the next timer tick applies the new
  limit. Nothing to deploy.
- If you previously enabled the mod_quotatab config from an earlier revision
  of this file, REMOVE that whole `<IfModule mod_quotatab.c>` block and its
  four SQLNamedQuery lines — quotas are kernel-side now.
- `df` inside the chroot still shows the whole filesystem; per-service usage
  is what `xfs_quota report -p` (and the portal) show.
