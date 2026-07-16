package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.charset.StandardCharsets;

/** Creates a new database from the maintained full schema resource. */
public class V1__baseline extends BaseJavaMigration {
    @Override
    public void migrate(Context context) {
        EncodedResource schema = new EncodedResource(
                new ClassPathResource("sql/schema-v1.sql"), StandardCharsets.UTF_8);
        ScriptUtils.executeSqlScript(context.getConnection(), schema);
    }
}
