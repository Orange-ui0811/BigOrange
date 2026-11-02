import java.sql.*;
import org.flywaydb.core.Flyway;

/** Run against a disposable MySQL instance; never point this at an application database. */
public class V3MigrationSmoke {
    private static final String URL = "jdbc:mysql://127.0.0.1:18336/";
    private static final String OPTIONS = "?allowPublicKeyRetrieval=true&useSSL=false";
    private static Flyway flyway(String schema, String target) {
        var config = Flyway.configure().dataSource(URL + schema + OPTIONS, "root", "")
            .locations("filesystem:orange-server/src/main/resources/db/migration");
        if (target != null) config.target(target);
        return config.load();
    }
    private static void require(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }
    public static void main(String[] args) throws Exception {
        try (var connection = DriverManager.getConnection(URL + OPTIONS, "root", "");
             var statement = connection.createStatement()) {
            // These schema names are reserved for this disposable-container check.
            statement.executeUpdate("CREATE DATABASE v3_merge_empty");
            statement.executeUpdate("CREATE DATABASE v3_merge_upgrade");
        }
        var empty = flyway("v3_merge_empty", null);
        require(empty.migrate().migrationsExecuted == 37, "fresh database must apply all 37 migrations");
        empty.validate();
        require(empty.migrate().migrationsExecuted == 0, "repeat migration must be a no-op");
        require(flyway("v3_merge_upgrade", "36").migrate().migrationsExecuted == 36, "prepare V36 database");
        try (var connection = DriverManager.getConnection(URL + "v3_merge_upgrade" + OPTIONS, "root", "");
             var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO account(id,username,password) VALUES(900001,'migration-fixture','synthetic-not-a-password')");
            statement.executeUpdate("INSERT INTO quota_log(account_id,type,amount,balance,source,request_id) VALUES(900001,'ADD',123,123,'TEST','synthetic-legacy-credit')");
        }
        var upgrade = flyway("v3_merge_upgrade", null);
        require(upgrade.migrate().migrationsExecuted == 1, "upgrade must apply only V37");
        upgrade.validate();
        try (var connection = DriverManager.getConnection(URL + "v3_merge_upgrade" + OPTIONS, "root", "");
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT amount,balance,transaction_no,status FROM quota_log WHERE request_id='synthetic-legacy-credit'")) {
            require(result.next(), "legacy quota record retained");
            require(result.getLong(1) == 123 && result.getLong(2) == 123, "legacy amounts retained");
            require(result.getString(3).startsWith("OVCQ_"), "legacy transaction number backfilled");
            require("POSTED".equals(result.getString(4)), "legacy posting status retained");
        }
        require(upgrade.migrate().migrationsExecuted == 0, "repeat upgrade must be a no-op");
        System.out.println("V3_MIGRATION_SMOKE_PASS fresh=37 upgrade=36->37 legacy-record=preserved repeat=no-op");
    }
}
