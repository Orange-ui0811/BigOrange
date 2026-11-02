# OrangeJD ePay 订单支付接入与服务器交接手册

> 文档状态：代码接入完成，生产默认关闭，等待服务商与服务器侧验收
> 创建日期：2026-07-19
> 适用范围：产品订单定金与尾款，不替代现有 Segments 充值
> 安全原则：未完成服务商主体、查单、退款、对账和多卖家结算审查前，不得开启真实订单支付。

## 1. 职责边界

### 本地接入与法律负责人

- 维护 ePay 接入代码、配置模板和接口说明。
- 核验支付服务商经营主体、官方文档、隐私政策及合作协议。
- 准备用户协议、隐私政策、支付规则、退款规则和第三方服务清单。
- 不接收生产数据库密码、商户密钥或服务器私钥。
- 不在服务商与法律条件未满足时要求服务器伙伴开启支付。

### 服务器与数据库负责人

- 执行上线前备份。
- 部署 JAR 和前端静态文件。
- 让 Flyway 执行 `V11__add_order_epay_transactions.sql`。
- 在服务器秘密环境文件中配置商户参数。
- 验证 Nginx、Cloudflare、回调、数据库事务和日志。
- 完成小额真实支付、重复回调、退款和对账测试。

双方都不得通过聊天、Git、截图或工单传递 `EPAY_KEY`。

## 2. 本地代码已实现的能力

- 订单支付和 Segments 充值使用不同账务表，避免业务混账。
- 一个订单支付阶段只对应一条本地支付流水；重复点击复用原流水。
- 定金和尾款金额只从数据库 `payment_stage.amount` 读取，前端不能指定金额。
- 只有订单买家可以创建支付并查询自己的支付结果。
- 只允许 `wxpay` 和 `alipay`。
- 回调校验签名、商户号、成功状态、渠道流水号、支付方式和数据库金额。
- 订单、支付阶段和支付流水按固定顺序加锁，条件更新保证重复或并发回调只入账一次。
- 浏览器 `return_url` 只返回订单页，不能确认支付状态。
- 定金到账后才允许卖家交付；买家确认交付后才允许支付尾款。
- 已进入履约或已付款的订单不能直接取消，避免出现“订单取消但资金未退”。
- 生产配置 `EPAY_ORDER_PAYMENTS_ENABLED` 默认是 `false`。

当前尚未实现且属于上线门禁的服务商特定能力：

- 主动查单。
- 全额与部分退款。
- 退款结果查询或异步通知。
- 平台服务费与卖家货款分账。
- 自动下载和核对渠道对账单。

以上接口必须根据最终选定的 ePay 服务商官方文档实现，不能猜测接口地址或参数。

## 3. 服务商材料交接

本地负责人只向服务器负责人提供非秘密信息：

- 服务商官网和官方开发文档链接。
- 服务商经营主体全称、统一社会信用代码。
- 持牌支付机构名称及合作关系说明。
- 官方网关域名。
- 官方回调方式：POST 或 GET。
- 回调成功响应原文，例如 `success`。
- 官方支付方式值，例如 `wxpay`、`alipay`。
- 回调出口 IP 清单及变更通知方式。
- 查单、退款、退款查询、对账和分账文档。
- 结算周期、费率、退款费率与资金冻结规则。

以下秘密只由服务器负责人从服务商后台复制到服务器：

- `EPAY_PID`。
- `EPAY_KEY`。
- 证书私钥或 API 私钥。
- 生产服务器 SSH 私钥和数据库密码。

## 4. 服务器部署前备份

服务器负责人执行项目现有运维手册中的数据库、JAR、静态文件和环境文件备份。至少确认：

```bash
sudo systemctl status orangejd --no-pager -l
sudo nginx -t
curl -fsS http://127.0.0.1:8080/health/detail
```

数据库备份完成前不得部署包含 V11 的 JAR。

## 5. 第一阶段：保持支付关闭部署

第一次部署只验证代码和迁移，不收真实订单款。

`/etc/orangejd/orangejd.env`：

```dotenv
EPAY_ORDER_PAYMENTS_ENABLED=false
```

如果 Segments 充值也尚未开放，五个现有 ePay 参数应全部留空；不能只填写其中一部分。

部署新版 JAR 后检查：

```bash
sudo systemctl restart orangejd
sudo systemctl status orangejd --no-pager -l
sudo journalctl -u orangejd -n 200 --no-pager
curl -fsS http://127.0.0.1:8080/health/detail
```

核对 Flyway：

