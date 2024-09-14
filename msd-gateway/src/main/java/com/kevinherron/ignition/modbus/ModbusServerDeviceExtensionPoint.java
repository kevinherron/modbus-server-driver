package com.kevinherron.ignition.modbus;

import com.inductiveautomation.ignition.gateway.opcua.server.api.Device;
import com.inductiveautomation.ignition.gateway.opcua.server.api.DeviceContext;
import com.inductiveautomation.ignition.gateway.opcua.server.api.DeviceExtensionPoint;
import com.inductiveautomation.ignition.gateway.opcua.server.api.DeviceProfileConfig;

public class ModbusServerDeviceExtensionPoint
    extends DeviceExtensionPoint<ModbusServerDeviceConfig> {

  protected ModbusServerDeviceExtensionPoint() {
    super(
        "com.kevinherron.modbus-server-driver",
        "ModbusServer.ModbusServerDeviceType.Name",
        "ModbusServer.ModbusServerDeviceType.Desc",
        ModbusServerDeviceConfig.class
    );
  }

  @Override
  protected Device createDevice(
      DeviceContext deviceContext,
      DeviceProfileConfig profileConfig,
      ModbusServerDeviceConfig deviceConfig
  ) {

    return new ModbusServerDevice(deviceContext, deviceConfig);
  }
}
