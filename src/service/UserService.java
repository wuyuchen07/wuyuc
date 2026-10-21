package service;

// 导入接口 导入用户数据类型
import dao.UserDao;
import dao.impl.UserDaoImpl;
import entity.User;

import java.util.List;
import java.util.regex.Pattern;


public final class UserService {
    // 一些常量的声明
    private static final int MAX_USERNAME = 50;      // varchar(50)
    private static final int MAX_PASSWORD = 255;     // varchar(255)，库里存的是加密后的串
    private static final int MAX_EMAIL = 100;        // varchar(100)
    private static final int MAX_PHONE = 20;         // varchar(20)
    private static final int MAX_AGE = 127;          // age  tinyint（-128~127）
    private static final int MAX_LIMIT = 100;        // 一次排序查询最多返回 100 条

    // 状态：0-禁用，1-正常（对应 status tinyint NOT NULL DEFAULT 1）
    private static final int STATUS_DISABLED = 0;
    private static final int STATUS_NORMAL = 1;

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[\\w.-]+@[\\w-]+(\\.[\\w-]+)+$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1\\d{10}$");

    // 调接口
    private final UserDao userDao;
    // 默认采用jdbk
    public UserService(){
        this(new UserDaoImpl());
    }
    // 父类接口 可以接收多个子类接口 从而实现userDao的多态
    // 更改userDao 只需要在创建对象时选定传入即可
    // 低耦合
    public UserService(UserDao userDao){
        if(userDao == null){
            throw new IllegalArgumentException("userDao 不能为空");
        }
        this.userDao = userDao;
    }

    /* ==================== 写操作 ==================== */

    /**
     * 注册一个新用户。
     *
     * 规则：用户名/密码必填；用户名、邮箱唯一（对应 uk_username / uk_email）；
     * 年龄 0~127；状态固定为正常。
     *
     * @return 是否插入成功
     * @throws IllegalArgumentException 参数不合法，或用户名/邮箱已存在
     */
    public boolean register(User u){
        checkBaseFields(u);
        checkUsernameUnique(u.getUsername(), 0L);
        if(u.getEmail() != null){
            checkEmailUnique(u.getEmail(), 0L);
        }
        u.setStatus(STATUS_NORMAL);   // 注册即启用（int 默认 0 会被当成"禁用"）
        return userDao.insert(u);
    }

    /**
     * 按 id 修改用户。
     *
     * 规则：用户必须先存在；字段规则同注册；唯一性校验要排除自己。
     *
     * @throws IllegalArgumentException id 非法、用户不存在、参数不合法或用户名/邮箱被占用
     */
    public boolean update(User u){
        if(u == null){
            throw new IllegalArgumentException("user 不能为 null");
        }
        requireId(u.getId());
        checkBaseFields(u);

        if(userDao.selectById(u.getId()) == null){
            throw new IllegalArgumentException("用户不存在: id=" + u.getId());
        }
        checkUsernameUnique(u.getUsername(), u.getId());
        if(u.getEmail() != null){
            checkEmailUnique(u.getEmail(), u.getId());
        }
        return userDao.updateById(u);
    }

    /** 按 id 删除用户。 */
    public boolean delete(long id){
        requireId(id);
        if(userDao.selectById(id) == null){
            throw new IllegalArgumentException("用户不存在: id=" + id);
        }
        return userDao.deleteById(id);
    }

    /* ==================== 读操作 ==================== */

    /** 按 id 查询，查不到返回 null。 */
    public User getById(long id){
        requireId(id);
        return userDao.selectById(id);
    }

    /** 按用户名精确查询，查不到返回 null。 */
    public User getByUsername(String username){
        String name = trimToNull(username);
        if(name == null){
            throw new IllegalArgumentException("用户名不能为空");
        }
        return userDao.selectByUsername(name);
    }

    /** 查询全部用户。 */
    public List<User> listAll(){
        return userDao.selectAll();
    }

    /** 按用户名模糊查询；关键字为空时返回全部。 */
    public List<User> searchByUsername(String keyword){
        String kw = trimToNull(keyword);
        if(kw == null){
            return userDao.selectAll();
        }
        return userDao.selectByUsernameLike(kw);
    }

