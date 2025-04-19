package com.kevinherron.ignition.modbus.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.kevinherron.ignition.modbus.address.DataTypeModifier;
import com.kevinherron.ignition.modbus.address.ModbusDataType;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Set;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.types.builtin.Matrix;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.ULong;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.junit.jupiter.api.Test;

class ModbusByteUtilTest {

  private static final Set<DataTypeModifier> NO_MODIFIERS = Collections.emptySet();

  @Test
  void testGetArrayValueForBytes_Bool() throws UaException {
    // Create a byte array for 3 boolean values: true, false, true
    byte[] bytes = new byte[] {1, 0, 0, 0, 1, 0};
    int[] dimensions = new int[] {3};

    Object result =
        ModbusByteUtil.getArrayValueForBytes(
            bytes, new ModbusDataType.Bool(), NO_MODIFIERS, dimensions);

    // Verify result is a Boolean array with the expected values
    assertEquals(Boolean[].class, result.getClass());
    Boolean[] booleans = (Boolean[]) result;
    assertEquals(3, booleans.length);
    assertEquals(true, booleans[0]);
    assertEquals(false, booleans[1]);
    assertEquals(true, booleans[2]);
  }

  @Test
  void testGetArrayValueForBytes_Int16() throws UaException {
    // Create a byte array for 3 short values: 1, 2, 3 (big endian)
    byte[] bytes = new byte[] {0, 1, 0, 2, 0, 3};
    int[] dimensions = new int[] {3};

    Object result =
        ModbusByteUtil.getArrayValueForBytes(
            bytes, new ModbusDataType.Int16(), NO_MODIFIERS, dimensions);

    // Verify result is a Short array with the expected values
    assertEquals(Short[].class, result.getClass());
    Short[] shorts = (Short[]) result;
    assertEquals(3, shorts.length);
    assertEquals((short) 1, shorts[0]);
    assertEquals((short) 2, shorts[1]);
    assertEquals((short) 3, shorts[2]);
  }

  @Test
  void testGetArrayValueForBytes_UInt16() throws UaException {
    // Create a byte array for 3 ushort values: 1, 2, 3 (big endian)
    byte[] bytes = new byte[] {0, 1, 0, 2, 0, 3};
    int[] dimensions = new int[] {3};

    Object result =
        ModbusByteUtil.getArrayValueForBytes(
            bytes, new ModbusDataType.UInt16(), NO_MODIFIERS, dimensions);

    // Verify result is a UShort array with the expected values
    assertEquals(UShort[].class, result.getClass());
    UShort[] ushorts = (UShort[]) result;
    assertEquals(3, ushorts.length);
    assertEquals(UShort.valueOf(1), ushorts[0]);
    assertEquals(UShort.valueOf(2), ushorts[1]);
    assertEquals(UShort.valueOf(3), ushorts[2]);
  }

  @Test
  void testGetArrayValueForBytes_Int32() throws UaException {
    // Create a byte array for 3 int values: 1, 2, 3 (big endian)
    byte[] bytes = new byte[] {0, 0, 0, 1, 0, 0, 0, 2, 0, 0, 0, 3};
    int[] dimensions = new int[] {3};

    Object result =
        ModbusByteUtil.getArrayValueForBytes(
            bytes, new ModbusDataType.Int32(), NO_MODIFIERS, dimensions);

    // Verify result is an Integer array with the expected values
    assertEquals(Integer[].class, result.getClass());
    Integer[] integers = (Integer[]) result;
    assertEquals(3, integers.length);
    assertEquals(1, integers[0]);
    assertEquals(2, integers[1]);
    assertEquals(3, integers[2]);
  }

  @Test
  void testGetArrayValueForBytes_UInt32() throws UaException {
    // Create a byte array for 3 uint values: 1, 2, 3 (big endian)
    byte[] bytes = new byte[] {0, 0, 0, 1, 0, 0, 0, 2, 0, 0, 0, 3};
    int[] dimensions = new int[] {3};

    Object result =
        ModbusByteUtil.getArrayValueForBytes(
            bytes, new ModbusDataType.UInt32(), NO_MODIFIERS, dimensions);

    // Verify result is a UInteger array with the expected values
    assertEquals(UInteger[].class, result.getClass());
    UInteger[] uintegers = (UInteger[]) result;
    assertEquals(3, uintegers.length);
    assertEquals(UInteger.valueOf(1), uintegers[0]);
    assertEquals(UInteger.valueOf(2), uintegers[1]);
    assertEquals(UInteger.valueOf(3), uintegers[2]);
  }

