# Redis 优化方案（已落地）

> 本文描述本次对 tlias 教学管理系统做的 Redis 改造：设计思路、Key 规划、每个场景的取舍，以及面试怎么讲。
> **验证状态**：`mvn test` 通过（68 个源文件编译成功，`contextLoads` Spring 容器装配测试通过）；运行时行为因本机未启动 Redis/MySQL 实例未做端到端实测，验证命令见第八节。

---

## 一、改造概览

一共落了 5 个场景，覆盖 Redis 的五种典型用法（**缓存 / 计数 / 集合标记 / 原子脚本 / 队列**）：

| # | 场景 | Redis 用法 | 解决的问题 |
|---|---|---|---|
| 1 | 热点数据缓存 | String + JSON（Cache-Aside） | 部门/班级/报表反复查库 |
| 2 | 登录失败锁定 | String 计数器（INCR + EXPIRE） | 密码可被暴力破解 |
| 3 | 登出 / 强制下线 | String 标记（按 jti） | JWT 无法主动失效 |
| 4 | 接口限流 | Lua 脚本（固定窗口） | 登录、上传接口可被刷 |
| 5 | 操作日志异步化 | List 队列（LPUSH / RPOP） | 写日志拖慢业务请求 |

**改动范围**：新增 12 个类，修改 14 个文件（含 `pom.xml`、`application.yml`）。

```
controller ──┬─ LoginController ── AuthService ──┬─ Redis: 失败计数 / 黑名单
             │                                    └─ EmpService（原有登录校验）
             ├─ UploadController  @RateLimit ── RateLimitAspect ── Lua
             └─ DeptController    @OperateLog ── OperateLogAspect ── LPUSH ──┐
                                                                              │
service ─────┬─ DeptService  ── CacheService ── Redis: cache:dept:list         │
             ├─ ClazzService ── CacheService ── Redis: cache:clazz:list        │
             └─ ReportService── CacheService ── Redis: cache:report:*          │
                                                                              ▼
task ───────────────────────────────── OperateLogConsumer(@Scheduled) ── RPOP ──> MySQL
```

---

## 二、Key 规划

统一前缀 `tlias:`，按「业务域:数据类型:标识」分层，方便 `SCAN tlias:*` 观测和清理。

| Key | 结构 | TTL | 说明 |
|---|---|---|---|
| `tlias:cache:dept:list` | String(JSON) | 30min + 0~60s 抖动 | 部门列表 |
| `tlias:cache:clazz:list` | String(JSON) | 30min + 0~60s 抖动 | 班级全量列表（下拉用） |
| `tlias:cache:report:emp-job` | String(JSON) | 5min + 抖动 | 员工职位分布 |
| `tlias:cache:report:emp-gender` | String(JSON) | 5min + 抖动 | 员工性别分布 |
| `tlias:cache:report:student-degree` | String(JSON) | 5min + 抖动 | 学员学历分布 |
| `tlias:cache:report:student-count` | String(JSON) | 5min + 抖动 | 班级人数分布 |
| `tlias:lock:cache:{cacheKey}` | String | 5s | 缓存重建互斥锁 |
| `tlias:auth:token:black:{jti}` | String | = token 剩余有效期 | 登出黑名单 |
| `tlias:auth:login:fail:{username}` | String(整数) | 15min | 登录失败计数 |
| `tlias:rate:{scene}:{ip}` | String(整数) | 60s | 限流计数器 |
| `tlias:queue:operate-log` | List | 无（消费即删） | 操作日志队列 |

**为什么列表用 String 存 JSON，而不是 Hash 或 List？**
列表是整体读、整体写的（下拉选项一次全要），Hash 需要多次 HGETALL 才能组装且字段管理复杂；用 String 存一段 JSON，读是一次 GET、写是一次 SET，语义最简单。代价是**无法做字段级更新**——对部门/班级这种「改一个就得整表重查」的数据无所谓。

---

## 三、场景 1：热点数据缓存

### 3.1 为什么只缓存这三个地方

