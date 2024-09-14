package com.kevinherron.ignition.modbus;

import com.inductiveautomation.ignition.gateway.localdb.persistence.BooleanField;
import com.inductiveautomation.ignition.gateway.localdb.persistence.IntField;
import com.inductiveautomation.ignition.gateway.localdb.persistence.LongField;
import com.inductiveautomation.ignition.gateway.localdb.persistence.PersistentRecord;
import com.inductiveautomation.ignition.gateway.localdb.persistence.RecordMeta;
import com.inductiveautomation.ignition.gateway.localdb.persistence.ReferenceField;
import com.inductiveautomation.ignition.gateway.localdb.persistence.StringField;
import com.inductiveautomation.ignition.gateway.opcua.server.api.DeviceSettingsRecord;
import java.io.Serial;
import simpleorm.dataset.SFieldFlags;

@Deprecated
@SuppressWarnings("DeprecatedIsStillUsed")
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

  public static final BooleanField PERSIST_DATA =
      new BooleanField(META, "PersistData", SFieldFlags.SMANDATORY);

  public static final StringField COIL_BROWSE_RANGES =
      new StringField(META, "CoilBrowseRanges");

  public static final StringField DISCRETE_INPUT_BROWSE_RANGES =
      new StringField(META, "DiscreteInputBrowseRanges");

  public static final StringField HOLDING_REGISTER_BROWSE_RANGES =
      new StringField(META, "HoldingRegisterBrowseRanges");

  public static final StringField INPUT_REGISTER_BROWSE_RANGES =
      new StringField(META, "InputRegisterBrowseRanges");

  static {
    DEVICE_SETTINGS.getFormMeta().setVisible(false);
    DEVICE_SETTINGS_ID.getFormMeta().setVisible(false);

    PERSIST_DATA.setDefault(true);
    COIL_BROWSE_RANGES.setDefault("0-10");
    DISCRETE_INPUT_BROWSE_RANGES.setDefault("0-10");
    HOLDING_REGISTER_BROWSE_RANGES.setDefault("0-10");
    INPUT_REGISTER_BROWSE_RANGES.setDefault("0-10");
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

  public boolean getPersistData() {
    return getBoolean(PERSIST_DATA);
  }

  public String getCoilBrowseRanges() {
    return getString(COIL_BROWSE_RANGES);
  }

  public String getDiscreteInputBrowseRanges() {
    return getString(DISCRETE_INPUT_BROWSE_RANGES);
  }

  public String getHoldingRegisterBrowseRanges() {
    return getString(HOLDING_REGISTER_BROWSE_RANGES);
  }

  public String getInputRegisterBrowseRanges() {
    return getString(INPUT_REGISTER_BROWSE_RANGES);
  }

}