  @Test
  void testGetArrayValueForBytes_Int64() throws UaException {
    // Create a byte array for 3 long values: 1, 2, 3 (big endian)
    byte[] bytes =
        new byte[] {
          0, 0, 0, 0, 0, 0, 0, 1,
          0, 0, 0, 0, 0, 0, 0, 2,
          0, 0, 0, 0, 0, 0, 0, 3
        };
    int[] dimensions = new int[] {3};

    Object result =
        ModbusByteUtil.getArrayValueForBytes(
            bytes, new ModbusDataType.Int64(), NO_MODIFIERS, dimensions);

    // Verify result is a Long array with the expected values
    assertEquals(Long[].class, result.getClass());
    Long[] longs = (Long[]) result;
    assertEquals(3, longs.length);
    assertEquals(1L, longs[0]);
    assertEquals(2L, longs[1]);
    assertEquals(3L, longs[2]);
  }

  @Test
  void testGetArrayValueForBytes_UInt64() throws UaException {
    // Create a byte array for 3 ulong values: 1, 2, 3 (big endian)
    byte[] bytes =
        new byte[] {
          0, 0, 0, 0, 0, 0, 0, 1,
          0, 0, 0, 0, 0, 0, 0, 2,
          0, 0, 0, 0, 0, 0, 0, 3
        };
    int[] dimensions = new int[] {3};

    Object result =
        ModbusByteUtil.getArrayValueForBytes(
            bytes, new ModbusDataType.UInt64(), NO_MODIFIERS, dimensions);

    // Verify result is a ULong array with the expected values
    assertEquals(ULong[].class, result.getClass());
    ULong[] ulongs = (ULong[]) result;
    assertEquals(3, ulongs.length);
    assertEquals(ULong.valueOf(1), ulongs[0]);
    assertEquals(ULong.valueOf(2), ulongs[1]);
    assertEquals(ULong.valueOf(3), ulongs[2]);
  }

  @Test
  void testGetArrayValueForBytes_Float32() throws UaException {
    // Create a byte array for 3 float values: 1.0, 2.5, 3.75 (big endian)
    // IEEE 754 representation of these values
    byte[] bytes =
        new byte[] {
          0x3F, (byte) 0x80, 0x00, 0x00, // 1.0
          0x40, 0x20, 0x00, 0x00, // 2.5
          0x40, 0x70, 0x00, 0x00 // 3.75
        };
    int[] dimensions = new int[] {3};

    Object result =
        ModbusByteUtil.getArrayValueForBytes(
            bytes, new ModbusDataType.Float32(), NO_MODIFIERS, dimensions);

    // Verify result is a Float array with the expected values
    assertEquals(Float[].class, result.getClass());
    Float[] floats = (Float[]) result;
    assertEquals(3, floats.length);
    assertEquals(1.0f, floats[0]);
    assertEquals(2.5f, floats[1]);
    assertEquals(3.75f, floats[2]);
  }

  @Test
  void testGetArrayValueForBytes_Double64() throws UaException {
    // Create a byte array for 3 double values: 1.0, 2.5, 3.75 (big endian)
    // IEEE 754 representation of these values
    byte[] bytes =
        new byte[] {
          0x3F, (byte) 0xF0, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // 1.0
          0x40, 0x04, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // 2.5
          0x40, 0x0E, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 // 3.75
        };
    int[] dimensions = new int[] {3};

    Object result =
        ModbusByteUtil.getArrayValueForBytes(
            bytes, new ModbusDataType.Double64(), NO_MODIFIERS, dimensions);

    // Verify result is a Double array with the expected values
    assertEquals(Double[].class, result.getClass());
    Double[] doubles = (Double[]) result;
    assertEquals(3, doubles.length);
    assertEquals(1.0, doubles[0]);
    assertEquals(2.5, doubles[1]);
    assertEquals(3.75, doubles[2]);
  }

  @Test
  void testGetArrayValueForBytes_String() throws UaException {
    // Create a byte array for 3 string values: "abc", "def", "ghi" with 4 bytes each
    byte[] bytes =
        new byte[] {
          'a', 'b', 'c', 0, // "abc" with null terminator
          'd', 'e', 'f', 0, // "def" with null terminator
          'g', 'h', 'i', 0 // "ghi" with null terminator
        };
    int[] dimensions = new int[] {3};

    Object result =
        ModbusByteUtil.getArrayValueForBytes(
            bytes, new ModbusDataType.String(4), NO_MODIFIERS, dimensions);

    // Verify result is a String array with the expected values
    assertEquals(String[].class, result.getClass());
    String[] strings = (String[]) result;
    assertEquals(3, strings.length);
    assertEquals("abc", strings[0]);
    assertEquals("def", strings[1]);
    assertEquals("ghi", strings[2]);
  }

  @Test
  void testGetArrayValueForBytes_InsufficientData() {
    // Create a byte array that's too short for the requested dimensions
    byte[] bytes = new byte[] {0, 1, 0, 2}; // Only enough for 2 Int16 values
    int[] dimensions = new int[] {3}; // But we're asking for 3

    // Verify that an exception is thrown with the expected status code
    UaException exception =
        assertThrows(
            UaException.class,
            () ->
                ModbusByteUtil.getArrayValueForBytes(
                    bytes, new ModbusDataType.Int16(), NO_MODIFIERS, dimensions));

    assertEquals(StatusCodes.Bad_InternalError, exception.getStatusCode().getValue());
  }

