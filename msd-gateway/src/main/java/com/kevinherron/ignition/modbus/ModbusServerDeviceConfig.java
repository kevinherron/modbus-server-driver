package com.kevinherron.ignition.modbus;

import com.inductiveautomation.ignition.gateway.dataroutes.openapi.annotations.DefaultValue;
import com.inductiveautomation.ignition.gateway.dataroutes.openapi.annotations.Description;
import com.inductiveautomation.ignition.gateway.dataroutes.openapi.annotations.FormCategory;
import com.inductiveautomation.ignition.gateway.dataroutes.openapi.annotations.FormField;
import com.inductiveautomation.ignition.gateway.dataroutes.openapi.annotations.Label;
import com.inductiveautomation.ignition.gateway.dataroutes.openapi.annotations.Required;
import com.inductiveautomation.ignition.gateway.web.nav.FormFieldType;

public record ModbusServerDeviceConfig(
    Connectivity connectivity, Browsing browsing, Persistence persistence) {

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

  public record Persistence(
      @FormCategory("PERSISTENCE")
          @FormField(FormFieldType.CHECKBOX)
          @Label("")
          @Description("Whether to persist the process image data across restarts.")
          @DefaultValue("false")
          boolean persistData) {}
}
