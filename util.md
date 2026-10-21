## 一、整体骨架

```java
public final class SqlUtils {                 // ①
    private static final String USER_COLUMNS = "...";        // ②
    private static final Set<String> SORTABLE_COLUMNS = ...; // ③
    private SqlUtils() { throw new AssertionError(...); }    // ④

    /* 通用方法 */   executeUpdate / executeQuery / RowMapper
    /* user 映射 */  mapUser
    /* user 增删改 */insert / updateById / deleteById
    /* user 查询 */  selectById / selectAll / selectByUsernameLike / selectSorted / countByAgeGreaterThan
    /* 内部工具 */   bind / checkSql / requireUser
}
```
API：

| 你想干的事                  | 调用                                                         |
| --------------------------- | ------------------------------------------------------------ |
| 跑任意 insert/update/delete | `SqlUtils.executeUpdate(sql, 值1, 值2)`                      |
| 跑任意 select               | `SqlUtils.executeQuery(sql, 映射器, 值1)`                    |
| 加用户                      | `SqlUtils.insert(user)`                                      |
| 改用户                      | `SqlUtils.updateById(user)`                                  |
| 删用户                      | `SqlUtils.deleteById(3)`                                     |
| 查一个 / 查全部             | `SqlUtils.selectById(1)` / `SqlUtils.selectAll()`            |
| 模糊查 / 排序取前 n / 统计  | `selectByUsernameLike("%张%")` / `selectSorted("age", true, 3)` / `countByAgeGreaterThan(20)` |

---

## 二、一次调用到底发生了什么

以 `SqlUtils.selectById(1)` 为例，控制流是这样一层层钻进去的：

```
selectById(1)                                   ← 你调用
 └─ executeQuery("select <列...> from user where id = ?", SqlUtils::mapUser, 1)   [L173→L73]
     ├─ checkSql(sql)                     非空校验，不合格直接抛 IllegalArgumentException
     ├─ MysqlConector.getConnection()      拿一条连接
     ├─ conn.prepareStatement(sql)         把 SQL 预编译：结构此刻定死，? 只是洞
     ├─ bind(ps, {1})                      setObject(1, 1L)   ← 只往洞里塞【值】
     ├─ ps.executeQuery()                  执行，返回 ResultSet
     ├─ while (rs.next()) rows.add(mapper.map(rs))   每行交给 mapUser 变成一个 User
     └─ try-with-resources 自动收尾：rs → ps → conn（反序关闭）
 └─ rows.isEmpty() ? null : rows.get(0)    列表转单个对象
```

**关键认识**：`executeQuery` 是"通用引擎"，`selectById` 只是"给引擎喂了一句 SQL 和一个映射器"。新增查询只要写一句 SQL，不用再复制一遍连接、绑定、循环、关闭那套样板代码 —— 这就是工具类的价值。

---

## 三、逐块讲

### ① `final` + 私有构造

```java
public final class SqlUtils {          // final：不允许被继承
    private SqlUtils() {               // private：外面 new 不出来
        throw new AssertionError("工具类不允许实例化");
    }
```
- Java **没有"静态类"**（你之前那个 `public static class` 报错就是这原因），规范上用 `final` + 私有构造来表达同一个意思：**这个类只提供静态方法，不需要也不能有实例**。
- 构造里抛 `AssertionError` 是双保险：反射也能挡住（`setAccessible(true)` 调构造照样炸）。
- 代价：正因为纯静态，它**没法被 mock、没法替换实现**，测试时不好隔离

### ② `USER_COLUMNS`

```java
private static final String USER_COLUMNS = "id, username, password, email, phone, age, status, create_time, update_time";
```
列名写一遍，5 个查询方法共用。以后表加字段，**只改这一行**。这比每个方法里写 `select *` 好：`*` 拿回来的列顺序/数量会随表结构变，映射代码容易悄悄崩。

### ③ `SORTABLE_COLUMNS`