  @Test
  void testGetArrayValueForBytes_InvalidDimensions() {
    byte[] bytes = new byte[] {0, 1, 0, 2, 0, 3};
    int[] dimensions = new int[] {2, 3}; // 2D array, not supported

    // Verify that an exception is thrown with the expected status code
    UaException exception =
        assertThrows(
            UaException.class,
            () ->
                ModbusByteUtil.getArrayValueForBytes(
                    bytes, new ModbusDataType.Int16(), NO_MODIFIERS, dimensions));

    assertEquals(StatusCodes.Bad_TypeMismatch, exception.getStatusCode().getValue());
  }

  @Test
  void testGetBytesForArrayValue_Bool() throws UaException {
    Boolean[] booleans = new Boolean[] {true, false, true};
    int[] dimensions = new int[] {booleans.length};

    byte[] bytes =
        ModbusByteUtil.getBytesForArrayValue(
            booleans, new ModbusDataType.Bool(), NO_MODIFIERS, dimensions);

    // Each Bool takes 2 bytes, so the total should be 6 bytes
    assertEquals(6, bytes.length);

    // Extract 2-byte chunks and convert back to verify
    byte[] chunk1 = new byte[2];
    byte[] chunk2 = new byte[2];
    byte[] chunk3 = new byte[2];

    System.arraycopy(bytes, 0, chunk1, 0, 2);
    System.arraycopy(bytes, 2, chunk2, 0, 2);
    System.arraycopy(bytes, 4, chunk3, 0, 2);

    assertEquals(
        true,
        ModbusByteUtil.getScalarValueForBytes(chunk1, new ModbusDataType.Bool(), NO_MODIFIERS));
    assertEquals(
        false,
        ModbusByteUtil.getScalarValueForBytes(chunk2, new ModbusDataType.Bool(), NO_MODIFIERS));
    assertEquals(
        true,
        ModbusByteUtil.getScalarValueForBytes(chunk3, new ModbusDataType.Bool(), NO_MODIFIERS));
  }

  @Test
  void testGetBytesForArrayValue_Int16() throws UaException {
    Short[] shorts = new Short[] {(short) 1, (short) 2, (short) 3};
    int[] dimensions = new int[] {shorts.length};

    byte[] bytes =
        ModbusByteUtil.getBytesForArrayValue(
            shorts, new ModbusDataType.Int16(), NO_MODIFIERS, dimensions);

    // Each Int16 takes 2 bytes, so the total should be 6 bytes
    assertEquals(6, bytes.length);

    // Verify the content (big endian by default)
    assertEquals(0, bytes[0]); // high byte of 1
    assertEquals(1, bytes[1]); // low byte of 1
    assertEquals(0, bytes[2]); // high byte of 2
    assertEquals(2, bytes[3]); // low byte of 2
    assertEquals(0, bytes[4]); // high byte of 3
    assertEquals(3, bytes[5]); // low byte of 3
  }

  @Test
  void testGetBytesForArrayValue_UInt16() throws UaException {
    UShort[] ushorts = new UShort[] {UShort.valueOf(1), UShort.valueOf(2), UShort.valueOf(3)};
    int[] dimensions = new int[] {ushorts.length};

    byte[] bytes =
        ModbusByteUtil.getBytesForArrayValue(
            ushorts, new ModbusDataType.UInt16(), NO_MODIFIERS, dimensions);

    // Each UInt16 takes 2 bytes, so the total should be 6 bytes
    assertEquals(6, bytes.length);

    // Verify the content (big endian by default)
    assertEquals(0, bytes[0]); // high byte of 1
    assertEquals(1, bytes[1]); // low byte of 1
    assertEquals(0, bytes[2]); // high byte of 2
    assertEquals(2, bytes[3]); // low byte of 2
    assertEquals(0, bytes[4]); // high byte of 3
    assertEquals(3, bytes[5]); // low byte of 3
  }

  @Test
  void testGetBytesForArrayValue_Int32() throws UaException {
    Integer[] ints = new Integer[] {1, 2, 3};
    int[] dimensions = new int[] {ints.length};

    byte[] bytes =
        ModbusByteUtil.getBytesForArrayValue(
            ints, new ModbusDataType.Int32(), NO_MODIFIERS, dimensions);

    // Each Int32 takes 4 bytes, so the total should be 12 bytes
    assertEquals(12, bytes.length);

    // Verify the content (big endian by default)
    assertEquals(0, bytes[0]); // highest byte of 1
    assertEquals(0, bytes[1]);
    assertEquals(0, bytes[2]);
    assertEquals(1, bytes[3]); // lowest byte of 1

    assertEquals(0, bytes[4]); // highest byte of 2
    assertEquals(0, bytes[5]);
    assertEquals(0, bytes[6]);
    assertEquals(2, bytes[7]); // lowest byte of 2

    assertEquals(0, bytes[8]); // highest byte of 3
    assertEquals(0, bytes[9]);
    assertEquals(0, bytes[10]);
    assertEquals(3, bytes[11]); // lowest byte of 3
  }

