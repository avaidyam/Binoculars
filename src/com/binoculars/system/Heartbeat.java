package com.binoculars.system;

import java.io.Serializable;
import java.util.Date;

/**
 * TODO:
 *  - Do more with Health (task-related status)
 */
public class Heartbeat implements Serializable {
    public final long timestamp = System.currentTimeMillis();

    @Override
    public String toString() {
        return "Health[" + new Date(timestamp) + "]";
    }
}
