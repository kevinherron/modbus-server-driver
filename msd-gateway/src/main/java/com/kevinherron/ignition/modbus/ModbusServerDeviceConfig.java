package com.kevinherron.ignition.modbus;

import com.inductiveautomation.ignition.gateway.dataroutes.openapi.annotations.DefaultValue;
import com.inductiveautomation.ignition.gateway.dataroutes.openapi.annotations.Description;
import com.inductiveautomation.ignition.gateway.dataroutes.openapi.annotations.FormCategory;
import com.inductiveautomation.ignition.gateway.dataroutes.openapi.annotations.FormField;
import com.inductiveautomation.ignition.gateway.dataroutes.openapi.annotations.Hidden;
import com.inductiveautomation.ignition.gateway.dataroutes.openapi.annotations.Label;
import com.inductiveautomation.ignition.gateway.dataroutes.openapi.annotations.MaxLength;
import com.inductiveautomation.ignition.gateway.dataroutes.openapi.annotations.Required;
import com.inductiveautomation.ignition.gateway.web.nav.FormFieldType;

/**
 * Defines the versioned connection, OPC UA browsing, process-image, and connection-security
 * settings for a Modbus server device.
 *
 * <p>The Ignition resource form uses the component annotations to build its schema. Decoders may
 * omit optional nested settings; the canonical constructor applies their documented defaults.
 */
public record ModbusServerDeviceConfig(
    @Hidden @Description("The version of this settings document.") @DefaultValue("2")
        int configVersion,
    Connectivity connectivity,
    Browsing browsing,
    ProcessImageSettings processImage,
    Security security) {

  /** Current persisted settings format. */
  public static final int CURRENT_CONFIG_VERSION = 2;

  /** Applies documented defaults to optional nested settings. */
  public ModbusServerDeviceConfig {
    processImage = processImage == null ? new ProcessImageSettings(false, false) : processImage;
    security = security == null ? new Security(null) : security;
  }

  /** Creates settings with unrestricted connection admission. */
  public ModbusServerDeviceConfig(
      int configVersion,
      Connectivity connectivity,
      Browsing browsing,
      ProcessImageSettings processImage) {
    this(configVersion, connectivity, browsing, processImage, null);
  }

  /** Identifies the local interface and TCP port used by the Modbus server. */
  public record Connectivity(
      @FormCategory("CONNECTIVITY")
          @FormField(FormFieldType.TEXT)
          @Label("Bind Address *")
          @Required
          @Description("The address to bind to.")
          @DefaultValue("0.0.0.0")
          String bindAddress,
      @FormCategory("CONNECTIVITY")
          @FormField(FormFieldType.NUMBER)
          @Label("Port *")
          @Required
          @Description("The port to bind to.")
          @DefaultValue("502")
          int port) {}

  /**
   * Selects the Modbus addresses and unit folders exposed by OPC UA browsing.
   *
   * <p>Browse ranges control discovery only. Valid Modbus addresses and unit IDs remain directly
   * addressable even when they are absent from these ranges.
   */
  public record Browsing(
      @FormCategory("BROWSING")
          @FormField(FormFieldType.TEXT)
          @Label("Coil Browse Ranges")
          @Description("The coil ranges to create browsable Nodes for.")
          @DefaultValue("")
          String coilBrowseRanges,
      @FormCategory("BROWSING")
          @FormField(FormFieldType.TEXT)
          @Label("Discrete Input Browse Ranges")
          @Description("The discrete input ranges to create browsable Nodes for.")
          @DefaultValue("")
          String discreteInputBrowseRanges,
      @FormCategory("BROWSING")
          @FormField(FormFieldType.TEXT)
          @Label("Holding Register Ranges")
          @Description("The holding register ranges to create browsable Nodes for.")
          @DefaultValue("")
          String holdingRegisterBrowseRanges,
      @FormCategory("BROWSING")
          @FormField(FormFieldType.TEXT)
          @Label("Input Register Ranges")
          @Description("The input register ranges to create browsable Nodes for.")
          @DefaultValue("")
          String inputRegisterBrowseRanges,
      @FormCategory("BROWSING")
          @FormField(FormFieldType.TEXT)
          @Label("Unit ID Browse Ranges")
          @Description(
              "The unit ID ranges to create browsable Nodes for when using separate process "
                  + "images.")
          @DefaultValue("0")
          String unitIdBrowseRanges) {

    /** Applies the unit-0 browse default while preserving an explicit empty selection. */
    public Browsing {
      unitIdBrowseRanges = unitIdBrowseRanges == null ? "0" : unitIdBrowseRanges;
    }
  }

  /** Selects process-image routing and retention behavior. */
  public record ProcessImageSettings(
      @FormCategory("PROCESS IMAGE")
          @FormField(FormFieldType.CHECKBOX)
          @Label("")
          @Description("Whether to persist the process image data across restarts.")
          @DefaultValue("false")
          boolean persistData,
      @FormCategory("PROCESS IMAGE")
          @FormField(FormFieldType.CHECKBOX)
          @Label("")
          @Description("Whether to use a separate process image for each unit ID.")
          @DefaultValue("false")
          boolean separatePerUnitId) {}

  /** Controls which remote addresses may open Modbus TCP connections. */
  public record Security(
      @FormCategory("SECURITY")
          @FormField(FormFieldType.TEXT)
          @Label("Allowed IP Addresses *")
          @Required
          @Description(
              "Comma-separated list of remote addresses allowed to connect. Accepts \"*\", "
                  + "IPv4 literals, IPv4 CIDR blocks, trailing IPv4 wildcards, and IPv4 ranges.")
          @DefaultValue("*")
          @MaxLength(4096)
          String allowedIpAddresses) {

    /** Normalizes an omitted allow list to {@code *} without changing blank text. */
    public Security {
      allowedIpAddresses = allowedIpAddresses == null ? "*" : allowedIpAddresses;
    }
  }
}