  @Test
  void testGetBytesForArrayValue_UInt32() throws UaException {
    UInteger[] uints =
        new UInteger[] {UInteger.valueOf(1), UInteger.valueOf(2), UInteger.valueOf(3)};
    int[] dimensions = new int[] {uints.length};

    byte[] bytes =
        ModbusByteUtil.getBytesForArrayValue(
            uints, new ModbusDataType.UInt32(), NO_MODIFIERS, dimensions);

    // Each UInt32 takes 4 bytes, so the total should be 12 bytes
    assertEquals(12, bytes.length);

    // Extract 4-byte chunks and convert back to verify
    byte[] chunk1 = new byte[4];
    byte[] chunk2 = new byte[4];
    byte[] chunk3 = new byte[4];

    System.arraycopy(bytes, 0, chunk1, 0, 4);
    System.arraycopy(bytes, 4, chunk2, 0, 4);
    System.arraycopy(bytes, 8, chunk3, 0, 4);

    assertEquals(
        UInteger.valueOf(1),
        ModbusByteUtil.getScalarValueForBytes(chunk1, new ModbusDataType.UInt32(), NO_MODIFIERS));
    assertEquals(
        UInteger.valueOf(2),
        ModbusByteUtil.getScalarValueForBytes(chunk2, new ModbusDataType.UInt32(), NO_MODIFIERS));
    assertEquals(
        UInteger.valueOf(3),
        ModbusByteUtil.getScalarValueForBytes(chunk3, new ModbusDataType.UInt32(), NO_MODIFIERS));
  }

  @Test
  void testGetBytesForArrayValue_Int64() throws UaException {
    Long[] longs = new Long[] {1L, 2L, 3L};
    int[] dimensions = new int[] {longs.length};

    byte[] bytes =
        ModbusByteUtil.getBytesForArrayValue(
            longs, new ModbusDataType.Int64(), NO_MODIFIERS, dimensions);

    // Each Int64 takes 8 bytes, so the total should be 24 bytes
    assertEquals(24, bytes.length);

    // Extract 8-byte chunks and convert back to verify
    byte[] chunk1 = new byte[8];
    byte[] chunk2 = new byte[8];
    byte[] chunk3 = new byte[8];

    System.arraycopy(bytes, 0, chunk1, 0, 8);
    System.arraycopy(bytes, 8, chunk2, 0, 8);
    System.arraycopy(bytes, 16, chunk3, 0, 8);

    assertEquals(
        1L,
        (Long)
            ModbusByteUtil.getScalarValueForBytes(
                chunk1, new ModbusDataType.Int64(), NO_MODIFIERS));
    assertEquals(
        2L,
        (Long)
            ModbusByteUtil.getScalarValueForBytes(
                chunk2, new ModbusDataType.Int64(), NO_MODIFIERS));
    assertEquals(
        3L,
        (Long)
            ModbusByteUtil.getScalarValueForBytes(
                chunk3, new ModbusDataType.Int64(), NO_MODIFIERS));
  }

  @Test
  void testGetBytesForArrayValue_UInt64() throws UaException {
    ULong[] ulongs = new ULong[] {ULong.valueOf(1), ULong.valueOf(2), ULong.valueOf(3)};
    int[] dimensions = new int[] {ulongs.length};

    byte[] bytes =
        ModbusByteUtil.getBytesForArrayValue(
            ulongs, new ModbusDataType.UInt64(), NO_MODIFIERS, dimensions);

    // Each UInt64 takes 8 bytes, so the total should be 24 bytes
    assertEquals(24, bytes.length);

    // Extract 8-byte chunks and convert back to verify
    byte[] chunk1 = new byte[8];
    byte[] chunk2 = new byte[8];
    byte[] chunk3 = new byte[8];

    System.arraycopy(bytes, 0, chunk1, 0, 8);
    System.arraycopy(bytes, 8, chunk2, 0, 8);
    System.arraycopy(bytes, 16, chunk3, 0, 8);

    assertEquals(
        ULong.valueOf(1),
        ModbusByteUtil.getScalarValueForBytes(chunk1, new ModbusDataType.UInt64(), NO_MODIFIERS));
    assertEquals(
        ULong.valueOf(2),
        ModbusByteUtil.getScalarValueForBytes(chunk2, new ModbusDataType.UInt64(), NO_MODIFIERS));
    assertEquals(
        ULong.valueOf(3),
        ModbusByteUtil.getScalarValueForBytes(chunk3, new ModbusDataType.UInt64(), NO_MODIFIERS));
  }

