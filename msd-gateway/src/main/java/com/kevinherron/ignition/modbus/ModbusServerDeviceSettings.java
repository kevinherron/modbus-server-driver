package com.kevinherron.ignition.modbus;

import com.inductiveautomation.ignition.gateway.localdb.persistence.*;
import com.inductiveautomation.ignition.gateway.opcua.server.api.DeviceSettingsRecord;
import simpleorm.dataset.SFieldFlags;

public class ModbusServerDeviceSettings extends PersistentRecord {

  public static final RecordMeta<ModbusServerDeviceSettings> META =
      new RecordMeta<>(ModbusServerDeviceSettings.class, "ModbusServerDeviceSettings");

  public static final LongField DEVICE_SETTINGS_ID =
      new LongField(META, "DeviceSettingsId", SFieldFlags.SPRIMARY_KEY);

  public static final ReferenceField<DeviceSettingsRecord> DEVICE_SETTINGS =
      new ReferenceField<>(META, DeviceSettingsRecord.META, "DeviceSettings", DEVICE_SETTINGS_ID);

  public static final StringField BIND_ADDRESS =
      new StringField(META, "BindAddress", SFieldFlags.SMANDATORY);

  public static final IntField PORT =
      new IntField(META, "Port", SFieldFlags.SMANDATORY);

  @Override
  public RecordMeta<?> getMeta() {
    return META;
  }

  public String getBindAddress() {
    return getString(BIND_ADDRESS);
  }

  public int getPort() {
    return getInt(PORT);
  }

}
