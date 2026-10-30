# Spore-Cloud

> Microservice backend for the Spore mobile app: Eureka discovery, a Spring Cloud Gateway that
> validates Bearer tokens and forwards a HMAC-signed identity, and separate user/content services
> over the shared `spore_cms` schema. Wire-compatible with the Spore-GUI monolith, so the Android
> client syncs with no code changes.

Spore 移动端（[Spore-Mobile](https://github.com/Offblink/Spore-Mobile)）的微服务后端。
Spring Cloud 全家桶：**Eureka 服务发现 + Gateway 网关 + 用户/内容两个服务**，协议与
[Spore-GUI](https://github.com/Offblink/Spore-GUI) 单体后端**逐字段一致**——手机端换了服务端，
同步代码一行不用改。

```
手机 SporeMobile ──┐
                   ├─→ spore-gateway :19080 ──┬─→ spore-user    :19081  (注册/登录/token)
桌面 Spore-GUI ────┘   (lb:// 路由 + 鉴权)      └─→ spore-content :19082  (同步/题图)
                              │
                        spore-registry :8761 (Eureka)
                              │
                     MySQL spore_cms（与单体同库）
```

## 模块

| 模块 | 端口 | 职责 | 拥有的表 |
|---|---|---|---|
| `spore-registry` | 8761 | Eureka 服务发现（控制台 `/`) | — |
| `spore-gateway` | 19080 | 唯一入口、`lb://` 路由、Bearer 鉴权、身份签名头 | — |
| `spore-user` | 19081 | 注册/登录/JWT/LAN token/设备令牌/`users/me` | `user` `role` `permission` `sys_config` |
| `spore-content` | 19082 | `sync/pull` `sync/push` 题图上/下推 | `category` `article` |
| `spore-common` | — | `R` 包装、错误码、同步 DTO、身份签名与过滤器 | — |

**端口为什么是 1908x**：本机 8080/8081 已被 Spore-GUI 单体与课程 Tomcat 占用。两套后端
连同一个库、可以同时跑，互不抢端口。要换端口改各模块 `application.yml` 的 `server.port` 即可
（网关路由用的是服务名，不写死 IP）。

## 快速开始

前置：JDK 17、Maven、MySQL 8（库 `spore_cms`，表结构见 Spore-GUI 的 `docs/schema.sql`）。

```bash
# 1) 每个服务第一次跑：把口令写进本机配置（已被 .gitignore 挡住）
cp spore-user/src/main/resources/application-local.yml.example \
   spore-user/src/main/resources/application-local.yml      # 填 spring.datasource.password
# spore-content 同理

# 2) 打包（父工程一次全打）
mvn -DskipTests package

# 3) 起服务（或直接双击 start-all.cmd；脚本会等两个服务真的注册上再起网关）
java -jar spore-registry/target/spore-registry-0.1.0-SNAPSHOT.jar
java -jar spore-user/target/spore-user-0.1.0-SNAPSHOT.jar
java -jar spore-content/target/spore-content-0.1.0-SNAPSHOT.jar
java -jar spore-gateway/target/spore-gateway-0.1.0-SNAPSHOT.jar
```

网关起来后：`http://<本机局域网IP>:19080/api`。手机设置页 →「配对与同步」→ 展开
**账号直连服务器**，填地址（`192.168.x.x:19080`，`/api` 自动补）、账号、口令即可。

首个注册的账号自动是 `ADMIN`（与单体一致，种子只预置角色不预置用户）。

## 接口

手机端用到的全部接口（`{api}` = `http://host:19080/api`）：

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/auth/register` | 注册（公开） |
| POST | `/auth/login` | 登录换 JWT（公开） |
| POST | `/auth/logout` | 作废当前 JWT |
| GET | `/auth/lan-token` | 取/改绑 LAN token（扫码配对用） |
| POST | `/auth/device-token` | 轮换本机设备令牌 |
| POST | `/auth/device-login` | 设备令牌换 JWT（公开） |
| GET | `/users/me` | 当前用户 + 权限码（配对连通校验） |
| GET | `/sync/pull?cursor&limit` | 游标增量（含墓碑），`{categories,articles,nextCursor}` |
| POST | `/sync/push` | `{categories,articles}` → `{results:[{id,accepted}]}` |
| POST | `/sync/push-attachment?articleId=` | multipart 字段 `file` → `{path}` |
| GET | `/sync/pull-attachment?path=` | 原始图片字节（非 R 包装） |

返回统一 `{code,message,data}`，`code=0` 成功；401 也带 R 包装（`40101`）。

## SSM 三层对照（课程口径：认三层形态，Boot 可以）

Spring Cloud 跑在 Spring Boot 上，所以没有 `web.xml` / `spring-mvc.xml` 那套外置 Tomcat 装配
（`spring-cloud-starter-gateway` 是 WebFlux/Netty，打不了 WAR）。但 SSM 的三层在这套代码里逐层都在：

| 层 | 位置 | 证据 |
|---|---|---|
| Spring（IoC / DI / 事务） | `@Service` `@Configuration` `@Transactional`（`SyncService.push`） | 构造器注入、`WebConfig` 装配过滤器与切面 |
| Spring MVC | `controller/*Controller.java`、`@RestController` `@RequestMapping` `@RequestBody` `@Valid` | 内嵌 Tomcat 跑 19081/19082；`@Valid` 失败统一转 40001 |
| MyBatis | `mapper/*Mapper.java` + `resources/mapper/*.xml`（resultMap + 手写 SQL） | 8 条同步 SQL / 3 条用户 SQL 全在 XML，`mybatis-plus.mapper-locations` 指过去 |
| MyBatis-Plus 高级特性 | `PaginationInnerInterceptor`、逻辑删除、`BaseMapper` | `GET /api/users?page&size` 走插件分页；`deleted` 由全局配置接管 |
| AOP 切面 | `spore-common/aspect/OpLogAspect`、`PermissionAspect` | `@OpLog` 落 `op_log`（实测有行）；`@RequirePermission` 无权限 403 |

**授权怎么在微服务里落地**：认证在网关（token → 身份），授权在服务（AOP → 权限码）。
网关调 user 服务 `verify` 时一并取回角色权限码，写进 `X-Spore-Perms` 并纳入 HMAC 签名，
下游 `@RequirePermission` 切面直接读——不查库、不 RPC，伪造权限头过不了验签。

## 设计要点

**鉴权链**：网关不解析 token。它拿 `Authorization: Bearer <token>` 调 user 服务的
`POST /internal/auth/verify`（JWT 与 LAN token 都由 user 服务判），拿到 `{userId, roleId}` 后
向请求注入 `X-Spore-User/Role/Perms/Ts/Sig`，签名原文 `userId|roleId|perms|ts|path`
（HMAC-SHA256，5 分钟窗口）。下游 servlet 服务用 `IdentityFilter` 验签，**客户端自带的 `X-Spore-*` 在网关先删掉**——
绕过网关直连 19081/19082 伪造身份一律 401。共享密钥走 `SPORE_SIGN_SECRET` 环境变量，
默认值只适合本机开发。

**为什么没有 OpenFeign / Nacos / 配置中心**：网关是 WebFlux（Netty），Feign 是阻塞客户端，
塞进事件循环会拖死网关，所以服务间调用用 `@LoadBalanced WebClient`。Eureka 是纯 Java 进程，
不用额外装二进制；这个规模的配置放各服务 `application.yml` 就够，配置中心是纯开销。

**同步语义（与单体逐条对齐）**：
- LWW：客户端 `updated` 严格大于服务端 `update_time` 才覆盖；
- 墓碑：`deleted=1` 双向传播，行不真删，pull 不过滤 deleted；
- 游标：`nextCursor` = 本批最大 `update_time`，任一类被 `limit` 截断时游标不越过该类本批最大值；
- `categoryId:""` = 移出科目（写成 NULL）；悬空科目 → NULL 但仍 `accepted:true`。

**与单体的三处实现差异**：
1. `messages` 用 Jackson `JsonNode` 原样存取——强类型 DTO 会悄悄裁掉手机端新增的消息字段；
2. UPDATE 手写全列 SQL——MyBatis-Plus 的 `updateById` 会自动追加 `deleted=0`，墓碑行永远更新不到；
3. JWT 签名密钥首次启动生成后持久化进 `sys_config`（`cloud_jwt_secret`），服务重启不再把手机踢回配对。
   `sys_config` 里本服务用的键统一 `cloud_` 前缀，避免和同库的 Spore-GUI 单体互相改绑 LAN token。

**数据归属**：一库两属主（表级）。跨服务外键（`article.user_id → user.id`）不做分布式事务，
靠「网关已验证的用户一定存在」这条不变量保证；题图落在 `spore.storage.data-dir`（默认
`./data/attachments`，相对启动目录）。

## 验证记录（本机 Windows，2026-10-30）

`mvn package` 五模块全绿；四进程起齐后 `GET /eureka/apps` 三个服务均 UP。经网关的全链路冒烟 27/27：

| 用例 | 结果 |
|---|---|
| 无 token → HTTP 401 + `code 40101` | ✅ |
| 注册 / 登录 / `users/me` 身份一致 | ✅ |
| push 科目+文章 → 两条 `accepted:true` | ✅ |
| pull 字段形状（`fav` 数字、`messages` 数组、`categoryId`） | ✅ |
| `messages` 未知键原样保真 | ✅ |
| LWW 旧时间戳拒收 / 新时间戳接收 | ✅ |
| 墓碑 `deleted=1` 随 pull 下发 | ✅ |
| 题图 multipart 上推 + 拉回字节一致 | ✅ |
| 直连 19082 无身份头 → 40101 | ✅ |
| 直连伪造 `X-Spore-User` + 假签名 → 401 | ✅ |
| LAN token 当 Bearer 用（并改绑） | ✅ |
| `@Valid` 空用户名 → 400 / 40001 | ✅ |
| 无 `user:manage` → 403（AOP 授权切面） | ✅ |
| 临时授权 → 分页可用 → 撤销 → 立刻 403 | ✅ |
| `@OpLog` 切面落 `op_log` 行 | ✅ |

手机端：`assembleDebug` 通过，APK 内 `assets/web/settings.html` 含「账号直连」卡、
`ServerConnector`/`connectPoll` 进 dex；设置页在浏览器 + 桥桩下 12/12 断言通过（空字段拦截、
载荷正确、成功清口令、状态行更新、失败显示服务端原话、零 JS 报错）。

## 安全红线

- 真实 DB 口令只在本机 `application-local.yml`（已 gitignore），不进 git/日志/文档；
- 口令哈希用 BCrypt，绝不明文；
- 手机端 token 只落本机 prefs，不进日志、不进设置页 JSON；
- 生产环境把 `SPORE_SIGN_SECRET` 换成随机值，并给网关加 HTTPS。
