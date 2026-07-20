# Security policy

Do not open a public issue containing credentials, personal data, payment records or vulnerability exploitation details. Report security problems through the private contact shown on the deployed site's legal/support page.

Production secrets must stay in the server secret manager or `/etc/orangejd/orangejd.env` with mode `0600`. They must never be committed, embedded in frontend bundles, copied into release archives or sent through chat.

Order payments remain disabled by default. Enabling them requires a verified provider, active order query, refunds, reconciliation, compliant settlement and the acceptance checklist in `docs/ePay订单支付接入与服务器交接手册.md`.
