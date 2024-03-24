package com.kevinherron.ignition.modbus;

import com.inductiveautomation.ignition.gateway.localdb.persistence.IntField;
import com.inductiveautomation.ignition.gateway.localdb.persistence.LongField;
import com.inductiveautomation.ignition.gateway.localdb.persistence.PersistentRecord;
import com.inductiveautomation.ignition.gateway.localdb.persistence.RecordMeta;
import com.inductiveautomation.ignition.gateway.localdb.persistence.ReferenceField;
import com.inductiveautomation.ignition.gateway.localdb.persistence.StringField;
import com.inductiveautomation.ignition.gateway.opcua.server.api.DeviceSettingsRecord;
import java.io.Serial;
import simpleorm.dataset.SFieldFlags;

public class ModbusServerDeviceSettings extends PersistentRecord {

  @Serial
  private static final long serialVersionUID = 1L;

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

  static {
    DEVICE_SETTINGS.getFormMeta().setVisible(false);
    DEVICE_SETTINGS_ID.getFormMeta().setVisible(false);
  }

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
