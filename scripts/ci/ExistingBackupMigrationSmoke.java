import java.sql.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.flywaydb.core.Flyway;

/** Read/upgrade only the explicitly named disposable restoration of the historical local backup. */
public class ExistingBackupMigrationSmoke {
    static final String URL="jdbc:mysql://127.0.0.1:18336/customer_acceptance_actual_v36?allowPublicKeyRetrieval=true&useSSL=false";
    record Snapshot(List<String> columns,long count,String digest) {}
    static Flyway migration(String target) {
        var config=Flyway.configure().dataSource(URL,"root","").locations("filesystem:orange-server/src/main/resources/db/migration");
        if(target!=null)config.target(target);return config.load();
    }
    static Snapshot snapshot(String table,List<String> columns) throws Exception {
        var hash=MessageDigest.getInstance("SHA-256");long count=0;List<String> selected=new ArrayList<>(columns);
        String projection=selected.isEmpty()?"*":String.join(",",selected.stream().map(c->"`"+c+"`").toList());
        try(var c=DriverManager.getConnection(URL,"root","");var s=c.createStatement();var rows=s.executeQuery("SELECT "+projection+" FROM `"+table+"` ORDER BY id")) {
            if(selected.isEmpty())for(int i=1;i<=rows.getMetaData().getColumnCount();i++)selected.add(rows.getMetaData().getColumnName(i));
            while(rows.next()) {count++;for(int i=1;i<=selected.size();i++) {
                String value=rows.getString(i);byte[] data=(value==null?"NULL":"VALUE:"+value).getBytes(StandardCharsets.UTF_8);
                hash.update(Integer.toString(data.length).getBytes(StandardCharsets.US_ASCII));hash.update((byte)':');hash.update(data);
            }}
        }
        return new Snapshot(List.copyOf(selected),count,HexFormat.of().formatHex(hash.digest()));
    }
    public static void main(String[] args) throws Exception {
        var before=new LinkedHashMap<String,Snapshot>();
        for(String table:List.of("account","quota_log","knowledge_document"))before.put(table,snapshot(table,List.of()));
        var old=migration("37");
        if(!old.info().current().getVersion().getVersion().equals("36"))throw new AssertionError("Expected actual historical V36 backup");
        if(old.migrate().migrationsExecuted!=1)throw new AssertionError("Expected V36 to V37");old.validate();
        var full=migration(null);
        if(full.migrate().migrationsExecuted!=9)throw new AssertionError("Expected V37 to V46");full.validate();
        for(var entry:before.entrySet()) {
            var after=snapshot(entry.getKey(),entry.getValue().columns());
            if(!entry.getValue().equals(after))throw new AssertionError("Legacy rows changed: "+entry.getKey());
            System.out.println("LEGACY_DATA_PRESERVED table="+entry.getKey()+" rows="+after.count());
        }
        if(full.migrate().migrationsExecuted!=0)throw new AssertionError("Repeat upgrade must be a no-op");
        System.out.println("EXISTING_BACKUP_MIGRATION_PASS actual-backup=V36 stage-one=V37 stage-two=V46 original-columns=unchanged");
    }
}
