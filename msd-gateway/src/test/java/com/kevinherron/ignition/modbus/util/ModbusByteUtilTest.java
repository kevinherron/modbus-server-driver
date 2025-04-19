package com.kevinherron.ignition.modbus.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.kevinherron.ignition.modbus.address.DataTypeModifier;
import com.kevinherron.ignition.modbus.address.ModbusDataType;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Set;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.ULong;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.junit.jupiter.api.Test;

class ModbusByteUtilTest {

  private static final Set<DataTypeModifier> NO_MODIFIERS = Collections.emptySet();

  @Test
  void testGetBytesForArrayValue_Bool() throws UaException {
    Boolean[] booleans = new Boolean[]{true, false, true};
    int[] dimensions = new int[]{booleans.length};

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
        true, ModbusByteUtil.getValueForBytes(chunk1, new ModbusDataType.Bool(), NO_MODIFIERS));
    assertEquals(
        false, ModbusByteUtil.getValueForBytes(chunk2, new ModbusDataType.Bool(), NO_MODIFIERS));
    assertEquals(
        true, ModbusByteUtil.getValueForBytes(chunk3, new ModbusDataType.Bool(), NO_MODIFIERS));
  }

  @Test
  void testGetBytesForArrayValue_Int16() throws UaException {
    Short[] shorts = new Short[]{(short) 1, (short) 2, (short) 3};
    int[] dimensions = new int[]{shorts.length};

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
    UShort[] ushorts = new UShort[]{UShort.valueOf(1), UShort.valueOf(2), UShort.valueOf(3)};
    int[] dimensions = new int[]{ushorts.length};

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
    Integer[] ints = new Integer[]{1, 2, 3};
    int[] dimensions = new int[]{ints.length};

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
        new UInteger[]{UInteger.valueOf(1), UInteger.valueOf(2), UInteger.valueOf(3)};
    int[] dimensions = new int[]{uints.length};

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
        ModbusByteUtil.getValueForBytes(chunk1, new ModbusDataType.UInt32(), NO_MODIFIERS));
    assertEquals(
        UInteger.valueOf(2),
        ModbusByteUtil.getValueForBytes(chunk2, new ModbusDataType.UInt32(), NO_MODIFIERS));
    assertEquals(
        UInteger.valueOf(3),
        ModbusByteUtil.getValueForBytes(chunk3, new ModbusDataType.UInt32(), NO_MODIFIERS));
  }

  @Test
  void testGetBytesForArrayValue_Int64() throws UaException {
    Long[] longs = new Long[]{1L, 2L, 3L};
    int[] dimensions = new int[]{longs.length};

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
        (Long) ModbusByteUtil.getValueForBytes(chunk1, new ModbusDataType.Int64(), NO_MODIFIERS));
    assertEquals(
        2L,
        (Long) ModbusByteUtil.getValueForBytes(chunk2, new ModbusDataType.Int64(), NO_MODIFIERS));
    assertEquals(
        3L,
        (Long) ModbusByteUtil.getValueForBytes(chunk3, new ModbusDataType.Int64(), NO_MODIFIERS));
  }

  @Test
  void testGetBytesForArrayValue_UInt64() throws UaException {
    ULong[] ulongs = new ULong[]{ULong.valueOf(1), ULong.valueOf(2), ULong.valueOf(3)};
    int[] dimensions = new int[]{ulongs.length};

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
        ModbusByteUtil.getValueForBytes(chunk1, new ModbusDataType.UInt64(), NO_MODIFIERS));
    assertEquals(
        ULong.valueOf(2),
        ModbusByteUtil.getValueForBytes(chunk2, new ModbusDataType.UInt64(), NO_MODIFIERS));
    assertEquals(
        ULong.valueOf(3),
        ModbusByteUtil.getValueForBytes(chunk3, new ModbusDataType.UInt64(), NO_MODIFIERS));
  }

  @Test
  void testGetBytesForArrayValue_Float32() throws UaException {
    Float[] floats = new Float[]{1.0f, 2.5f, 3.75f};
    int[] dimensions = new int[]{floats.length};

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
            ModbusByteUtil.getValueForBytes(chunk1, new ModbusDataType.Float32(), NO_MODIFIERS));
    assertEquals(
        2.5f,
        (Float)
            ModbusByteUtil.getValueForBytes(chunk2, new ModbusDataType.Float32(), NO_MODIFIERS));
    assertEquals(
        3.75f,
        (Float)
            ModbusByteUtil.getValueForBytes(chunk3, new ModbusDataType.Float32(), NO_MODIFIERS));
  }

  @Test
  void testGetBytesForArrayValue_Double64() throws UaException {
    Double[] doubles = new Double[]{1.0, 2.5, 3.75};
    int[] dimensions = new int[]{doubles.length};

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
            ModbusByteUtil.getValueForBytes(chunk1, new ModbusDataType.Double64(), NO_MODIFIERS));
    assertEquals(
        2.5,
        (Double)
            ModbusByteUtil.getValueForBytes(chunk2, new ModbusDataType.Double64(), NO_MODIFIERS));
    assertEquals(
        3.75,
        (Double)
            ModbusByteUtil.getValueForBytes(chunk3, new ModbusDataType.Double64(), NO_MODIFIERS));
  }

  @Test
  void testGetBytesForArrayValue_String() throws UaException {
    String[] strings = new String[]{"abc", "def", "ghi"};
    int[] dimensions = new int[]{strings.length};

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
}
