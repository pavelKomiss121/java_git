/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.jdbc.postgres;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import ru.mentee.power.config.ApplicationConfig;
import ru.mentee.power.jdbc.interfaces.DynamicQueryProcessor;

/**
 * Реализация динамического процессора SQL запросов с ResultSetMetaData для PostgreSQL.
 */
public class PostgresDynamicQueryProcessor implements DynamicQueryProcessor {

    private final ApplicationConfig config;

    public PostgresDynamicQueryProcessor(ApplicationConfig config) {
        this.config = config;
    }

    /**
     * Получить соединение с базой данных.
     */
    private Connection getConnection() throws SQLException {
        Connection conn =
                DriverManager.getConnection(
                        config.getUrl(), config.getUsername(), config.getPassword());
        try (var statement = conn.prepareStatement("SET search_path TO mentee_power, public")) {
            statement.execute();
        } catch (SQLException e) {
            conn.close();
            throw new SQLException("Ошибка установки search_path", e);
        }
        return conn;
    }

    @Override
    public List<Map<String, Object>> executeDynamicQuery(String sql) throws SQLException {
        List<Map<String, Object>> results = new ArrayList<>();

        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            // Получаем метаданные результата
            ResultSetMetaData rsmd = rs.getMetaData();
            int columnCount = rsmd.getColumnCount();

            // Динамический маппинг
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();

                for (int i = 1; i <= columnCount; i++) {
                    String columnName = rsmd.getColumnLabel(i);
                    int columnType = rsmd.getColumnType(i);

                    // Маппинг по типу колонки
                    Object value = mapColumnValue(rs, i, columnType);
                    row.put(columnName, value);
                }

                results.add(row);
            }
        }

        return results;
    }

    /**
     * Маппинг значения колонки по типу SQL.
     */
    private Object mapColumnValue(ResultSet rs, int index, int sqlType) throws SQLException {
        if (rs.getObject(index) == null) {
            return null;
        }

        return switch (sqlType) {
            case Types.VARCHAR, Types.CHAR, Types.LONGVARCHAR, Types.CLOB -> rs.getString(index);
            case Types.INTEGER, Types.SMALLINT, Types.TINYINT -> rs.getInt(index);
            case Types.BIGINT -> rs.getLong(index);
            case Types.DECIMAL, Types.NUMERIC -> rs.getBigDecimal(index);
            case Types.DOUBLE, Types.FLOAT, Types.REAL -> rs.getDouble(index);
            case Types.BOOLEAN, Types.BIT -> rs.getBoolean(index);
            case Types.DATE -> rs.getDate(index);
            case Types.TIME -> rs.getTime(index);
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> {
                Timestamp timestamp = rs.getTimestamp(index);
                yield timestamp != null ? timestamp.toLocalDateTime() : null;
            }
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> rs.getBytes(
                    index);
            case Types.ARRAY -> rs.getArray(index);
            default -> rs.getObject(index);
        };
    }

    @Override
    public int exportQueryToCSV(String sql, String outputPath) throws SQLException {
        int exportedRows = 0;

        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql);
                BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath))) {

            ResultSetMetaData rsmd = rs.getMetaData();
            int columnCount = rsmd.getColumnCount();

            // Записываем заголовки
            List<String> headers = new ArrayList<>();
            for (int i = 1; i <= columnCount; i++) {
                headers.add(rsmd.getColumnLabel(i));
            }
            writer.write(String.join(",", headers));
            writer.newLine();

            // Записываем данные
            while (rs.next()) {
                List<String> row = new ArrayList<>();
                for (int i = 1; i <= columnCount; i++) {
                    Object value = rs.getObject(i);
                    String stringValue = formatValueForCSV(value);
                    row.add(stringValue);
                }
                writer.write(String.join(",", row));
                writer.newLine();
                exportedRows++;
            }

            writer.flush();
        } catch (IOException e) {
            throw new SQLException("Ошибка записи в CSV файл: " + e.getMessage(), e);
        }

        return exportedRows;
    }

    /**
     * Форматирование значения для CSV (экранирование запятых и кавычек).
     */
    private String formatValueForCSV(Object value) {
        if (value == null) {
            return "";
        }

        String stringValue = value.toString();

        // Если значение содержит запятую, кавычку или перенос строки, оборачиваем в кавычки
        if (stringValue.contains(",") || stringValue.contains("\"") || stringValue.contains("\n")) {
            // Экранируем кавычки
            stringValue = stringValue.replace("\"", "\"\"");
            return "\"" + stringValue + "\"";
        }

        return stringValue;
    }

    @Override
    public String generateEntityClass(String sql, String className) throws SQLException {
        StringBuilder classCode = new StringBuilder();

        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            ResultSetMetaData rsmd = rs.getMetaData();
            int columnCount = rsmd.getColumnCount();

            // Генерируем заголовок класса
            classCode.append("/* @MENTEE_POWER (C)2025 */\n");
            classCode.append("package ru.mentee.power.model.generated;\n\n");
            classCode.append("import lombok.AllArgsConstructor;\n");
            classCode.append("import lombok.Builder;\n");
            classCode.append("import lombok.Data;\n");
            classCode.append("import lombok.NoArgsConstructor;\n");

            // Добавляем необходимые импорты для типов
            List<String> imports = new ArrayList<>();
            Map<String, String> fieldTypes = new HashMap<>();

            for (int i = 1; i <= columnCount; i++) {
                String columnName = rsmd.getColumnLabel(i);
                int columnType = rsmd.getColumnType(i);
                String javaType = mapSqlTypeToJavaType(columnType, imports);
                fieldTypes.put(columnName, javaType);
            }

            // Добавляем импорты
            for (String importStmt : imports) {
                classCode.append("import ").append(importStmt).append(";\n");
            }

            classCode.append("\n");
            classCode.append("/**\n");
            classCode.append(" * Автоматически сгенерированный класс на основе SQL запроса.\n");
            classCode.append(" */\n");
            classCode.append("@Data\n");
            classCode.append("@Builder\n");
            classCode.append("@NoArgsConstructor\n");
            classCode.append("@AllArgsConstructor\n");
            classCode.append("public class ").append(className).append(" {\n\n");

            // Генерируем поля
            for (int i = 1; i <= columnCount; i++) {
                String columnName = rsmd.getColumnLabel(i);
                String javaType = fieldTypes.get(columnName);
                String fieldName = toCamelCase(columnName);

                classCode
                        .append("    private ")
                        .append(javaType)
                        .append(" ")
                        .append(fieldName)
                        .append(";\n");
            }

            classCode.append("}\n");
        }

        return classCode.toString();
    }

    /**
     * Преобразование SQL типа в Java тип.
     */
    private String mapSqlTypeToJavaType(int sqlType, List<String> imports) {
        return switch (sqlType) {
            case Types.VARCHAR, Types.CHAR, Types.LONGVARCHAR, Types.CLOB -> "String";
            case Types.INTEGER, Types.SMALLINT, Types.TINYINT -> "Integer";
            case Types.BIGINT -> "Long";
            case Types.DECIMAL, Types.NUMERIC -> {
                if (!imports.contains("java.math.BigDecimal")) {
                    imports.add("java.math.BigDecimal");
                }
                yield "BigDecimal";
            }
            case Types.DOUBLE -> "Double";
            case Types.FLOAT, Types.REAL -> "Float";
            case Types.BOOLEAN, Types.BIT -> "Boolean";
            case Types.DATE -> {
                if (!imports.contains("java.time.LocalDate")) {
                    imports.add("java.time.LocalDate");
                }
                yield "LocalDate";
            }
            case Types.TIME -> {
                if (!imports.contains("java.time.LocalTime")) {
                    imports.add("java.time.LocalTime");
                }
                yield "LocalTime";
            }
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> {
                if (!imports.contains("java.time.LocalDateTime")) {
                    imports.add("java.time.LocalDateTime");
                }
                yield "LocalDateTime";
            }
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> "byte[]";
            case Types.ARRAY -> {
                if (!imports.contains("java.sql.Array")) {
                    imports.add("java.sql.Array");
                }
                yield "Array";
            }
            default -> "Object";
        };
    }

    /**
     * Преобразование имени колонки в camelCase.
     */
    private String toCamelCase(String columnName) {
        if (columnName == null || columnName.isEmpty()) {
            return columnName;
        }

        String[] parts = columnName.toLowerCase().split("_");
        StringBuilder camelCase = new StringBuilder(parts[0]);

        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                camelCase.append(Character.toUpperCase(parts[i].charAt(0)));
                if (parts[i].length() > 1) {
                    camelCase.append(parts[i].substring(1));
                }
            }
        }

        return camelCase.toString();
    }
}
