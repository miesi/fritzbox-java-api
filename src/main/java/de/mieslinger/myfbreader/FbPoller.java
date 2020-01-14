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

import com.github.kaklakariada.fritzbox.HomeAutomation;
import com.github.kaklakariada.fritzbox.model.homeautomation.Device;
import com.github.kaklakariada.fritzbox.model.homeautomation.DeviceList;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author mieslingert
 */
public class FbPoller implements Runnable {

    private String fritzBoxUrl;
    private String fritzBoxUser;
    private String fritzBoxPassword;
    private Integer fritzBoxInterval;
    private boolean keepOnRunning = true;
    private ConcurrentLinkedQueue<FbEvent> queue;

    private Logger logger = LoggerFactory.getLogger(FbPoller.class);

    private FbPoller() {
    }

    public FbPoller(String fritzBoxUrl, String fritzBoxUser, String fritzBoxPassword, Integer fritzBoxInterval, ConcurrentLinkedQueue<FbEvent> queue) {
        this.fritzBoxUrl = fritzBoxUrl;
        this.fritzBoxUser = fritzBoxUser;
        this.fritzBoxPassword = fritzBoxPassword;
        this.fritzBoxInterval = fritzBoxInterval;
        this.queue = queue;
    }

    @Override
    public void run() {
        while (keepOnRunning) {
            logger.debug("Logging in to '{}' with username '{}'", fritzBoxUrl, fritzBoxUser);
            HomeAutomation homeAutomation = HomeAutomation.connect(fritzBoxUrl, fritzBoxUser, fritzBoxPassword);

            final DeviceList devices = homeAutomation.getDeviceListInfos();
            logger.debug("Found {} devices", devices.getDevices().size());
            for (final Device device : devices.getDevices()) {
                logger.debug("\tid: {}, identifier: {}, name: {}",
                        device.getId(),
                        device.getIdentifier(),
                        device.getName());
                if (null != device.getTemperature()) {
                    logger.debug("\t name: {} has Temperature {}°C",
                            device.getName(),
                            device.getTemperature().getCelsius());
                    queue.add(new FbEvent(device.getIdentifier(), device.getName(), "temp", device.getTemperature().getCelsius()));
                }
                if (null != device.getPowerMeter()) {
                    logger.debug("\t name: {} has PowerMeter current {}W, energy {}Wh, Voltage {}V",
                            device.getName(),
                            device.getPowerMeter().getPowerWatt(),
                            device.getPowerMeter().getEnergyWattHours(),
                            device.getPowerMeter().getVoltageVolt());
                    queue.add(new FbEvent(device.getIdentifier(), device.getName(), "power", device.getPowerMeter().getPowerWatt()));
                    queue.add(new FbEvent(device.getIdentifier(), device.getName(), "energy", device.getPowerMeter().getEnergyWattHours() / 1F));
                    queue.add(new FbEvent(device.getIdentifier(), device.getName(), "voltage", device.getPowerMeter().getVoltageVolt()));
                }
            }
            homeAutomation.logout();
            try {
                Thread.sleep(fritzBoxInterval * 1000);
            } catch (Exception e) {
                logger.info("ignoring sleep interruption");
            }
        }
    }

    public void setKeepOnRunning(boolean set) {
        keepOnRunning = set;
    }
}
