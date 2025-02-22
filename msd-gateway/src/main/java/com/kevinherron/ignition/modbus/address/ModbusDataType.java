package com.kevinherron.ignition.modbus.address;

import org.eclipse.milo.opcua.stack.core.OpcUaDataType;

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

  OpcUaDataType getOpcUaDataType();

  int getRegisterCount();

  record Bit(ModbusDataType underlyingType, int bit) implements ModbusDataType {
    @Override
    public OpcUaDataType getOpcUaDataType() {
      return OpcUaDataType.Boolean;
    }

    @Override
    public int getRegisterCount() {
      return underlyingType.getRegisterCount();
    }
  }

  final class Bool implements ModbusDataType {
    @Override
    public OpcUaDataType getOpcUaDataType() {
      return OpcUaDataType.Boolean;
    }

    @Override
    public int getRegisterCount() {
      return 1;
    }
  }

  final class Int16 implements ModbusDataType {
    @Override
    public OpcUaDataType getOpcUaDataType() {
      return OpcUaDataType.Int16;
    }

    @Override
    public int getRegisterCount() {
      return 1;
    }
  }

  final class Int32 implements ModbusDataType {
    @Override
    public OpcUaDataType getOpcUaDataType() {
      return OpcUaDataType.Int32;
    }

    @Override
    public int getRegisterCount() {
      return 2;
    }
  }

  final class Int64 implements ModbusDataType {
    @Override
    public OpcUaDataType getOpcUaDataType() {
      return OpcUaDataType.Int64;
    }

    @Override
    public int getRegisterCount() {
      return 4;
    }
  }

  final class UInt16 implements ModbusDataType {
    @Override
    public OpcUaDataType getOpcUaDataType() {
      return OpcUaDataType.UInt16;
    }

    @Override
    public int getRegisterCount() {
      return 1;
    }
  }

  final class UInt32 implements ModbusDataType {
    @Override
    public OpcUaDataType getOpcUaDataType() {
      return OpcUaDataType.UInt32;
    }

    @Override
    public int getRegisterCount() {
      return 2;
    }
  }

  final class UInt64 implements ModbusDataType {
    @Override
    public OpcUaDataType getOpcUaDataType() {
      return OpcUaDataType.UInt64;
    }

    @Override
    public int getRegisterCount() {
      return 4;
    }
  }

  final class Float32 implements ModbusDataType {
    @Override
    public OpcUaDataType getOpcUaDataType() {
      return OpcUaDataType.Float;
    }

    @Override
    public int getRegisterCount() {
      return 2;
    }
  }

  final class Double64 implements ModbusDataType {
    @Override
    public OpcUaDataType getOpcUaDataType() {
      return OpcUaDataType.Double;
    }

    @Override
    public int getRegisterCount() {
      return 4;
    }
  }

  record String(int length) implements ModbusDataType {
    @Override
    public OpcUaDataType getOpcUaDataType() {
      return OpcUaDataType.String;
    }

    @Override
    public int getRegisterCount() {
      return (length + 1) / 2;
    }
  }
}
