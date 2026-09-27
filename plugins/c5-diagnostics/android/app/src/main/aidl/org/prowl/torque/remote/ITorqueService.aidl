package org.prowl.torque.remote;

/*
 * Minimal prefix of Torque Pro's published remote AIDL API.
 *
 * Do not reorder these methods. Binder transaction numbers are positional.
 * We intentionally stop immediately after sendCommandGetResponse(), which is
 * the last method this plugin needs.
 */
interface ITorqueService {
    int getVersion();
    float getValueForPid(long pid, boolean triggersDataRefresh);
    String getDescriptionForPid(long pid);
    String getShortNameForPid(long pid);
    String getUnitForPid(long pid);
    float getMinValueForPid(long pid);
    float getMaxValueForPid(long pid);
    long[] getListOfActivePids();
    long[] getListOfECUSupportedPids();
    long[] getListOfAllPids();
    boolean hasFullPermissions();
    String[] sendCommandGetResponse(String header, String command);
}