```java
private static final Set<String> SORTABLE_COLUMNS = Set.of("id", "username", "age", "status", "create_time");
```
`Set.of(...)` 造的是**不可变集合**，没人能往里加东西。它的作用在 [`selectSorted`](src/util/SqlUtils.java#L209-L219)：排序字段必须拼进 SQL（`?` 拼不进去），那就只允许这几个词 —— 传 `"password; drop table user"` 会被直接拦掉（实测已验证）。

### ④ `executeUpdate`

```java
public static boolean executeUpdate(String sql, Object... params) {
    checkSql(sql);
    try (Connection conn = MysqlConector.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
        bind(ps, params);
        return ps.executeUpdate() > 0;
    } catch (SQLException e) {
        e.printStackTrace();
        return false;
    }
}
```
- **`Object... params`** 是可变参数，编译后就是个 `Object[]`；所以你能写 `executeUpdate(sql, a, b, c)`，也能一个都不传。这也是它在内部能把 `params` 直接当数组传给 [`bind`](src/util/SqlUtils.java#L231-L235) 的原因。
- **`try (...)` 里声明两个资源**，正常/异常退出都会自动 `close()`，关闭顺序**反过来**（先 ps 后 conn）。你再也不用手写 `finally { conn.close(); }`。
- **`> 0`** 表示至少影响一行。注意一个坑：`update` 时**影响 0 行不等于失败** —— 可能只是"新值和旧值一模一样"。严格判断要连 `update` 的返回值一起看。
- **异常被 `catch` 掉并返回 false**（见第 4 节，这是这个类的最大取舍）。

### ⑤ `executeQuery`

```java
public static <T> List<T> executeQuery(String sql, RowMapper<T> mapper, Object... params) {
    List<T> rows = new ArrayList<>();
    try (... ) {
        bind(ps, params);
        try (ResultSet rs = ps.executeQuery()) {     // ← 查询必须用 executeQuery
            while (rs.next()) {
                rows.add(mapper.map(rs));             // 一行 → 一个对象
            }
        }
    } catch (SQLException e) { e.printStackTrace(); }
    return rows;                                       // ← 永不返回 null
}
```
这里有三层设计：

1. **`<T>` 泛型方法**：`T` 在方法名前面声明，返回 `List<T>`。调用时 `SqlUtils::mapUser` 让编译器推出 `T = User`，所以 `selectById` 能直接接 `List<User>`。
2. **`mapper` 回调**：工具类**不知道**也不该知道业务对象，它只负责"把每一行递给你的函数"。这叫**把变化的部分参数化** —— 唯一的变量是"这一行怎么变成对象"，于是抽出成 `RowMapper`。
3. **`ResultSet` 绝不外泄**：`rs` 的作用域被锁在 try 块里，方法出来时它已经关了。这逼着 `mapper` **必须同步、当场**把行读完转成对象，不能把 `rs` 存到字段里稍后再读（那样会读到"结果集已关闭"报错）。正确性靠作用域保证，不靠注释提醒。

顺带：`select` 查不到数据时返回**空集合**（`isEmpty()` 为 true），而不是 `null` —— 调用方不用到处判空，`for` 循环也直接安全。这是"返回空集合优于返回 null"的规范实践。

### ⑥ `RowMapper`

```java
@FunctionalInterface
public interface RowMapper<T> {
    T map(ResultSet rs) throws SQLException;
}
```
- **只有一个抽象方法**，所以能写成 lambda 或方法引用。
- `SqlUtils::mapUser` 就是方法引用，完全等价于 `rs -> SqlUtils.mapUser(rs)`；[`countByAgeGreaterThan`](src/util/SqlUtils.java#L221-L227) 里则干脆用 lambda `rs -> rs.getInt(1)`，因为它只需要一列数字。
- 它也声明了 `throws SQLException`，所以映射代码里可以直接用 `rs.getString(...)`，不必自己包 try。

### ⑦ `mapUser`

```java
u.setUsername(rs.getString("username"));   // 用列名，不用序号
```
用**列名**取值，抗 SQL 列顺序变化，可读性也好。唯一用序号的是 `rs.getInt(1)`（`count(*)` 只返回一列，用序号反而清楚）。

### ⑧ 增删改三个方法

```java
public static boolean deleteById(long id) {
    if (id <= 0) throw new IllegalArgumentException("id 必须 > 0");   // ← 防呆
    return executeUpdate("delete from user where id = ?", id);
}
```
- 这三个都是"**拼一句写死的 SQL，把值丢给 `executeUpdate`**"，非常薄。它们存在的意义是：把 SQL 和参数顺序**收进一个地方**，调用方不会拼错。
- `deleteById` 拒绝 `id <= 0`，是为了防止"条件写歪导致 `delete from user` 删全表"这类事故。
- `updateById` 要求 `id > 0`，否则一句 `where id = ?` 绑个 0 进去什么也改不到，静默失败最难查。

### ⑨ 常量与内部小工具

```java
private static void bind(PreparedStatement ps, Object[] params) throws SQLException {
    for (int i = 0; i < params.length; i++) {
        ps.setObject(i + 1, params[i]);   // 注意：JDBC 下标从 1 开始！
    }
}
```
- **JDBC 参数下标是 1 起**
- 用 `setObject` 而不是 `setString/setInt`：让驱动按 Java 类型自己选，少写一堆分支。代价是性能略低、类型错误会推迟到运行期才暴露；练习阶段完全够用。
- `checkSql` / `requireUser` 就是"前置校验提取成私有方法"，让主流程读起来干净。

---

## 四、这个设计的取舍与局限（要能说出来才算懂）

| 设计                                             | 好处                                  | 代价 / 风险                                                  |
| ------------------------------------------------ | ------------------------------------- | ------------------------------------------------------------ |
| 异常 `printStackTrace()` 后返回 `false` / 空集合 | 调用方不用处理 `SQLException`，代码短 | **"数据库连不上"和"查不到数据"分不清了** —— `selectAll()` 返回空列表时你无法判断是没数据还是连接炸了。正式项目应 `throws SQLException` 上抛，或抛自己的业务异常，并交给日志框架 |
| 每个方法都 `MysqlConector.getConnection()`       | 简单、不会忘记关连接                  | **每次调用新建一条 TCP 连接**，慢。生产用连接池（`javax.sql.DataSource` + HikariCP） |
| 纯静态工具类                                     | 调用方便                              | 无法 mock / 无法替换实现，单元测试要连真库                   |
| user 表方法写在 `SqlUtils` 里                    | 练习方便                              | 业务 SQL 混进通用工具类，违反分层。规范做法：`SqlUtils` 只留 `executeUpdate` / `executeQuery` 两个通用方法，`selectById` 这些挪到 `dao/impl/UserDaoImpl` |

另外一个细节：传 `null` 值进去时，`setObject(i, null)` 在 MySQL 驱动上能正常发 NULL，但 JDBC 规范允许某些驱动报错；严格写法是 `ps.setNull(i, Types.VARCHAR)`。目前这套代码没用到 null 参数，先知道有这回事就行。
