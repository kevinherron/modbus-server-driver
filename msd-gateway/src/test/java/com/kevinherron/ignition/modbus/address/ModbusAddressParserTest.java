package com.kevinherron.ignition.modbus.address;

import static com.kevinherron.ignition.modbus.address.ModbusAddressParser.ADDRESS_PATTERN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kevinherron.ignition.modbus.address.ModbusAddress.ModbusArea;
import org.junit.jupiter.api.Test;

class ModbusAddressParserTest {

  @Test
  void patternMatchesBasicAddress() {
    assertTrue(ADDRESS_PATTERN.matcher("C1").matches());
    assertTrue(ADDRESS_PATTERN.matcher("DI1").matches());
    assertTrue(ADDRESS_PATTERN.matcher("HR1").matches());
    assertTrue(ADDRESS_PATTERN.matcher("IR1").matches());
  }

  @Test
  void patternMatchesAddressWithDataType() {
    assertTrue(ADDRESS_PATTERN.matcher("C<bool>1").matches());
    assertTrue(ADDRESS_PATTERN.matcher("DI<bool>1").matches());
    assertTrue(ADDRESS_PATTERN.matcher("HR<int16>1").matches());
    assertTrue(ADDRESS_PATTERN.matcher("IR<int16>1").matches());
  }

  @Test
  void patternMatchesAddressWithDataTypeModifier() {
    assertTrue(ADDRESS_PATTERN.matcher("HR<int16@BE>1").matches());
    assertTrue(ADDRESS_PATTERN.matcher("HR<int16@LE>1").matches());
    assertTrue(ADDRESS_PATTERN.matcher("HR<int32@HL>1").matches());
    assertTrue(ADDRESS_PATTERN.matcher("HR<int16@LH>1").matches());

    assertTrue(ADDRESS_PATTERN.matcher("HR<int16@BE@HL>1").matches());
    assertTrue(ADDRESS_PATTERN.matcher("HR<int16@BE@LH>1").matches());
    assertTrue(ADDRESS_PATTERN.matcher("HR<int32@LE@HL>1").matches());
    assertTrue(ADDRESS_PATTERN.matcher("HR<int16@LE@LH>1").matches());
  }

  @Test
  void patternMatchesAddressWithArrayDataType() {
    assertTrue(ADDRESS_PATTERN.matcher("HR<int16[10]>1").matches());
    assertTrue(ADDRESS_PATTERN.matcher("HR<int16[10][20]>1").matches());
    assertTrue(ADDRESS_PATTERN.matcher("HR<int16[10][20][30]>1").matches());
  }

  @Test
  void patternMatchesAddressWithArrayDataTypeAndDimensions() {
    assertTrue(ADDRESS_PATTERN.matcher("HR<int16[10]>1[0]").matches());
    assertTrue(ADDRESS_PATTERN.matcher("HR<int16[10][20]>1[0][1]").matches());
    assertTrue(ADDRESS_PATTERN.matcher("HR<int16[10][20][30]>1[0][1][2]").matches());
  }

  @Test
  void patternMatchesAddressWithBitSpecified() {
    assertTrue(ADDRESS_PATTERN.matcher("HR1.0").matches());
    assertTrue(ADDRESS_PATTERN.matcher("IR1.0").matches());
    assertTrue(ADDRESS_PATTERN.matcher("HR<int32>1.20").matches());
    assertTrue(ADDRESS_PATTERN.matcher("IR<int32>1.20").matches());
  }

  @Test
  void patternMatchesKitchenSink() {
    // a bit within a single element of a 2d array of int16 with multiple modifiers
    assertTrue(ADDRESS_PATTERN.matcher("HR<int16[10][20]@LE@LH>1[2][3].0").matches());
  }

  @Test
  @SuppressWarnings("indentation")
  void parseBasicAddress() throws Exception {
    for (int offset = 0; offset <= 65535; offset += 5) {
      {
        var address = ModbusAddressParser.parse("C%d".formatted(offset));
        assertEquals(ModbusArea.COILS, address.getArea());
        assertEquals(offset, address.getOffset());
        assertInstanceOf(ModbusDataType.Bool.class, address.getDataType());
      }

      {
        var address = ModbusAddressParser.parse("DI%d".formatted(offset));
        assertEquals(ModbusArea.DISCRETE_INPUTS, address.getArea());
        assertEquals(offset, address.getOffset());
        assertInstanceOf(ModbusDataType.Bool.class, address.getDataType());
      }

      {
        var address = ModbusAddressParser.parse("HR%d".formatted(offset));
        assertEquals(ModbusArea.HOLDING_REGISTERS, address.getArea());
        assertEquals(offset, address.getOffset());
        assertInstanceOf(ModbusDataType.Int16.class, address.getDataType());
      }

      {
        var address = ModbusAddressParser.parse("IR%d".formatted(offset));
        assertEquals(ModbusArea.INPUT_REGISTERS, address.getArea());
        assertEquals(offset, address.getOffset());
        assertInstanceOf(ModbusDataType.Int16.class, address.getDataType());
      }
    }
  }

