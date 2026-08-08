package com.kevinherron.ignition.modbus;

import com.inductiveautomation.ignition.gateway.config.JsonSettingsUpgrader;
import com.inductiveautomation.ignition.gateway.config.ValidationErrors;
import com.inductiveautomation.ignition.gateway.dataroutes.openapi.SchemaUtil;
import com.inductiveautomation.ignition.gateway.opcua.server.api.Device;
import com.inductiveautomation.ignition.gateway.opcua.server.api.DeviceContext;
import com.inductiveautomation.ignition.gateway.opcua.server.api.DeviceExtensionPoint;
import com.inductiveautomation.ignition.gateway.opcua.server.api.DeviceProfileConfig;
import com.inductiveautomation.ignition.gateway.web.nav.ExtensionPointResourceForm;
import com.inductiveautomation.ignition.gateway.web.nav.WebUiComponent;
import com.kevinherron.ignition.modbus.security.AllowedIpAddressFilter;
import java.util.Optional;

/**
 * Registers the Modbus server device type with Ignition and defines its configuration boundary.
 *
 * <p>The extension point supplies the web configuration schema, validates settings—including
 * connection admission—before device creation, and creates {@link ModbusServerDevice} instances for
 * the Ignition device lifecycle.
 */
public class ModbusServerDeviceExtensionPoint
    extends DeviceExtensionPoint<ModbusServerDeviceConfig> {

  /** Creates the extension point registered by the gateway module hook. */
  protected ModbusServerDeviceExtensionPoint() {
    super(
        "com.kevinherron.modbus-server-driver",
        "ModbusServer.ModbusServerDeviceType.Name",
        "ModbusServer.ModbusServerDeviceType.Desc",
        ModbusServerDeviceConfig.class);
  }

  @Override
  protected Device createDevice(
      DeviceContext deviceContext,
      DeviceProfileConfig profileConfig,
      ModbusServerDeviceConfig deviceConfig) {

    return new ModbusServerDevice(deviceContext, deviceConfig);
  }

  @Override
  public Optional<JsonSettingsUpgrader> getSettingsUpgrader() {
    return Optional.of(ModbusServerDeviceConfigUpgrader.INSTANCE);
  }

  @Override
  protected void validate(ModbusServerDeviceConfig settings, ValidationErrors.Builder errors) {
    errors.checkField(
        settings.connectivity().port() > 0 && settings.connectivity().port() < 65536,
        "connectivity.port",
        "port must be between 1 and 65535");

    String allowedIpAddresses = settings.security().allowedIpAddresses();
    errors.requireNotBlank("security.allowedIpAddresses", allowedIpAddresses);
    if (!allowedIpAddresses.isBlank()) {
      try {
        AllowedIpAddressFilter.parse(allowedIpAddresses);
      } catch (IllegalArgumentException e) {
        errors.addFieldMessage("security.allowedIpAddresses", e.getMessage());
      }
    }

    try {
      String ranges = settings.browsing().coilBrowseRanges();
      if (ranges != null && !ranges.isEmpty()) {
        BrowsableAddressSpace.parseRanges(ranges);
      }
    } catch (Exception e) {
      errors.addFieldMessage("browsing.coilBrowseRanges", "invalid coil ranges");
    }

    try {
      String ranges = settings.browsing().discreteInputBrowseRanges();
      if (ranges != null && !ranges.isEmpty()) {
        BrowsableAddressSpace.parseRanges(ranges);
      }
    } catch (Exception e) {
      errors.addFieldMessage("browsing.discreteInputBrowseRanges", "invalid discrete input ranges");
    }

    try {
      String ranges = settings.browsing().holdingRegisterBrowseRanges();
      if (ranges != null && !ranges.isEmpty()) {
        BrowsableAddressSpace.parseRanges(ranges);
      }
    } catch (Exception e) {
      errors.addFieldMessage(
          "browsing.holdingRegisterBrowseRanges", "invalid holding register ranges");
    }

    try {
      String ranges = settings.browsing().inputRegisterBrowseRanges();
      if (ranges != null && !ranges.isEmpty()) {
        BrowsableAddressSpace.parseRanges(ranges);
      }
    } catch (Exception e) {
      errors.addFieldMessage("browsing.inputRegisterBrowseRanges", "invalid input register ranges");
    }

    try {
      validateUnitIdBrowseRanges(settings.browsing().unitIdBrowseRanges());
    } catch (IllegalArgumentException e) {
      errors.addFieldMessage("browsing.unitIdBrowseRanges", "invalid unit ID ranges");
    }
  }

  /**
   * Validates the grammar and bounds of unit IDs selected for OPC UA browsing.
   *
   * <p>An empty value is valid and selects no unit folders. Entries are comma-separated unit IDs or
   * inclusive ranges, and every bound must be between 0 and 255. This setting affects browsing
   * only; it is not a protocol allowlist.
   *
   * @param ranges the unit ID browse-range expression.
   * @throws IllegalArgumentException if an entry is malformed, reversed, or out of range.
   */
  static void validateUnitIdBrowseRanges(String ranges) {
    if (ranges == null || ranges.isEmpty()) {
      return;
    }

    // Delegating to the runtime parser guarantees validation accepts exactly the expressions
    // BrowsableAddressSpace accepts at device startup.
    BrowsableAddressSpace.expandUnitIdRanges(ranges);
  }

  @Override
  public Optional<WebUiComponent> getWebUiComponent(ComponentType type) {
    return Optional.of(
        new ExtensionPointResourceForm(
            DEVICE_RESOURCE_TYPE,
            "Device Connection",
            "com.kevinherron.modbus-server-driver",
            SchemaUtil.fromType(DeviceProfileConfig.class),
            SchemaUtil.fromType(ModbusServerDeviceConfig.class)));
  }
}
