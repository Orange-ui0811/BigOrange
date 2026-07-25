# OrangeJD GitHub 克隆与服务器数据库部署

本文面向服务器和数据库负责人。公开仓库不得携带生产密码、商户密钥、证书、真实服务器标识或原 Git 历史。

## 1. 生成并上传公开源码

```bash
./scripts/export-public.sh /absolute/path/BigOrange-public
cd /absolute/path/BigOrange-public
git init
git add .
git commit -m "Initial public-safe OrangeJD release"
git branch -M main
git remote add origin https://github.com/YOUR_ORG/YOUR_REPO.git
git push -u origin main
```

公开目录会排除 `.git`、真实 `.env`、证书、密钥、构建产物、上传文件、数据库备份、内部生产运维文档和开发日志。上传前还应启用 GitHub Secret Scanning。

## 2. 同事克隆与构建

要求：JDK 21、Maven 3.9+、Node.js 20+、npm、MySQL 8、Redis Stack、Nginx。

```bash
git clone https://github.com/YOUR_ORG/YOUR_REPO.git BigOrange
cd BigOrange
VITE_TURNSTILE_SITE_KEY='PUBLIC_SITE_KEY' \
VITE_LEGAL_OPERATOR='运营主体全称' \
VITE_LEGAL_CONTACT_EMAIL='网站公示的联系邮箱' \
./scripts/build-release.sh
```

脚本会执行后端测试、两个前端的锁文件安装和生产构建，生成 `build/orangejd-release.tar.gz`。Turnstile 公钥和法律主体属于前端构建时变量，脚本缺少任一值会拒绝构建；不得把生产值写回仓库。API 默认使用同域 `/api`，WebSocket 默认跟随当前 HTTPS 域名。

## 3. 数据库必须先判断新旧

### 已有生产数据库

不要手工导入任何结构快照。先用数据库平台快照或 `backup.sh` 完成可恢复备份，再发布新 JAR。应用启动时 Flyway 从现有版本继续执行迁移；上线后确认 `flyway_schema_history` 全部 `success=1`。

### 全新空数据库

先创建 `utf8mb4` 空数据库和最小权限应用账号：

```bash
mysql --host=YOUR_MYSQL_HOST --user=DB_ADMIN -p \
  --execute="CREATE DATABASE orangejd CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci"
```

随后首次启动后端，Flyway 会按顺序执行 V1 至当前最新迁移。空库不包含默认管理员，需要临时提供 `ORANGE_ADMIN_BOOTSTRAP_USERNAME` 和至少 12 位的 `ORANGE_ADMIN_BOOTSTRAP_PASSWORD`；创建成功后从环境文件删除。不要对已有库手工执行 V1。

MySQL 和 Redis 只能由应用服务器内网访问，禁止向公网开放 3306、6379。生产数据库账号不应有建库、删库或全局管理权限；首次 Flyway 迁移需要目标 schema 的建表和改表权限。

## 4. 服务器环境文件

```bash
sudo install -d -m 700 /etc/orangejd
sudo install -m 600 .env.prod.example /etc/orangejd/orangejd.env
sudoedit /etc/orangejd/orangejd.env
```

至少填写 MySQL、Redis、两个不同的 JWT 随机密钥、HTTPS 域名白名单、公开地址和 Turnstile 密钥。分别用 `openssl rand -base64 48` 生成 JWT 密钥。所有 ePay 值默认留空，`EPAY_ORDER_PAYMENTS_ENABLED=false`；未完成查单、退款、对账和结算验收不得开启。

环境文件不得通过 Git、聊天、邮件、截图或构建包传递，应由服务器负责人从密钥管理系统直接写入，权限保持 `0600`。

## 5. 安装发布包

先把当前 JAR 和 `web-dist` 复制到带时间戳的只读回滚目录，然后：

```bash
mkdir -p /tmp/orangejd-release
tar -xzf build/orangejd-release.tar.gz -C /tmp/orangejd-release
sudo install -d -m 750 /data/orangejd /data/orangejd/uploads /data/orangejd/web-dist
sudo install -m 640 /tmp/orangejd-release/orange-server.jar /data/orangejd/orange-server.jar
sudo cp -a /tmp/orangejd-release/web-dist/. /data/orangejd/web-dist/
sudo install -m 644 scripts/orangejd.service /etc/systemd/system/orangejd.service
sudo systemctl daemon-reload
sudo systemctl enable --now orangejd
```

前端必须整体发布，不能只覆盖用户端而误删 `web-dist/admin`。将根目录 `nginx.conf` 复制到站点配置并替换 `YOUR_DOMAIN`，安装证书后：

```bash
sudo nginx -t
sudo systemctl reload nginx
```

Cloudflare 与 HTTPS 的完整步骤见 `docs/Cloudflare域名与HTTPS接入手册.md`。

## 6. 上线验收

```bash
sudo systemctl status orangejd --no-pager -l
sudo journalctl -u orangejd -n 200 --no-pager
curl -fsS http://127.0.0.1:8080/health/detail
curl -I https://YOUR_DOMAIN/
curl -I https://YOUR_DOMAIN/admin/
curl -I https://YOUR_DOMAIN/legal/privacy
```

同时验证注册登录、邮箱、私信 WebSocket、订单、上传、法律页面和管理后台。支付仍应显示未开放。数据库检查：

```sql
SELECT version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

## 7. 回滚边界

应用异常时先关闭支付开关，保留日志和数据库现场，恢复上一版 JAR 与静态文件。已成功执行的 Flyway 迁移不要手工删除，V11 支付审计表也不要回滚；旧应用不会使用新增表。数据库恢复只能由数据库负责人依据上线前快照执行。