  @Test
  void parseAddressWithUnitId() throws Exception {
    for (int i = 0; i < 256; i++) {
      var address = ModbusAddressParser.parse("%d.HR1".formatted(i));
      assertEquals(i, address.getUnitId().orElseThrow());
      assertEquals(ModbusArea.HOLDING_REGISTERS, address.getArea());
      assertEquals(1, address.getOffset());
      assertInstanceOf(ModbusDataType.Int16.class, address.getDataType());
    }

    assertThrows(Exception.class, () -> ModbusAddressParser.parse("256.HR1"));
  }

  @Test
  void parseArrayAddress() throws Exception {
    // 1-dimensional array
    var address = ModbusAddressParser.parse("HR<int16[10]>1");
    assertInstanceOf(ModbusAddress.ArrayAddress.class, address);

    var arrayAddress = (ModbusAddress.ArrayAddress) address;
    assertEquals(ModbusArea.HOLDING_REGISTERS, arrayAddress.getArea());
    assertEquals(1, arrayAddress.getOffset());
    assertInstanceOf(ModbusDataType.Int16.class, arrayAddress.getDataType());
    assertEquals(1, arrayAddress.getDimensions().length);
    assertEquals(10, arrayAddress.getDimensions()[0]);

    // 2-dimensional array
    address = ModbusAddressParser.parse("HR<int32[5][2]>100");
    assertInstanceOf(ModbusAddress.ArrayAddress.class, address);

    arrayAddress = (ModbusAddress.ArrayAddress) address;
    assertEquals(ModbusArea.HOLDING_REGISTERS, arrayAddress.getArea());
    assertEquals(100, arrayAddress.getOffset());
    assertInstanceOf(ModbusDataType.Int32.class, arrayAddress.getDataType());
    assertEquals(2, arrayAddress.getDimensions().length);
    assertEquals(5, arrayAddress.getDimensions()[0]);
    assertEquals(2, arrayAddress.getDimensions()[1]);

    // 3-dimensional array
    address = ModbusAddressParser.parse("IR<float[3][4][5]>200");
    assertInstanceOf(ModbusAddress.ArrayAddress.class, address);

    arrayAddress = (ModbusAddress.ArrayAddress) address;
    assertEquals(ModbusArea.INPUT_REGISTERS, arrayAddress.getArea());
    assertEquals(200, arrayAddress.getOffset());
    assertInstanceOf(ModbusDataType.Float32.class, arrayAddress.getDataType());
    assertEquals(3, arrayAddress.getDimensions().length);
    assertEquals(3, arrayAddress.getDimensions()[0]);
    assertEquals(4, arrayAddress.getDimensions()[1]);
    assertEquals(5, arrayAddress.getDimensions()[2]);
  }

  @Test
  void parseArrayAddressWithUnitId() throws Exception {
    var address = ModbusAddressParser.parse("10.HR<int16[10]>1");
    assertInstanceOf(ModbusAddress.ArrayAddress.class, address);

    var arrayAddress = (ModbusAddress.ArrayAddress) address;
    assertEquals(10, arrayAddress.getUnitId().orElseThrow());
    assertEquals(ModbusArea.HOLDING_REGISTERS, arrayAddress.getArea());
    assertEquals(1, arrayAddress.getOffset());
    assertInstanceOf(ModbusDataType.Int16.class, arrayAddress.getDataType());
    assertEquals(1, arrayAddress.getDimensions().length);
    assertEquals(10, arrayAddress.getDimensions()[0]);
  }

