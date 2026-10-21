package util;

import java.sql.*;

public class MysqlConector {
    private static final String url = "jdbc:mysql://localhost:3306/up_test_db"
            + "?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true";
    private static final String user = "root";
    private static final String password = "123456";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }
}
