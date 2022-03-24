package org.sralab.emgimu.unity_bindings;

public interface PluginCallback {
    public void onSuccess(String msg);
    public void onError(String msg);
    public void sendDeviceList(String msg);
    public void onBatteryLife(String msg);
    public void onFirmwareVersion(String msg);
}
