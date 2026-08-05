package com.kevinherron.ignition.modbus.util;

import com.digitalpetri.util.ByteArrayByteOps;
import com.kevinherron.ignition.modbus.address.DataTypeModifier;
import com.kevinherron.ignition.modbus.address.ModbusAddress;
import com.kevinherron.ignition.modbus.address.ModbusDataType;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
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

    return getScalarValueForBytes(registerBytes, 0, dataType, modifiers);
  }

  static Object getScalarValueForBytes(
      byte[] registerBytes, int offset, ModbusDataType dataType, Set<DataTypeModifier> modifiers)
      throws UaException {

    if (dataType instanceof ModbusDataType.Bit d) {
      // read the underlying value, check and return the specified bit
      Object value = getScalarValueForBytes(registerBytes, offset, d.underlyingType(), modifiers);
      if (value instanceof Number n) {
        return (n.longValue() & (1L << d.bit())) != 0L;
      } else {
        throw new UaException(StatusCodes.Bad_InternalError, "underlying: " + d.underlyingType());
      }
    } else if (dataType instanceof ModbusDataType.Bool) {
      return getByteOps(modifiers).getBoolean(registerBytes, offset);
    } else if (dataType instanceof ModbusDataType.Int16) {
      return getByteOps(modifiers).getShort(registerBytes, offset);
    } else if (dataType instanceof ModbusDataType.UInt16) {
      short v = getByteOps(modifiers).getShort(registerBytes, offset);
      return UShort.valueOf(v);
    } else if (dataType instanceof ModbusDataType.Int32) {
      return getByteOps(modifiers).getInt(registerBytes, offset);
    } else if (dataType instanceof ModbusDataType.UInt32) {
      int v = getByteOps(modifiers).getInt(registerBytes, offset);
      return UInteger.valueOf(v);
    } else if (dataType instanceof ModbusDataType.Int64) {
      return getByteOps(modifiers).getLong(registerBytes, offset);
    } else if (dataType instanceof ModbusDataType.UInt64) {
      long v = getByteOps(modifiers).getLong(registerBytes, offset);
      return ULong.valueOf(v);
    } else if (dataType instanceof ModbusDataType.Float32) {
      return getByteOps(modifiers).getFloat(registerBytes, offset);
    } else if (dataType instanceof ModbusDataType.Double64) {
      return getByteOps(modifiers).getDouble(registerBytes, offset);
    } else if (dataType instanceof ModbusDataType.String d) {
      int length = d.length();
      for (int i = 0; i < length; i++) {
        if (registerBytes[offset + i] == 0) {
          length = i;
          break;
        }
      }
      return new String(registerBytes, offset, length, StandardCharsets.UTF_8);
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

    if (dataType instanceof ModbusDataType.Bit) {
      throw new UaException(StatusCodes.Bad_InternalError, "Bit arrays are not allowed");
    }

    int arrayLength = dimensions[0];
    int bytesPerElement = dataType.getRegisterCount() * 2;
    int totalBytes = arrayLength * bytesPerElement;

    if (registerBytes.length < totalBytes) {
      throw new UaException(StatusCodes.Bad_InternalError, "registerBytes.length < " + totalBytes);
    }

    Class<?> componentType = dataType.getOpcUaDataType().getBackingClass();
    Object array = Array.newInstance(componentType, arrayLength);

    for (int i = 0; i < arrayLength; i++) {
      Array.set(
          array, i, getScalarValueForBytes(registerBytes, i * bytesPerElement, dataType, modifiers));
    }

    return array;
  }

  public static Object getMatrixValueForBytes(
      byte[] registerBytes,
      ModbusDataType dataType,
      Set<DataTypeModifier> modifiers,
      int[] dimensions)
      throws UaException {

    if (dimensions.length <= 1) {
      throw new UaException(
          StatusCodes.Bad_InternalError,
          "expected multi-dimensional array (dimensions.length > 1)");
    }

    Object flatArray =
        getArrayValueForBytes(
            registerBytes, dataType, modifiers, new int[] {elementCount(dimensions)});

    return new Matrix(flatArray, dimensions, dataType.getOpcUaDataType());
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
    setBytesForScalarValue(value, valueBytes, 0, dataType, modifiers);
    return valueBytes;
  }

  static void setBytesForScalarValue(
      Object value,
      byte[] valueBytes,
      int offset,
      ModbusDataType dataType,
      Set<DataTypeModifier> modifiers)
      throws UaException {

    if (dataType instanceof ModbusDataType.Bool) {
      if (value instanceof Boolean v) {
        getByteOps(modifiers).setBoolean(valueBytes, offset, v);
      } else {
        throw new UaException(StatusCodes.Bad_TypeMismatch);
      }
    } else if (dataType instanceof ModbusDataType.Int16) {
      if (value instanceof Short v) {
        getByteOps(modifiers).setShort(valueBytes, offset, v);
      } else {
        throw new UaException(StatusCodes.Bad_TypeMismatch);
      }
    } else if (dataType instanceof ModbusDataType.UInt16) {
      if (value instanceof UShort v) {
        getByteOps(modifiers).setShort(valueBytes, offset, v.shortValue());
      } else {
        throw new UaException(StatusCodes.Bad_TypeMismatch);
      }
    } else if (dataType instanceof ModbusDataType.Int32) {
      if (value instanceof Integer v) {
        getByteOps(modifiers).setInt(valueBytes, offset, v);
      } else {
        throw new UaException(StatusCodes.Bad_TypeMismatch);
      }
    } else if (dataType instanceof ModbusDataType.UInt32) {
      if (value instanceof UInteger v) {
        getByteOps(modifiers).setInt(valueBytes, offset, v.intValue());
      } else {
        throw new UaException(StatusCodes.Bad_TypeMismatch);
      }
    } else if (dataType instanceof ModbusDataType.Int64) {
      if (value instanceof Long v) {
        getByteOps(modifiers).setLong(valueBytes, offset, v);
      } else {
        throw new UaException(StatusCodes.Bad_TypeMismatch);
      }
    } else if (dataType instanceof ModbusDataType.UInt64) {
      if (value instanceof ULong v) {
        getByteOps(modifiers).setLong(valueBytes, offset, v.longValue());
      } else {
        throw new UaException(StatusCodes.Bad_TypeMismatch);
      }
    } else if (dataType instanceof ModbusDataType.Float32) {
      if (value instanceof Float v) {
        getByteOps(modifiers).setFloat(valueBytes, offset, v);
      } else {
        throw new UaException(StatusCodes.Bad_TypeMismatch);
      }
    } else if (dataType instanceof ModbusDataType.Double64) {
      if (value instanceof Double v) {
        getByteOps(modifiers).setDouble(valueBytes, offset, v);
      } else {
        throw new UaException(StatusCodes.Bad_TypeMismatch);
      }
    } else if (dataType instanceof ModbusDataType.String) {
      if (value instanceof String v) {
        byte[] stringBytes = v.getBytes(StandardCharsets.UTF_8);
        int length = Math.min(stringBytes.length, dataType.getRegisterCount() * 2);
        System.arraycopy(stringBytes, 0, valueBytes, offset, length);
      } else {
        throw new UaException(StatusCodes.Bad_TypeMismatch);
      }
    } else {
      throw new UaException(StatusCodes.Bad_InternalError, "dataType: " + dataType);
    }
  }

  static byte[] getBytesForArrayValue(
      Object value, ModbusDataType dataType, Set<DataTypeModifier> modifiers, int[] dimensions)
      throws UaException {

    // Check if the input value is actually a Java array
    if (value == null || !value.getClass().isArray()) {
      throw new UaException(StatusCodes.Bad_TypeMismatch, "expected array");
    }

    // Validate dimensions array
    if (dimensions.length != 1) {
      throw new UaException(StatusCodes.Bad_TypeMismatch, "expected 1-dimensional array");
    }

    int arrayLength = dimensions[0];
    if (Array.getLength(value) != arrayLength) {
      throw new UaException(StatusCodes.Bad_TypeMismatch, "array length does not match dimensions");
    }

    int bytesPerElement = dataType.getRegisterCount() * 2;
    byte[] valueBytes = new byte[arrayLength * bytesPerElement];

    for (int i = 0; i < arrayLength; i++) {
      setBytesForScalarValue(
          Array.get(value, i), valueBytes, i * bytesPerElement, dataType, modifiers);
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

      if (!Arrays.equals(matrix.getDimensions(), dimensions)) {
        throw new UaException(StatusCodes.Bad_TypeMismatch);
      }

      Object flatArrayValue = matrix.getElements();
      assert flatArrayValue != null;

      return getBytesForArrayValue(
          flatArrayValue, dataType, modifiers, new int[] {elementCount(dimensions)});
    } else {
      throw new UaException(StatusCodes.Bad_TypeMismatch);
    }
  }

  private static int elementCount(int[] dimensions) {
    int elementCount = 1;
    for (int dimension : dimensions) {
      elementCount *= dimension;
    }
    return elementCount;
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
