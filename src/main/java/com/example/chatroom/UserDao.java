package com.example.chatroom;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 数据库操作类，提供通过用户名获取用户的功能。
 */
public class UserDao {

    private static final String DB_URL = "jdbc:mysql://localhost:3306/java_homework?serverTimezone=Asia/Shanghai&verifyServerCertificate=false&useSSL=false";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "root";

    /**
     * 根据用户名获取用户信息。
     *
     * @param username 用户名
     * @return 包含用户ID和密码的对象，如果未找到则返回null。
     * @throws SQLException 如果与数据库交互时发生错误
     */
    public User getUserByName(String username) throws SQLException {
        // SQL查询语句
        String query = "SELECT id, username, password FROM users WHERE username = ?";

        // 使用try-with-resources确保资源自动关闭
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            // 设置参数值
            pstmt.setString(1, username);

            // 执行查询
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    // 创建User对象并设置其属性
                    return new User(rs.getLong("id"), rs.getString("username"), rs.getString("password"));
                }
            }
            catch (Exception e){
                System.out.println(e.getMessage());
            }
        }
        catch (Exception e){
            System.out.println(e.getMessage());
        }
        // 如果没有找到匹配的记录，则返回null
        return null;
    }

    // 假设有一个User类用于表示用户实体
    public static class User {
        private long id;
        private String username;
        private String password;

        public User(long id, String username, String password) {
            this.id = id;
            this.username = username;
            this.password = password;
        }

        // 提供getter和setter方法

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}