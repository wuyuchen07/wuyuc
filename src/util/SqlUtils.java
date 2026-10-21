package util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 通用 JDBC 工具类：只提供与具体表无关的「写 / 读」能力。
 *
 * <p>职责边界：
 * <ul>
 *   <li>本类只负责连接获取、参数绑定、语句执行、资源释放；</li>
 *   <li>表相关的 SQL、参数顺序与结果映射属于 DAO 层，
 *       见 {@code dao.UserDao} 与 {@code dao.impl.UserDaoImpl}；</li>
 *   <li>因此本类不依赖任何实体类。</li>
 * </ul>
 */
public final class SqlUtils {

    /** 工具类禁止实例化。 */
    private SqlUtils() {
        throw new AssertionError("工具类不允许实例化: SqlUtils");
    }

    /* ==================== 通用方法 ==================== */

    /**
     * 执行 insert / update / delete 语句。
     *
     * @param sql    使用 ? 占位符的 SQL，不能为空
     * @param params 与 ? 一一对应的参数（顺序不能错）
     * @return 是否至少影响 1 行；SQL 出错时打印堆栈并返回 false
     * @throws IllegalArgumentException sql 为空时抛出
     */
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

    /**
     * 执行 select 语句，并把每一行交给 mapper 转换成对象。
     *
     * <p>查不到数据返回空集合（不是 null）；SQL 出错时打印堆栈并返回已取到的部分。
     *
     * @param sql    使用 ? 占位符的 SQL，不能为空
     * @param mapper 行映射器，不能为 null
     * @param params 与 ? 一一对应的参数
     * @param <T>    返回的元素类型
     * @return 结果列表，永不返回 null
     * @throws IllegalArgumentException sql 为空或 mapper 为 null 时抛出
     */
    public static <T> List<T> executeQuery(String sql, RowMapper<T> mapper, Object... params) {
        checkSql(sql);
        if (mapper == null) {
            throw new IllegalArgumentException("mapper 不能为 null");
        }
        List<T> rows = new ArrayList<>();
        try (Connection conn = MysqlConector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {     // 查询必须用 executeQuery
                while (rs.next()) {
                    rows.add(mapper.map(rs));             // 一行 → 一个对象
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return rows;
    }

    /** 把结果集的一行映射成一个对象。 */
    @FunctionalInterface
    public interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    /* ==================== 内部小工具 ==================== */

    /** 把参数按顺序绑定到 ? 占位符上。 */
    private static void bind(PreparedStatement ps, Object[] params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);   // JDBC 下标从 1 开始
        }
    }

    private static void checkSql(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("sql 不能为空");
        }
    }
}
