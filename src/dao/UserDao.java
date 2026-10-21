package dao;

import entity.User;

import java.util.List;

/**
 * user 表的数据访问接口。
 *
 * <p>只声明「对 user 表能做什么」，不包含连接管理、参数绑定与 SQL 实现。
 */
public interface UserDao {

    /** 新增一个用户。 */
    boolean insert(User u);

    /** 按主键整行更新。 */
    boolean updateById(User u);

    /** 按主键删除一个用户。 */
    boolean deleteById(long id);

    /** 按主键查询一个用户，查不到返回 null。 */
    User selectById(long id);

    /** 按用户名精确查询，查不到返回 null。 */
    User selectByUsername(String username);

    /** 按邮箱精确查询，查不到返回 null。 */
    User selectByEmail(String email);

    /** 查询全部用户。 */
    List<User> selectAll();

    /** 按用户名模糊查询。 */
    List<User> selectByUsernameLike(String keyword);

    /** 排序后取前 n 条。 */
    List<User> selectSorted(String column, boolean asc, int limit);

    /** 统计年龄大于给定值的用户数。 */
    int countByAgeGreaterThan(int age);
}
