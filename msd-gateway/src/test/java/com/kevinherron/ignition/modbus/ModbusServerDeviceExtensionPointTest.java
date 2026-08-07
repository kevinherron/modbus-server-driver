package com.kevinherron.ignition.modbus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.inductiveautomation.ignition.gateway.config.ValidationErrors;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModbusServerDeviceExtensionPointTest {

  private static final String ALLOWED_IP_ADDRESSES_FIELD = "security.allowedIpAddresses";

  @Test
  void acceptsValidAllowedIpAddresses() {
    ValidationErrors errors = validate("192.168.1.50, 10.0.0.0/8, 172.16.*, 192.168.2.10-20");

    assertTrue(errors.isEmpty());
  }

  @Test
  void rejectsBlankAllowedIpAddresses() {
    ValidationErrors errors = validate("  ");

    assertSingleAllowedIpAddressesError(errors, "'security.allowedIpAddresses' is required.");
  }

  @Test
  void rejectsMalformedCidrAllowedIpAddresses() {
    ValidationErrors errors = validate("192.168.1.5/24");

    assertSingleAllowedIpAddressesError(errors, "invalid allowed IP address entry: 192.168.1.5/24");
  }

  private static ValidationErrors validate(String allowedIpAddresses) {
    ModbusServerDeviceConfig config =
        new ModbusServerDeviceConfig(
            new ModbusServerDeviceConfig.Connectivity("0.0.0.0", 502),
            new ModbusServerDeviceConfig.Browsing("", "", "", ""),
            new ModbusServerDeviceConfig.Persistence(false),
            new ModbusServerDeviceConfig.Security(allowedIpAddresses));
    ValidationErrors.Builder errors = ValidationErrors.newBuilder();

    new ModbusServerDeviceExtensionPoint().validate(config, errors);

    return errors.build();
  }

  private static void assertSingleAllowedIpAddressesError(
      ValidationErrors errors, String expectedMessage) {
    assertEquals(List.of(), errors.messages());
    assertEquals(1, errors.fieldMessages().size());

    ValidationErrors.FieldValidationErrors fieldErrors = errors.fieldMessages().get(0);
    assertEquals(ALLOWED_IP_ADDRESSES_FIELD, fieldErrors.fieldName());
    assertEquals(List.of(expectedMessage), fieldErrors.messages());
  }
}