| 接口 | 是否缓存 | 理由 |
|---|---|---|
| `GET /depts` 部门列表 | ✅ | 读多写极少，一个机构也就几十条，命中率接近 100% |
| `GET /clazzs/list` 班级全量 | ✅ | 每次进页面、开弹窗都拉，且写操作很稀疏 |
| `GET /report/*` 报表 | ✅ | `GROUP BY` 聚合，数据量上来后是全表扫描，且业务上容忍几分钟延迟 |
| `GET /emps` 员工分页 | ❌ | 条件组合多（姓名/性别/时间区间/页码），key 数量爆炸且命中率低 |
| `GET /clazzs` 班级分页 | ❌ | 同上 |
| `GET /emps/{id}` 员工详情 | ❌ | 修改频繁，失效逻辑复杂，收益不抵成本 |

**原则：缓存不是越多越好。** 给分页查询加缓存是最典型的伪优化——key 数量随参数组合指数增长，命中率却很低，还会拖来一堆一致性问题。

### 3.2 Cache-Aside 的读写路径

```java
// 读：先查缓存 → 未命中查库 → 回填
List<Dept> findAll() {
    return cacheService.get(DEPT_LIST_KEY, new TypeReference<List<Dept>>() {}, TTL, deptMapper::findAll);
}

// 写：先更库 → 再删缓存
void add(Dept dept) {
    deptMapper.insert(dept);
    cacheService.evict(DEPT_LIST_KEY);
}
```

**为什么是「先更库、再删缓存」而不是「先删缓存、再更库」？**
后者在并发下会留下脏数据：线程 A 删缓存 → 线程 B 读缓存未命中，读到**旧值**并写回 → 线程 A 才更新库。结果是缓存里躺着旧值，直到 TTL 过期。
「先更库再删」的最坏情况只是「极短时间内读到旧值」，不会长期脏。

**为什么是删缓存而不是更新缓存？**
① 写多读少时，更新缓存做的都是无用功；② 并发写会互相覆盖，可能把旧值写回去；③ 删掉更简单，下次读自然回填。

### 3.3 三大缓存问题（都在 `RedisCacheServiceImpl` 里实现了）

| 问题 | 现象 | 本项目做法 |
|---|---|---|
| **穿透** | 大量请求查不存在的数据，缓存永远不命中，全打到 DB | 回源结果为空时写入 `__NULL__` 占位，TTL 60s |
| **击穿** | 某个热点 key 恰好在高并发瞬间过期，几百个线程同时回源 | 回源前 `SETNX` 抢重建锁，抢不到的线程等 100ms 后重读，仍失败才回源（**不无限等待**） |
| **雪崩** | 大批 key 同一时刻过期 / Redis 整体挂掉 | TTL 加 0~60s 随机抖动；Redis 异常时全部降级回源 |

关键实现片段：

```java
// 防击穿：只有抢到锁的线程回源
Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", LOCK_TTL);
if (Boolean.FALSE.equals(locked)) {
    sleepQuietly(100);
    String again = safeGet(key);
    if (again != null) return parse(again);
    // 仍取不到就回源，宁可多查一次库也不让 Tomcat 线程堆积
}
```

**为什么不加 `synchronized`？** 那是 JVM 级别的锁，多实例部署下各锁各的，毫无作用。

### 3.4 降级：缓存绝不能成为单点

所有 Redis 操作都被 `safeXxx` 包了一层，异常时返回中性值（null / false），调用方继续回源：

```java
private String safeGet(String key) {
    try { return redisTemplate.opsForValue().get(key); }
    catch (Exception e) { log.warn("读取缓存失败，降级回源: {}", e.getMessage()); return null; }
}
```

再加一个总开关 `app.cache.enabled`，排障或压测时可以一键关掉缓存直连数据库（`CACHE_ENABLED=false`）。

补充一个容易被忽略的细节：`count(*)` 走 MyBatis 时是 `Long`，经 JSON 往返后小数值会变成 `Integer`（数值不变）。前端 ECharts 不关心类型，但如果后端要对报表做二次计算，应该定义 DTO 而不是返回原始 `Map`。

