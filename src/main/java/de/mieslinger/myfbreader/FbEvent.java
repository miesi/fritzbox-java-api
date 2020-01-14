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

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 *
 * @author mieslingert
 */
public class FbEvent {

    private String identifier;
    private String name;
    private String metric;
    private Float value;
    private LocalDateTime ts;

    private FbEvent() {
    }

    public FbEvent(String identifier, String name, String metric, Float value) {
        this.ts = LocalDateTime.now();
        this.identifier = identifier;
        this.metric = metric;
        this.name = name;
        this.value = value;
    }

    public String getIdentifier() {
        return identifier;
    }

    public String getName() {
        return name;
    }

    public String getMetric() {
        return metric;
    }

    public Float getValue() {
        return value;
    }

    public LocalDateTime getLdtTs() {
        return ts;
    }

    public Timestamp getSqlTs() {
        ZoneId zone = ZoneId.of("Europe/Berlin");
        ZoneOffset zoneOffSet = zone.getRules().getOffset(ts);
        return new Timestamp(ts.toEpochSecond(zoneOffSet) * 1000);
    }
}