  @Test
  void testGetBytesForArrayValue_Float32() throws UaException {
    Float[] floats = new Float[] {1.0f, 2.5f, 3.75f};
    int[] dimensions = new int[] {floats.length};

    byte[] bytes =
        ModbusByteUtil.getBytesForArrayValue(
            floats, new ModbusDataType.Float32(), NO_MODIFIERS, dimensions);

    // Each Float32 takes 4 bytes, so the total should be 12 bytes
    assertEquals(12, bytes.length);

    // Extract 4-byte chunks and convert back to verify
    byte[] chunk1 = new byte[4];
    byte[] chunk2 = new byte[4];
    byte[] chunk3 = new byte[4];

    System.arraycopy(bytes, 0, chunk1, 0, 4);
    System.arraycopy(bytes, 4, chunk2, 0, 4);
    System.arraycopy(bytes, 8, chunk3, 0, 4);

    assertEquals(
        1.0f,
        (Float)
            ModbusByteUtil.getScalarValueForBytes(
                chunk1, new ModbusDataType.Float32(), NO_MODIFIERS));
    assertEquals(
        2.5f,
        (Float)
            ModbusByteUtil.getScalarValueForBytes(
                chunk2, new ModbusDataType.Float32(), NO_MODIFIERS));
    assertEquals(
        3.75f,
        (Float)
            ModbusByteUtil.getScalarValueForBytes(
                chunk3, new ModbusDataType.Float32(), NO_MODIFIERS));
  }

  @Test
  void testGetBytesForArrayValue_Double64() throws UaException {
    Double[] doubles = new Double[] {1.0, 2.5, 3.75};
    int[] dimensions = new int[] {doubles.length};

    byte[] bytes =
        ModbusByteUtil.getBytesForArrayValue(
            doubles, new ModbusDataType.Double64(), NO_MODIFIERS, dimensions);

    // Each Double64 takes 8 bytes, so the total should be 24 bytes
    assertEquals(24, bytes.length);

    // Extract 8-byte chunks and convert back to verify
    byte[] chunk1 = new byte[8];
    byte[] chunk2 = new byte[8];
    byte[] chunk3 = new byte[8];

    System.arraycopy(bytes, 0, chunk1, 0, 8);
    System.arraycopy(bytes, 8, chunk2, 0, 8);
    System.arraycopy(bytes, 16, chunk3, 0, 8);

    assertEquals(
        1.0,
        (Double)
            ModbusByteUtil.getScalarValueForBytes(
                chunk1, new ModbusDataType.Double64(), NO_MODIFIERS));
    assertEquals(
        2.5,
        (Double)
            ModbusByteUtil.getScalarValueForBytes(
                chunk2, new ModbusDataType.Double64(), NO_MODIFIERS));
    assertEquals(
        3.75,
        (Double)
            ModbusByteUtil.getScalarValueForBytes(
                chunk3, new ModbusDataType.Double64(), NO_MODIFIERS));
  }

  @Test
  void testGetBytesForArrayValue_String() throws UaException {
    String[] strings = new String[] {"abc", "def", "ghi"};
    int[] dimensions = new int[] {strings.length};

    // Create a String data type with length 4 (which means 2 registers or 4 bytes per string)
    byte[] bytes =
        ModbusByteUtil.getBytesForArrayValue(
            strings, new ModbusDataType.String(4), NO_MODIFIERS, dimensions);

    // Each String takes 4 bytes, so the total should be 12 bytes
    assertEquals(12, bytes.length);

    // Verify the content
    byte[] abc = "abc".getBytes(StandardCharsets.UTF_8);
    byte[] def = "def".getBytes(StandardCharsets.UTF_8);
    byte[] ghi = "ghi".getBytes(StandardCharsets.UTF_8);

    for (int i = 0; i < abc.length; i++) {
      assertEquals(abc[i], bytes[i]);
    }
    assertEquals(0, bytes[3]); // Padding

    for (int i = 0; i < def.length; i++) {
      assertEquals(def[i], bytes[4 + i]);
    }
    assertEquals(0, bytes[7]); // Padding

    for (int i = 0; i < ghi.length; i++) {
      assertEquals(ghi[i], bytes[8 + i]);
    }
    assertEquals(0, bytes[11]); // Padding
  }

