package com.kevinherron.ignition.modbus;

import com.inductiveautomation.ignition.gateway.localdb.persistence.PersistentRecord;
import com.inductiveautomation.ignition.gateway.localdb.persistence.RecordMeta;
import com.inductiveautomation.ignition.gateway.localdb.persistence.ReferenceField;
import com.inductiveautomation.ignition.gateway.opcua.server.api.Device;
import com.inductiveautomation.ignition.gateway.opcua.server.api.DeviceContext;
import com.inductiveautomation.ignition.gateway.opcua.server.api.DeviceSettingsRecord;
import com.inductiveautomation.ignition.gateway.opcua.server.api.DeviceType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ModbusServerDeviceType extends DeviceType {

  public ModbusServerDeviceType() {
    super(
        "com.kevinherron.modbus-server-driver",
        "ModbusServerDeviceType.Name",
        "ModbusServerDeviceType.Desc"
    );
  }

  @Override
  public @NotNull Device createDevice(
      DeviceContext deviceContext,
      @NotNull DeviceSettingsRecord deviceSettingsRecord
  ) {

    ModbusServerDeviceSettings modbusServerSettings = findProfileSettingsRecord(
        deviceContext.getGatewayContext(),
        deviceSettingsRecord
    );

    return new ModbusServerDevice(deviceSettingsRecord, modbusServerSettings);
  }

  @Override
  public @Nullable ReferenceField<?> getSettingsRecordForeignKey() {
    return ModbusServerDeviceSettings.DEVICE_SETTINGS;
  }

  @Override
  public @Nullable RecordMeta<? extends PersistentRecord> getSettingsRecordType() {
    return ModbusServerDeviceSettings.META;
  }

}
