package com.zevrant.services.zevrantbackupservice.config;

import liquibase.integration.spring.SpringLiquibase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Arrays;

import static java.lang.String.format;

@Configuration
public class LiquibaseConfig {

    private static final Logger logger = LoggerFactory.getLogger(LiquibaseConfig.class);

    private final ConfigurableApplicationContext applicationContext;

    @Autowired
    public LiquibaseConfig(ConfigurableApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Bean
    public SpringLiquibase liquibase(DataSource dataSource) {
        removeDBLock(dataSource);
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        String[] profiles = applicationContext.getEnvironment().getActiveProfiles();
        if (Arrays.asList(profiles).contains("develop")) {
            liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
        } else if (Arrays.asList(profiles).contains("local")) {
            liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
        } else {
            liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
        }
        return liquibase;
    }


    private void removeDBLock(DataSource dataSource) {

        //Timestamp, currently set to 3 mins or older.

        final Timestamp lastDBLockTime = new Timestamp(System.currentTimeMillis() - (3 * 60 * 1000));

        logger.debug(lastDBLockTime.toString());


        final String checkQuery = "SELECT EXISTS ("
                + "   SELECT FROM pg_catalog.pg_class c"
                + "   JOIN   pg_catalog.pg_namespace n ON n.oid = c.relnamespace"
                + "   WHERE  n.nspname = 'public'"
                + "   AND    c.relname = 'DATABASECHANGELOGLOCK'"
                + "   AND    c.relkind = 'r')";

        final String query = format("DELETE FROM DATABASECHANGELOGLOCK WHERE LOCKED=true AND LOCKGRANTED<'%s'", lastDBLockTime);


        try (Statement stmt = dataSource.getConnection().createStatement()) {
            ResultSet resultSet = stmt.executeQuery(checkQuery);
            resultSet.next();
            boolean tableExists = resultSet.getBoolean("exists");
            if (tableExists) {
                int updateCount = stmt.executeUpdate(query);
                if (updateCount > 0) {
                    logger.info("Locks Removed Count: {} .", updateCount);
                }
            }
        } catch (SQLException e) {
            logger.error("Error! Remove Change Lock threw and Exception. ", e);
            System.exit(1);
        }
    }
}
