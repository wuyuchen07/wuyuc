package dao.impl;

import dao.UserDao;
import entity.User;
import util.SqlUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

/**
 * {@link UserDao} 的 JDBC 实现。
 * <p>SQL 语句、参数传递、结果行映射；
 */
public final class UserDaoImpl implements UserDao {

    /** user 表查询列，集中维护。 */
    private static final String USER_COLUMNS =
            "id, username, password, email, phone, age, status, create_time, update_time";

    /** 允许拼入 order by 的列，防 SQL 注入。 */
    private static final Set<String> SORTABLE_COLUMNS =
            Set.of("id", "username", "age", "status", "create_time");

    @Override
    public boolean insert(User u) {
        requireUser(u);
        String sql = "insert into user(username, password, email, phone, age, status) "
                   + "values(?, ?, ?, ?, ?, ?)";
        return SqlUtils.executeUpdate(sql, u.getUsername(), u.getPassword(), u.getEmail(),
                u.getPhone(), u.getAge(), u.getStatus());
    }

    @Override
    public boolean updateById(User u) {
        requireUser(u);
        if (u.getId() <= 0) {
            throw new IllegalArgumentException("按 id 更新时 id 必须 > 0");
        }
        String sql = "update user set username = ?, password = ?, email = ?, phone = ?, "
                   + "age = ?, status = ? where id = ?";
        return SqlUtils.executeUpdate(sql, u.getUsername(), u.getPassword(), u.getEmail(),
                u.getPhone(), u.getAge(), u.getStatus(), u.getId());
    }

    @Override
    public boolean deleteById(long id) {
        if (id <= 0) {
            throw new IllegalArgumentException("id 必须 > 0");
        }
        return SqlUtils.executeUpdate("delete from user where id = ?", id);
    }

    @Override
    public User selectById(long id) {
        List<User> rows = SqlUtils.executeQuery(
                "select " + USER_COLUMNS + " from user where id = ?",
                UserDaoImpl::mapUser, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public User selectByUsername(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        List<User> rows = SqlUtils.executeQuery(
                "select " + USER_COLUMNS + " from user where username = ?",
                UserDaoImpl::mapUser, username);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public User selectByEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        List<User> rows = SqlUtils.executeQuery(
                "select " + USER_COLUMNS + " from user where email = ?",
                UserDaoImpl::mapUser, email);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public List<User> selectAll() {
        return SqlUtils.executeQuery("select " + USER_COLUMNS + " from user",
                UserDaoImpl::mapUser);
    }

    @Override
    public List<User> selectByUsernameLike(String keyword) {
        if (keyword == null) {
            throw new IllegalArgumentException("keyword 不能为 null");
        }
        return SqlUtils.executeQuery(
                "select " + USER_COLUMNS + " from user where username like ?",
                UserDaoImpl::mapUser, "%" + keyword + "%");
    }

    @Override
    public List<User> selectSorted(String column, boolean asc, int limit) {
        if (!SORTABLE_COLUMNS.contains(column)) {
            throw new IllegalArgumentException("不支持的排序字段: " + column);
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit 必须 > 0");
        }
        String sql = "select " + USER_COLUMNS + " from user order by " + column
                   + (asc ? " asc" : " desc") + " limit ?";
        return SqlUtils.executeQuery(sql, UserDaoImpl::mapUser, limit);
    }

    @Override
    public int countByAgeGreaterThan(int age) {
        List<Integer> rows = SqlUtils.executeQuery(
                "select count(*) from user where age > ?",
                rs -> rs.getInt(1), age);
        return rows.isEmpty() ? 0 : rows.get(0);
    }

    /* ==================== 行映射与校验 ==================== */

    /** 一行 user 记录 → 一个 User 对象。 */
    private static User mapUser(ResultSet rs) throws SQLException {
        User u = new User();
        u.setId(rs.getLong("id"));
        u.setUsername(rs.getString("username"));
        u.setPassword(rs.getString("password"));
        u.setEmail(rs.getString("email"));
        u.setPhone(rs.getString("phone"));
        u.setAge(rs.getInt("age"));
        u.setStatus(rs.getInt("status"));
        u.setCreate_time(rs.getString("create_time"));
        u.setUpdate_time(rs.getString("update_time"));
        return u;
    }

    private static void requireUser(User u) {
        if (u == null) {
            throw new IllegalArgumentException("user 不能为 null");
        }
    }
}
