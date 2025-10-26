/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.connection;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class SimpleConnection {

    private static final String URL = "jdbc:postgresql://localhost:5432/mentee_db";
    private static final String USERNAME = "mentee";
    private static final String PASSWORD = "password123";

    public static void main(String[] args) {
        System.out.println("🔍 Тестируем подключение к PostgreSQL...");

        try {
            Connection connection = DriverManager.getConnection(URL, USERNAME, PASSWORD);
            System.out.println("✅ Соединение установлено!");

            Statement statement = connection.createStatement();
            ResultSet resultSet =
                    statement.executeQuery("SELECT COUNT(*) as user_count FROM users");

            if (resultSet.next()) {
                int userCount = resultSet.getInt("user_count");
                System.out.println("👥 Пользователей в базе: " + userCount);
            }

            ResultSet versionResult = statement.executeQuery("SELECT version()");
            if (versionResult.next()) {
                String version = versionResult.getString(1);
                String shortVersion = version.split(" ")[1]; // Только номер версии
                System.out.println("🗄️ Версия PostgreSQL: " + shortVersion);
            }

            resultSet.close();
            versionResult.close();
            statement.close();
            connection.close();

            System.out.println("🎯 Все работает! Готов к изучению SQL.");

        } catch (Exception e) {
            System.err.println("❌ Ошибка подключения: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
