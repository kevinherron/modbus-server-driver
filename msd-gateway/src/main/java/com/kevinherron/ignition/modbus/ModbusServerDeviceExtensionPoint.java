package com.kevinherron.ignition.modbus;

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
 * Integrates {@link ModbusServerDevice} with Ignition's device-extension framework.
 *
 * <p>This extension point supplies the Gateway resource form, validates device configuration at
 * save time, and creates the device instance managed by the Gateway.
 */
public class ModbusServerDeviceExtensionPoint
    extends DeviceExtensionPoint<ModbusServerDeviceConfig> {

  /** Creates the extension-point registration for the Modbus server device profile. */
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

  /**
   * Adds field-scoped errors before the Gateway accepts a device configuration.
   *
   * <p>Connection allow lists use the same parser as runtime admission, while browse ranges use the
   * address-space parser that later consumes them.
   *
   * @param settings the complete settings being considered for persistence.
   * @param errors the builder that receives field-scoped validation messages.
   */
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
