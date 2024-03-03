package com.kevinherron.ignition.modbus.address;

import java.util.Set;

public sealed abstract class ModbusAddress {

  private final ModbusArea area;
  private final int offset;
  private final ModbusDataType dataType;
  private final Set<DataTypeModifier> dataTypeModifiers;

  protected ModbusAddress(
      ModbusArea area,
      int offset,
      ModbusDataType dataType,
      Set<DataTypeModifier> dataTypeModifiers
  ) {

    this.area = area;
    this.offset = offset;
    this.dataType = dataType;
    this.dataTypeModifiers = Set.copyOf(dataTypeModifiers);
  }

  public ModbusArea getArea() {
    return area;
  }

  public int getOffset() {
    return offset;
  }

  public ModbusDataType getDataType() {
    return dataType;
  }

  public Set<DataTypeModifier> getDataTypeModifiers() {
    return dataTypeModifiers;
  }


  public static final class ArrayAddress extends ModbusAddress {

    private final int[] dimensions;

    public ArrayAddress(
        ModbusArea area,
        int address,
        ModbusDataType dataType,
        Set<DataTypeModifier> dataTypeModifiers,
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

    public ScalarAddress(
        ModbusArea area,
        int address,
        ModbusDataType dataType,
        Set<DataTypeModifier> dataTypeModifiers
    ) {

      super(area, address, dataType, dataTypeModifiers);
    }

  }

  public enum ModbusArea {
    COILS,
    DISCRETE_INPUTS,
    HOLDING_REGISTERS,
    INPUT_REGISTERS
  }

}
