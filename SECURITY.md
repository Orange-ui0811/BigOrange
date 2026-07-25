# Security Policy

## Reporting

Do not open a public issue for a suspected vulnerability or exposed credential. Contact the project maintainer privately and include the affected version, reproduction steps, impact and any temporary mitigation.

## Secret handling

- Production credentials belong in `/etc/orangejd/orangejd.env` with owner `root:root` and mode `600`.
- Frontend environment variables are public at runtime and must never contain secrets.
- Logs, screenshots, fixtures, SQL exports and release archives must be checked for credentials and personal data.
- Rotate a credential immediately if it is committed or shared outside its intended operators.

Order payments remain disabled by default. Enabling them requires a verified provider, active order query, refunds, reconciliation, compliant settlement and the acceptance checklist in `docs/ePay订单支付接入与服务器交接手册.md`.

## Supported code

Security fixes target the active `V2` branch and the currently deployed production release. Older development snapshots are not supported.