---

## 四、场景 2：登录失败锁定（防暴力破解）

原项目任何账号都可以无限次试密码。改造后：

```
第 1~4 次失败 → "用户名或密码错误，还可尝试 N 次"
第 5 次失败   → "登录失败次数过多，账号已锁定 15 分钟"
锁定期间      → 直接拒绝，不再查库（省一次 DB 查询）
登录成功      → DEL 计数 key
```

实现要点：

```java
Long count = redisTemplate.opsForValue().increment(failKey);   // 原子自增
if (count == 1L) {
    redisTemplate.expire(failKey, lockMinutes, TimeUnit.MINUTES);  // 只在首次设置 TTL
}
```

- **为什么要判断 `count == 1` 才设过期？** 否则每次失败都 `EXPIRE`，攻击者只要一直试，计数器就永远不会过期，锁定时长被无限顺延，正常用户永远解不开。
- **为什么要用 `INCR` 而不是「GET 出来 +1 再 SET」？** 后者在并发下会丢失计数（读-改-写不是原子的）。
- **Redis 挂了怎么办？** 计数读写全部 try-catch 吞掉，退化为原始提示——限流组件故障不能演变成「所有人都登不上」。

> 说明：`INCR` 与 `EXPIRE` 之间仍有一个极小的窗口（进程恰好在两步之间崩溃 → 计数器无 TTL）。彻底严谨的做法是把这两步也放进 Lua（和限流一样）。这里保留两步是因为「首次设 TTL」本身需要判断结果，放 Lua 里要额外返回值，收益不大。

---

## 五、场景 3：登出 / 主动失效（JWT 黑名单）

### 5.1 问题

JWT 是无状态的：签发后服务端无法收回。原项目没有登出接口，token 泄露就只能等 1 小时自然过期。

### 5.2 方案

```
登录：JWT 里多带一个 jti（UUID）
登出：POST /logout → 解析 token → 取 jti + 过期时间 → SET tlias:auth:token:black:{jti} "1" EX <剩余秒数>
拦截：TokenInterceptor 解析成功后先查黑名单，命中直接 401
```

三个设计决策：

1. **存 jti 而不是整 token**：token 有几百字节，jti 是 UUID；而且整 token 作 key 在日志里容易泄露凭证。
2. **TTL 与 token 剩余有效期严格对齐**：token 自然过期后黑名单记录自动消失，内存不会无限增长。这相当于「用 TTL 做定时清理」，比写定时任务扫过期记录简单得多。
3. **查黑名单失败时放行（fail-open）**：Redis 抖动不能把全站用户踢下线。注意这与「token 签名校验失败必须拒绝」是两码事——签名是安全底线，黑名单是增强项，可用性优先级不同。

```java
Duration ttl = Duration.ofMillis(claims.getExpiration().getTime() - System.currentTimeMillis());
tokenBlacklistService.blacklist(claims.getId(), ttl);
```

### 5.3 局限（面试主动说）

黑名单是**内存态**：Redis 丢数据 → 已登出的 token 又能用了。对教学系统可接受；要严格保证，需要把「用户-密码版本号」落库，token 里带版本号，改密码时递增版本使旧 token 全局失效。

---

## 六、场景 4：接口限流（Lua 原子脚本）

### 6.1 为什么需要

`/login` 和 `/upload` 是典型的「低频高危」接口：前者被撞库，后者被刷存储费用。加了 `@RateLimit` 注解：

```java
@RateLimit(key = "login", permits = 10, windowSeconds = 60)
public Result login(@RequestBody Emp emp) { ... }

@RateLimit(key = "upload", permits = 20, windowSeconds = 60)
public Result upload(MultipartFile file) { ... }
```

### 6.2 为什么必须用 Lua

`INCR` 和 `EXPIRE` 是两条命令。分开执行会有两个问题：
1. 进程在两步之间挂掉 → 计数器 key 永不过期，内存泄漏；
2. 并发下其他线程可能读到「已自增但还没有 TTL」的中间态。

