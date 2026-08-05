package com.kevinherron.ignition.modbus;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.inductiveautomation.ignition.common.gson.Gson;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ModbusServerDeviceConfigTest {

  private static final Gson GSON = new Gson();

  @Nested
  class JsonCompatibility {

    // Existing gateway backups omit both properties, so decoding them must retain the historical
    // unified image and must not unexpectedly remove unit 0 from the browse tree.
    @Test
    void missingPropertiesUseCompatibilityDefaults() {
      ModbusServerDeviceConfig config =
          GSON.fromJson(
              """
              {
                "connectivity": {"bindAddress": "0.0.0.0", "port": 502},
                "browsing": {
                  "coilBrowseRanges": "",
                  "discreteInputBrowseRanges": "",
                  "holdingRegisterBrowseRanges": "",
                  "inputRegisterBrowseRanges": ""
                },
                "persistence": {"persistData": false}
              }
              """,
              ModbusServerDeviceConfig.class);

      assertFalse(config.processImage().separatePerUnitId());
      assertEquals("0", config.browsing().unitIdBrowseRanges());
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
        new ModbusServerDeviceConfig.Connectivity("0.0.0.0", 502),
        new ModbusServerDeviceConfig.Browsing("", "", "", "", unitIdBrowseRanges),
        new ModbusServerDeviceConfig.ProcessImageSettings(separatePerUnitId),
        new ModbusServerDeviceConfig.Persistence(false));
  }

  private static ModbusServerDeviceConfig roundTrip(ModbusServerDeviceConfig config) {
    return GSON.fromJson(GSON.toJson(config), ModbusServerDeviceConfig.class);
  }
}