  @Test
  void parseArrayAddressWithDataTypeModifiers() throws Exception {
    var address = ModbusAddressParser.parse("HR<int32[5][2]@LE@LH>100");
    assertInstanceOf(ModbusAddress.ArrayAddress.class, address);

    var arrayAddress = (ModbusAddress.ArrayAddress) address;
    assertEquals(ModbusArea.HOLDING_REGISTERS, arrayAddress.getArea());
    assertEquals(100, arrayAddress.getOffset());
    assertInstanceOf(ModbusDataType.Int32.class, arrayAddress.getDataType());
    assertEquals(2, arrayAddress.getDimensions().length);
    assertEquals(5, arrayAddress.getDimensions()[0]);
    assertEquals(2, arrayAddress.getDimensions()[1]);

    var modifiers = arrayAddress.getDataTypeModifiers();
    assertEquals(2, modifiers.size());
    assertTrue(modifiers.stream().anyMatch(m -> m instanceof DataTypeModifier.ByteOrderModifier));
    assertTrue(modifiers.stream().anyMatch(m -> m instanceof DataTypeModifier.WordOrderModifier));
  }

  @Test
  void parseArrayAddressWithIndices() throws Exception {
    // 1-dimensional array with index
    var address = ModbusAddressParser.parse("HR<int16[10]>1[5]");
    assertInstanceOf(ModbusAddress.ScalarAddress.class, address);

    var scalarAddress = (ModbusAddress.ScalarAddress) address;
    assertEquals(ModbusArea.HOLDING_REGISTERS, scalarAddress.getArea());
    assertEquals(6, scalarAddress.getOffset()); // base offset 1 + index 5 * register count 1
    assertInstanceOf(ModbusDataType.Int16.class, scalarAddress.getDataType());

    // 2-dimensional array with indices
    address = ModbusAddressParser.parse("HR<int32[5][2]>100[2][1]");
    assertInstanceOf(ModbusAddress.ScalarAddress.class, address);

    scalarAddress = (ModbusAddress.ScalarAddress) address;
    assertEquals(ModbusArea.HOLDING_REGISTERS, scalarAddress.getArea());
    assertEquals(110, scalarAddress.getOffset()); // base offset 100 + (2*2 + 1) * register count 2
    assertInstanceOf(ModbusDataType.Int32.class, scalarAddress.getDataType());

    // 3-dimensional array with indices
    address = ModbusAddressParser.parse("IR<float[3][4][5]>200[1][2][3]");
    assertInstanceOf(ModbusAddress.ScalarAddress.class, address);

    scalarAddress = (ModbusAddress.ScalarAddress) address;
    assertEquals(ModbusArea.INPUT_REGISTERS, scalarAddress.getArea());
    assertEquals(
        266, scalarAddress.getOffset()); // base offset 200 + (1*4*5 + 2*5 + 3) * register count 2
    assertInstanceOf(ModbusDataType.Float32.class, scalarAddress.getDataType());

    // With unit ID and data type modifiers
    address = ModbusAddressParser.parse("10.HR<int32[5][2]@LE@LH>100[4][0]");
    assertInstanceOf(ModbusAddress.ScalarAddress.class, address);

    scalarAddress = (ModbusAddress.ScalarAddress) address;
    assertEquals(10, scalarAddress.getUnitId().orElseThrow());
    assertEquals(ModbusArea.HOLDING_REGISTERS, scalarAddress.getArea());
    assertEquals(116, scalarAddress.getOffset()); // base offset 100 + (4*2 + 0) * register count 2
    assertInstanceOf(ModbusDataType.Int32.class, scalarAddress.getDataType());

    var modifiers = scalarAddress.getDataTypeModifiers();
    assertEquals(2, modifiers.size());
    assertTrue(modifiers.stream().anyMatch(m -> m instanceof DataTypeModifier.ByteOrderModifier));
    assertTrue(modifiers.stream().anyMatch(m -> m instanceof DataTypeModifier.WordOrderModifier));
  }

  @Test
  void parseArrayAddressWithInvalidIndices() {
    // The number of indices doesn't match dimensions
    assertThrows(Exception.class, () -> ModbusAddressParser.parse("HR<int16[10][20]>1[5]"));

    assertThrows(Exception.class, () -> ModbusAddressParser.parse("HR<int16[10]>1[5][2]"));

    // Index out of bounds
    assertThrows(
        Exception.class,
        () -> ModbusAddressParser.parse("HR<int16[10]>1[10]")); // index must be < dimension

    assertThrows(Exception.class, () -> ModbusAddressParser.parse("HR<int16[10][20]>1[5][20]"));

    // Negative index
    assertThrows(Exception.class, () -> ModbusAddressParser.parse("HR<int16[10]>1[-1]"));
  }
}
