package org.example;

import java.sql.*;

public class DatabaseService {
    private final String url = "jdbc:postgresql://gondola.proxy.rlwy.net:38524/railway?sslmode=require";
    private final String user = "postgres";
    private final String password = "MZCrgYPEXODCyEbaOFxFspZbQXIJAOyR";

//    private final String url = "jdbc:postgresql://localhost:5432/postgres";
//    private final String user = "postgres";
//    private final String password = "1";

    public DatabaseService() {
        try (Connection conn = getConnection()) {
            String createTable = """
                    CREATE TABLE IF NOT EXISTS users (
                        chat_id BIGINT PRIMARY KEY,
                        username TEXT,
                        firstname TEXT,
                        created_at TIMESTAMP DEFAULT NOW()
                    );
                    """;
            conn.createStatement().execute(createTable);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    public void saveUser(Long chatId, String username, String firstName) {
        try (Connection conn = getConnection()) {
            String sql = "INSERT INTO users (chat_id, username, firstname) VALUES (?, ?, ?) ON CONFLICT (chat_id) DO NOTHING";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setLong(1, chatId);
            ps.setString(2, username);
            ps.setString(3, firstName);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public ResultSet getAllUsers() {
        try {
            Connection conn = getConnection();
            String sql = "SELECT chat_id FROM users";
            return conn.createStatement().executeQuery(sql);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
