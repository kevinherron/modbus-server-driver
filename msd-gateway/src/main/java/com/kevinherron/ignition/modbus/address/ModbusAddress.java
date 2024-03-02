package com.kevinherron.ignition.modbus.address;

import java.util.Set;

public sealed abstract class ModbusAddress {

  private final ModbusArea area;
  private final int address;
  private final ModbusDataType dataType;
  private final Set<ModbusDataType.Modifier> dataTypeModifiers;

  protected ModbusAddress(
      ModbusArea area,
      int address,
      ModbusDataType dataType,
      Set<ModbusDataType.Modifier> dataTypeModifiers
  ) {

    this.area = area;
    this.address = address;
    this.dataType = dataType;
    this.dataTypeModifiers = Set.copyOf(dataTypeModifiers);
  }

  public ModbusArea getArea() {
    return area;
  }

  public int getAddress() {
    return address;
  }

  public ModbusDataType getDataType() {
    return dataType;
  }

  public Set<ModbusDataType.Modifier> getDataTypeModifiers() {
    return dataTypeModifiers;
  }


  public static final class ArrayAddress extends ModbusAddress {

    private final int[] dimensions;

    public ArrayAddress(
        ModbusArea area,
        int address,
        ModbusDataType dataType,
        Set<ModbusDataType.Modifier> dataTypeModifiers,
        int[] dimensions
    ) {

      super(area, address, dataType, dataTypeModifiers);

      this.dimensions = dimensions;
    }

    public int[] getDimensions() {
      return dimensions;
    }

  }

  public static final class ScalarAddress extends ModbusAddress {

    private final int bitIndex;

    public ScalarAddress(
        ModbusArea area,
        int address,
        ModbusDataType dataType,
        Set<ModbusDataType.Modifier> dataTypeModifiers
    ) {

      this(area, address, dataType, dataTypeModifiers, -1);
    }

    public ScalarAddress(
        ModbusArea area,
        int address,
        ModbusDataType dataType,
        Set<ModbusDataType.Modifier> dataTypeModifiers,
        int bitIndex
    ) {

      super(area, address, dataType, dataTypeModifiers);

      this.bitIndex = bitIndex;
    }

    public int getBitIndex() {
      return bitIndex;
    }

  }

  public enum ModbusArea {
    COILS,
    DISCRETE_INPUTS,
    HOLDING_REGISTERS,
    INPUT_REGISTERS
  }

}