  @Test
  void testGetBytesForMatrixValue_Bool_2D() throws UaException {
    // Create a 2x2 matrix of Boolean values
    Boolean[] booleans = new Boolean[] {true, false, false, true};
    int[] dimensions = new int[] {2, 2};
    Matrix matrix = new Matrix(booleans, dimensions);

    byte[] bytes =
        ModbusByteUtil.getBytesForMatrixValue(
            matrix, new ModbusDataType.Bool(), NO_MODIFIERS, dimensions);

    // Each Bool takes 2 bytes, so the total should be 8 bytes
    assertEquals(8, bytes.length);

    // Extract 2-byte chunks and convert back to verify
    byte[] chunk1 = new byte[2];
    byte[] chunk2 = new byte[2];
    byte[] chunk3 = new byte[2];
    byte[] chunk4 = new byte[2];

    System.arraycopy(bytes, 0, chunk1, 0, 2);
    System.arraycopy(bytes, 2, chunk2, 0, 2);
    System.arraycopy(bytes, 4, chunk3, 0, 2);
    System.arraycopy(bytes, 6, chunk4, 0, 2);

    assertEquals(
        true,
        ModbusByteUtil.getScalarValueForBytes(chunk1, new ModbusDataType.Bool(), NO_MODIFIERS));
    assertEquals(
        false,
        ModbusByteUtil.getScalarValueForBytes(chunk2, new ModbusDataType.Bool(), NO_MODIFIERS));
    assertEquals(
        false,
        ModbusByteUtil.getScalarValueForBytes(chunk3, new ModbusDataType.Bool(), NO_MODIFIERS));
    assertEquals(
        true,
        ModbusByteUtil.getScalarValueForBytes(chunk4, new ModbusDataType.Bool(), NO_MODIFIERS));
  }

  @Test
  void testGetBytesForMatrixValue_Int16_2D() throws UaException {
    // Create a 2x2 matrix of Int16 values
    Short[] shorts = new Short[] {(short) 1, (short) 2, (short) 3, (short) 4};
    int[] dimensions = new int[] {2, 2};
    Matrix matrix = new Matrix(shorts, dimensions);

    byte[] bytes =
        ModbusByteUtil.getBytesForMatrixValue(
            matrix, new ModbusDataType.Int16(), NO_MODIFIERS, dimensions);

    // Each Int16 takes 2 bytes, so the total should be 8 bytes
    assertEquals(8, bytes.length);

    // Verify the content (big endian by default)
    assertEquals(0, bytes[0]); // high byte of 1
    assertEquals(1, bytes[1]); // low byte of 1
    assertEquals(0, bytes[2]); // high byte of 2
    assertEquals(2, bytes[3]); // low byte of 2
    assertEquals(0, bytes[4]); // high byte of 3
    assertEquals(3, bytes[5]); // low byte of 3
    assertEquals(0, bytes[6]); // high byte of 4
    assertEquals(4, bytes[7]); // low byte of 4
  }

  @Test
  void testGetBytesForMatrixValue_UInt16_2D() throws UaException {
    // Create a 2x2 matrix of UInt16 values
    UShort[] ushorts =
        new UShort[] {UShort.valueOf(1), UShort.valueOf(2), UShort.valueOf(3), UShort.valueOf(4)};
    int[] dimensions = new int[] {2, 2};
    Matrix matrix = new Matrix(ushorts, dimensions);

    byte[] bytes =
        ModbusByteUtil.getBytesForMatrixValue(
            matrix, new ModbusDataType.UInt16(), NO_MODIFIERS, dimensions);

    // Each UInt16 takes 2 bytes, so the total should be 8 bytes
    assertEquals(8, bytes.length);

    // Verify the content (big endian by default)
    assertEquals(0, bytes[0]); // high byte of 1
    assertEquals(1, bytes[1]); // low byte of 1
    assertEquals(0, bytes[2]); // high byte of 2
    assertEquals(2, bytes[3]); // low byte of 2
    assertEquals(0, bytes[4]); // high byte of 3
    assertEquals(3, bytes[5]); // low byte of 3
    assertEquals(0, bytes[6]); // high byte of 4
    assertEquals(4, bytes[7]); // low byte of 4
  }

  @Test
  void testGetBytesForMatrixValue_Int32_2D() throws UaException {
    // Create a 2x2 matrix of Int32 values
    Integer[] integers = new Integer[] {1, 2, 3, 4};
    int[] dimensions = new int[] {2, 2};
    Matrix matrix = new Matrix(integers, dimensions);

    byte[] bytes =
        ModbusByteUtil.getBytesForMatrixValue(
            matrix, new ModbusDataType.Int32(), NO_MODIFIERS, dimensions);

    // Each Int32 takes 4 bytes, so the total should be 16 bytes
    assertEquals(16, bytes.length);

    // Extract 4-byte chunks and convert back to verify
    for (int i = 0; i < 4; i++) {
      byte[] chunk = new byte[4];
      System.arraycopy(bytes, i * 4, chunk, 0, 4);

      int expectedValue = integers[i];
      int actualValue =
          (Integer)
              ModbusByteUtil.getScalarValueForBytes(
                  chunk, new ModbusDataType.Int32(), NO_MODIFIERS);

      assertEquals(expectedValue, actualValue);
    }
  }

