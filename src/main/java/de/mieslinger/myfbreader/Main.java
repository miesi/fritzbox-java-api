/*
 * Copyright (C) 2020 mieslingert
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.mieslinger.myfbreader;

import com.sampullara.cli.Args;
import com.sampullara.cli.Argument;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author mieslingert
 */
public class Main {

    @Argument(alias = "b", description = "Fritz!Box URL")
    private static String fritzBoxUrl = "http://192.168.178.1";

    @Argument(alias = "fbu", description = "Fritz!Box user")
    private static String fritzBoxUser = "";

    @Argument(alias = "fbp", description = "Fritz!Box password")
    private static String fritzBoxPassword = "changeMe";

    @Argument(alias = "i", description = "Fritz!Box polling interval")
    private static Integer fritzBoxInterval = 300;

    @Argument(alias = "j", description = "jdbcurl")
    private static String jdbcUrl = "jdbc:mysql://localhost:3306/metric_test_db";

    @Argument(alias = "c", description = "jdbc class")
    private static String jdbcClass = "com.mysql.jdbc.Driver";

    @Argument(alias = "dbu", description = "user to connect to db")
    private static String dbUser = "root";

    @Argument(alias = "dbp", description = "db password")
    private static String dbPassword = "";

    // FIXME: implement me!
    @Argument(alias = "v", description = "be verbose")
    private static boolean verbose = false;

    // FIXME: implement me!
    @Argument(alias = "d", description = "enable debug")
    private static boolean debug = false;

    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private static final ConcurrentLinkedQueue<FbEvent> queue = new ConcurrentLinkedQueue<>();

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        List<String> unparsed = Args.parseOrExit(Main.class, args);
        // setup communication ConcurrentLinkedQueue
        // setup thread for Fritz!Box Poller and run it
        Thread tFbPoller = new Thread(new FbPoller(fritzBoxUrl, fritzBoxUser, fritzBoxPassword, fritzBoxInterval, queue));
        tFbPoller.setDaemon(true);
        tFbPoller.setName("FbPoller");
        tFbPoller.start();
        logger.debug("Fritzbox Poller Thread started");

        // setup DB wirter and run it
        Thread tDBWriter = new Thread(new DbWriter(jdbcClass, jdbcUrl, dbUser, dbPassword, queue));
        tDBWriter.setDaemon(true);
        tDBWriter.setName("DBWriter");
        tDBWriter.run();
        logger.debug("DBWriter started");
    }

}
