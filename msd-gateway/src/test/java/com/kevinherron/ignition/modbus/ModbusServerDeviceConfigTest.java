package com.kevinherron.ignition.modbus;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.inductiveautomation.ignition.common.gson.Gson;
import org.junit.jupiter.api.Test;

class ModbusServerDeviceConfigTest {

  private static final Gson GSON = new Gson();

  @Test
  void missingSecurityDefaultsToAllowAll() {
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

    assertEquals("*", config.security().allowedIpAddresses());
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
            "{\"security\": {\"allowedIpAddresses\": null}}", ModbusServerDeviceConfig.class);

    assertEquals("*", nullObject.security().allowedIpAddresses());
    assertEquals("*", nullField.security().allowedIpAddresses());
  }

  @Test
  void explicitBlankSecurityValueIsPreservedForValidation() {
    ModbusServerDeviceConfig config =
        GSON.fromJson(
            "{\"security\": {\"allowedIpAddresses\": \"  \"}}", ModbusServerDeviceConfig.class);

    assertEquals("  ", config.security().allowedIpAddresses());
  }

  @Test
  void explicitAllowedIpAddressesRoundTrip() {
    ModbusServerDeviceConfig expected =
        new ModbusServerDeviceConfig(
            new ModbusServerDeviceConfig.Connectivity("127.0.0.1", 1502),
            new ModbusServerDeviceConfig.Browsing("0-9", "10-19", "20-29", "30-39"),
            new ModbusServerDeviceConfig.Persistence(true),
            new ModbusServerDeviceConfig.Security("192.168.1.50, 10.0.0.0/8, 172.16.*"));

    ModbusServerDeviceConfig actual =
        GSON.fromJson(GSON.toJson(expected), ModbusServerDeviceConfig.class);

    assertEquals(expected, actual);
  }
}
