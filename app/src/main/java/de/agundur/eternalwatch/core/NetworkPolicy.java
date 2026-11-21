// SPDX-License-Identifier: GPL-3.0-only
package de.agundur.eternalwatch.core;

/** Transport-independent policy: Wi-Fi can be metered as well. */
public final class NetworkPolicy {
    public enum Status { ALLOWED, OFFLINE, METERED, DATA_SAVER }
    private NetworkPolicy() { }
    public static Status decide(boolean internet,boolean validated,boolean metered,boolean allowMetered,boolean dataSaver) {
        if(!internet || !validated)return Status.OFFLINE;
        if(metered && dataSaver)return Status.DATA_SAVER;
        if(metered && !allowMetered)return Status.METERED;
        return Status.ALLOWED;
    }
}
