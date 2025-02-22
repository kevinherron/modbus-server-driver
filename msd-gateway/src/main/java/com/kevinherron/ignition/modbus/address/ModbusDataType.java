package com.kevinherron.ignition.modbus.address;

import org.eclipse.milo.opcua.stack.core.BuiltinDataType;

public sealed interface ModbusDataType
    permits ModbusDataType.Bit,
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

  BuiltinDataType getBuiltinDataType();

  int getRegisterCount();

  record Bit(ModbusDataType underlyingType, int bit) implements ModbusDataType {
    @Override
    public BuiltinDataType getBuiltinDataType() {
      return BuiltinDataType.Boolean;
    }

    @Override
    public int getRegisterCount() {
      return underlyingType.getRegisterCount();
    }
  }

  final class Bool implements ModbusDataType {
    @Override
    public BuiltinDataType getBuiltinDataType() {
      return BuiltinDataType.Boolean;
    }

    @Override
    public int getRegisterCount() {
      return 1;
    }
  }

  final class Int16 implements ModbusDataType {
    @Override
    public BuiltinDataType getBuiltinDataType() {
      return BuiltinDataType.Int16;
    }

    @Override
    public int getRegisterCount() {
      return 1;
    }
  }

  final class Int32 implements ModbusDataType {
    @Override
    public BuiltinDataType getBuiltinDataType() {
      return BuiltinDataType.Int32;
    }

    @Override
    public int getRegisterCount() {
      return 2;
    }
  }

  final class Int64 implements ModbusDataType {
    @Override
    public BuiltinDataType getBuiltinDataType() {
      return BuiltinDataType.Int64;
    }

    @Override
    public int getRegisterCount() {
      return 4;
    }
  }

  final class UInt16 implements ModbusDataType {
    @Override
    public BuiltinDataType getBuiltinDataType() {
      return BuiltinDataType.UInt16;
    }

    @Override
    public int getRegisterCount() {
      return 1;
    }
  }

  final class UInt32 implements ModbusDataType {
    @Override
    public BuiltinDataType getBuiltinDataType() {
      return BuiltinDataType.UInt32;
    }

    @Override
    public int getRegisterCount() {
      return 2;
    }
  }

  final class UInt64 implements ModbusDataType {
    @Override
    public BuiltinDataType getBuiltinDataType() {
      return BuiltinDataType.UInt64;
    }

    @Override
    public int getRegisterCount() {
      return 4;
    }
  }

  final class Float32 implements ModbusDataType {
    @Override
    public BuiltinDataType getBuiltinDataType() {
      return BuiltinDataType.Float;
    }

    @Override
    public int getRegisterCount() {
      return 2;
    }
  }

  final class Double64 implements ModbusDataType {
    @Override
    public BuiltinDataType getBuiltinDataType() {
      return BuiltinDataType.Double;
    }

    @Override
    public int getRegisterCount() {
      return 4;
    }
  }

  record String(int length) implements ModbusDataType {
    @Override
    public BuiltinDataType getBuiltinDataType() {
      return BuiltinDataType.String;
    }

    @Override
    public int getRegisterCount() {
      return (length + 1) / 2;
    }
  }
}
