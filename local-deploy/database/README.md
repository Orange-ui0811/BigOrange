# 本地数据库恢复

本目录中的 `orangevc-database-20260726.sql.gz.age` 是使用
[`age`](https://github.com/FiloSottile/age) 加密的生产数据库备份，只用于获授权的
本地开发和迁移验证。

## 安全边界

- 仓库只保存 `.age` 密文，不保存数据库明文或 `age` 私钥。
- 当前密文仅授权给公钥
  `age1mhp4mddt4xvygjqzdwahat3x0cjryw8kqmm09mmswmr4eaccnuxsyxyhu4`。
- `keys.txt` 或任何以 `AGE-SECRET-KEY-` 开头的内容不得提交、发送到群聊或放入网盘。
- 数据来自生产环境，解密后仍包含真实业务数据。只能在隔离的本机环境使用，不得暴露到公网。
- 每位同事应生成自己的 `age1...` 公钥。后续备份应同时加密给所有获授权公钥，不要共享私钥。

## 完整性信息

| 项目 | 值 |
| --- | --- |
| 备份时间 | 2026-07-26 |
| 解密后压缩包 SHA-256 | `0d23bfc18fb0fd389db781ca9d3261f856334d017cdfd294dd4b96dd056ef131` |
| 加密文件 SHA-256 | `1e69d6fd720a12012ce3043a2e3729712a65c51a6d16e1cd2434f8500e5d676f` |

## 解密

安装 `age`，将自己的私钥保存在 `~/.config/age/keys.txt`，然后执行：

```bash
mkdir -p /private/tmp/orangevc-local-restore
age --decrypt \
  --identity "$HOME/.config/age/keys.txt" \
  --output /private/tmp/orangevc-local-restore/orangevc-database.sql.gz \
  local-deploy/database/orangevc-database-20260726.sql.gz.age

shasum -a 256 /private/tmp/orangevc-local-restore/orangevc-database.sql.gz
gzip -t /private/tmp/orangevc-local-restore/orangevc-database.sql.gz
```

SHA-256 必须与上表一致，否则停止恢复。

## 导入本地 MySQL

以下操作会覆盖本地 `orangejd` 数据库。不要对生产数据库执行：

```bash
docker compose --env-file .env.local -f compose.local.yml stop backend
docker compose --env-file .env.local -f compose.local.yml up -d mysql redis

gzip -dc /private/tmp/orangevc-local-restore/orangevc-database.sql.gz \
  | docker compose --env-file .env.local -f compose.local.yml exec -T mysql sh -c \
  'exec mysql --default-character-set=utf8mb4 \
  -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"'

docker compose --env-file .env.local -f compose.local.yml up -d backend web admin
```

验证：

```bash
docker compose --env-file .env.local -f compose.local.yml ps
curl --fail-with-body http://127.0.0.1:18080/health/detail
```

恢复完成后删除本机明文：

```bash
rm -f /private/tmp/orangevc-local-restore/orangevc-database.sql.gz
rmdir /private/tmp/orangevc-local-restore
```
