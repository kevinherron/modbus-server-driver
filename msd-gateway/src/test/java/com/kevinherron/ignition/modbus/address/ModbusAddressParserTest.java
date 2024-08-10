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

}
