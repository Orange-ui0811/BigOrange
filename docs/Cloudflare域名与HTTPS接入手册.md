# OrangeJD Cloudflare 域名与 HTTPS 接入手册

> 状态：待填写正式域名并执行
> 创建日期：2026-07-19
> 适用环境：OrangeJD 当前生产环境
> 本文只配置域名、HTTPS 和 Cloudflare，不迁移、不覆盖生产数据库。

## 1. 当前生产环境

| 项目 | 当前值 |
| --- | --- |
| 公网 IP | `YOUR_SERVER_PUBLIC_IP` |
| SSH 用户 | `YOUR_SSH_USER` |
| Nginx 配置 | `/etc/nginx/sites-available/orangejd` |
| 后端服务 | `orangejd.service` |
| 后端监听 | `127.0.0.1:8080` |
| 用户端 | `/data/orangejd/web-dist` |
| 管理端 | `/data/orangejd/web-dist/admin` |
| 环境变量 | `/etc/orangejd/orangejd.env` |
| 源站证书 | `/etc/nginx/ssl` |

访问链路：

```text
浏览器 -> Cloudflare -> YOUR_SERVER_PUBLIC_IP:443 -> Nginx
  /         -> Vue 用户端
  /admin/   -> Vue 管理端
  /api/*    -> Spring Boot 127.0.0.1:8080
  /ws/*     -> WebSocket 127.0.0.1:8080
```

下文以 `example.com` 为占位符，执行时必须替换为正式域名。

## 2. Cloudflare 接入

在 Cloudflare 添加根域名 `example.com`，配置：

| 类型 | 名称 | 目标 | 状态 |
| --- | --- | --- | --- |
| A | `@` | `YOUR_SERVER_PUBLIC_IP` | Proxied 橙色云 |
| CNAME | `www` | `example.com` | Proxied 橙色云 |

- 没有服务器 IPv6 时不要添加 AAAA。
- MX、邮件验证 TXT 等记录按邮件服务商要求保留；邮件服务器通常使用灰色云。
- 到域名注册商将 nameserver 全部换成 Cloudflare 分配的两个地址。
- 已开启 DNSSEC 时，先关闭，等待 Cloudflare 显示 `Active` 后再重新开启。
- 橙色云开启后，DNS 查询返回 Cloudflare IP 而不是源站 IP，属于正常现象。

官方文档：<https://developers.cloudflare.com/dns/zone-setups/full-setup/setup/>

## 3. HTTPS 源站证书

Cloudflare 控制台进入：

```text
SSL/TLS -> Origin Server -> Create Certificate
```

建议选择 RSA，并包含：

```text
example.com
*.example.com
```

将 Origin Certificate 和 Private Key 分别安装到服务器。私钥不得提交 Git。

```bash
sudo install -d -m 700 /etc/nginx/ssl
sudo touch /etc/nginx/ssl/fullchain.pem /etc/nginx/ssl/privkey.pem
sudo chmod 600 /etc/nginx/ssl/fullchain.pem /etc/nginx/ssl/privkey.pem
sudoedit /etc/nginx/ssl/fullchain.pem
sudoedit /etc/nginx/ssl/privkey.pem
```

Cloudflare 加密模式设置为：

```text
SSL/TLS -> Overview -> Full (strict)
```

不得使用 `Flexible`，否则容易形成 HTTPS 重定向循环。

官方文档：<https://developers.cloudflare.com/ssl/origin-configuration/ssl-modes/full-strict/>

## 4. Nginx 切换域名

先备份：

```bash
sudo cp -a /etc/nginx/sites-available/orangejd \
  /etc/nginx/sites-available/orangejd.before-domain
sudoedit /etc/nginx/sites-available/orangejd
```

生产配置以项目根目录 `nginx.conf` 为准，替换所有 `YOUR_DOMAIN`：

```nginx
server_name example.com www.example.com;
```

证书路径：

```nginx
ssl_certificate /etc/nginx/ssl/fullchain.pem;
ssl_certificate_key /etc/nginx/ssl/privkey.pem;
```

HTTP 入口跳转 HTTPS：

```nginx
server {
    listen 80;
    listen [::]:80;
    server_name example.com www.example.com;
    return 301 https://example.com$request_uri;
}
```

必须保留以下路由语义：

- `/`：用户端 SPA。
- `/admin/`：管理端 SPA。
- `/api/`：代理到 `http://127.0.0.1:8080/`。
- `/api/epay/notify`：单独代理到 `http://127.0.0.1:8080/api/epay/notify`，必须保留 `/api` 前缀。
- `/ws/`：WebSocket，传递 `Upgrade` 和 `Connection`。
- `/uploads/`：历史本地上传。
- `/health/detail`：只允许本机访问。

项目 CSP 中的 `wss://YOUR_DOMAIN` 也要替换为 `wss://example.com`。

```bash
sudo nginx -t
sudo systemctl reload nginx
```

