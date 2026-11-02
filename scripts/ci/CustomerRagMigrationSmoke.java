import java.sql.*;
import org.flywaydb.core.Flyway;

/** Synthetic V37 backup/restore rehearsal on the disposable MySQL at port 18336. */
public class CustomerRagMigrationSmoke {
    static final String ROOT="jdbc:mysql://127.0.0.1:18336/", OPTIONS="?allowPublicKeyRetrieval=true&useSSL=false";
    static Connection connect(String schema) throws Exception {return DriverManager.getConnection(ROOT+schema+OPTIONS,"root","");}
    static Flyway migration(String schema,String target) {
        var config=Flyway.configure().dataSource(ROOT+schema+OPTIONS,"root","")
            .locations("filesystem:orange-server/src/main/resources/db/migration");
        if(target!=null)config.target(target);return config.load();
    }
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args) throws Exception {
        if(args.length!=1)throw new IllegalArgumentException("prepare or verify is required");
        if(args[0].equals("prepare")) {
            try(var c=connect("");var s=c.createStatement()) {
                for(String schema:new String[]{"customer_acceptance_seed_v37","customer_acceptance_upgrade","customer_acceptance_fresh","customer_acceptance_dbtest","customer_acceptance_dbtest_empty","customer_context_acceptance_ci","customer_acceptance_runtime"})
                    s.executeUpdate("CREATE DATABASE "+schema);
            }
            require(migration("customer_acceptance_seed_v37","37").migrate().migrationsExecuted==37,"V37 seed schema");
            try(var c=connect("customer_acceptance_seed_v37");var s=c.createStatement()) {
                s.executeUpdate("INSERT INTO account(id,username,password) VALUES(900001,'integration-fixture','synthetic-not-a-password')");
                s.executeUpdate("INSERT INTO quota_log(account_id,type,amount,balance,source,request_id,transaction_no) VALUES(900001,'ADD',123,123,'TEST','integration-legacy-credit','OVCQ_synthetic_backup_fixture')");
            }
            System.out.println("V37_FIXTURE_READY_FOR_DUMP");return;
        }
        if(!args[0].equals("verify"))throw new IllegalArgumentException("Unknown phase");
        var fresh=migration("customer_acceptance_fresh",null);
        require(fresh.migrate().migrationsExecuted==46,"fresh V1-V46 migration");fresh.validate();
        var upgrade=migration("customer_acceptance_upgrade",null);
        require(upgrade.info().current().getVersion().getVersion().equals("37"),"restored backup starts at V37");
        require(upgrade.migrate().migrationsExecuted==9,"restored V37 backup upgrades through V46");upgrade.validate();
        try(var c=connect("customer_acceptance_upgrade");var s=c.createStatement();var r=s.executeQuery("SELECT amount,balance,transaction_no FROM quota_log WHERE request_id='integration-legacy-credit'")) {
            require(r.next()&&r.getLong(1)==123&&r.getLong(2)==123&&r.getString(3).equals("OVCQ_synthetic_backup_fixture"),"backup restore and upgrade preserve quota data");
        }
        for(String table:new String[]{"rag_b_document","customer_session","customer_graph_checkpoint","customer_context_summary"})
            try(var c=connect("customer_acceptance_upgrade");var s=c.createStatement()){s.executeQuery("SELECT COUNT(*) FROM "+table).close();}
        require(upgrade.migrate().migrationsExecuted==0&&fresh.migrate().migrationsExecuted==0,"repeat migrations are no-ops");
        System.out.println("CUSTOMER_RAG_MIGRATION_PASS fresh=46 restored=37->46 synthetic-legacy=preserved repeat=no-op");
    }
}
