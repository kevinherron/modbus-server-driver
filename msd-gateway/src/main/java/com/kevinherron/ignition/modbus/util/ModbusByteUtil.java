package com.kevinherron.ignition.modbus.util;

import com.digitalpetri.util.ByteArrayByteOps;
import com.kevinherron.ignition.modbus.address.DataTypeModifier;
import com.kevinherron.ignition.modbus.address.ModbusAddress;
import com.kevinherron.ignition.modbus.address.ModbusDataType;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.types.builtin.Matrix;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.ULong;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;

public final class ModbusByteUtil {

  private ModbusByteUtil() {}

  public static Object getValueForBytes(byte[] registerBytes, ModbusAddress address)
      throws UaException {

    if (address instanceof ModbusAddress.ArrayAddress array) {
      if (array.getDimensions().length == 1) {
        return getArrayValueForBytes(
            registerBytes,
            array.getDataType(),
            array.getDataTypeModifiers(),
            array.getDimensions());
      } else {
        assert array.getDimensions().length > 1;

        return getMatrixValueForBytes(
            registerBytes,
            array.getDataType(),
            array.getDataTypeModifiers(),
            array.getDimensions());
      }
    } else if (address instanceof ModbusAddress.ScalarAddress) {
      return getScalarValueForBytes(
          registerBytes, address.getDataType(), address.getDataTypeModifiers());
    } else {
      throw new IllegalArgumentException("address: " + address);
    }
  }

