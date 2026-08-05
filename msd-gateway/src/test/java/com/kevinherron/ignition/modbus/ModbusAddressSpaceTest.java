package com.kevinherron.ignition.modbus;

import static org.junit.jupiter.api.Assertions.*;

import com.digitalpetri.modbus.server.ProcessImage;
import com.kevinherron.ignition.modbus.address.ModbusAddress;
import com.kevinherron.ignition.modbus.address.ModbusAddress.ArrayAddress;
import com.kevinherron.ignition.modbus.address.ModbusAddressParser;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.eclipse.milo.opcua.sdk.core.AccessLevel;
import org.eclipse.milo.opcua.sdk.core.ValueRank;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.OpcUaDataType;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.Matrix;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.junit.jupiter.api.Test;
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

  @ParameterizedTest(name = "{0}")
  @MethodSource("registerWriteBytesArguments")
  void getRegisterWriteBytes(String addressString, Object value, byte[] expectedBytes)
      throws Exception {

    ModbusAddress address = ModbusAddressParser.parse(addressString);

    byte[] bytes = ModbusAddressSpace.getRegisterWriteBytes(address, new Variant(value));

    assertArrayEquals(expectedBytes, bytes);
  }

  @Test
  void getRegisterWriteBytesRejectsScalarForArrayAddress() throws Exception {
    assertRegisterWriteTypeMismatch("HR<int16[10]>0", (short) 1);
  }

  @Test
  void getRegisterWriteBytesRejectsShortArray() throws Exception {
    assertRegisterWriteTypeMismatch(
        "HR<int16[10]>0", new Short[] {1, 2, 3, 4, 5});
  }

  @Test
  void getRegisterWriteBytesRejectsWrongMatrixShape() throws Exception {
    assertRegisterWriteTypeMismatch(
        "HR<int16[2][2]>0",
        new Matrix(new Short[] {1, 2, 3, 4, 5, 6}, new int[] {2, 3}));
  }

  @Test
  void getRegisterWriteBytesRejectsWrongArrayElementType() throws Exception {
    assertRegisterWriteTypeMismatch("HR<int16[4]>0", new Integer[] {1, 2, 3, 4});
  }

  @Test
  void getRegisterWriteBytesRejectsNull() throws Exception {
    assertRegisterWriteTypeMismatch("HR<int16[4]>0", null);
  }

  @Test
  void registerArrayWriteReadBytesRoundTrip() throws Exception {
    ModbusAddress address = ModbusAddressParser.parse("HR<int16[10]>0");
    Short[] value = new Short[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
    byte[] writtenBytes =
        ModbusAddressSpace.getRegisterWriteBytes(address, new Variant(value));
    Map<Integer, byte[]> registers = new java.util.HashMap<>();

    for (int i = 0; i < writtenBytes.length / 2; i++) {
      registers.put(i, new byte[] {writtenBytes[i * 2], writtenBytes[i * 2 + 1]});
    }

    assertArrayEquals(writtenBytes, ModbusAddressSpace.readRegisters(registers, address));
  }

  @Test
  void shapeCoilBooleanMatrix() throws Exception {
    ArrayAddress address = arrayAddress("C<bool[2][2]>0");

    Object value =
        ModbusAddressSpace.shapeBooleanArray(
            new boolean[] {true, false, false, true}, address);

    Matrix matrix = assertInstanceOf(Matrix.class, value);
    assertArrayEquals(new int[] {2, 2}, matrix.getDimensions());
    assertArrayEquals(new Boolean[] {true, false, false, true}, (Boolean[]) matrix.getElements());
    assertEquals(Boolean.class, matrix.getElementType().orElseThrow());
    assertEquals(OpcUaDataType.Boolean, matrix.getDataType().orElseThrow());
  }

  @Test
  void shapeDiscreteInputBooleanMatrix() throws Exception {
    ArrayAddress address = arrayAddress("DI<bool[2][3]>0");

    Object value =
        ModbusAddressSpace.shapeBooleanArray(
            new boolean[] {true, false, true, false, true, false}, address);

    Matrix matrix = assertInstanceOf(Matrix.class, value);
    assertArrayEquals(new int[] {2, 3}, matrix.getDimensions());
    assertArrayEquals(
        new Boolean[] {true, false, true, false, true, false},
        (Boolean[]) matrix.getElements());
    assertEquals(Boolean.class, matrix.getElementType().orElseThrow());
    assertEquals(OpcUaDataType.Boolean, matrix.getDataType().orElseThrow());
  }

  @Test
  void shapeOneDimensionalBooleanArrayAsBoxedArray() throws Exception {
    ArrayAddress address = arrayAddress("C<bool[3]>0");

    Object value =
        ModbusAddressSpace.shapeBooleanArray(new boolean[] {true, false, true}, address);

    assertArrayEquals(
        new Boolean[] {true, false, true}, assertInstanceOf(Boolean[].class, value));
  }

  @Test
  void coilMatrixShapeAgreesWithNodeMetadata() throws Exception {
    ArrayAddress address = arrayAddress("C<bool[2][2]>0");
    Matrix matrix =
        assertInstanceOf(
            Matrix.class,
            ModbusAddressSpace.shapeBooleanArray(
                new boolean[] {true, false, false, true}, address));

    Variant valueRank = ModbusAddressSpace.readAddressAttribute(AttributeId.ValueRank, address);
    Variant arrayDimensions =
        ModbusAddressSpace.readAddressAttribute(AttributeId.ArrayDimensions, address);

    assertEquals(matrix.getValueRank(), valueRank.getValue());
    assertArrayEquals(
        matrix.getDimensions(),
        Stream.of((UInteger[]) arrayDimensions.getValue()).mapToInt(UInteger::intValue).toArray());
  }

  @Test
  void shapedCoilMatrixCanBeWrittenBackToSameAddress() throws Exception {
    ArrayAddress address = arrayAddress("C<bool[2][2]>0");
    Object value =
        ModbusAddressSpace.shapeBooleanArray(
            new boolean[] {true, false, false, true}, address);
    Map<Integer, Boolean> booleans = new HashMap<>();

    ModbusAddressSpace.writeBooleanArray(booleans, new Variant(value), address);

    assertEquals(
        Map.of(
            0, true,
            1, false,
            2, false,
            3, true),
        booleans);
  }

  @Test
  void registerArrayDimensionsHaveResolvableUint32Type() throws Exception {
    ModbusAddress address = ModbusAddressParser.parse("HR<int16[4][5]>0");

    Variant arrayDimensions =
        ModbusAddressSpace.readAddressAttribute(AttributeId.ArrayDimensions, address);

    assertArrayEquals(
        new UInteger[] {UInteger.valueOf(4), UInteger.valueOf(5)},
        (UInteger[]) arrayDimensions.getValue());
    assertEquals(OpcUaDataType.UInt32, arrayDimensions.getDataType().orElseThrow());
    assertTrue(arrayDimensions.getDataTypeId().isPresent());
  }

  @Test
  void scalarMetadataHasNullArrayDimensionsAndScalarValueRank() throws Exception {
    ModbusAddress address = ModbusAddressParser.parse("HR<int16>0");

    Variant arrayDimensions =
        ModbusAddressSpace.readAddressAttribute(AttributeId.ArrayDimensions, address);
    Variant valueRank = ModbusAddressSpace.readAddressAttribute(AttributeId.ValueRank, address);

    assertNull(arrayDimensions.getValue());
    assertEquals(ValueRank.Scalar.getValue(), valueRank.getValue());
  }

  @Test
  void addressDerivedAttributesPreserveDataTypeAndAccessLevels() throws Exception {
    ModbusAddress address = ModbusAddressParser.parse("C<bool[2][2]>0");

    assertEquals(
        NodeIds.Boolean,
        ModbusAddressSpace.readAddressAttribute(AttributeId.DataType, address).getValue());
    assertEquals(
        AccessLevel.toValue(AccessLevel.READ_WRITE),
        ModbusAddressSpace.readAddressAttribute(AttributeId.AccessLevel, address).getValue());
    assertEquals(
        AccessLevel.toValue(AccessLevel.READ_WRITE),
        ModbusAddressSpace.readAddressAttribute(AttributeId.UserAccessLevel, address).getValue());
  }

  @Test
  void readHoldingRegisterArrayRanges() throws Exception {
    ProcessImage processImage = new ProcessImage();
    ModbusAddress address = ModbusAddressParser.parse("HR<int16[10]>0");
    writeValue(processImage, address, shorts(0, 10));

    assertArrayEquals(
        new Short[] {2, 3, 4},
        (Short[])
            ModbusAddressSpace.readValueAttribute(processImage, address, "2:4").getValue());
    assertArrayEquals(
        new Short[] {0},
        (Short[])
            ModbusAddressSpace.readValueAttribute(processImage, address, "0").getValue());
  }

  @Test
  void readHoldingRegisterSubmatrix() throws Exception {
    ProcessImage processImage = new ProcessImage();
    ModbusAddress address = ModbusAddressParser.parse("HR<int16[4][4]>0");
    writeValue(
        processImage,
        address,
        new Matrix(shorts(0, 16), new int[] {4, 4}));

    Matrix matrix =
        assertInstanceOf(
            Matrix.class,
            ModbusAddressSpace.readValueAttribute(processImage, address, "1:2,0:1")
                .getValue());

    assertArrayEquals(new int[] {2, 2}, matrix.getDimensions());
    assertArrayEquals(new Short[] {4, 5, 8, 9}, (Short[]) matrix.getElements());
  }

  @Test
  void readCoilArrayRange() throws Exception {
    ProcessImage processImage = new ProcessImage();
    ModbusAddress address = ModbusAddressParser.parse("C<bool[8]>0");
    writeValue(
        processImage,
        address,
        new Boolean[] {false, true, false, true, true, false, true, false});

    assertArrayEquals(
        new Boolean[] {true, true, false},
        (Boolean[])
            ModbusAddressSpace.readValueAttribute(processImage, address, "3:5").getValue());
  }

  @Test
  void rangedHoldingRegisterWritePreservesNeighbours() throws Exception {
    ProcessImage processImage = new ProcessImage();
    ModbusAddress address = ModbusAddressParser.parse("HR<int16[10]>0");
    writeValue(processImage, address, shorts(0, 10));

    ModbusAddressSpace.writeValueAttribute(
        processImage, address, new Variant(new Short[] {20, 30, 40}), "2:4");

    assertArrayEquals(
        new Short[] {0, 1, 20, 30, 40, 5, 6, 7, 8, 9},
        (Short[])
            ModbusAddressSpace.readValueAttribute(processImage, address, null).getValue());
  }

  @Test
  void rangedHoldingRegisterMatrixWritePreservesUnselectedCells() throws Exception {
    ProcessImage processImage = new ProcessImage();
    ModbusAddress address = ModbusAddressParser.parse("HR<int16[4][4]>0");
    writeValue(
        processImage,
        address,
        new Matrix(shorts(0, 16), new int[] {4, 4}));

    ModbusAddressSpace.writeValueAttribute(
        processImage,
        address,
        new Variant(new Matrix(new Short[] {40, 41, 80, 81}, new int[] {2, 2})),
        "1:2,0:1");

    Matrix matrix =
        assertInstanceOf(
            Matrix.class,
            ModbusAddressSpace.readValueAttribute(processImage, address, null).getValue());
    assertArrayEquals(
        new Short[] {0, 1, 2, 3, 40, 41, 6, 7, 80, 81, 10, 11, 12, 13, 14, 15},
        (Short[]) matrix.getElements());
  }

  @Test
  void rangedHoldingRegisterWriteUsesZeroForSparseBase() throws Exception {
    ProcessImage processImage = new ProcessImage();
    ModbusAddress address = ModbusAddressParser.parse("HR<int16[6]>100");

    ModbusAddressSpace.writeValueAttribute(
        processImage, address, new Variant(new Short[] {7, 8}), "2:3");

    assertArrayEquals(
        new Short[] {0, 0, 7, 8, 0, 0},
        (Short[])
            ModbusAddressSpace.readValueAttribute(processImage, address, null).getValue());
  }

  @Test
  void malformedPastEndAndScalarRangesReturnSpecifiedStatusCodes() throws Exception {
    ProcessImage processImage = new ProcessImage();
    ModbusAddress array = ModbusAddressParser.parse("HR<int16[10]>0");
    ModbusAddress scalar = ModbusAddressParser.parse("HR<int16>20");
    writeValue(processImage, array, shorts(0, 10));
    writeValue(processImage, scalar, (short) 1);

    List<DataValue> values =
        ModbusAddressSpace.readValueAttributes(
            processImage,
            List.of(
                new ModbusAddressSpace.ValueRead(array, "1::2"),
                new ModbusAddressSpace.ValueRead(scalar, "1::2"),
                new ModbusAddressSpace.ValueRead(array, "1:"),
                new ModbusAddressSpace.ValueRead(array, "1,"),
                new ModbusAddressSpace.ValueRead(array, "10"),
                new ModbusAddressSpace.ValueRead(scalar, "0")));

    assertStatus(StatusCodes.Bad_IndexRangeInvalid, values.get(0).getStatusCode());
    assertStatus(StatusCodes.Bad_IndexRangeInvalid, values.get(1).getStatusCode());
    assertStatus(StatusCodes.Bad_IndexRangeInvalid, values.get(2).getStatusCode());
    assertStatus(StatusCodes.Bad_IndexRangeInvalid, values.get(3).getStatusCode());
    assertStatus(StatusCodes.Bad_IndexRangeNoData, values.get(4).getStatusCode());
    assertStatus(StatusCodes.Bad_IndexRangeNoData, values.get(5).getStatusCode());
  }

  @Test
  void malformedScalarWriteRangesReturnInvalidWithoutMutation() throws Exception {
    ProcessImage processImage = new ProcessImage();
    ModbusAddress register = ModbusAddressParser.parse("HR<int16>0");
    ModbusAddress coil = ModbusAddressParser.parse("C10");
    writeValue(processImage, register, (short) 7);
    writeValue(processImage, coil, true);

    List<StatusCode> statuses =
        ModbusAddressSpace.writeValueAttributes(
            processImage,
            List.of(
                new ModbusAddressSpace.ValueWrite(
                    register, new Variant((short) 99), "1::2"),
                new ModbusAddressSpace.ValueWrite(coil, new Variant(false), "1::2"),
                new ModbusAddressSpace.ValueWrite(register, new Variant((short) 99), "1:"),
                new ModbusAddressSpace.ValueWrite(coil, new Variant(false), "1,")));

    assertStatus(StatusCodes.Bad_IndexRangeInvalid, statuses.get(0));
    assertStatus(StatusCodes.Bad_IndexRangeInvalid, statuses.get(1));
    assertStatus(StatusCodes.Bad_IndexRangeInvalid, statuses.get(2));
    assertStatus(StatusCodes.Bad_IndexRangeInvalid, statuses.get(3));
    assertEquals(
        (short) 7,
        ModbusAddressSpace.readValueAttribute(processImage, register, null).getValue());
    assertEquals(
        true, ModbusAddressSpace.readValueAttribute(processImage, coil, null).getValue());
  }

  @Test
  void mismatchedRangedWriteDoesNotMutateProcessImage() throws Exception {
    ProcessImage processImage = new ProcessImage();
    ModbusAddress address = ModbusAddressParser.parse("HR<int16[6]>0");
    Short[] initial = shorts(0, 6);
    writeValue(processImage, address, initial);

    List<StatusCode> statuses =
        ModbusAddressSpace.writeValueAttributes(
            processImage,
            List.of(
                new ModbusAddressSpace.ValueWrite(
                    address, new Variant(new Short[] {90, 91}), "2:4")));

    assertStatus(StatusCodes.Bad_IndexRangeDataMismatch, statuses.get(0));
    assertArrayEquals(
        initial,
        (Short[])
            ModbusAddressSpace.readValueAttribute(processImage, address, null).getValue());
  }

  @Test
  void multidimensionalRangesRequireEveryDimensionWithoutMutation() throws Exception {
    ProcessImage processImage = new ProcessImage();
    ModbusAddress address = ModbusAddressParser.parse("HR<int16[2][2]>0");
    Short[] initial = shorts(0, 4);
    writeValue(processImage, address, new Matrix(initial, new int[] {2, 2}));

    DataValue read =
        ModbusAddressSpace.readValueAttributes(
                processImage, List.of(new ModbusAddressSpace.ValueRead(address, "1")))
            .get(0);
    StatusCode write =
        ModbusAddressSpace.writeValueAttributes(
                processImage,
                List.of(
                    new ModbusAddressSpace.ValueWrite(
                        address, new Variant(new Short[] {8, 9}), "1")))
            .get(0);

    assertStatus(StatusCodes.Bad_IndexRangeNoData, read.getStatusCode());
    assertStatus(StatusCodes.Bad_IndexRangeNoData, write);
    Matrix actual =
        assertInstanceOf(
            Matrix.class,
            ModbusAddressSpace.readValueAttribute(processImage, address, null).getValue());
    assertArrayEquals(initial, (Short[]) actual.getElements());
  }

  @Test
  void rangedWriteElementTypeMismatchDoesNotMutateProcessImage() throws Exception {
    ProcessImage processImage = new ProcessImage();
    ModbusAddress address = ModbusAddressParser.parse("HR<int16[4]>0");
    Short[] initial = shorts(0, 4);
    writeValue(processImage, address, initial);

    StatusCode status =
        ModbusAddressSpace.writeValueAttributes(
                processImage,
                List.of(
                    new ModbusAddressSpace.ValueWrite(
                        address, new Variant(new Integer[] {8, 9}), "1:2")))
            .get(0);

    assertStatus(StatusCodes.Bad_TypeMismatch, status);
    assertArrayEquals(
        initial,
        (Short[])
            ModbusAddressSpace.readValueAttribute(processImage, address, null).getValue());
  }

  @Test
  void stringRangeLengthMismatchDoesNotMutateProcessImage() throws Exception {
    ProcessImage processImage = new ProcessImage();
    ModbusAddress address = ModbusAddressParser.parse("HR<string10>0");
    writeValue(processImage, address, "HELLOWORLD");

    StatusCode status =
        ModbusAddressSpace.writeValueAttributes(
                processImage,
                List.of(
                    new ModbusAddressSpace.ValueWrite(address, new Variant("NO"), "0:4")))
            .get(0);

    assertStatus(StatusCodes.Bad_IndexRangeDataMismatch, status);
    assertEquals(
        "HELLOWORLD",
        ModbusAddressSpace.readValueAttribute(processImage, address, null).getValue());
  }

  @Test
  void scalarStringIndexRangeReadsAndWritesSubString() throws Exception {
    ProcessImage processImage = new ProcessImage();
    ModbusAddress address = ModbusAddressParser.parse("HR<string10>0");
    writeValue(processImage, address, "HELLOWORLD");

    assertEquals(
        "HELLO", ModbusAddressSpace.readValueAttribute(processImage, address, "0:4").getValue());

    ModbusAddressSpace.writeValueAttribute(processImage, address, new Variant("WORLD"), "0:4");

    assertEquals(
        "WORLDWORLD",
        ModbusAddressSpace.readValueAttribute(processImage, address, null).getValue());
  }

  @Test
  void stringArraySubStringRangeReadsAndWrites() throws Exception {
    ProcessImage processImage = new ProcessImage();
    ModbusAddress address = ModbusAddressParser.parse("HR<string8[4]>0");
    writeValue(processImage, address, new String[] {"alpha", "beta", "gamma", "delta"});

    assertArrayEquals(
        new String[] {"bet"},
        (String[])
            ModbusAddressSpace.readValueAttribute(processImage, address, "1,0:2").getValue());

    ModbusAddressSpace.writeValueAttribute(
        processImage, address, new Variant(new String[] {"BET"}), "1,0:2");

    assertArrayEquals(
        new String[] {"alpha", "BETa", "gamma", "delta"},
        (String[]) ModbusAddressSpace.readValueAttribute(processImage, address, null).getValue());
  }

  @Test
  void stringArrayElementRangeReadsAndWritesWholeStrings() throws Exception {
    ProcessImage processImage = new ProcessImage();
    ModbusAddress address = ModbusAddressParser.parse("HR<string8[4]>0");
    writeValue(processImage, address, new String[] {"alpha", "beta", "gamma", "delta"});

    assertArrayEquals(
        new String[] {"beta", "gamma"},
        (String[]) ModbusAddressSpace.readValueAttribute(processImage, address, "1:2").getValue());

    ModbusAddressSpace.writeValueAttribute(
        processImage, address, new Variant(new String[] {"BETA", "GAMMA"}), "1:2");

    assertArrayEquals(
        new String[] {"alpha", "BETA", "GAMMA", "delta"},
        (String[]) ModbusAddressSpace.readValueAttribute(processImage, address, null).getValue());
  }

  @Test
  void threeReadBatchWithMiddleRangeReturnsCompleteResults() throws Exception {
    ProcessImage processImage = new ProcessImage();
    ModbusAddress first = ModbusAddressParser.parse("HR<int16>0");
    ModbusAddress middle = ModbusAddressParser.parse("HR<int16[4]>10");
    ModbusAddress last = ModbusAddressParser.parse("C0");
    writeValue(processImage, first, (short) 11);
    writeValue(processImage, middle, new Short[] {1, 2, 3, 4});
    writeValue(processImage, last, true);

    List<DataValue> values =
        ModbusAddressSpace.readValueAttributes(
            processImage,
            List.of(
                new ModbusAddressSpace.ValueRead(first, null),
                new ModbusAddressSpace.ValueRead(middle, "1:2"),
                new ModbusAddressSpace.ValueRead(last, null)));

    assertEquals(3, values.size());
    assertTrue(values.stream().allMatch(v -> v != null && v.getValue() != null));
    assertArrayEquals(new Short[] {2, 3}, (Short[]) values.get(1).getValue().getValue());
  }

  @Test
  void normalValueWriteBatchReturnsNonNullStatuses() throws Exception {
    ProcessImage processImage = new ProcessImage();
    ModbusAddress first = ModbusAddressParser.parse("HR<int16>0");
    ModbusAddress second = ModbusAddressParser.parse("C0");

    List<StatusCode> statuses =
        ModbusAddressSpace.writeValueAttributes(
            processImage,
            List.of(
                new ModbusAddressSpace.ValueWrite(first, new Variant((short) 7), null),
                new ModbusAddressSpace.ValueWrite(second, new Variant(true), null)));

    assertEquals(2, statuses.size());
    assertTrue(statuses.stream().allMatch(status -> status != null && status.isGood()));
  }

  private static void writeValue(
      ProcessImage processImage, ModbusAddress address, Object value) throws UaException {
    ModbusAddressSpace.writeValueAttribute(processImage, address, new Variant(value), null);
  }

  private static Short[] shorts(int startInclusive, int endExclusive) {
    Short[] values = new Short[endExclusive - startInclusive];
    for (int i = 0; i < values.length; i++) {
      values[i] = (short) (startInclusive + i);
    }
    return values;
  }

  private static void assertStatus(long expected, StatusCode actual) {
    assertNotNull(actual);
    assertEquals(expected, actual.getValue());
  }

  private static ArrayAddress arrayAddress(String addressString) throws Exception {
    return assertInstanceOf(ArrayAddress.class, ModbusAddressParser.parse(addressString));
  }

  private static void assertRegisterWriteTypeMismatch(String addressString, Object value)
      throws Exception {
    ModbusAddress address = ModbusAddressParser.parse(addressString);

    UaException exception =
        assertThrows(
            UaException.class,
            () -> ModbusAddressSpace.getRegisterWriteBytes(address, new Variant(value)));

    assertEquals(StatusCodes.Bad_TypeMismatch, exception.getStatusCode().getValue());
  }

  private static Stream<Arguments> registerWriteBytesArguments() {
    return Stream.of(
        Arguments.of(
            "HR<int16[10]>0",
            new Short[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10},
            new byte[] {
              0, 1, 0, 2, 0, 3, 0, 4, 0, 5, 0, 6, 0, 7, 0, 8, 0, 9, 0, 10
            }),
        Arguments.of(
            "HR<float[4]>0",
            new Float[] {1.0f, -2.5f, 0.0f, 3.75f},
            new byte[] {
              0x3F, (byte) 0x80, 0, 0,
              (byte) 0xC0, 0x20, 0, 0,
              0, 0, 0, 0,
              0x40, 0x70, 0, 0
            }),
        Arguments.of(
            "IR<uint16[3]>0",
            new UShort[] {UShort.valueOf(1), UShort.valueOf(32768), UShort.valueOf(65535)},
            new byte[] {0, 1, (byte) 0x80, 0, (byte) 0xFF, (byte) 0xFF}),
        Arguments.of(
            "HR<int16[2][2]>0",
            new Matrix(new Short[] {1, 2, -1, 32767}, new int[] {2, 2}),
            new byte[] {0, 1, 0, 2, (byte) 0xFF, (byte) 0xFF, 0x7F, (byte) 0xFF}),
        Arguments.of("HR<int16>0", (short) -2, new byte[] {(byte) 0xFF, (byte) 0xFE}));
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
