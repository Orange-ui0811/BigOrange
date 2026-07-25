## Change summary

-

## Risk and compatibility

- [ ] Existing API URLs and response fields remain compatible, or the migration is documented.
- [ ] Database changes use an additive Flyway migration.
- [ ] Quota, reward, payment, and asynchronous operations are idempotent.
- [ ] Logs and fixtures contain no credentials or personal data.

## Verification

- [ ] `mvn -B clean verify`
- [ ] User app lint, typecheck, tests, and build
- [ ] Admin app lint, typecheck, tests, and build
- [ ] Relevant core flow tested manually
- [ ] Rollback path documented