  @Test
  void testGetBytesForMatrixValue_UInt32_2D() throws UaException {
    // Create a 2x2 matrix of UInt32 values
    UInteger[] uintegers =
        new UInteger[] {
          UInteger.valueOf(1), UInteger.valueOf(2),
          UInteger.valueOf(3), UInteger.valueOf(4)
        };
    int[] dimensions = new int[] {2, 2};
    Matrix matrix = new Matrix(uintegers, dimensions);

    byte[] bytes =
        ModbusByteUtil.getBytesForMatrixValue(
            matrix, new ModbusDataType.UInt32(), NO_MODIFIERS, dimensions);

    // Each UInt32 takes 4 bytes, so the total should be 16 bytes
    assertEquals(16, bytes.length);

    // Extract 4-byte chunks and convert back to verify
    for (int i = 0; i < 4; i++) {
      byte[] chunk = new byte[4];
      System.arraycopy(bytes, i * 4, chunk, 0, 4);

      UInteger expectedValue = uintegers[i];
      UInteger actualValue =
          (UInteger)
              ModbusByteUtil.getScalarValueForBytes(
                  chunk, new ModbusDataType.UInt32(), NO_MODIFIERS);

      assertEquals(expectedValue, actualValue);
    }
  }

  @Test
  void testGetBytesForMatrixValue_Int64_2D() throws UaException {
    // Create a 2x2 matrix of Int64 values
    Long[] longs = new Long[] {1L, 2L, 3L, 4L};
    int[] dimensions = new int[] {2, 2};
    Matrix matrix = new Matrix(longs, dimensions);

    byte[] bytes =
        ModbusByteUtil.getBytesForMatrixValue(
            matrix, new ModbusDataType.Int64(), NO_MODIFIERS, dimensions);

    // Each Int64 takes 8 bytes, so the total should be 32 bytes
    assertEquals(32, bytes.length);

    // Extract 8-byte chunks and convert back to verify
    for (int i = 0; i < 4; i++) {
      byte[] chunk = new byte[8];
      System.arraycopy(bytes, i * 8, chunk, 0, 8);

      Long expectedValue = longs[i];
      Long actualValue =
          (Long)
              ModbusByteUtil.getScalarValueForBytes(
                  chunk, new ModbusDataType.Int64(), NO_MODIFIERS);

      assertEquals(expectedValue, actualValue);
    }
  }

  @Test
  void testGetBytesForMatrixValue_UInt64_2D() throws UaException {
    // Create a 2x2 matrix of UInt64 values
    ULong[] ulongs =
        new ULong[] {
          ULong.valueOf(1), ULong.valueOf(2),
          ULong.valueOf(3), ULong.valueOf(4)
        };
    int[] dimensions = new int[] {2, 2};
    Matrix matrix = new Matrix(ulongs, dimensions);

    byte[] bytes =
        ModbusByteUtil.getBytesForMatrixValue(
            matrix, new ModbusDataType.UInt64(), NO_MODIFIERS, dimensions);

    // Each UInt64 takes 8 bytes, so the total should be 32 bytes
    assertEquals(32, bytes.length);

    // Extract 8-byte chunks and convert back to verify
    for (int i = 0; i < 4; i++) {
      byte[] chunk = new byte[8];
      System.arraycopy(bytes, i * 8, chunk, 0, 8);

      ULong expectedValue = ulongs[i];
      ULong actualValue =
          (ULong)
              ModbusByteUtil.getScalarValueForBytes(
                  chunk, new ModbusDataType.UInt64(), NO_MODIFIERS);

      assertEquals(expectedValue, actualValue);
    }
  }

  @Test
  void testGetBytesForMatrixValue_Float32_3D() throws UaException {
    // Create a 2x2x2 matrix of Float32 values
    Float[] floats = new Float[] {1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f};
    int[] dimensions = new int[] {2, 2, 2};
    Matrix matrix = new Matrix(floats, dimensions);

    byte[] bytes =
        ModbusByteUtil.getBytesForMatrixValue(
            matrix, new ModbusDataType.Float32(), NO_MODIFIERS, dimensions);

    // Each Float32 takes 4 bytes, so the total should be 32 bytes
    assertEquals(32, bytes.length);

    // Extract 4-byte chunks and convert back to verify
    for (int i = 0; i < 8; i++) {
      byte[] chunk = new byte[4];
      System.arraycopy(bytes, i * 4, chunk, 0, 4);

      float expectedValue = floats[i];
      float actualValue =
          (Float)
              ModbusByteUtil.getScalarValueForBytes(
                  chunk, new ModbusDataType.Float32(), NO_MODIFIERS);

      assertEquals(expectedValue, actualValue);
    }
  }

