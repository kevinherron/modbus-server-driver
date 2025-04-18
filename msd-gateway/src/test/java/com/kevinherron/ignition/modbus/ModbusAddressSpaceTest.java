package com.kevinherron.ignition.modbus;

import static org.junit.jupiter.api.Assertions.*;

import com.kevinherron.ignition.modbus.address.ModbusAddress;
import com.kevinherron.ignition.modbus.address.ModbusAddressParser;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ModbusAddressSpaceTest {

  @ParameterizedTest(name = "{0}")
  @MethodSource("readRegistersArguments")
  void readRegisters(String addressString, Map<Integer, byte[]> registers, byte[] expectedBytes)
      throws Exception {

    ModbusAddress address = ModbusAddressParser.parse(addressString);
    byte[] bytes = ModbusAddressSpace.readRegisters(registers, address);
    assertArrayEquals(expectedBytes, bytes);
  }

  private static Stream<Arguments> readRegistersArguments() {
    return Stream.of(
        // Test INT32 (2 registers)
        Arguments.of(
            "HR<int32>0",
            Map.of(
                0, new byte[] {0x00, 0x01},
                1, new byte[] {0x02, 0x03}),
            new byte[] {0x00, 0x01, 0x02, 0x03}),

        // Test INT16 (1 register)
        Arguments.of("HR<int16>10", Map.of(10, new byte[] {0x04, 0x05}), new byte[] {0x04, 0x05}),

        // Test INT64 (4 registers)
        Arguments.of(
            "HR<int64>20",
            Map.of(
                20, new byte[] {0x00, 0x01},
                21, new byte[] {0x02, 0x03},
                22, new byte[] {0x04, 0x05},
                23, new byte[] {0x06, 0x07}),
            new byte[] {0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07}),

        // Test FLOAT (2 registers)
        Arguments.of(
            "HR<float>30",
            Map.of(
                30, new byte[] {0x41, 0x20}, // IEEE 754 representation of 10.0
                31, new byte[] {0x00, 0x00}),
            new byte[] {0x41, 0x20, 0x00, 0x00}),

        // Test STRING (3 registers for 5 characters)
        Arguments.of(
            "HR<string5>40",
            Map.of(
                40, new byte[] {0x48, 0x65}, // "He"
                41, new byte[] {0x6C, 0x6C}, // "ll"
                42, new byte[] {0x6F, 0x00}), // "o" + null
            new byte[] {0x48, 0x65, 0x6C, 0x6C, 0x6F, 0x00}),

        // Test Input Registers
        Arguments.of(
            "IR<int32>50",
            Map.of(
                50, new byte[] {0x08, 0x09},
                51, new byte[] {0x0A, 0x0B}),
            new byte[] {0x08, 0x09, 0x0A, 0x0B}),

        // Test 1D array of INT16 (3 elements)
        Arguments.of(
            "HR<int16[3]>100",
            Map.of(
                100, new byte[] {0x01, 0x02}, // First element
                101, new byte[] {0x03, 0x04}, // Second element
                102, new byte[] {0x05, 0x06}), // Third element
            new byte[] {0x01, 0x02, 0x03, 0x04, 0x05, 0x06}),

        // Test 1D array of INT32 (2 elements)
        Arguments.of(
            "HR<int32[2]>200",
            Map.of(
                200, new byte[] {0x00, 0x01}, // First element, first register
                201, new byte[] {0x02, 0x03}, // First element, second register
                202, new byte[] {0x04, 0x05}, // Second element, first register
                203, new byte[] {0x06, 0x07}), // Second element, second register
            new byte[] {0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07}),

        // Test 2D array of INT16 (2x2 elements)
        Arguments.of(
            "HR<int16[2][2]>300",
            Map.of(
                300, new byte[] {0x0A, 0x0B}, // [0][0]
                301, new byte[] {0x0C, 0x0D}, // [0][1]
                302, new byte[] {0x0E, 0x0F}, // [1][0]
                303, new byte[] {0x10, 0x11}), // [1][1]
            new byte[] {0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10, 0x11}),

        // Test array with missing values (should return zeros for missing registers)
        Arguments.of(
            "HR<int16[3]>400",
            Map.of(
                400, new byte[] {0x01, 0x02},
                // 401 is missing
                402, new byte[] {0x05, 0x06}),
            new byte[] {0x01, 0x02, 0x00, 0x00, 0x05, 0x06}));
  }
}