  public static Object getScalarValueForBytes(
      byte[] registerBytes, ModbusDataType dataType, Set<DataTypeModifier> modifiers)
      throws UaException {

    if (dataType instanceof ModbusDataType.Bit d) {
      // read the underlying value, check and return the specified bit
      Object value = getScalarValueForBytes(registerBytes, d.underlyingType(), modifiers);
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

  public static Object getArrayValueForBytes(
      byte[] registerBytes,
      ModbusDataType dataType,
      Set<DataTypeModifier> modifiers,
      int[] dimensions)
      throws UaException {

    if (dimensions.length != 1) {
      throw new UaException(StatusCodes.Bad_TypeMismatch, "expected 1-dimensional array");
    }

    int arrayLength = dimensions[0];
    int bytesPerElement = dataType.getRegisterCount() * 2;
    int totalBytes = arrayLength * bytesPerElement;

    if (registerBytes.length < totalBytes) {
      throw new UaException(StatusCodes.Bad_InternalError, "registerBytes.length < " + totalBytes);
    }

    ByteArrayByteOps byteOps = getByteOps(modifiers);

    Class<?> componentType;
    if (dataType instanceof ModbusDataType.Bool) {
      componentType = Boolean.class;
    } else if (dataType instanceof ModbusDataType.Int16) {
      componentType = Short.class;
    } else if (dataType instanceof ModbusDataType.UInt16) {
      componentType = UShort.class;
    } else if (dataType instanceof ModbusDataType.Int32) {
      componentType = Integer.class;
    } else if (dataType instanceof ModbusDataType.UInt32) {
      componentType = UInteger.class;
    } else if (dataType instanceof ModbusDataType.Int64) {
      componentType = Long.class;
    } else if (dataType instanceof ModbusDataType.UInt64) {
      componentType = ULong.class;
    } else if (dataType instanceof ModbusDataType.Float32) {
      componentType = Float.class;
    } else if (dataType instanceof ModbusDataType.Double64) {
      componentType = Double.class;
    } else if (dataType instanceof ModbusDataType.String) {
      componentType = String.class;
    } else if (dataType instanceof ModbusDataType.Bit) {
      throw new UaException(StatusCodes.Bad_InternalError, "Bit arrays are not allowed");
    } else {
      throw new UaException(StatusCodes.Bad_InternalError, "dataType: " + dataType);
    }

    Object array = Array.newInstance(componentType, arrayLength);

    // Iterate and parse
    for (int i = 0; i < arrayLength; i++) {
      int offset = i * bytesPerElement;

      Object value;
      if (dataType instanceof ModbusDataType.Bool) {
        value = byteOps.getBoolean(registerBytes, offset);
      } else if (dataType instanceof ModbusDataType.Int16) {
        value = byteOps.getShort(registerBytes, offset);
      } else if (dataType instanceof ModbusDataType.UInt16) {
        short v = byteOps.getShort(registerBytes, offset);
        value = UShort.valueOf(v);
      } else if (dataType instanceof ModbusDataType.Int32) {
        value = byteOps.getInt(registerBytes, offset);
      } else if (dataType instanceof ModbusDataType.UInt32) {
        int v = byteOps.getInt(registerBytes, offset);
        value = UInteger.valueOf(v);
      } else if (dataType instanceof ModbusDataType.Int64) {
        value = byteOps.getLong(registerBytes, offset);
      } else if (dataType instanceof ModbusDataType.UInt64) {
        long v = byteOps.getLong(registerBytes, offset);
        value = ULong.valueOf(v);
      } else if (dataType instanceof ModbusDataType.Float32) {
        value = byteOps.getFloat(registerBytes, offset);
      } else if (dataType instanceof ModbusDataType.Double64) {
        value = byteOps.getDouble(registerBytes, offset);
      } else if (dataType instanceof ModbusDataType.String str) {
        int length = str.length();
        for (int j = 0; j < length; j++) {
          if (registerBytes[offset + j] == 0) {
            length = j;
            break;
          }
        }
        value = new String(registerBytes, offset, length, StandardCharsets.UTF_8);
      } else {
        throw new UaException(StatusCodes.Bad_InternalError, "dataType: " + dataType);
      }

      Array.set(array, i, value);
    }

    return array;
  }

  public static Object getMatrixValueForBytes(
      byte[] registerBytes,
      ModbusDataType dataType,
      Set<DataTypeModifier> modifiers,
      int[] dimensions)
      throws UaException {

    // TODO
    throw new UaException(StatusCodes.Bad_NotImplemented);
  }

  public static byte[] getBytesForValue(Object value, ModbusAddress address) throws UaException {
    if (address instanceof ModbusAddress.ArrayAddress array) {
      if (array.getDimensions().length == 1) {
        return getBytesForArrayValue(
            value, array.getDataType(), array.getDataTypeModifiers(), array.getDimensions());
      } else {
        assert array.getDimensions().length > 1;

        return getBytesForMatrixValue(
            value, array.getDataType(), array.getDataTypeModifiers(), array.getDimensions());
      }
    } else if (address instanceof ModbusAddress.ScalarAddress) {
      return getBytesForScalarValue(value, address.getDataType(), address.getDataTypeModifiers());
    } else {
      throw new IllegalArgumentException("address: " + address);
    }
  }

  public static byte[] getBytesForScalarValue(
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

  static byte[] getBytesForArrayValue(
      Object value, ModbusDataType dataType, Set<DataTypeModifier> modifiers, int[] dimensions)
      throws UaException {

    // Check if the input value is actually a Java array
    if (!value.getClass().isArray()) {
      throw new UaException(StatusCodes.Bad_TypeMismatch, "expected array");
    }

    // Validate dimensions array
    if (dimensions.length != 1) {
      throw new UaException(StatusCodes.Bad_TypeMismatch, "expected 1-dimensional array");
    }

    int arrayLength = dimensions[0];

    int bytesPerElement = dataType.getRegisterCount() * 2;
    int totalBytes = arrayLength * bytesPerElement;
    byte[] valueBytes = new byte[totalBytes];

    ByteArrayByteOps byteOps = getByteOps(modifiers);

    for (int i = 0; i < arrayLength; i++) {
      Object element = Array.get(value, i);
      int offset = i * bytesPerElement;

      if (dataType instanceof ModbusDataType.Bool) {
        if (element instanceof Boolean v) {
          byteOps.setBoolean(valueBytes, offset, v);
        } else {
          throw new UaException(StatusCodes.Bad_TypeMismatch);
        }
      } else if (dataType instanceof ModbusDataType.Int16) {
        if (element instanceof Short v) {
          byteOps.setShort(valueBytes, offset, v);
        } else {
          throw new UaException(StatusCodes.Bad_TypeMismatch);
        }
      } else if (dataType instanceof ModbusDataType.UInt16) {
        if (element instanceof UShort v) {
          byteOps.setShort(valueBytes, offset, v.shortValue());
        } else {
          throw new UaException(StatusCodes.Bad_TypeMismatch);
        }
      } else if (dataType instanceof ModbusDataType.Int32) {
        if (element instanceof Integer v) {
          byteOps.setInt(valueBytes, offset, v);
        } else {
          throw new UaException(StatusCodes.Bad_TypeMismatch);
        }
      } else if (dataType instanceof ModbusDataType.UInt32) {
        if (element instanceof UInteger v) {
          byteOps.setInt(valueBytes, offset, v.intValue());
        } else {
          throw new UaException(StatusCodes.Bad_TypeMismatch);
        }
      } else if (dataType instanceof ModbusDataType.Int64) {
        if (element instanceof Long v) {
          byteOps.setLong(valueBytes, offset, v);
        } else {
          throw new UaException(StatusCodes.Bad_TypeMismatch);
        }
      } else if (dataType instanceof ModbusDataType.UInt64) {
        if (element instanceof ULong v) {
          byteOps.setLong(valueBytes, offset, v.longValue());
        } else {
          throw new UaException(StatusCodes.Bad_TypeMismatch);
        }
      } else if (dataType instanceof ModbusDataType.Float32) {
        if (element instanceof Float v) {
          byteOps.setFloat(valueBytes, offset, v);
        } else {
          throw new UaException(StatusCodes.Bad_TypeMismatch);
        }
      } else if (dataType instanceof ModbusDataType.Double64) {
        if (element instanceof Double v) {
          byteOps.setDouble(valueBytes, offset, v);
        } else {
          throw new UaException(StatusCodes.Bad_TypeMismatch);
        }
      } else if (dataType instanceof ModbusDataType.String) {
        if (element instanceof String v) {
          // note: padding with null bytes happens automatically
          // since valueBytes is initialized with zeros
          byte[] stringBytes = v.getBytes(StandardCharsets.UTF_8);
          int lengthToCopy = Math.min(stringBytes.length, bytesPerElement);
          System.arraycopy(stringBytes, 0, valueBytes, offset, lengthToCopy);
        } else {
          throw new UaException(StatusCodes.Bad_TypeMismatch);
        }
      } else {
        throw new UaException(StatusCodes.Bad_InternalError, "dataType: " + dataType);
      }
    }

    return valueBytes;
  }

  static byte[] getBytesForMatrixValue(
      Object value, ModbusDataType dataType, Set<DataTypeModifier> modifiers, int[] dimensions)
      throws UaException {

    if (value instanceof Matrix matrix) {
      if (matrix.isNull()) {
        throw new UaException(StatusCodes.Bad_TypeMismatch);
      }

      if (matrix.getDimensions().length != dimensions.length) {
        throw new UaException(StatusCodes.Bad_TypeMismatch);
      }

      for (int i = 0; i < dimensions.length; i++) {
        if (matrix.getDimensions()[i] != dimensions[i]) {
          throw new UaException(StatusCodes.Bad_TypeMismatch);
        }
      }

      Object flatArrayValue = matrix.getElements();
      assert flatArrayValue != null;

      int elementCount = 1;
      for (int dimension : dimensions) {
        elementCount *= dimension;
      }

      return getBytesForArrayValue(flatArrayValue, dataType, modifiers, new int[] {elementCount});
    } else {
      throw new UaException(StatusCodes.Bad_TypeMismatch);
    }
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
