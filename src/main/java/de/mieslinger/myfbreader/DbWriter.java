/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.mieslinger.myfbreader;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author mieslingert
 */
public class DbWriter implements Runnable {

    private boolean keepOnRunning = true;
    private String jdbcClass;
    private String jdbcUrl;
    private String user;
    private String password;
    private Connection conn;
    private ConcurrentLinkedQueue<FbEvent> queue;
    private PreparedStatement checkTableExists;
    private PreparedStatement createTable;
    private PreparedStatement insertLog;
    private PreparedStatement cleanupLog;
    private PreparedStatement insertData;

    private final static Logger logger = LoggerFactory.getLogger(DbWriter.class);

    private DbWriter() {
    }

    public DbWriter(String jdbcClass, String jdbcUrl, String user, String password, ConcurrentLinkedQueue<FbEvent> queue) {
        this.jdbcClass = jdbcClass;
        this.jdbcUrl = jdbcUrl;
        this.user = user;
        this.password = password;
        this.queue = queue;
        try {
            Class.forName(jdbcClass);
            conn = DriverManager.getConnection(jdbcUrl, user, password);
            if (conn.isValid(5)) {
                logger.info("Successfully connected to DB");
            }
        } catch (Exception e) {
            logger.error("DB Connection failed on initial connection, exiting", e);
            System.exit(1);
        }
        logger.info("DbWriter instantiated");
    }

    @Override
    public void run() {
        while (keepOnRunning) {
            try {
                FbEvent fbev = queue.poll();
                if (fbev != null) {
                    persist(fbev);
                } else {
                    Thread.sleep(5000);
                }
            } catch (Exception e) {
                logger.warn("DbWriter Exception: ", e);
            }
        }
    }

    private void persist(FbEvent fbev) {
        tryAndReconnect();
        checkAndCreateLogTable(fbev);
        checkAndCreateDataTable(fbev);
    }

    private void checkAndCreateLogTable(FbEvent e) {
        // select 1 from table name
        try {
            checkTableExists = conn.prepareStatement("select 1 from fb_log");
            checkTableExists.execute();
            checkTableExists.close();
        } catch (Exception ex) {
            // -> Exception -> create table
            logger.info("Table fb_log does not exist, creating");
            try {
                createTable = conn.prepareStatement("create table fb_log ("
                        + "ts timestamp NOT NULL DEFAULT current_timestamp(),"
                        + "identifier varchar(256) not null,"
                        + "name varchar(265) not null,"
                        + "metric varchar(40) not null,"
                        + "value double not null,"
                        + "key (ts)"
                        + ")");
                createTable.executeUpdate();
                createTable.close();
                logger.info("created table fb_log");
            } catch (Exception exc) {
                logger.warn("unexpected exception during create table fb_log: {}", exc.getMessage());
                exc.printStackTrace();
            }
        }

        // -> do insert
        try {
            insertLog = conn.prepareStatement("insert into fb_log (ts, identifier, name, metric, value) values (?,?,?,?,?)");
            insertLog.setTimestamp(1, e.getSqlTs());
            insertLog.setString(2, e.getIdentifier());
            insertLog.setString(3, e.getName());
            insertLog.setString(4, e.getMetric());
            insertLog.setDouble(5, e.getValue());
            insertLog.execute();
            insertLog.close();
            cleanupLog = conn.prepareStatement("delete from fb_log where ts < date_sub(now(), interval 3 month)");
            cleanupLog.executeUpdate();
            cleanupLog.close();
        } catch (Exception ex) {
            logger.warn("unexpected exception during insert fb_log data: {}", ex.getMessage());
            ex.printStackTrace();
        }

    }

    private void checkAndCreateDataTable(FbEvent e) {
        // generate table name
        /*
         *[main] INFO de.mieslinger.myfbreader.FbPoller - Found 4 devices
         *[main] INFO de.mieslinger.myfbreader.FbPoller -         id: 16, identifier: 09995 0239511, name: HZK Wohnzimmer
         *[main] INFO de.mieslinger.myfbreader.FbPoller -          name: HZK Wohnzimmer has Temperature 20.5°C
         *[main] INFO de.mieslinger.myfbreader.FbPoller -         id: 17, identifier: 11630 0072371, name: SD
         *[main] INFO de.mieslinger.myfbreader.FbPoller -          name: SD has Temperature 20.5°C
         *[main] INFO de.mieslinger.myfbreader.FbPoller -          name: SD has PowerMeter current 20.67W, energy 5976Wh, Voltage 235.996V
         *[main] INFO de.mieslinger.myfbreader.FbPoller -         id: 18, identifier: 09995 0335493, name: HZK Büro
         *[main] INFO de.mieslinger.myfbreader.FbPoller -          name: HZK Büro has Temperature 20.5°C
         *[main] INFO de.mieslinger.myfbreader.FbPoller -         id: 19, identifier: 09995 0330576, name: FRITZ!DECT 301 #4
         *[main] INFO de.mieslinger.myfbreader.FbPoller -          name: FRITZ!DECT 301 #4 has Temperature 0.0°C
         */

        // Good enough as long as only one temp per ain
        // fb_099950330576_temp
        String tableName = "fb_" + e.getIdentifier().replaceAll("\\s", "") + "_" + e.getMetric();

        // select 1 from table name
        try {
            checkTableExists = conn.prepareStatement("select 1 from " + tableName);
            checkTableExists.execute();
        } catch (Exception ex) {
            // -> Exception -> create table
            logger.info("Table {} does not exist, creating", tableName);
            try {
                createTable = conn.prepareStatement("create table " + tableName + " ("
                        + "ts timestamp NOT NULL DEFAULT current_timestamp(),"
                        + "value double not null,"
                        + "primary key (ts)"
                        + ")");
                createTable.executeUpdate();
                createTable.close();
                logger.info("created table {}", tableName);
            } catch (Exception exc) {
                logger.warn("unexpected exception during create table {}: {}", tableName, exc.getMessage());
                exc.printStackTrace();
            }
        }

        // -> do insert
        try {
            insertData = conn.prepareStatement("insert into " + tableName + " (ts,value) values (?,?)");
            insertData.setTimestamp(1, e.getSqlTs());
            insertData.setDouble(2, e.getValue());
            insertData.execute();
            insertData.close();
        } catch (Exception ex) {
            logger.warn("unexpected exception during insert data into {}: {}", tableName, ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void tryAndReconnect() {
        // sleep 30s after every connection fail
        boolean connectionOK;
        try {
            connectionOK = conn.isValid(5);
        } catch (Exception e) {
            connectionOK = false;
            logger.warn("Connection to DB broken", e);
        }

        if (connectionOK) {
            return;
        }

        while (!connectionOK) {
            try {
                conn = DriverManager.getConnection(jdbcUrl, user, password);
                connectionOK = true;
                return;
            } catch (Exception e) {

            }
            if (!connectionOK) {
                logger.warn("Connection to DB still broken, sleeping");
                try {
                    Thread.sleep(600000); // use min 600s in production
                } catch (Exception e) {

                }
            }
        }
    }
}
