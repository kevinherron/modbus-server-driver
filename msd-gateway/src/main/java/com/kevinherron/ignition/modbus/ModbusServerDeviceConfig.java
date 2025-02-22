package com.kevinherron.ignition.modbus;

public record ModbusServerDeviceConfig(
    Connectivity connectivity, Browsing browsing, Persistence persistence) {

  public record Connectivity(String bindAddress, int port) {}

  public record Browsing(
      String coilBrowseRanges,
      String discreteInputBrowseRanges,
      String holdingRegisterBrowseRanges,
      String inputRegisterBrowseRanges) {}

  public record Persistence(boolean persistData) {}
}
