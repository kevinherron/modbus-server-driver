package com.kevinherron.ignition.modbus.address;

public sealed interface ModbusDataType permits
    ModbusDataType.Bit,
    ModbusDataType.Bool,
    ModbusDataType.Int16,
    ModbusDataType.Int32,
    ModbusDataType.Int64,
    ModbusDataType.UInt16,
    ModbusDataType.UInt32,
    ModbusDataType.UInt64,
    ModbusDataType.Float32,
    ModbusDataType.Double64,
    ModbusDataType.String {

  record Bit(ModbusDataType underlyingType, int bit) implements ModbusDataType {}

  final class Bool implements ModbusDataType {}

  final class Int16 implements ModbusDataType {}

  final class Int32 implements ModbusDataType {}

  final class Int64 implements ModbusDataType {}

  final class UInt16 implements ModbusDataType {}

  final class UInt32 implements ModbusDataType {}

  final class UInt64 implements ModbusDataType {}

  final class Float32 implements ModbusDataType {}

  final class Double64 implements ModbusDataType {}

  record String(int length) implements ModbusDataType {}

  enum Modifier {
    BYTE_ORDER_BIG_ENDIAN,
    BYTE_ORDER_LITTLE_ENDIAN,
    WORD_ORDER_HIGH_LOW,
    WORD_ORDER_LOW_HIGH
  }

}
