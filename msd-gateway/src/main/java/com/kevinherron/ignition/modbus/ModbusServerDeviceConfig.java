package com.kevinherron.ignition.modbus;

import com.inductiveautomation.ignition.gateway.dataroutes.openapi.annotations.DefaultValue;
import com.inductiveautomation.ignition.gateway.dataroutes.openapi.annotations.Description;
import com.inductiveautomation.ignition.gateway.dataroutes.openapi.annotations.FormCategory;
import com.inductiveautomation.ignition.gateway.dataroutes.openapi.annotations.FormField;
import com.inductiveautomation.ignition.gateway.dataroutes.openapi.annotations.Label;
import com.inductiveautomation.ignition.gateway.dataroutes.openapi.annotations.MaxLength;
import com.inductiveautomation.ignition.gateway.dataroutes.openapi.annotations.Required;
import com.inductiveautomation.ignition.gateway.web.nav.FormFieldType;

/**
 * Defines the serialized settings and Gateway form schema for a {@link ModbusServerDevice}.
 *
 * <p>The nested records group settings by runtime concern. An omitted {@link Security} section is
 * normalized to unrestricted defaults, so {@link #security()} is available after construction.
 *
 * @param connectivity the local TCP listener settings.
 * @param browsing the Modbus addresses exposed while browsing OPC UA nodes.
 * @param persistence the process-image persistence settings.
 * @param security the Modbus TCP connection-admission settings, or {@code null} to use defaults.
 */
public record ModbusServerDeviceConfig(
    Connectivity connectivity, Browsing browsing, Persistence persistence, Security security) {

  /** Normalizes an omitted security section to its unrestricted default. */
  public ModbusServerDeviceConfig {
    security = security == null ? new Security(null) : security;
  }

  /**
   * Identifies the local endpoint for the Modbus TCP listener.
   *
   * @param bindAddress the local address on which the listener binds.
   * @param port the TCP port on which the listener accepts connections.
   */
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
   * Selects the address ranges advertised while OPC UA clients browse the device.
   *
   * <p>These ranges control discovery only; clients can still address valid nodes directly.
   *
   * @param coilBrowseRanges the coil ranges exposed during browsing.
   * @param discreteInputBrowseRanges the discrete-input ranges exposed during browsing.
   * @param holdingRegisterBrowseRanges the holding-register ranges exposed during browsing.
   * @param inputRegisterBrowseRanges the input-register ranges exposed during browsing.
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
          String inputRegisterBrowseRanges) {}

  /**
   * Controls whether process-image values survive device restarts.
   *
   * @param persistData whether to restore and store process-image values.
   */
  public record Persistence(
      @FormCategory("PERSISTENCE")
          @FormField(FormFieldType.CHECKBOX)
          @Label("")
          @Description("Whether to persist the process image data across restarts.")
          @DefaultValue("false")
          boolean persistData) {}

  /**
   * Controls which remote addresses may open Modbus TCP connections.
   *
   * <p>{@code allowedIpAddresses} accepts {@code *} or a comma-separated list of strict IPv4
   * literals, CIDR blocks, trailing-octet wildcards, and inclusive ranges. An explicit list rejects
   * non-IPv4 peers. {@link
   * com.kevinherron.ignition.modbus.security.AllowedIpAddressFilter#parse(String)} defines the
   * accepted grammar and limits used by both validation and runtime enforcement.
   *
   * @param allowedIpAddresses the connection allow list; {@code null} is normalized to {@code *},
   *     while blank text is preserved for validation.
   */
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