Redis 执行 Lua 时是单线程且**脚本内命令不可被其他命令插入**，所以整个「自增 + 首次设过期 + 判断超限」是原子的：

```lua
local current = redis.call('INCR', KEYS[1])
if current == 1 then
    redis.call('EXPIRE', KEYS[1], ARGV[1])
end
if current > tonumber(ARGV[2]) then
    return 0      -- 超限
end
return 1          -- 放行
```

**取 IP 的坑**：部署在 Nginx 后面时 `getRemoteAddr()` 拿到的是代理地址，等于全站共用一个计数器。所以优先读 `X-Forwarded-For` 的第一个值，再退 `X-Real-IP`，最后才用 `getRemoteAddr()`。

### 6.3 固定窗口的缺陷（要知道，但本场景够用）

固定窗口在**窗口边界**会放行两倍流量：第 59 秒打满 10 次，第 61 秒又能打满 10 次。要平滑就得换：

- **滑动窗口**：`ZSET` 存每次请求时间戳，`ZREMRANGEBYSCORE` 清理窗口外 + `ZCARD` 计数（内存随请求量增长）；
- **令牌桶 / 漏桶**：Lua 维护 token 数与上次填充时间，能应对突发流量。

登录接口的诉求是「别让脚本无限撞库」，固定窗口 10 次/分钟已经足够，没必要上令牌桶。

---

## 七、场景 5：操作日志异步化

### 7.1 改造前

```java
// 请求线程里同步写库
operateLogMapper.insert(operateLog);
```
写日志的 RT 直接叠加在业务接口上，而且日志表越来越大后 insert 会变慢。

### 7.2 改造后

```
业务线程：LPUSH tlias:queue:operate-log <json>        （一次 O(1) 的 Redis 写，耗时 ~0.2ms）
消费线程：@Scheduled 每 2s → RPOP <count=50> → 批量 INSERT
```

```java
@Scheduled(fixedDelayString = "${app.log.consume-delay:2000}")
public void consume() {
    List<String> batch = redisTemplate.opsForList().rightPop(KEY, batchSize);
    // ... 反序列化后 operateLogMapper.insertBatch(logs)
}
```

四个设计点：

1. **用 List 而不是「开线程池异步写」**：线程池里的任务在应用重启时直接丢；Redis 队列能跨重启保留。
2. **`RPOP` 天然支持多消费者**：Redis 单线程执行 `RPOP`，一条消息只会被一个消费者取走，多实例部署不需要额外加锁。
3. **降级**：`LPUSH` 失败（Redis 挂了）或队列积压超 1 万条时，自动退回同步写库，日志不丢。
4. **落库失败重推**：`insertBatch` 抛异常时把原始消息 `LPUSH` 回队列等下一轮，而不是直接丢弃。

### 7.3 这个方案的可靠性边界（面试会被问）

`RPOP` 之后、`INSERT` 之前如果进程崩溃，**这批日志就丢了**（消息已出队且未落库）。这是「List 队列」的天花板。要严格可靠，用 **Redis Stream**：

```
XADD stream * ...           生产
XREADGROUP GROUP g c COUNT 50 STREAMS stream >   消费（进 PEL，Pending Entries List）
XACK stream g <id>          落库成功才确认
XCLAIM ...                  处理消费者宕机遗留的 pending 消息
```

代价是要处理 pending 消息的补偿逻辑，复杂度高不少。教学系统的操作日志丢个位数可接受，所以先用 List；把 Stream 作为演进方向写在文档里，面试时能体现「知道代价」而不是「不知道」。

### 7.4 运维要点

- **队列不能被淘汰**：Redis 建议配 `volatile-lru`（只淘汰**设置了 TTL** 的 key）。缓存 key 都有 TTL、队列 key 没有，这样内存吃紧时牺牲的是缓存而不是日志。若用 `allkeys-lru`，队列 key 可能被直接淘汰。
- **持久化**：缓存不需要，丢了能重建；队列需要 AOF `everysec`，否则 Redis 重启会丢掉最多 1s 的日志。
- **监控 `LLEN tlias:queue:operate-log`**：持续增长说明消费跟不上或 DB 写不动。