```sql
SELECT installed_rank, version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

应看到 V11 成功。

核对新表：

```sql
SHOW CREATE TABLE order_payment;
SHOW CREATE TABLE order_payment_callback_event;
```

此阶段用户端“付定金”和“付尾款”应显示为不可用，接口也应返回“产品订单在线支付尚未开放”。

## 6. 第二阶段：填写支付配置但仍保持订单支付关闭

只有在服务商主体和官方域名核验后，服务器负责人填写：

```dotenv
EPAY_PID=<生产商户ID>
EPAY_KEY=<生产商户密钥>
EPAY_GATEWAY=https://<已核验官方网关>/submit.php
EPAY_NOTIFY_URL=https://<正式域名>/api/epay/notify
EPAY_RETURN_URL=https://<正式域名>/me/orders
EPAY_ORDER_PAYMENTS_ENABLED=false
```

要求：

- 网关、通知和返回地址全部为 HTTPS。
- `EPAY_KEY` 只存在 `/etc/orangejd/orangejd.env`，文件权限为 `600`。
- Nginx 中 `/api/epay/notify` 必须保留 `/api` 前缀。
- Cloudflare 不缓存支付回调，不对回调使用 JS Challenge 或 Turnstile。
- IP 白名单只能作为辅助，不能替代签名验证。

## 7. 第三阶段：服务商专项能力完成后灰度开启

以下项目全部通过后才能设置：

```dotenv
EPAY_ORDER_PAYMENTS_ENABLED=true
```

上线门禁：

- [ ] 服务商实际经营主体和持牌合作关系已书面确认。
- [ ] 商户合同主体与 Orange 运营主体一致。
- [ ] 结算账户是合规对公账户。
- [ ] 多卖家场景使用支付机构提供的子商户或分账能力，不由 Orange 人工二次清算。
- [ ] 主动查单已实现并通过测试。
- [ ] 全额和部分退款已实现并通过测试。
- [ ] 退款失败、重复退款和退款通知丢失可恢复。
- [ ] 渠道对账单可获取并能与本地流水自动或半自动核对。
- [ ] 支付、退款、争议和发票规则已在页面公示。
- [ ] 隐私政策和第三方服务清单已经填写真实支付服务商主体与链接。
- [ ] 客服和财务已具备支付事故处理与双人复核流程。

如服务商只有 MD5 回调，没有主动查单、退款、分账和对账接口，不得开启多卖家订单真实支付。

## 8. 灰度测试用例

使用两个普通用户账号和最低允许金额完成：

1. 买家创建订单。
2. 买家创建定金支付并跳转官方收银台。
3. 正常支付，等待异步回调。
4. 验证 `order_payment`、`payment_stage` 和订单状态一致。
5. 重放同一回调至少 20 次，确认只入账一次。
6. 并发发送相同回调，确认只入账一次。
7. 篡改金额、商户号、支付方式、签名和渠道流水，确认全部失败。
8. 卖家提交交付；买家未确认前不能创建尾款支付。
9. 买家确认交付后支付尾款，订单进入 `COMPLETED`。
10. 完成全额退款、部分退款和重复退款测试。
11. 模拟回调丢失，通过主动查单恢复。
12. 下载渠道对账单并核对全部流水。

只要出现一笔金额、订单状态或退款状态不一致，就关闭开关并停止灰度。

## 9. 只读核对 SQL

```sql
SELECT trade_no, order_id, payment_stage_id, payer_account_id,
       stage_type, amount, payment_method, provider_trade_no,
       status, create_time, complete_time
FROM order_payment
ORDER BY id DESC
LIMIT 50;

SELECT provider, provider_event_id, trade_no, provider_trade_no,
       trade_status, create_time
FROM order_payment_callback_event
ORDER BY id DESC
LIMIT 100;
```

禁止通过 SQL 直接把 `PENDING` 改为 `SUCCESS`，也禁止直接修改订单为 `DEPOSIT_PAID` 或 `COMPLETED`。补单必须先调用支付渠道主动查单并保留双人复核记录。

## 10. 回滚

如果部署后应用无法启动：

1. 保留数据库和日志，不删除 V11 表。
2. 将 `EPAY_ORDER_PAYMENTS_ENABLED=false`。
3. 恢复上一版 JAR 和前端静态文件。
4. 重启 `orangejd` 并验证健康检查。
5. 不要在存在支付流水后直接删除 `order_payment` 或回滚数据库结构。

如果支付运行中出现异常：

1. 立即关闭订单支付开关。
2. 保留 ePay、Nginx、Cloudflare、应用和数据库审计记录。
3. 停止人工改余额、改订单状态或私下向卖家转账。
4. 使用渠道主动查单和对账单定位差异。
5. 退款、补单和余额补偿实行双人复核。

## 11. 法律页面更新门禁

生产开启前，本地法律负责人必须据实填写：

- Orange 运营主体全称、统一社会信用代码和联系地址。
- 实际 ePay 服务商和底层支付渠道经营主体。
- 支付服务商隐私政策链接。
- 支付处理的数据：账号标识、订单号、金额、方式、渠道流水、回调数据、IP。
- 处理目的：支付确认、退款、对账、客服、审计和争议处理。
- 交易和支付记录保存期限。
- 定金、尾款、交付确认、退款、争议、发票和客服规则。
- 是否存在分账或子商户结算。

在持牌分账或托管能力未落地前，页面不得使用“资金托管”“担保交易”“平台代管货款”等表述。
