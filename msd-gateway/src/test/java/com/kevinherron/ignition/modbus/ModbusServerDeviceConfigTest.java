package com.kevinherron.ignition.modbus;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.inductiveautomation.ignition.common.gson.Gson;
import com.inductiveautomation.ignition.common.gson.JsonElement;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ModbusServerDeviceConfigTest {

  private static final Gson GSON = new Gson();
  private static final ModbusServerDeviceExtensionPoint EXTENSION_POINT =
      new ModbusServerDeviceExtensionPoint();

  @Nested
  class JsonCompatibility {

    // Existing gateway resources have no version and keep persistence in its own object.
    @Test
    void legacySettingsMovePersistenceIntoProcessImage() throws Exception {
      ModbusServerDeviceConfig config = decode(legacySettings(true));

      assertEquals(ModbusServerDeviceConfig.CURRENT_CONFIG_VERSION, config.configVersion());
      assertTrue(config.processImage().persistData());
      assertFalse(config.processImage().separatePerUnitId());
      assertEquals("0", config.browsing().unitIdBrowseRanges());
      assertEquals("*", config.security().allowedIpAddresses());
    }

    @Test
    void explicitVersionOneSettingsUseTheLegacyUpgradePath() throws Exception {
      JsonObject settings = parse(legacySettings(true));
      settings.addProperty("configVersion", 1);

      ModbusServerDeviceConfig config = decode(settings.toString());

      assertEquals(ModbusServerDeviceConfig.CURRENT_CONFIG_VERSION, config.configVersion());
      assertTrue(config.processImage().persistData());
      assertFalse(config.processImage().separatePerUnitId());
    }

    // Draft builds of the per-unit feature may have written both the old persistence object and
    // the new process-image object. Upgrading that shape must retain both choices.
    @Test
    void intermediateSettingsPreservePerUnitMode() throws Exception {
      ModbusServerDeviceConfig config =
          decode(
              """
              {
                "connectivity": {"bindAddress": "0.0.0.0", "port": 502},
                "browsing": {},
                "processImage": {"separatePerUnitId": true},
                "persistence": {"persistData": true}
              }
              """);

      assertTrue(config.processImage().persistData());
      assertTrue(config.processImage().separatePerUnitId());
    }

    // The editable form omits configVersion, so its otherwise-current payload must be stamped
    // without requiring the retired persistence object.
    @Test
    void versionlessCurrentSettingsFromFormAreStamped() throws Exception {
      ModbusServerDeviceConfig config =
          decode(
              """
              {
                "connectivity": {"bindAddress": "0.0.0.0", "port": 502},
                "browsing": {},
                "processImage": {"persistData": true, "separatePerUnitId": true}
              }
              """);

      assertEquals(ModbusServerDeviceConfig.CURRENT_CONFIG_VERSION, config.configVersion());
      assertTrue(config.processImage().persistData());
      assertTrue(config.processImage().separatePerUnitId());
    }

    // When a hand-written document contains both paths, the current location is authoritative.
    @Test
    void currentPersistenceValueWinsOverLegacyValue() throws Exception {
      ModbusServerDeviceConfig config =
          decode(
              """
              {
                "connectivity": {"bindAddress": "0.0.0.0", "port": 502},
                "browsing": {},
                "processImage": {"persistData": false, "separatePerUnitId": true},
                "persistence": {"persistData": true}
              }
              """);

      assertFalse(config.processImage().persistData());
      assertTrue(config.processImage().separatePerUnitId());
    }

    @Test
    void upgradeDoesNotMutateInputAndIsIdempotent() {
      JsonObject legacy = parse(legacySettings(false));

      JsonObject upgraded = upgrade(legacy);

      assertNotSame(legacy, upgraded);
      assertTrue(legacy.has("persistence"));
      assertFalse(legacy.has("configVersion"));
      assertFalse(upgraded.has("persistence"));
      assertFalse(upgraded.getAsJsonObject("processImage").get("persistData").getAsBoolean());
      assertEquals(upgraded, upgrade(upgraded));
    }

    @Test
    void futureVersionIsRejected() {
      JsonObject settings = parse(legacySettings(false));
      settings.addProperty("configVersion", ModbusServerDeviceConfig.CURRENT_CONFIG_VERSION + 1);

      assertThrows(IllegalArgumentException.class, () -> upgrade(settings));
    }

    @Test
    void emptySecurityDefaultsToAllowAll() {
      ModbusServerDeviceConfig config =
          GSON.fromJson("{\"security\": {}}", ModbusServerDeviceConfig.class);

      assertEquals("*", config.security().allowedIpAddresses());
    }

    @Test
    void nullSecurityValuesDefaultToAllowAll() {
      ModbusServerDeviceConfig nullObject =
          GSON.fromJson("{\"security\": null}", ModbusServerDeviceConfig.class);
      ModbusServerDeviceConfig nullField =
          GSON.fromJson(
              "{\"security\": {\"allowedIpAddresses\": null}}",
              ModbusServerDeviceConfig.class);

      assertEquals("*", nullObject.security().allowedIpAddresses());
      assertEquals("*", nullField.security().allowedIpAddresses());
    }

    @Test
    void explicitBlankSecurityValueIsPreservedForValidation() {
      ModbusServerDeviceConfig config =
          GSON.fromJson(
              "{\"security\": {\"allowedIpAddresses\": \"  \"}}",
              ModbusServerDeviceConfig.class);

      assertEquals("  ", config.security().allowedIpAddresses());
    }

    @Test
    void explicitAllowedIpAddressesSurviveJsonRoundTrip() {
      ModbusServerDeviceConfig expected =
          new ModbusServerDeviceConfig(
              ModbusServerDeviceConfig.CURRENT_CONFIG_VERSION,
              new ModbusServerDeviceConfig.Connectivity("127.0.0.1", 1502),
              new ModbusServerDeviceConfig.Browsing(
                  "0-9", "10-19", "20-29", "30-39", "0,2"),
              new ModbusServerDeviceConfig.ProcessImageSettings(true, true),
              new ModbusServerDeviceConfig.Security(
                  "192.168.1.50, 10.0.0.0/8, 172.16.*"));

      assertEquals(expected, roundTrip(expected));
    }

    // Ignition persists this record as JSON, so both mode choices and the exact browse expression
    // must survive serialization rather than falling back to constructor defaults.
    @Test
    void explicitProcessImageSettingsSurviveJsonRoundTrip() {
      ModbusServerDeviceConfig separate = roundTrip(config(true, "0,2,10-15"));
      ModbusServerDeviceConfig unified = roundTrip(config(false, "0"));

      assertTrue(separate.processImage().separatePerUnitId());
      assertFalse(unified.processImage().separatePerUnitId());
      assertEquals("0,2,10-15", separate.browsing().unitIdBrowseRanges());
      assertEquals(ModbusServerDeviceConfig.CURRENT_CONFIG_VERSION, separate.configVersion());
    }

    // An explicit empty expression means no unit folders; normalizing it to the default "0"
    // would change the user's browse tree after a save and reload.
    @Test
    void explicitEmptyUnitIdBrowseRangesSurvivesJsonRoundTrip() {
      ModbusServerDeviceConfig config = roundTrip(config(true, ""));

      assertEquals("", config.browsing().unitIdBrowseRanges());
      assertDoesNotThrow(
          () ->
              ModbusServerDeviceExtensionPoint.validateUnitIdBrowseRanges(
                  config.browsing().unitIdBrowseRanges()));
    }

    @Test
    void encodedSettingsUseOnlyTheCurrentPublicShape() {
      JsonObject encoded = EXTENSION_POINT.encode(config(true, "0")).getAsJsonObject();

      assertEquals(
          ModbusServerDeviceConfig.CURRENT_CONFIG_VERSION,
          encoded.get("configVersion").getAsInt());
      assertFalse(encoded.has("persistence"));
      assertTrue(encoded.getAsJsonObject("processImage").has("persistData"));
      assertTrue(encoded.getAsJsonObject("processImage").has("separatePerUnitId"));
      assertEquals("*", encoded.getAsJsonObject("security").get("allowedIpAddresses").getAsString());
    }
  }

  @Nested
  class SettingsSchema {

    @Test
    void settingsSchemaHidesVersionAndDocumentsProcessImageShape() {
      JsonObject properties =
          EXTENSION_POINT.settingsSchema().orElseThrow().getAsJsonObject("properties");
      JsonObject processImageProperties =
          properties.getAsJsonObject("processImage").getAsJsonObject("properties");
      JsonObject securityProperties =
          properties.getAsJsonObject("security").getAsJsonObject("properties");

      assertFalse(properties.has("configVersion"));
      assertFalse(properties.has("persistence"));
      assertTrue(processImageProperties.has("persistData"));
      assertTrue(processImageProperties.has("separatePerUnitId"));
      assertEquals("PROCESS IMAGE", formCategory(processImageProperties, "persistData"));
      assertEquals("PROCESS IMAGE", formCategory(processImageProperties, "separatePerUnitId"));
      assertTrue(securityProperties.has("allowedIpAddresses"));
      assertEquals("SECURITY", formCategory(securityProperties, "allowedIpAddresses"));
    }
  }

  @Nested
  class UnitIdBrowseRangeValidation {

    @ParameterizedTest
    @ValueSource(strings = {"0", "255", "0,2,10-15", "0-255", "001-002"})
    void validUnitIdBrowseRangesPassValidation(String ranges) {
      assertDoesNotThrow(
          () -> ModbusServerDeviceExtensionPoint.validateUnitIdBrowseRanges(ranges),
          () -> "valid range rejected: " + ranges);
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "-1", "256", "2-1", "1-256", "1-", "-1-2", "1--2", "1,", ",1", "1,,2",
          "1.0", "one", "1 2", " 1", "1 ", "+1", "999999999999999999999"
        })
    void invalidUnitIdBrowseRangesFailValidation(String ranges) {
      assertThrows(
          IllegalArgumentException.class,
          () -> ModbusServerDeviceExtensionPoint.validateUnitIdBrowseRanges(ranges),
          () -> "invalid range accepted: " + ranges);
    }
  }

  private static ModbusServerDeviceConfig config(
      boolean separatePerUnitId, String unitIdBrowseRanges) {
    return new ModbusServerDeviceConfig(
        ModbusServerDeviceConfig.CURRENT_CONFIG_VERSION,
        new ModbusServerDeviceConfig.Connectivity("0.0.0.0", 502),
        new ModbusServerDeviceConfig.Browsing("", "", "", "", unitIdBrowseRanges),
        new ModbusServerDeviceConfig.ProcessImageSettings(false, separatePerUnitId));
  }

  private static ModbusServerDeviceConfig roundTrip(ModbusServerDeviceConfig config) {
    return GSON.fromJson(GSON.toJson(config), ModbusServerDeviceConfig.class);
  }

  private static ModbusServerDeviceConfig decode(String json) throws Exception {
    return EXTENSION_POINT.decode(upgrade(parse(json)));
  }

  private static JsonObject upgrade(JsonElement settings) {
    return EXTENSION_POINT
        .getSettingsUpgrader()
        .orElseThrow()
        .upgrade(settings)
        .getAsJsonObject();
  }

  private static JsonObject parse(String json) {
    return GSON.fromJson(json, JsonObject.class);
  }

  private static String legacySettings(boolean persistData) {
    return """
        {
          "connectivity": {"bindAddress": "0.0.0.0", "port": 502},
          "browsing": {
            "coilBrowseRanges": "",
            "discreteInputBrowseRanges": "",
            "holdingRegisterBrowseRanges": "",
            "inputRegisterBrowseRanges": ""
          },
          "persistence": {"persistData": %s}
        }
        """
        .formatted(persistData);
  }

  private static String formCategory(JsonObject properties, String propertyName) {
    return properties
        .getAsJsonObject(propertyName)
        .getAsJsonObject("x-form")
        .get("category")
        .getAsString();
  }
}
