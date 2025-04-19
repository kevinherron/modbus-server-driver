package com.kevinherron.ignition.modbus;

import static org.junit.jupiter.api.Assertions.*;

import com.kevinherron.ignition.modbus.address.ModbusAddress;
import com.kevinherron.ignition.modbus.address.ModbusAddress.ArrayAddress;
import com.kevinherron.ignition.modbus.address.ModbusAddressParser;
import java.util.Map;
import java.util.stream.Stream;
import org.eclipse.milo.opcua.stack.core.types.builtin.Matrix;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ModbusAddressSpaceTest {

  @ParameterizedTest(name = "{0}")
  @MethodSource("readBooleansArguments")
  void readBooleans(String addressString, Map<Integer, Boolean> booleans, boolean[] expectedValues)
      throws Exception {

    ModbusAddress address = ModbusAddressParser.parse(addressString);
    assertInstanceOf(ArrayAddress.class, address, "Address must be an array address");
    boolean[] values = ModbusAddressSpace.readBooleans(booleans, (ArrayAddress) address);
    assertArrayEquals(expectedValues, values);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("readRegistersArguments")
  void readRegisters(String addressString, Map<Integer, byte[]> registers, byte[] expectedBytes)
      throws Exception {

    ModbusAddress address = ModbusAddressParser.parse(addressString);
    byte[] bytes = ModbusAddressSpace.readRegisters(registers, address);
    assertArrayEquals(expectedBytes, bytes);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("writeBooleanArrayArguments")
  void writeBooleanArray(String addressString, Object value, Map<Integer, Boolean> expectedMap)
      throws Exception {

    ModbusAddress address = ModbusAddressParser.parse(addressString);
    assertInstanceOf(ArrayAddress.class, address, "Address must be an array address");

    Map<Integer, Boolean> booleanMap = new java.util.HashMap<>();
    Variant variant = new Variant(value);

    ModbusAddressSpace.writeBooleanArray(booleanMap, variant, (ArrayAddress) address);

    assertEquals(expectedMap.size(), booleanMap.size(), "Map size should match");
    for (Map.Entry<Integer, Boolean> entry : expectedMap.entrySet()) {
      assertEquals(
          entry.getValue(),
          booleanMap.get(entry.getKey()),
          "Value at offset " + entry.getKey() + " should match");
    }
  }

  private static Stream<Arguments> readBooleansArguments() {
    return Stream.of(
        // Test 1D array of booleans (3 elements)
        Arguments.of(
            "C<bool[3]>0",
            Map.of(
                0, true,
                1, false,
                2, true),
            new boolean[] {true, false, true}),

        // Test 2D array of booleans (2x2 elements)
        Arguments.of(
            "C<bool[2][2]>10",
            Map.of(
                10, true, // [0][0]
                11, false, // [0][1]
                12, false, // [1][0]
                13, true), // [1][1]
            new boolean[] {true, false, false, true}),

        // Test 3D array of booleans (2x2x2 elements)
        Arguments.of(
            "C<bool[2][2][2]>50",
            Map.of(
                50, true, // [0][0][0]
                51, false, // [0][0][1]
                52, true, // [0][1][0]
                53, false, // [0][1][1]
                54, false, // [1][0][0]
                55, true, // [1][0][1]
                56, true, // [1][1][0]
                57, false // [1][1][1]
                ),
            new boolean[] {true, false, true, false, false, true, true, false}),

        // Test array with missing values (should return false for missing values)
        Arguments.of(
            "C<bool[3]>20",
            Map.of(
                20, true,
                // 21 is missing
                22, true),
            new boolean[] {true, false, true}),

        // Test discrete inputs
        Arguments.of(
            "DI<bool[4]>30",
            Map.of(
                30, true,
                31, true,
                32, false,
                33, true),
            new boolean[] {true, true, false, true}),

        // Test larger array
        Arguments.of(
            "C<bool[6]>40",
            Map.of(
                40, true,
                41, false,
                42, true,
                43, true,
                44, false,
                45, true),
            new boolean[] {true, false, true, true, false, true}));
  }

  private static Stream<Arguments> writeBooleanArrayArguments() {
    // 1D array test
    Boolean[] array1d = new Boolean[] {true, false, true};
    Map<Integer, Boolean> expected1d =
        Map.of(
            0, true,
            1, false,
            2, true);

    // 2D array test (2x2)
    Boolean[] array2d = new Boolean[] {true, false, false, true};
    int[] dimensions2d = new int[] {2, 2};
    Matrix matrix2d = new Matrix(array2d, dimensions2d);
    Map<Integer, Boolean> expected2d =
        Map.of(
            10, true, // [0][0]
            11, false, // [0][1]
            12, false, // [1][0]
            13, true); // [1][1]

    // 3D array test (2x2x2)
    Boolean[] array3d =
        new Boolean[] {
          true, false, true, false,
          false, true, true, false
        };
    int[] dimensions3d = new int[] {2, 2, 2};
    Matrix matrix3d = new Matrix(array3d, dimensions3d);
    Map<Integer, Boolean> expected3d =
        Map.of(
            50, true, // [0][0][0]
            51, false, // [0][0][1]
            52, true, // [0][1][0]
            53, false, // [0][1][1]
            54, false, // [1][0][0]
            55, true, // [1][0][1]
            56, true, // [1][1][0]
            57, false); // [1][1][1]

    return Stream.of(
        // Test 1D array
        Arguments.of("C<bool[3]>0", array1d, expected1d),

        // Test 2D array (Matrix)
        Arguments.of("C<bool[2][2]>10", matrix2d, expected2d),

        // Test 3D array (Matrix)
        Arguments.of("C<bool[2][2][2]>50", matrix3d, expected3d));
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

        // Test 3D array of INT16 (2x2x2 elements)
        Arguments.of(
            "HR<int16[2][2][2]>500",
            Map.of(
                500, new byte[] {0x01, 0x01}, // [0][0][0]
                501, new byte[] {0x02, 0x02}, // [0][0][1]
                502, new byte[] {0x03, 0x03}, // [0][1][0]
                503, new byte[] {0x04, 0x04}, // [0][1][1]
                504, new byte[] {0x05, 0x05}, // [1][0][0]
                505, new byte[] {0x06, 0x06}, // [1][0][1]
                506, new byte[] {0x07, 0x07}, // [1][1][0]
                507, new byte[] {0x08, 0x08} // [1][1][1]
                ),
            new byte[] {
              0x01, 0x01, 0x02, 0x02, 0x03, 0x03, 0x04, 0x04, 0x05, 0x05, 0x06, 0x06, 0x07, 0x07,
              0x08, 0x08
            }),

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
