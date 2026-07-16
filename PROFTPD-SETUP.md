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
  Home dirs are `/srv/sftp/svc<serviceId>/<username>`; uid/gid fixed at 2001.
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
GRANT SELECT ON proftpd_users, proftpd_allowed_ips TO proftpd;
```

Allow the SFTP host's IP in `pg_hba.conf` if it's a separate machine.

### 5. ProFTPD configuration

`/etc/proftpd/conf.d/sftp-sql.conf`:

```apacheconf
<IfModule mod_sftp.c>
<VirtualHost 0.0.0.0>
    Port                 2222
    SFTPEngine           on
    SFTPLog              /var/log/proftpd/sftp.log
    SFTPHostKey          /etc/proftpd/sftp_host_rsa_key
    SFTPHostKey          /etc/proftpd/sftp_host_ed25519_key

    # ── Auth from PostgreSQL ─────────────────────────────────────────
    SQLBackend           postgres
    SQLConnectInfo       sftpmanager@db-host:5432 proftpd choose-a-strong-password
    SQLAuthenticate      users
    # bcrypt hashes are verified via crypt(3) / libxcrypt:
    SQLAuthTypes         Crypt
    SQLUserInfo          custom:/get-user
    SQLNamedQuery        get-user SELECT "userid, passwd, uid, gid, homedir, shell \
                             FROM proftpd_users WHERE userid = '%U'"

    # Public keys (RFC 4716, from the app's converted column)
    SFTPAuthorizedUserKeys sql:/get-user-key
    SQLNamedQuery        get-user-key SELECT "ssh_key FROM proftpd_users \
                             WHERE userid = '%U' AND ssh_key IS NOT NULL"

    # Try key first, then password (both enabled)
    SFTPAuthMethods      publickey password

    # ── Virtual users: no real shell accounts needed ────────────────
    AuthOrder            mod_sql.c
    RequireValidShell    off
    CreateHome           on 700 dirmode 711
    DefaultRoot          ~                      # chroot each user to their homedir

    # ── Map app permissions (READ / WRITE / DELETE) ──────────────────
    # Simplest robust mapping: read-only unless WRITE is granted.
    # %{env:...} tricks are fragile in mod_sql — do it with a second query
    # + mod_ifsession, or start permissive and tighten later:
    <Limit WRITE>
        AllowAll
    </Limit>
</VirtualHost>
</IfModule>
```

Remove/disable the default FTP vhost in `/etc/proftpd/proftpd.conf`
(`ServerType standalone`, comment out `Port 21` usage) if you only want SFTP.

```bash
sudo systemctl restart proftpd
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

| Symptom | Likely cause |
|---|---|
| Password always rejected | Old plaintext row (re-save the password in the app), or distro without bcrypt in crypt(3) — check `SQLAuthTypes`; on old distros switch app hashing strategy |
| Key always rejected | Key column empty (`ssh_key IS NULL`) — the app only converts keys saved after this feature; re-save the key |
| `no such user` | Row filtered out by the view — account disabled or owner deactivated/locked/closed |
| Login OK but wrong directory | `CreateHome` missing, or `/srv/sftp` perms — homedir must be creatable by ProFTPD |
| SQL connect errors | `pg_hba.conf` / role grants / `SQLConnectInfo` credentials |

## Security notes

- The `proftpd` DB role can read **only the two views** — never the users,
  payments, or portal tables.
- Port 2222 keeps ProFTPD clear of the host's own OpenSSH on 22. Firewall
  everything else.
- The app's `permissions` column is exposed in the view but not yet enforced
  in the sample config (see the `<Limit>` block) — tightening per-user
  write/delete is the natural next step once basic auth works.