`nginx -t` 未通过时禁止 reload。必要时恢复备份。

## 5. OrangeJD 环境变量

```bash
sudoedit /etc/orangejd/orangejd.env
```

设置：

```dotenv
ALLOWED_ORIGINS=https://example.com,https://www.example.com
PUBLIC_BASE_URL=https://example.com
EPAY_NOTIFY_URL=https://example.com/api/epay/notify
EPAY_RETURN_URL=https://example.com/me/orders
EPAY_ORDER_PAYMENTS_ENABLED=false
```

数据库、Redis、JWT、OSS 和 AI 等其他生产配置不得清空或覆盖。

```bash
sudo systemctl restart orangejd
sudo systemctl status orangejd --no-pager -l
curl -fsS http://127.0.0.1:8080/health/detail
```

确认 `db`、`redis` 和 `rag_vector_store` 均为 `UP`。

## 6. 前端与 WebSocket

当前用户端在 `VITE_WS_URL` 为空时会自动使用同域地址：

```text
https://example.com -> wss://example.com/ws/
```

检查生产包是否写死旧地址：

```bash
sudo rg -n "YOUR_OLD_SERVER_IP|ws://|127\.0\.0\.1:8080" \
  /data/orangejd/web-dist
```

发现旧 WebSocket 地址时重新构建：

```bash
cd orange-web
VITE_API_BASE_URL=/api VITE_WS_URL=wss://example.com npm run build
```

只发布用户端时必须保留 `/data/orangejd/web-dist/admin`。

## 7. Turnstile

在对应 Turnstile 站点的 hostname 白名单加入：

```text
example.com
www.example.com
```

否则注册或登录可能因 hostname 不匹配而失败。

## 8. 真实访客 IP

Cloudflare 代理后，OrangeJD 的日志和登录限流需要通过 Nginx 恢复真实 IP。只信任 Cloudflare 官方网段：

```bash
cloudflare_ip_file="$(mktemp)"
{
  echo 'real_ip_header CF-Connecting-IP;'
  echo 'real_ip_recursive on;'
  curl -fsSL https://www.cloudflare.com/ips-v4 |
    sed 's#^#set_real_ip_from #;s#$#;#'
  curl -fsSL https://www.cloudflare.com/ips-v6 |
    sed 's#^#set_real_ip_from #;s#$#;#'
} > "$cloudflare_ip_file"
sudo install -m 644 "$cloudflare_ip_file" \
  /etc/nginx/conf.d/cloudflare-real-ip.conf
rm -f "$cloudflare_ip_file"
sudo nginx -t
sudo systemctl reload nginx
```

官方文档：<https://developers.cloudflare.com/support/troubleshooting/restoring-visitor-ips/restoring-original-visitor-ips/>

## 9. 安全组

公网只开放：

- TCP 80、443。
- SSH 只允许运维来源。

禁止直接向公网开放：

- Spring Boot 8080。
- MySQL 3306。
- Redis 6379。

## 10. 验收

```bash
sudo nginx -t
sudo systemctl status nginx --no-pager -l
sudo systemctl status orangejd --no-pager -l
curl -fsS http://127.0.0.1:8080/health/detail
curl -I https://example.com
curl -I https://example.com/admin/
curl -I https://example.com/legal/privacy
curl -sS https://example.com/health
```

浏览器检查首页、管理端、注册登录、Turnstile、私信 WebSocket、图片、法律页面和支付页面。响应头出现 `server: cloudflare` 或 `cf-ray`，说明已经经过 Cloudflare。

常见错误：

| 错误 | 常见原因 |
| --- | --- |
| 521 | Nginx 未运行，或安全组未开放 80/443 |
| 522 | Cloudflare 连接源站超时 |
| 525/526 | 源站 TLS 或证书不匹配 |
| 无限跳转 | Cloudflare 使用 Flexible |
| 登录失败 | CORS 或 Turnstile hostname 未更新 |
| 私信失败 | 仍使用 `ws://` 或 `/ws/` 代理错误 |

## 11. ePay 配置衔接

域名和 HTTPS 验收完成后配置：

```dotenv
EPAY_PID=<商户ID>
EPAY_KEY=<商户密钥>
EPAY_GATEWAY=<支付网关>
EPAY_NOTIFY_URL=https://example.com/api/epay/notify
EPAY_RETURN_URL=https://example.com/me/orders
EPAY_ORDER_PAYMENTS_ENABLED=false
```

- `EPAY_NOTIFY_URL` 是服务端异步通知地址，必须允许支付平台从公网访问。
- `EPAY_RETURN_URL` 是用户付款后的浏览器返回页面，不能代替异步通知。
- Cloudflare WAF、限流和缓存规则不能拦截或缓存支付通知。
- 后端只能在验签成功后更新订单状态。
- 异步通知必须支持重复通知的幂等处理。
- ePay 密钥只能保存在服务器环境文件中，不能进入 Git。
