package com.kevinherron.ignition.modbus;

import com.inductiveautomation.ignition.gateway.config.ValidationErrors;
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
        ModbusServerDeviceConfig.class);
  }

  @Override
  protected Device createDevice(
      DeviceContext deviceContext,
      DeviceProfileConfig profileConfig,
      ModbusServerDeviceConfig deviceConfig) {

    return new ModbusServerDevice(deviceContext, deviceConfig);
  }

  @Override
  protected void validate(ModbusServerDeviceConfig settings, ValidationErrors.Builder errors) {
    errors.checkField(
        settings.connectivity().port() > 0 && settings.connectivity().port() < 65536,
        "connectivity.port",
        "port must be between 1 and 65535");

    try {
      BrowsableAddressSpace.parseRanges(settings.browsing().coilBrowseRanges());
    } catch (Exception e) {
      errors.addFieldMessage("browsing.coilBrowseRanges", "invalid coil ranges");
    }

    try {
      BrowsableAddressSpace.parseRanges(settings.browsing().discreteInputBrowseRanges());
    } catch (Exception e) {
      errors.addFieldMessage("browsing.discreteInputBrowseRanges", "invalid discrete input ranges");
    }

    try {
      BrowsableAddressSpace.parseRanges(settings.browsing().holdingRegisterBrowseRanges());
    } catch (Exception e) {
      errors.addFieldMessage(
          "browsing.holdingRegisterBrowseRanges", "invalid holding register ranges");
    }

    try {
      BrowsableAddressSpace.parseRanges(settings.browsing().inputRegisterBrowseRanges());
    } catch (Exception e) {
      errors.addFieldMessage("browsing.inputRegisterBrowseRanges", "invalid input register ranges");
    }
  }
}