---

## 八、验证步骤

### 8.1 启动依赖

```bash
# Redis（Docker 最省事）
docker run -d --name tlias-redis -p 6379:6379 redis:7-alpine
# MySQL 保持原配置 localhost:3306/tlias
```

### 8.2 启动应用并观察

```bash
cd tlias-web-management
mvn spring-boot:run
```

```bash
# 1. 缓存是否生效：连续调两次 /depts
curl -H "token: <TOKEN>" http://localhost:8080/depts
redis-cli get tlias:cache:dept:list        # 应返回部门 JSON 数组
redis-cli ttl tlias:cache:dept:list        # 应在 1740~1860 之间（30min + 抖动）

# 2. 写操作是否清缓存：新增部门后再看 key
curl -X POST -H "token: <TOKEN>" -H "Content-Type: application/json" \
     -d '{"name":"测试部"}' http://localhost:8080/depts
redis-cli exists tlias:cache:dept:list     # 应返回 0（已被删除）

# 3. 登出后 token 是否失效
curl -X POST -H "token: <TOKEN>" http://localhost:8080/logout
redis-cli keys 'tlias:auth:token:black:*'
curl -H "token: <TOKEN>" http://localhost:8080/depts   # 应返回 401

# 4. 登录失败锁定：连续 5 次错误密码
for i in 1 2 3 4 5; do
  curl -X POST -H "Content-Type: application/json" \
       -d '{"username":"zhangsan","password":"wrong"}' http://localhost:8080/login
done
redis-cli get tlias:auth:login:fail:zhangsan   # 应为 5
redis-cli ttl tlias:auth:login:fail:zhangsan   # 约 900

# 5. 限流：1 分钟内打 11 次登录
redis-cli get tlias:rate:login:127.0.0.1       # 第 11 次返回「登录请求过于频繁」

# 6. 日志队列：调几个增删改接口后看队列
redis-cli llen tlias:queue:operate-log
# 等 2s 后队列应为 0，且 operate_log 表多出对应记录
```

### 8.3 已完成的自动化验证

```bash
mvn test
```

- 68 个源文件编译成功；
- `TliasWebManagementApplicationTests#contextLoads` 通过（7.3s）——说明新增的 12 个 Bean、`@Scheduled`、`@Aspect`、`@Value` 配置都能正确装配，无循环依赖。

---

## 九、为什么没做分布式锁

这是最容易被问「你是不是漏了」的点，所以要能讲清**为什么不适用**：

- 项目里唯一的并发写场景是**违纪扣分**，已经用 `violation_score = violation_score + #{score}` 的数据库原子自增解决，不需要额外加锁；
- 「删除部门前校验是否有员工」属于 **TOCTOU**（检查后、删除前状态可能变），理论上需要锁，但那是管理端低频操作，用数据库外键/事务隔离级别更划算；
- 缓存重建用的是 `SETNX` 短锁，本质就是分布式锁的最简形式。

如果后续加「班级名额抢报」这类高并发写，方案是 Redisson `RLock`（带看门狗自动续期）或自己用 `SET key value NX PX 30000` + Lua 比对 value 释放（防止误删别人的锁）。

---

## 十、数据一致性策略总表

| 数据类型 | 一致性要求 | 策略 |
|---|---|---|
| 部门 / 班级列表 | 强（改完立刻要看到） | 写后删缓存（先更库再删） |
| 统计报表 | 弱（容忍 5 分钟） | 只靠 TTL，不做主动失效 |
| 登录失败计数 | 弱（丢几次不影响安全） | 无持久化要求，Redis 挂了退化为不计数 |
| token 黑名单 | 中（丢数据 = 已登出 token 复活） | TTL 对齐，可接受；严格场景需落库版本号 |
| 操作日志 | 高（审计） | List 队列 + 失败重推；最高要求用 Stream |

