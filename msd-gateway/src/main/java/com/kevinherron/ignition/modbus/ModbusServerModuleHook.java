package com.kevinherron.ignition.modbus;

import com.inductiveautomation.ignition.common.BundleUtil;
import com.inductiveautomation.ignition.common.licensing.LicenseState;
import com.inductiveautomation.ignition.gateway.config.migration.ExtensionPointRecordMigrationStrategy;
import com.inductiveautomation.ignition.gateway.config.migration.IdbMigrationStrategy;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import com.inductiveautomation.ignition.gateway.opcua.server.api.AbstractDeviceModuleHook;
import com.inductiveautomation.ignition.gateway.opcua.server.api.DeviceExtensionPoint;
import com.inductiveautomation.ignition.gateway.opcua.server.api.DeviceSettingsRecord;
import java.util.List;

public class ModbusServerModuleHook extends AbstractDeviceModuleHook {

  @Override
  public void setup(GatewayContext context) {
    BundleUtil.get().addBundle("ModbusServer", ModbusServerDevice.class, "ModbusServer");

    super.setup(context);
  }

  @Override
  public void startup(LicenseState activationState) {
    super.startup(activationState);
  }

  @Override
  public void shutdown() {
    BundleUtil.get().removeBundle(ModbusServerDevice.class);

    super.shutdown();
  }

  @Override
  protected List<DeviceExtensionPoint<?>> getDeviceExtensionPoints() {
    return List.of(new ModbusServerDeviceExtensionPoint());
  }

  @Override
  public List<IdbMigrationStrategy> getRecordMigrationStrategies() {
    @SuppressWarnings("deprecation")
    var strategy = ExtensionPointRecordMigrationStrategy
        .newBuilder("com.kevinherron.modbus-server-driver")
        .resourceType(DeviceExtensionPoint.DEVICE_RESOURCE_TYPE)
        .profileMeta(DeviceSettingsRecord.META)
        .settingsMeta(ModbusServerDeviceSettings.META)
        .settingsRecordForeignKey(ModbusServerDeviceSettings.DEVICE_SETTINGS)
        .settingsEncoder(encoder ->
            encoder.withCustomFieldName(
                    ModbusServerDeviceSettings.BIND_ADDRESS,
                    "connectivity.bindAddress")
                .withCustomFieldName(
                    ModbusServerDeviceSettings.PORT,
                    "connectivity.port")
                .withCustomFieldName(
                    ModbusServerDeviceSettings.PERSIST_DATA,
                    "persistence.persistData")
                .withCustomFieldName(
                    ModbusServerDeviceSettings.COIL_BROWSE_RANGES,
                    "browsing.coilBrowseRanges")
                .withCustomFieldName(
                    ModbusServerDeviceSettings.DISCRETE_INPUT_BROWSE_RANGES,
                    "browsing.discreteInputBrowseRanges")
                .withCustomFieldName(
                    ModbusServerDeviceSettings.HOLDING_REGISTER_BROWSE_RANGES,
                    "browsing.holdingRegisterBrowseRanges")
                .withCustomFieldName(
                    ModbusServerDeviceSettings.INPUT_REGISTER_BROWSE_RANGES,
                    "browsing.inputRegisterBrowseRanges"))
        .build();

    return List.of(strategy);
  }
}
