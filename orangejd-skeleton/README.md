# OrangeJD Java Project Skeleton

这是从“苍穹外卖”去业务化后提炼出的一个可复用 Java 中后台项目骨架。

## 保留的通用能力

- 多模块结构
- 分层架构
- JWT 鉴权
- 统一返回
- 全局异常
- DTO / Entity / VO 分离
- Redis / 缓存
- 定时任务
- WebSocket
- 报表导出

## 模块说明

- `orange-common`
  - 放公共能力，不放具体业务
- `orange-pojo`
  - 放 `dto / entity / vo`
- `orange-server`
  - 放运行时业务入口与系统级配置

## 推荐你接下来怎么用

1. 保留当前骨架与包结构。
2. 把 `AuthController / AuthService` 这类示例类替换成你自己的业务模块。
3. 按下面模板持续新增模块：
   - `XxxController`
   - `XxxService`
   - `XxxServiceImpl`
   - `XxxMapper`
   - `XxxMapper.xml`
   - `XxxDTO`
   - `XxxEntity`
   - `XxxVO`

## 目录概览

```text
orangejd-skeleton
├─ orange-common
├─ orange-pojo
└─ orange-server
```

