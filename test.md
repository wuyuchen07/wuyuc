要在 Java 中连接数据库并在终端实现 CRUD（增删改查），最标准且利于维护的做法是采用**分层架构（三层架构）**。这样可以做到职责分离，代码清晰。

下面为你设计一套标准的类结构，并解释它们之间的关系。以 MySQL 数据库和 `User`（用户）表为例。

### 📁 项目结构图

```text
src
 └─ com.example.crud
     ├─ entity          (实体层：存放数据模型)
     │   └─ User.java
     ├─ util            (工具层：存放通用工具)
     │   └─ DBUtil.java
     ├─ dao             (数据访问层：定义操作接口)
     │   ├─ UserDao.java
     │   └─ impl
     │       └─ UserDaoImpl.java
     ├─ service         (业务逻辑层：处理业务规则)
     │   └─ UserService.java
     └─ main            (视图/控制层：终端交互)
         └─ MainApp.java
```

---

### 🧱 各层详细设计与关系

#### 1. 实体层 (Entity / Model)
*   **作用**：对应数据库中的表结构。一张表对应一个 Java 类。
*   **类**：`User.java`
*   **设计**：包含私有属性（如 id, name, age）、无参构造器、全参构造器、Getter/Setter 方法、`toString()` 方法。
*   **关系**：被 DAO 层和 Service 层使用，作为数据传递的载体（DTO）。

```java
public class User {
    private int id;
    private String name;
    private int age;
    // 构造方法、getter/setter、toString 省略
}
```

#### 2. 工具层 (Util)

*   **作用**：封装数据库连接和资源释放的重复代码。
*   **类**：`DBUtil.java`
*   **设计**：
    *   加载数据库驱动（JDBC 4.0 后通常可省略，但显式写更稳妥）。
    *   提供 `getConnection()` 静态方法返回 `java.sql.Connection`。
    *   提供 `close(Connection, Statement, ResultSet)` 静态方法释放资源（推荐使用 Java 7+ 的 `try-with-resources` 替代手动关闭）。
*   **关系**：被 DAO 层的实现类调用。

```java
public class DBUtil {
    private static final String URL = "jdbc:mysql://localhost:3306/your_db?useSSL=false&serverTimezone=UTC";
    private static final String USER = "root";
    private static final String PASSWORD = "your_password";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}
```

#### 3. 数据访问层 (DAO - Data Access Object)
*   **作用**：直接与数据库打交道，执行 SQL 语句。不包含业务逻辑。
*   **接口**：`UserDao.java`（定义规范，方便后续切换数据库实现）。
*   **实现类**：`UserDaoImpl.java`（编写具体的 JDBC 代码，如 `PreparedStatement` 执行 SQL）。
*   **关系**：依赖 Entity 层（接收和返回 User 对象）和 Util 层（获取连接）。

```java
public interface UserDao {
    void insert(User user);
    void update(User user);
    void deleteById(int id);
    User findById(int id);
    List<User> findAll();
}

// UserDaoImpl.java 中通过 DBUtil.getConnection() 获取连接并执行 SQL
```

#### 4. 业务逻辑层 (Service)
*   **作用**：处理业务规则（例如：验证年龄不能为负数、名字不能为空等），并调用 DAO 层。
*   **类**：`UserService.java`
*   **关系**：依赖 DAO 层和 Entity 层。它不直接接触数据库。
*   **终端项目的简化**：如果只是简单的练习，这一层可以省略，直接在 `MainApp` 中调用 DAO。但加上它能体现完整的分层思维。

```java
public class UserService {
    private UserDao userDao = new UserDaoImpl();

    public void registerUser(User user) {
        if (user.getName() == null || user.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        userDao.insert(user);
    }
    // 其他业务方法...
}
```

#### 5. 视图/控制层 (Main / View)
*   **作用**：与用户交互。负责在终端打印菜单、接收用户输入（`Scanner`），并调用 Service 层。
*   **类**：`MainApp.java`（包含 `main` 方法）。
*   **关系**：依赖 Service 层和 Entity 层。

```java
public class MainApp {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        UserService userService = new UserService();

        while (true) {
            System.out.println("1. 添加用户  2. 查询所有  3. 修改  4. 删除  5. 退出");
            int choice = scanner.nextInt();
            // 根据 choice 调用 userService 的方法，并打印结果
        }
    }
}
```

---

### 🔄 数据流向（调用关系）

当用户在终端输入 `1`（添加用户）时，数据流向如下：

```text
用户输入数据
   ↓
MainApp (终端接收，封装成 User 对象)
   ↓
UserService (校验数据合法性)
   ↓
UserDaoImpl (生成 SQL: INSERT INTO users...)
   ↓
DBUtil (获取数据库连接)
   ↓
MySQL 数据库 (执行插入)
   ↑
返回结果，逐层向上返回，最终在终端打印 "添加成功"
```

### ⚠️ 核心注意事项

1.  **JDBC 驱动**：务必在项目中导入 MySQL 的 JDBC 驱动包（例如 `mysql-connector-j-8.x.jar`），否则会报 `ClassNotFoundException`。
2.  **防止 SQL 注入**：DAO 层中**必须使用 `PreparedStatement`** 而不是 `Statement`，通过 `?` 占位符传参。
3.  **资源释放**：务必使用 `try-with-resources` 语法（如下所示），确保 `Connection`、`PreparedStatement`、`ResultSet` 用完后自动关闭，防止内存泄漏和数据库连接耗尽。

```java
// try-with-resources 示例
String sql = "INSERT INTO users(name, age) VALUES(?, ?)";
try (Connection conn = DBUtil.getConnection();
     PreparedStatement pstmt = conn.prepareStatement(sql)) {
    pstmt.setString(1, user.getName());
    pstmt.setInt(2, user.getAge());
    pstmt.executeUpdate();
} catch (SQLException e) {
    e.printStackTrace();
}
```

按照这个结构去写，你的代码会非常规范，以后如果想加新功能（比如增加一个“根据名字模糊查询”），只需要在 DAO 加方法，在 Service 加逻辑，在 Main 加菜单即可，不会互相干扰。