package org.prowl.torque.remote;

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
