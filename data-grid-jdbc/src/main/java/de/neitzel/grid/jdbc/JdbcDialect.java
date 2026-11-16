package de.neitzel.grid.jdbc;

import java.sql.*;

/** Explicit dialect boundary; only H2 is qualified by this delivery. */
public interface JdbcDialect {
    String executionId();

    void beginSnapshot(Connection connection, int timeoutSeconds) throws SQLException;

    String paging();

    private static ResultSet execute(PreparedStatement statement, int timeoutSeconds)
            throws SQLException {
        statement.setQueryTimeout(timeoutSeconds);
        return statement.executeQuery();
    }

    /** H2 default binary/Java-string collation and its vendor snapshot isolation level. */
    static JdbcDialect h2() {
        return new JdbcDialect() {
            public String executionId() {
                return "jdbc-h2";
            }

            public void beginSnapshot(Connection connection, int timeoutSeconds)
                    throws SQLException {
                if (!connection.getMetaData().getDatabaseProductName().equals("H2"))
                    throw new SQLException("H2 dialect requires H2");
                try (var s =
                                connection.prepareStatement(
                                        "SELECT SETTING_VALUE FROM INFORMATION_SCHEMA.SETTINGS"
                                                + " WHERE SETTING_NAME = 'COLLATION'");
                        var rs = execute(s, timeoutSeconds)) {
                    if (rs.next() && !rs.getString(1).equals("OFF"))
                        throw new SQLException("Only H2 OFF collation is qualified");
                }
                connection.setTransactionIsolation(
                        6); // H2 TRANSACTION_SNAPSHOT, not JDBC REPEATABLE_READ.
                connection.setAutoCommit(false);
            }

            public String paging() {
                return " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
            }
        };
    }
}