**为什么报表不做主动失效？** 如果在 `StudentService` 的增删改里逐个 `evict` 报表 key，写路径就和缓存强耦合了——每加一个写接口都要记得清一遍，漏一处就是脏数据。用 5 分钟 TTL 换取「写路径零感知」，在报表场景是更划算的交易。**这是个明确的取舍，不是遗漏。**

---

## 十一、面试问答速查

| 问题 | 答题要点 |
|---|---|
| 为什么用 Redis 不用本地 Caffeine？ | 多实例部署时本地缓存各存各的，A 实例改完 B 实例看不到。Redis 集中式天然一致。**单机纯读场景 Caffeine 更快（无网络开销）**，最佳实践是两级：Caffeine（L1，秒级 TTL）+ Redis（L2）。 |
| 缓存穿透/击穿/雪崩分别怎么解决？ | 穿透→空值缓存或布隆过滤器；击穿→互斥重建（SETNX）或逻辑过期；雪崩→TTL 抖动 + 熔断降级 + 集群高可用。 |
| 为什么先更库再删缓存？ | 见 3.2。补充：极端情况下「先更库再删」仍可能读到旧值（删缓存前的读请求把旧值写回），彻底解决要靠延迟双删或 binlog 订阅（Canal）。 |
| 延迟双删是什么？ | 更新库 → 删缓存 → 延迟 500ms~1s 再删一次。第二次删是为了清掉「更新期间被并发读回填的旧值」。缺点是延迟时间难估、且第二次删失败就失效。 |
| TTL 为什么要加随机值？ | 同一批写入的 key 会在同一秒集体过期，瞬间全部回源 → 雪崩。加 0~60s 抖动把失效时间打散。 |
| Lua 为什么能保证原子？ | Redis 单线程执行脚本，中间不插入其他命令；且脚本整体是一个原子操作对外可见。注意脚本要短（阻塞主线程），且不要用随机命令（主从复制不一致）。 |
| 固定窗口限流的缺点？ | 临界突刺：窗口边界可能放过 2 倍流量。改进：滑动窗口（ZSET 存时间戳）或令牌桶。 |
| JWT 怎么做登出？ | 无状态所以服务端要存失效名单。按 jti 存 Redis，TTL = token 剩余有效期。更彻底：token 带用户密码版本号，改密码即全量失效。 |
| 黑名单存 Redis 挂了怎么办？ | 设计成 fail-open（放行），可用性优先。签名校验仍然 fail-closed。 |
| 为什么日志用 List 不用 Stream？ | List + RPOP 足够简单且原子，多消费者不重复。缺点是「出队后未落库时崩溃会丢」。Stream 有消费组 + PEL + XACK，可靠性更高但复杂度大。 |
| Redis 挂了系统会怎么样？ | 缓存降级回源（变慢但可用）、登录计数失效（退回原逻辑）、限流放行、日志退回同步写库。**全链路没有一条路径因 Redis 不可用而整体不可用。** |
| 缓存和队列放同一个 Redis 实例有问题吗？ | 有：淘汰策略可能把队列 key 淘汰。用 `volatile-lru` + 队列不设 TTL 可以规避；更稳妥是队列独立实例/独立 db。 |
| 为什么不用 `@Cacheable`？ | Spring Cache 默认无法方便地实现空值缓存、互斥重建、TTL 抖动和降级，封装一层 `CacheService` 反而更可控，也便于统一改 Key 规范。 |

---

## 十二、遗留问题（本次未处理）

本次只做 Redis 相关改造，前一轮代码审查里的问题仍然存在，**不要误以为已经修完**：

- 员工密码明文存储 + `select e.*` 把密码返回前端（P0-1 / P0-2）
- 员工更新跨表操作缺 `@Transactional`（P0-4）
- 修改班级会把 `create_time` 置空（P0-5）
- `GlobalExceptionHander` 解析 `Duplicate entry` 的越界风险（P1-7）
- 无参数校验、无建表脚本

详见 [代码审查报告](代码审查报告.md)。