    /** 排序后取前 limit 条（可选排序列由 DAO 的白名单把关）。 */
    public List<User> topBy(String column, boolean asc, int limit){
        String col = trimToNull(column);
        if(col == null){
            throw new IllegalArgumentException("排序字段不能为空");
        }
        if(limit < 1 || limit > MAX_LIMIT){
            throw new IllegalArgumentException("limit 必须在 1~" + MAX_LIMIT + " 之间");
        }
        return userDao.selectSorted(col, asc, limit);
    }

    /** 统计年龄大于 age 的用户数。 */
    public int countAgeGreaterThan(int age){
        if(age < 0){
            throw new IllegalArgumentException("age 不能为负数");
        }
        return userDao.countByAgeGreaterThan(age);
    }

    /* ==================== 唯一性校验 ==================== */

    /** @param excludeId 新增传 0；修改传自己的 id，避免和自己冲突 */
    private void checkUsernameUnique(String username, long excludeId){
        User existed = userDao.selectByUsername(username);
        if(existed != null && existed.getId() != excludeId){
            throw new IllegalArgumentException("用户名已存在: " + username);
        }
    }

    /** @param excludeId 新增传 0；修改传自己的 id，避免和自己冲突 */
    private void checkEmailUnique(String email, long excludeId){
        User existed = userDao.selectByEmail(email);
        if(existed != null && existed.getId() != excludeId){
            throw new IllegalArgumentException("邮箱已被使用: " + email);
        }
    }

    /*
    服务层查询常见问题 (内部小工具)
    1. 字符串匹配
    2. id 检验
    */
    private static String trimToNull(String s){
        if(s == null){
            return null;
        }
        // 返回字符串的副本，忽略前导空白和尾部空白。
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static void requireId(long id){
        if(id <= 0){
            throw new IllegalArgumentException("id 必须 > 0");
        }
    }

    /** 校验 user 的业务字段，并把字符串统一 trim 后写回对象。 */
    private static void checkBaseFields(User u) {
        if (u == null) {
            throw new IllegalArgumentException("user 不能为 null");
        }

        String username = trimToNull(u.getUsername());
        if (username == null) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (username.length() > MAX_USERNAME) {
            throw new IllegalArgumentException("用户名长度不能超过 " + MAX_USERNAME);
        }
        u.setUsername(username);

        // 注意：表里的 password 存的是加密后的串（示例数据是 bcrypt），这里只校验非空和长度
        String password = u.getPassword();
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        if (password.length() > MAX_PASSWORD) {
            throw new IllegalArgumentException("密码长度不能超过 " + MAX_PASSWORD);
        }

        // email 可空；给了才校验
        String email = trimToNull(u.getEmail());
        if (email != null) {
            if (email.length() > MAX_EMAIL) {
                throw new IllegalArgumentException("邮箱长度不能超过 " + MAX_EMAIL);
            }
            if (!EMAIL_PATTERN.matcher(email).matches()) {
                throw new IllegalArgumentException("邮箱格式不正确: " + email);
            }
        }
        u.setEmail(email);

        // phone 可空；给了才校验
        String phone = trimToNull(u.getPhone());
        if (phone != null) {
            if (phone.length() > MAX_PHONE) {
                throw new IllegalArgumentException("手机号长度不能超过 " + MAX_PHONE);
            }
            if (!PHONE_PATTERN.matcher(phone).matches()) {
                throw new IllegalArgumentException("手机号格式不正确: " + phone);
            }
        }
        u.setPhone(phone);

        // age 在数据库里是 tinyint（-128~127）
        if (u.getAge() < 0 || u.getAge() > MAX_AGE) {
            throw new IllegalArgumentException("年龄必须在 0~" + MAX_AGE + " 之间");
        }

        if (u.getStatus() != STATUS_DISABLED && u.getStatus() != STATUS_NORMAL) {
            throw new IllegalArgumentException("状态只能是 0 或 1");
        }
    }
}
