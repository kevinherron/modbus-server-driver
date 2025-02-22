package com.kevinherron.ignition.modbus.address;

public sealed interface DataTypeModifier {

  record ByteOrderModifier(ByteOrder byteOrder) implements DataTypeModifier {}

  record WordOrderModifier(WordOrder wordOrder) implements DataTypeModifier {}

  enum ByteOrder {
    BIG_ENDIAN,
    LITTLE_ENDIAN
  }

  enum WordOrder {
    HIGH_LOW,
    LOW_HIGH
  }
}
