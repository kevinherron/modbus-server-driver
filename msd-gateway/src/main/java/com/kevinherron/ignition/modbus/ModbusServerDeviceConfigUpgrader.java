package com.kevinherron.ignition.modbus;

import com.inductiveautomation.ignition.common.gson.JsonElement;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.gateway.config.JsonSettingsUpgrader;

/** Upgrades persisted Modbus server device settings to the current JSON format. */
final class ModbusServerDeviceConfigUpgrader implements JsonSettingsUpgrader {

  static final ModbusServerDeviceConfigUpgrader INSTANCE =
      new ModbusServerDeviceConfigUpgrader();

  private static final int LEGACY_CONFIG_VERSION = 1;

  private ModbusServerDeviceConfigUpgrader() {}

  /**
   * Upgrades a settings document without mutating the caller's JSON tree.
   *
   * <p>Version 1 settings have no {@code configVersion} property and store persistence under
   * {@code persistence.persistData}. Version 2 co-locates persistence and per-unit routing under
   * {@code processImage}.
   *
   * @param settings the persisted extension-point settings.
   * @return settings in the current format.
   * @throws IllegalArgumentException if the document is malformed or uses a newer version.
   */
  @Override
  public JsonElement upgrade(JsonElement settings) {
    if (!settings.isJsonObject()) {
      throw new IllegalArgumentException("Modbus server device settings must be a JSON object");
    }

    JsonObject upgraded = settings.getAsJsonObject().deepCopy();
    int version = configVersion(upgraded);

    if (version > ModbusServerDeviceConfig.CURRENT_CONFIG_VERSION) {
      throw new IllegalArgumentException(
          "unsupported Modbus server device configVersion: " + version);
    }
    if (version < LEGACY_CONFIG_VERSION) {
      throw new IllegalArgumentException(
          "invalid Modbus server device configVersion: " + version);
    }

    if (version == LEGACY_CONFIG_VERSION) {
      upgradeLegacySettings(upgraded);
    }

    return upgraded;
  }

  private static int configVersion(JsonObject settings) {
    JsonElement version = settings.get("configVersion");
    if (version == null || version.isJsonNull()) {
      return LEGACY_CONFIG_VERSION;
    }
    if (!version.isJsonPrimitive() || !version.getAsJsonPrimitive().isNumber()) {
      throw new IllegalArgumentException("configVersion must be an integer");
    }

    try {
      return version.getAsBigDecimal().intValueExact();
    } catch (RuntimeException e) {
      throw new IllegalArgumentException("configVersion must be an integer", e);
    }
  }

  private static void upgradeLegacySettings(JsonObject settings) {
    JsonObject processImage = getOrCreateProcessImage(settings);
    JsonObject persistence = getOptionalPersistence(settings);

    if (!processImage.has("persistData")) {
      JsonElement persistData = persistence == null ? null : persistence.get("persistData");
      if (persistData != null && !persistData.isJsonNull()) {
        processImage.add("persistData", persistData.deepCopy());
      } else {
        processImage.addProperty("persistData", false);
      }
    }

    if (!processImage.has("separatePerUnitId")) {
      processImage.addProperty("separatePerUnitId", false);
    }

    settings.add("processImage", processImage);
    settings.remove("persistence");
    settings.addProperty("configVersion", ModbusServerDeviceConfig.CURRENT_CONFIG_VERSION);
  }

  private static JsonObject getOrCreateProcessImage(JsonObject settings) {
    JsonElement property = settings.get("processImage");
    if (property == null || property.isJsonNull()) {
      return new JsonObject();
    }
    if (!property.isJsonObject()) {
      throw new IllegalArgumentException("processImage must be a JSON object");
    }
    return property.getAsJsonObject();
  }

  private static JsonObject getOptionalPersistence(JsonObject settings) {
    JsonElement property = settings.get("persistence");
    if (property == null || property.isJsonNull()) {
      return null;
    }
    if (!property.isJsonObject()) {
      throw new IllegalArgumentException("persistence must be a JSON object");
    }
    return property.getAsJsonObject();
  }
}