  @Test
  void testGetBytesForMatrixValue_Double64_2D() throws UaException {
    // Create a 2x2 matrix of Double64 values
    Double[] doubles = new Double[] {1.0, 2.5, 3.75, 4.125};
    int[] dimensions = new int[] {2, 2};
    Matrix matrix = new Matrix(doubles, dimensions);

    byte[] bytes =
        ModbusByteUtil.getBytesForMatrixValue(
            matrix, new ModbusDataType.Double64(), NO_MODIFIERS, dimensions);

    // Each Double64 takes 8 bytes, so the total should be 32 bytes
    assertEquals(32, bytes.length);

    // Extract 8-byte chunks and convert back to verify
    for (int i = 0; i < 4; i++) {
      byte[] chunk = new byte[8];
      System.arraycopy(bytes, i * 8, chunk, 0, 8);

      Double expectedValue = doubles[i];
      Double actualValue =
          (Double)
              ModbusByteUtil.getScalarValueForBytes(
                  chunk, new ModbusDataType.Double64(), NO_MODIFIERS);

      assertEquals(expectedValue, actualValue);
    }
  }

  @Test
  void testGetBytesForMatrixValue_String_2D() throws UaException {
    // Create a 2x2 matrix of String values
    String[] strings = new String[] {"ab", "cd", "ef", "gh"};
    int[] dimensions = new int[] {2, 2};
    Matrix matrix = new Matrix(strings, dimensions);

    // Create a String data type with length 4 (which means 2 registers or 4 bytes per string)
    byte[] bytes =
        ModbusByteUtil.getBytesForMatrixValue(
            matrix, new ModbusDataType.String(4), NO_MODIFIERS, dimensions);

    // Each String takes 4 bytes, so the total should be 16 bytes
    assertEquals(16, bytes.length);

    // Verify the content
    byte[] ab = "ab".getBytes(StandardCharsets.UTF_8);
    byte[] cd = "cd".getBytes(StandardCharsets.UTF_8);
    byte[] ef = "ef".getBytes(StandardCharsets.UTF_8);
    byte[] gh = "gh".getBytes(StandardCharsets.UTF_8);

    // First string "ab"
    assertEquals(ab[0], bytes[0]);
    assertEquals(ab[1], bytes[1]);
    assertEquals(0, bytes[2]); // Padding
    assertEquals(0, bytes[3]); // Padding

    // Second string "cd"
    assertEquals(cd[0], bytes[4]);
    assertEquals(cd[1], bytes[5]);
    assertEquals(0, bytes[6]); // Padding
    assertEquals(0, bytes[7]); // Padding

    // Third string "ef"
    assertEquals(ef[0], bytes[8]);
    assertEquals(ef[1], bytes[9]);
    assertEquals(0, bytes[10]); // Padding
    assertEquals(0, bytes[11]); // Padding

    // Fourth string "gh"
    assertEquals(gh[0], bytes[12]);
    assertEquals(gh[1], bytes[13]);
    assertEquals(0, bytes[14]); // Padding
    assertEquals(0, bytes[15]); // Padding
  }

  @Test
  void testGetBytesForMatrixValue_DimensionMismatch() {
    // Create a 2x2 matrix of Boolean values
    Boolean[] booleans = new Boolean[] {true, false, false, true};
    int[] matrixDimensions = new int[] {2, 2};
    Matrix matrix = new Matrix(booleans, matrixDimensions);

    // Try to use with mismatched dimensions (3x3)
    int[] requestedDimensions = new int[] {3, 3};

    UaException exception =
        assertThrows(
            UaException.class,
            () ->
                ModbusByteUtil.getBytesForMatrixValue(
                    matrix, new ModbusDataType.Bool(), NO_MODIFIERS, requestedDimensions));

    assertEquals(StatusCodes.Bad_TypeMismatch, exception.getStatusCode().getValue());
  }

  @Test
  void testGetBytesForMatrixValue_DimensionCountMismatch() {
    // Create a 2x2 matrix of Boolean values
    Boolean[] booleans = new Boolean[] {true, false, false, true};
    int[] matrixDimensions = new int[] {2, 2};
    Matrix matrix = new Matrix(booleans, matrixDimensions);

    // Try to use with mismatched dimension count (1D instead of 2D)
    int[] requestedDimensions = new int[] {4};

    UaException exception =
        assertThrows(
            UaException.class,
            () ->
                ModbusByteUtil.getBytesForMatrixValue(
                    matrix, new ModbusDataType.Bool(), NO_MODIFIERS, requestedDimensions));

    assertEquals(StatusCodes.Bad_TypeMismatch, exception.getStatusCode().getValue());
  }

  @Test
  void testGetBytesForMatrixValue_NotMatrix() {
    // Try to use a non-Matrix object
    Boolean[] booleans = new Boolean[] {true, false, false, true};
    int[] dimensions = new int[] {2, 2};

    UaException exception =
        assertThrows(
            UaException.class,
            () ->
                ModbusByteUtil.getBytesForMatrixValue(
                    booleans, new ModbusDataType.Bool(), NO_MODIFIERS, dimensions));

    assertEquals(StatusCodes.Bad_TypeMismatch, exception.getStatusCode().getValue());
  }
}
