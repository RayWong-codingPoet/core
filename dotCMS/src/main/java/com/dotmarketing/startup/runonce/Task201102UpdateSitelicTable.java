package com.dotmarketing.startup.runonce;

import com.dotmarketing.business.APILocator;
import com.dotmarketing.common.db.DotConnect;
import com.dotmarketing.common.db.DotDatabaseMetaData;
import com.dotmarketing.db.DbConnectionFactory;
import com.dotmarketing.exception.DotDataException;
import com.dotmarketing.exception.DotRuntimeException;
import com.dotmarketing.startup.StartupTask;
import com.dotmarketing.util.Logger;

import java.lang.management.ManagementFactory;
import java.sql.SQLException;
import java.sql.Timestamp;

/**
 * Task used to create `startup_time` column at sitelic table.
 *
 * @author victor
 */
public class Task201102UpdateSitelicTable implements StartupTask {
    private final static String TIMESTAMP_COLUMN_TYPE = "TIMESTAMP";
    private final static String DATETIME_COLUMN_TYPE = "DATETIME";

    @Override
    public boolean forceRun() {
        try {
            return !new DotDatabaseMetaData().hasColumn("sitelic", "startup_time");
        } catch (SQLException e) {
            Logger.error(this, e.getMessage(),e);
            return false;
        }
    }

    @Override
    public void executeUpgrade() throws DotDataException, DotRuntimeException {
        final String addColumnSql = addColumnSql(DbConnectionFactory.isMsSql() || DbConnectionFactory.isMySql()
                ? DATETIME_COLUMN_TYPE
                : TIMESTAMP_COLUMN_TYPE);
        final DotConnect dotConnect = new DotConnect();
        dotConnect.setSQL(addColumnSql).loadObjectResults();

        dotConnect.setSQL("UPDATE sitelic SET startup_time = ? WHERE serverid = ? AND startup_time IS NULL");
        dotConnect
                .addParam(new Timestamp(ManagementFactory.getRuntimeMXBean().getStartTime()))
                .addParam(APILocator.getServerAPI().readServerId())
                .loadObjectResults();
    }

    private static String addColumnSql(final String type) {
        return String.format("ALTER TABLE sitelic ADD COLUMN startup_time %s", type);
    }
}
