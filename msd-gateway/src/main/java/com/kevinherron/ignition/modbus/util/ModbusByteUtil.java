package com.kevinherron.ignition.modbus.util;

import com.digitalpetri.util.ByteArrayByteOps;
import com.kevinherron.ignition.modbus.address.DataTypeModifier;
import com.kevinherron.ignition.modbus.address.ModbusAddress;
import com.kevinherron.ignition.modbus.address.ModbusDataType;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.ULong;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;

public final class ModbusByteUtil {

  private ModbusByteUtil() {}

  public static Object getValueForBytes(byte[] registerBytes, ModbusAddress address)
      throws UaException {

    return getValueForBytes(registerBytes, address.getDataType(), address.getDataTypeModifiers());
  }

  public static Object getValueForBytes(
      byte[] registerBytes, ModbusDataType dataType, Set<DataTypeModifier> modifiers)
      throws UaException {

    if (dataType instanceof ModbusDataType.Bit d) {
      // read underlying value, check and return specified bit
      Object value = getValueForBytes(registerBytes, d.underlyingType(), modifiers);
      if (value instanceof Number n) {
        return (n.longValue() & (1L << d.bit())) != 0L;
      } else {
        throw new UaException(StatusCodes.Bad_InternalError, "underlying: " + d.underlyingType());
      }
    } else if (dataType instanceof ModbusDataType.Bool) {
      return getByteOps(modifiers).getBoolean(registerBytes, 0);
    } else if (dataType instanceof ModbusDataType.Int16) {
      return getByteOps(modifiers).getShort(registerBytes, 0);
    } else if (dataType instanceof ModbusDataType.UInt16) {
      short v = getByteOps(modifiers).getShort(registerBytes, 0);
      return UShort.valueOf(v);
    } else if (dataType instanceof ModbusDataType.Int32) {
      return getByteOps(modifiers).getInt(registerBytes, 0);
    } else if (dataType instanceof ModbusDataType.UInt32) {
      int v = getByteOps(modifiers).getInt(registerBytes, 0);
      return UInteger.valueOf(v);
    } else if (dataType instanceof ModbusDataType.Int64) {
      return getByteOps(modifiers).getLong(registerBytes, 0);
    } else if (dataType instanceof ModbusDataType.UInt64) {
      long v = getByteOps(modifiers).getLong(registerBytes, 0);
      return ULong.valueOf(v);
    } else if (dataType instanceof ModbusDataType.Float32) {
      return getByteOps(modifiers).getFloat(registerBytes, 0);
    } else if (dataType instanceof ModbusDataType.Double64) {
      return getByteOps(modifiers).getDouble(registerBytes, 0);
    } else if (dataType instanceof ModbusDataType.String d) {
      int length = d.length();
      for (int i = 0; i < length; i++) {
        if (registerBytes[i] == 0) {
          length = i;
          break;
        }
      }
      return new String(registerBytes, 0, length, StandardCharsets.UTF_8);
    } else {
      throw new UaException(StatusCodes.Bad_InternalError, "dataType: " + dataType);
    }
  }

  public static byte[] getBytesForValue(Object value, ModbusAddress address) throws UaException {
    return getBytesForValue(value, address.getDataType(), address.getDataTypeModifiers());
  }

  public static byte[] getBytesForValue(
      Object value, ModbusDataType dataType, Set<DataTypeModifier> modifiers) throws UaException {

    byte[] valueBytes = new byte[dataType.getRegisterCount() * 2];

    if (dataType instanceof ModbusDataType.Bool) {
      if (value instanceof Boolean v) {
        getByteOps(modifiers).setBoolean(valueBytes, 0, v);
      } else {
        throw new UaException(StatusCodes.Bad_TypeMismatch);
      }
    } else if (dataType instanceof ModbusDataType.Int16) {
      if (value instanceof Short v) {
        getByteOps(modifiers).setShort(valueBytes, 0, v);
      } else {
        throw new UaException(StatusCodes.Bad_TypeMismatch);
      }
    } else if (dataType instanceof ModbusDataType.UInt16) {
      if (value instanceof UShort v) {
        getByteOps(modifiers).setShort(valueBytes, 0, v.shortValue());
      } else {
        throw new UaException(StatusCodes.Bad_TypeMismatch);
      }
    } else if (dataType instanceof ModbusDataType.Int32) {
      if (value instanceof Integer v) {
        getByteOps(modifiers).setInt(valueBytes, 0, v);
      } else {
        throw new UaException(StatusCodes.Bad_TypeMismatch);
      }
    } else if (dataType instanceof ModbusDataType.UInt32) {
      if (value instanceof UInteger v) {
        getByteOps(modifiers).setInt(valueBytes, 0, v.intValue());
      } else {
        throw new UaException(StatusCodes.Bad_TypeMismatch);
      }
    } else if (dataType instanceof ModbusDataType.Int64) {
      if (value instanceof Long v) {
        getByteOps(modifiers).setLong(valueBytes, 0, v);
      } else {
        throw new UaException(StatusCodes.Bad_TypeMismatch);
      }
    } else if (dataType instanceof ModbusDataType.UInt64) {
      if (value instanceof ULong v) {
        getByteOps(modifiers).setLong(valueBytes, 0, v.longValue());
      } else {
        throw new UaException(StatusCodes.Bad_TypeMismatch);
      }
    } else if (dataType instanceof ModbusDataType.Float32) {
      if (value instanceof Float v) {
        getByteOps(modifiers).setFloat(valueBytes, 0, v);
      } else {
        throw new UaException(StatusCodes.Bad_TypeMismatch);
      }
    } else if (dataType instanceof ModbusDataType.Double64) {
      if (value instanceof Double v) {
        getByteOps(modifiers).setDouble(valueBytes, 0, v);
      } else {
        throw new UaException(StatusCodes.Bad_TypeMismatch);
      }
    } else if (dataType instanceof ModbusDataType.String) {
      if (value instanceof String v) {
        byte[] stringBytes = v.getBytes(StandardCharsets.UTF_8);
        int length = Math.min(stringBytes.length, valueBytes.length);
        System.arraycopy(stringBytes, 0, valueBytes, 0, length);
      } else {
        throw new UaException(StatusCodes.Bad_TypeMismatch);
      }
    } else {
      throw new UaException(StatusCodes.Bad_InternalError, "dataType: " + dataType);
    }

    return valueBytes;
  }

  static ByteArrayByteOps getByteOps(Set<DataTypeModifier> modifiers) {
    DataTypeModifier.ByteOrder byteOrder = DataTypeModifier.ByteOrder.BIG_ENDIAN;
    DataTypeModifier.WordOrder wordOrder = DataTypeModifier.WordOrder.HIGH_LOW;

    for (DataTypeModifier modifier : modifiers) {
      if (modifier instanceof DataTypeModifier.ByteOrderModifier m) {
        byteOrder = m.byteOrder();
      }
      if (modifier instanceof DataTypeModifier.WordOrderModifier m) {
        wordOrder = m.wordOrder();
      }
    }

    return switch (byteOrder) {
      case BIG_ENDIAN ->
          switch (wordOrder) {
            case HIGH_LOW -> ByteArrayByteOps.BIG_ENDIAN;
            case LOW_HIGH -> ByteArrayByteOps.BIG_ENDIAN_LOW_HIGH;
          };
      case LITTLE_ENDIAN ->
          switch (wordOrder) {
            case HIGH_LOW -> ByteArrayByteOps.LITTLE_ENDIAN;
            case LOW_HIGH -> ByteArrayByteOps.LITTLE_ENDIAN_LOW_HIGH;
          };
    };
  }
}
