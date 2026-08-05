/*
 * Copyright (c) 2026 the Eclipse Milo Authors
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package com.kevinherron.ignition.modbus;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ulong;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ushort;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.digitalpetri.modbus.client.ModbusClient;
import com.digitalpetri.modbus.client.ModbusTcpClient;
import com.digitalpetri.modbus.pdu.ReadCoilsRequest;
import com.digitalpetri.modbus.pdu.ReadDiscreteInputsRequest;
import com.digitalpetri.modbus.pdu.ReadHoldingRegistersRequest;
import com.digitalpetri.modbus.pdu.ReadInputRegistersRequest;
import com.digitalpetri.modbus.pdu.WriteMultipleCoilsRequest;
import com.digitalpetri.modbus.pdu.WriteMultipleRegistersRequest;
import com.digitalpetri.modbus.tcp.client.NettyTcpClientTransport;
import com.mussonindustrial.testcontainers.ignition.IgnitionContainer;
import com.mussonindustrial.testcontainers.ignition.IgnitionGatewayEdition;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.identity.UsernameProvider;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.OpcUaDataType;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.Matrix;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.ULong;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId;
import org.eclipse.milo.opcua.stack.core.types.structured.WriteValue;
import org.eclipse.milo.opcua.stack.core.util.EndpointUtil;
import org.eclipse.milo.opcua.stack.transport.client.tcp.OpcTcpClientTransportConfigBuilder;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Verifies the externally observable array contract across the Ignition OPC UA server and the
 * embedded Modbus server.
 *
 * <p>These tests use both protocol clients so an error in one adapter cannot be hidden by applying
 * the same inverse error on the return path.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ArrayModbusToOpcUaIT {

  private static final int ARRAY_OFFSET = 4096;

  private IgnitionContainer ignitionContainer;
  private OpcUaClient opcUaClient;
  private ModbusClient modbusClient;

  @BeforeAll
  void setUpContainer() throws Exception {
    ignitionContainer =
        new IgnitionContainer(IgnitionTestSupport.IGNITION_IMAGE)
            .acceptLicense()
            .withCredentials("admin", "password")
            .withEdition(IgnitionGatewayEdition.STANDARD)
            .withAllowUnsignedModules()
            .withGatewayBackup(IgnitionTestSupport.GATEWAY_BACKUP, false)
            .withThirdPartyModule(IgnitionTestSupport.requireModuleArchive())
            .withAdditionalExposedPort(IgnitionTestSupport.MODBUS_PORT);

    ignitionContainer.start();

    String endpointUrl = ignitionContainer.getOpcUaDiscoveryUrl();
    int opcUaPort = ignitionContainer.getMappedOpcUaPort();

    opcUaClient =
        OpcUaClient.create(
            endpointUrl,
            endpoints ->
                endpoints.stream()
                    .filter(
                        endpoint ->
                            Objects.equals(
                                endpoint.getSecurityPolicyUri(), SecurityPolicy.None.getUri()))
                    .findFirst()
                    .map(
                        endpoint ->
                            EndpointUtil.updateUrl(
                                endpoint, ignitionContainer.getHost(), opcUaPort)),
            OpcTcpClientTransportConfigBuilder::build,
            builder ->
                builder.setIdentityProvider(new UsernameProvider("opcuauser", "password")).build());
    opcUaClient.connect();

    modbusClient =
        ModbusTcpClient.create(
            NettyTcpClientTransport.create(
                config -> {
                  config.hostname = ignitionContainer.getHost();
                  config.port = ignitionContainer.getMappedPort(IgnitionTestSupport.MODBUS_PORT);
                }));
    modbusClient.connect();
  }

  @AfterAll
  void tearDown() throws Exception {
    try {
      if (opcUaClient != null) {
        opcUaClient.disconnect();
      }
    } finally {
      try {
        if (modbusClient != null) {
          modbusClient.disconnect();
        }
      } finally {
        if (ignitionContainer != null) {
          ignitionContainer.stop();
        }
      }
    }
  }

  @Nested
  class ValueShapeAndMetadata {

    // Every supported register type and rank must survive the OPC UA-to-Modbus conversion without
    // changing its Java element type or declared shape.
    @ParameterizedTest(name = "{0}")
    @MethodSource("registerArrayCases")
    void registerArrayValuesRoundTripForEverySupportedAreaTypeAndRank(
        RegisterArrayCase testCase) throws Exception {

      NodeId nodeId = nodeId(testCase.address());

      assertGood(writeValue(nodeId, testCase.value()));
      assertValueShape(
          readValue(nodeId).getValue().getValue(),
          testCase.expectedElements(),
          testCase.dimensions());
    }

    // OPC UA Part 3 §5.6.2 requires DataType, ValueRank, and ArrayDimensions to describe a
    // Variable's value consistently. The fixtures cover every data type, rank, and Modbus area
    // without repeating the full Cartesian product already exercised by the value tests.
    @ParameterizedTest(name = "{0}")
    @MethodSource("arrayMetadataCases")
    void arrayNodesExposeTheirDeclaredMetadata(ArrayMetadataCase testCase) throws Exception {

      assertArrayMetadata(
          nodeId(testCase.address()), testCase.dataType(), testCase.dimensions());
    }

    // Boolean arrays use a bit-oriented Modbus representation but must retain their OPC UA rank
    // and dimensions for both writable and read-only Modbus areas.
    @ParameterizedTest(name = "{0}")
    @MethodSource("booleanArrayCases")
    void booleanArrayValuesRoundTripForEveryAreaAndRank(BooleanArrayCase testCase)
        throws Exception {

      NodeId nodeId = nodeId(testCase.address());

      assertGood(writeValue(nodeId, testCase.value()));
      assertValueShape(
          readValue(nodeId).getValue().getValue(),
          testCase.expectedElements(),
          testCase.dimensions());
    }

    static Stream<RegisterArrayCase> registerArrayCases() {
      return ArrayModbusToOpcUaIT.registerArrayCases();
    }

    static Stream<BooleanArrayCase> booleanArrayCases() {
      return ArrayModbusToOpcUaIT.booleanArrayCases();
    }

    static Stream<ArrayMetadataCase> arrayMetadataCases() {
      return ArrayModbusToOpcUaIT.arrayMetadataCases();
    }
  }

  @Nested
  class ModbusInteroperability {

    // Reading through the independent Modbus client prevents a matching encoder/decoder defect
    // from making an OPC UA-only round trip pass.
    @ParameterizedTest(name = "{0}")
    @MethodSource("registerArrayCases")
    void opcUaRegisterArrayWritesPreserveModbusWireEncoding(RegisterArrayCase testCase)
        throws Exception {

      assertGood(writeValue(nodeId(testCase.address()), testCase.value()));

      assertArrayEquals(
          testCase.expectedRegisters(),
          readRegisters(
              testCase.address().substring(0, 2),
              ARRAY_OFFSET,
              testCase.expectedRegisters().length / 2));
    }

    // Boolean array elements must occupy consecutive Modbus bits in OPC UA array order.
    @ParameterizedTest(name = "{0}")
    @MethodSource("booleanArrayCases")
    void opcUaBooleanArrayWritesPreserveModbusBitEncoding(BooleanArrayCase testCase)
        throws Exception {

      assertGood(writeValue(nodeId(testCase.address()), testCase.value()));

      assertArrayEquals(
          testCase.expectedBits(),
          readBits(testCase.address(), testCase.expectedElements().length));
    }

    // Byte- and word-order modifiers are defined per scalar value, so every array element must be
    // transformed independently rather than treating the array as one byte sequence.
    @ParameterizedTest(name = "{0}")
    @MethodSource("registerModifierCases")
    void registerArrayModifiersTransformEveryWireElement(RegisterModifierCase testCase)
        throws Exception {

      assertGood(writeValue(nodeId(testCase.address()), testCase.value()));

      assertArrayEquals(
          testCase.expectedRegisters(),
          readRegisters(
              testCase.address().substring(0, 2),
              ARRAY_OFFSET,
              testCase.expectedRegisters().length / 2));
    }

    // Applying an address modifier on write and its inverse on read must preserve each original
    // array value exposed to OPC UA clients.
    @ParameterizedTest(name = "{0}")
    @MethodSource("registerModifierCases")
    void registerArrayModifiersAreReversibleThroughOpcUa(RegisterModifierCase testCase)
        throws Exception {

      NodeId nodeId = nodeId(testCase.address());

      assertGood(writeValue(nodeId, testCase.value()));
      assertArrayEquals(
          (Object[]) testCase.value(), (Object[]) readValue(nodeId).getValue().getValue());
    }

    // A direct Modbus coil write must be visible as a one-dimensional OPC UA Boolean array rather
    // than as the packed transport byte.
    @Test
    void modbusCoilWritesAreExposedAsOneDimensionalOpcUaArrays() throws Exception {
      Boolean[] expected = {true, false, true, true, false, true};

      modbusClient.writeMultipleCoils(
          0,
          new WriteMultipleCoilsRequest(ARRAY_OFFSET, expected.length, packBooleans(expected)));

      assertArrayEquals(
          expected,
          (Boolean[])
              readValue(nodeId("C<bool[6]>" + ARRAY_OFFSET)).getValue().getValue());
    }

    // A direct Modbus register write must be decoded into the declared OPC UA scalar element type.
    @Test
    void modbusRegisterWritesAreExposedAsOneDimensionalOpcUaArrays() throws Exception {
      byte[] registers = hex("1234 FEDC 0001");

      modbusClient.writeMultipleRegisters(
          0,
          new WriteMultipleRegistersRequest(
              ARRAY_OFFSET, registers.length / 2, registers));

      assertArrayEquals(
          new Short[] {(short) 0x1234, (short) 0xFEDC, (short) 1},
          (Short[])
              readValue(nodeId("HR<int16[3]>" + ARRAY_OFFSET)).getValue().getValue());
    }

    // Multi-dimensional OPC UA values are flattened on the Modbus wire but must be reconstructed
    // with the address-declared dimensions.
    @Test
    void modbusRegisterWritesAreExposedAsOpcUaMatrices() throws Exception {
      byte[] registers = hex("00000001 00000002 00000003 00000004");

      modbusClient.writeMultipleRegisters(
          0,
          new WriteMultipleRegistersRequest(
              ARRAY_OFFSET, registers.length / 2, registers));

      Matrix matrix =
          assertInstanceOf(
              Matrix.class,
              readValue(nodeId("HR<int32[2][2]>" + ARRAY_OFFSET)).getValue().getValue());
      assertNotNull(matrix);
      int[] dimensions = matrix.getDimensions();
      Object elements = matrix.getElements();
      assertNotNull(dimensions);
      assertNotNull(elements);
      assertArrayEquals(new int[] {2, 2}, dimensions);
      assertArrayEquals(new Integer[] {1, 2, 3, 4}, (Integer[]) elements);
    }

    static Stream<RegisterArrayCase> registerArrayCases() {
      return ArrayModbusToOpcUaIT.registerArrayCases();
    }

    static Stream<BooleanArrayCase> booleanArrayCases() {
      return ArrayModbusToOpcUaIT.booleanArrayCases();
    }

    static Stream<RegisterModifierCase> registerModifierCases() {
      return ArrayModbusToOpcUaIT.registerModifierCases();
    }
  }

  @Nested
  class IndexedElements {

    // Array-address indexes select scalar child nodes using OPC UA's row-major array ordering.
    @Test
    void indexedRegisterReadsUseRowMajorOffsets() throws Exception {
      String address = "HR<int32[2][3]>" + ARRAY_OFFSET;
      Integer[] initial = {10, 20, 30, 40, 50, 60};
      assertGood(
          writeValue(
              nodeId(address),
              new Matrix(initial, new int[] {2, 3}, OpcUaDataType.Int32)));

      assertEquals(60, readValue(nodeId(address + "[1][2]")).getValue().getValue());
    }

    // Updating an indexed element must target only its row-major Modbus offset and leave adjacent
    // elements unchanged.
    @Test
    void indexedRegisterWritesUseRowMajorOffsets() throws Exception {
      String address = "HR<int32[2][3]>" + ARRAY_OFFSET;
      Integer[] initial = {10, 20, 30, 40, 50, 60};
      assertGood(
          writeValue(
              nodeId(address),
              new Matrix(initial, new int[] {2, 3}, OpcUaDataType.Int32)));

      assertGood(writeValue(nodeId(address + "[1][2]"), 99));

      Matrix fullValue =
          assertInstanceOf(Matrix.class, readValue(nodeId(address)).getValue().getValue());
      assertNotNull(fullValue);
      Object elements = fullValue.getElements();
      assertNotNull(elements);
      assertArrayEquals(
          new Integer[] {10, 20, 30, 40, 50, 99}, (Integer[]) elements);
      assertArrayEquals(
          hex("00000063"), readRegisters("HR", ARRAY_OFFSET + (5 * 2), 2));
    }

    // OPC UA Part 3 §5.6.2 requires a selected element node to advertise scalar metadata even
    // when its parent Variable is an array or matrix.
    @ParameterizedTest(name = "{0}")
    @MethodSource("indexedMetadataCases")
    void indexedElementNodesExposeScalarMetadata(IndexedMetadataCase testCase)
        throws Exception {

      assertScalarMetadata(nodeId(testCase.address()), testCase.dataType());
    }

    // Bit selection is applied after array indexing, so a write must affect the selected integer
    // element without spilling into the neighboring element.
    @Test
    void indexedIntegerElementsMaySelectAndWriteIndividualBits() throws Exception {
      String address = "HR<int16[2]>" + ARRAY_OFFSET;
      assertGood(writeValue(nodeId(address), new Short[] {0, 0}));
      NodeId bitNode = nodeId(address + "[1].5");

      assertGood(writeValue(bitNode, true));

      assertEquals(Boolean.TRUE, readValue(bitNode).getValue().getValue());
      assertArrayEquals(
          new Short[] {0, 32},
          (Short[]) readValue(nodeId(address)).getValue().getValue());
    }

    // Three-dimensional Boolean indexes must flatten in row-major order before selecting the
    // backing Modbus bit.
    @Test
    void indexedBooleanWritesUseThreeDimensionalRowMajorOffsets() throws Exception {
      String address = "C<bool[2][2][2]>" + ARRAY_OFFSET;
      Boolean[] initial = new Boolean[8];
      Arrays.fill(initial, false);
      assertGood(
          writeValue(
              nodeId(address),
              new Matrix(initial, new int[] {2, 2, 2}, OpcUaDataType.Boolean)));

      NodeId indexedNode = nodeId(address + "[1][0][1]");
      assertGood(writeValue(indexedNode, true));

      assertEquals(Boolean.TRUE, readValue(indexedNode).getValue().getValue());
      assertArrayEquals(new byte[] {0x20}, readBits(address, 8));
    }

    static Stream<IndexedMetadataCase> indexedMetadataCases() {
      return ArrayModbusToOpcUaIT.indexedMetadataCases();
    }
  }

  @Nested
  class NumericRanges {

    // OPC UA Part 4 §5.11.2 and §7.27 require a one-dimensional NumericRange read to return only
    // the selected composite elements in their original order.
    @ParameterizedTest(name = "{0}")
    @MethodSource("oneDimensionalRangeCases")
    void oneDimensionalRangeReadsReturnTheSelectedElements(OneDimensionalRangeCase testCase)
        throws Exception {

      NodeId nodeId = nodeId(testCase.address());
      assertGood(writeValue(nodeId, testCase.initial()));

      Object slice = readValue(nodeId, "2:4").getValue().getValue();

      assertArrayEquals((Object[]) testCase.expectedSlice(), (Object[]) slice);
    }

    // OPC UA Part 4 §5.11.4 and §7.27 require a range write to replace exactly the selected
    // elements while preserving every element outside the range.
    @ParameterizedTest(name = "{0}")
    @MethodSource("oneDimensionalRangeCases")
    void oneDimensionalRangeWritesPreserveUnselectedElements(
        OneDimensionalRangeCase testCase) throws Exception {

      NodeId nodeId = nodeId(testCase.address());
      assertGood(writeValue(nodeId, testCase.initial()));

      assertGood(writeValue(nodeId, "1:2", testCase.update()));

      assertArrayEquals(
          (Object[]) testCase.expectedFullValue(),
          (Object[]) readValue(nodeId).getValue().getValue());
      assertArrayEquals(
          testCase.expectedModbusValue(), readModbusArray(testCase.address(), 6));
    }

    // OPC UA Part 4 §7.27 defines one NumericRange component per matrix dimension; the returned
    // Matrix must retain the dimensions of the selected block.
    @ParameterizedTest(name = "{0}")
    @MethodSource("twoDimensionalRangeCases")
    void twoDimensionalRangeReadsPreserveTheSelectedShape(
        TwoDimensionalRangeCase testCase) throws Exception {

      NodeId nodeId = nodeId(testCase.address());
      assertGood(
          writeValue(
              nodeId,
              new Matrix(testCase.initialElements(), new int[] {3, 3}, testCase.dataType())));

      Matrix slice =
          assertInstanceOf(Matrix.class, readValue(nodeId, "1:2,0:1").getValue().getValue());

      assertNotNull(slice);
      int[] dimensions = slice.getDimensions();
      Object elements = slice.getElements();
      assertNotNull(dimensions);
      assertNotNull(elements);
      assertArrayEquals(new int[] {2, 2}, dimensions);
      assertArrayEquals(
          (Object[]) testCase.expectedSliceElements(), (Object[]) elements);
    }

    // A matrix range write must map each update coordinate into the full row-major matrix without
    // overwriting values outside the selected block.
    @ParameterizedTest(name = "{0}")
    @MethodSource("twoDimensionalRangeCases")
    void twoDimensionalRangeWritesPreserveUnselectedElements(
        TwoDimensionalRangeCase testCase) throws Exception {

      NodeId nodeId = nodeId(testCase.address());
      assertGood(
          writeValue(
              nodeId,
              new Matrix(testCase.initialElements(), new int[] {3, 3}, testCase.dataType())));
      Matrix update =
          new Matrix(testCase.updateElements(), new int[] {2, 2}, testCase.dataType());

      assertGood(writeValue(nodeId, "0:1,1:2", update));

      Matrix fullValue =
          assertInstanceOf(Matrix.class, readValue(nodeId).getValue().getValue());
      assertNotNull(fullValue);
      Object elements = fullValue.getElements();
      assertNotNull(elements);
      assertArrayEquals(
          (Object[]) testCase.expectedFullElements(), (Object[]) elements);
      assertArrayEquals(
          testCase.expectedModbusValue(), readModbusArray(testCase.address(), 9));
    }

    // A three-dimensional NumericRange proves that range coordinates are not accidentally handled
    // as a two-dimensional special case.
    @Test
    void threeDimensionalRangeReadsPreserveTheSelectedShape() throws Exception {
      String address = "IR<int16[2][2][2]>" + ARRAY_OFFSET;
      NodeId nodeId = nodeId(address);
      assertGood(
          writeValue(
              nodeId,
              new Matrix(
                  shorts(0, 1, 2, 3, 4, 5, 6, 7),
                  new int[] {2, 2, 2},
                  OpcUaDataType.Int16)));

      Matrix slice =
          assertInstanceOf(
              Matrix.class, readValue(nodeId, "0:1,1,0:1").getValue().getValue());

      assertNotNull(slice);
      int[] dimensions = slice.getDimensions();
      Object elements = slice.getElements();
      assertNotNull(dimensions);
      assertNotNull(elements);
      assertArrayEquals(new int[] {2, 1, 2}, dimensions);
      assertArrayEquals(shorts(2, 3, 6, 7), (Short[]) elements);
    }

    // A three-dimensional range write must preserve unselected values across every dimension.
    @Test
    void threeDimensionalRangeWritesPreserveUnselectedElements() throws Exception {
      String address = "IR<int16[2][2][2]>" + ARRAY_OFFSET;
      NodeId nodeId = nodeId(address);
      assertGood(
          writeValue(
              nodeId,
              new Matrix(
                  shorts(0, 1, 2, 3, 4, 5, 6, 7),
                  new int[] {2, 2, 2},
                  OpcUaDataType.Int16)));
      Matrix update =
          new Matrix(shorts(50, 70), new int[] {1, 2, 1}, OpcUaDataType.Int16);

      assertGood(writeValue(nodeId, "1,0:1,1", update));

      Matrix fullValue =
          assertInstanceOf(Matrix.class, readValue(nodeId).getValue().getValue());
      assertNotNull(fullValue);
      Object elements = fullValue.getElements();
      assertNotNull(elements);
      assertArrayEquals(
          shorts(0, 1, 2, 3, 4, 50, 6, 70), (Short[]) elements);
    }

    // OPC UA Part 4 §7.27 treats a String NumericRange as a substring selection.
    @ParameterizedTest(name = "{0} scalar String read")
    @MethodSource("registerAreas")
    void scalarStringRangeReadsReturnTheSelectedCharacters(String area) throws Exception {
      NodeId nodeId = nodeId(area + "<string10>" + ARRAY_OFFSET);
      assertGood(writeValue(nodeId, "HELLOWORLD"));

      assertEquals("HELLO", readValue(nodeId, "0:4").getValue().getValue());
    }

    // A substring write replaces only the selected characters of the scalar String.
    @ParameterizedTest(name = "{0} scalar String write")
    @MethodSource("registerAreas")
    void scalarStringRangeWritesPreserveUnselectedCharacters(String area) throws Exception {
      NodeId nodeId = nodeId(area + "<string10>" + ARRAY_OFFSET);
      assertGood(writeValue(nodeId, "HELLOWORLD"));

      assertGood(writeValue(nodeId, "0:4", "WORLD"));

      assertEquals("WORLDWORLD", readValue(nodeId).getValue().getValue());
    }

    // The replacement length must exactly match the selected substring length.
    @ParameterizedTest(name = "{0} scalar String size mismatch")
    @MethodSource("registerAreas")
    void scalarStringRangeWriteLengthMismatchIsRejectedWithoutMutation(String area)
        throws Exception {

      NodeId nodeId = nodeId(area + "<string10>" + ARRAY_OFFSET);
      assertGood(writeValue(nodeId, "HELLOWORLD"));

      assertStatus(
          StatusCodes.Bad_IndexRangeDataMismatch,
          writeValue(nodeId, "0:4", "NO"));
      assertEquals("HELLOWORLD", readValue(nodeId).getValue().getValue());
    }

    // OPC UA Part 4 §7.27 treats String arrays as an additional substring dimension after the
    // array dimensions.
    @ParameterizedTest(name = "{0} String array read")
    @MethodSource("registerAreas")
    void stringArrayRangeReadsSelectElementsAndCharacters(String area) throws Exception {
      NodeId nodeId = nodeId(area + "<string8[4]>" + (ARRAY_OFFSET + 16));
      assertGood(writeValue(nodeId, new String[] {"alpha", "beta", "gamma", "delta"}));

      assertArrayEquals(
          new String[] {"bet"},
          (String[]) readValue(nodeId, "1,0:2").getValue().getValue());
    }

    // A String-array range write must preserve both unselected array elements and unselected
    // characters in the selected element.
    @ParameterizedTest(name = "{0} String array write")
    @MethodSource("registerAreas")
    void stringArrayRangeWritesPreserveUnselectedValuesAndCharacters(String area)
        throws Exception {

      NodeId nodeId = nodeId(area + "<string8[4]>" + (ARRAY_OFFSET + 16));
      assertGood(writeValue(nodeId, new String[] {"alpha", "beta", "gamma", "delta"}));

      assertGood(writeValue(nodeId, "1,0:2", new String[] {"BET"}));

      assertArrayEquals(
          new String[] {"alpha", "BETa", "gamma", "delta"},
          (String[]) readValue(nodeId).getValue().getValue());
    }

    // A range with only the array dimension selects complete String elements, not characters.
    @ParameterizedTest(name = "{0} whole String elements")
    @MethodSource("registerAreas")
    void stringArrayElementRangesReadAndWriteWholeStrings(String area) throws Exception {
      NodeId nodeId = nodeId(area + "<string8[4]>" + (ARRAY_OFFSET + 16));
      assertGood(writeValue(nodeId, new String[] {"alpha", "beta", "gamma", "delta"}));

      assertArrayEquals(
          new String[] {"beta", "gamma"},
          (String[]) readValue(nodeId, "1:2").getValue().getValue());

      assertGood(writeValue(nodeId, "1:2", new String[] {"BETA", "GAMMA"}));
      assertArrayEquals(
          new String[] {"alpha", "BETA", "GAMMA", "delta"},
          (String[]) readValue(nodeId).getValue().getValue());
    }

    // OPC UA Part 4 §7.27 distinguishes malformed NumericRange syntax from a valid range that has
    // no data, and clients depend on that distinction for request correction.
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidReadRangeCases")
    void invalidRangeReadsReturnTheSpecifiedStatus(InvalidRangeCase testCase)
        throws Exception {

      NodeId nodeId = nodeId("HR<int16[6]>" + ARRAY_OFFSET);
      assertGood(writeValue(nodeId, shorts(0, 1, 2, 3, 4, 5)));

      assertStatus(
          testCase.expectedStatus(), readValue(nodeId, testCase.indexRange()).getStatusCode());
    }

    // A rejected range write must be atomic; otherwise an error response could conceal a partial
    // device update.
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidWriteRangeCases")
    void invalidRangeWritesDoNotMutateTheArray(InvalidRangeCase testCase)
        throws Exception {

      NodeId nodeId = nodeId("HR<int16[6]>" + ARRAY_OFFSET);
      Short[] initial = shorts(0, 1, 2, 3, 4, 5);
      assertGood(writeValue(nodeId, initial));

      assertStatus(
          testCase.expectedStatus(),
          writeValue(nodeId, testCase.indexRange(), testCase.writeValue()));

      assertArrayEquals(initial, (Short[]) readValue(nodeId).getValue().getValue());
    }

    // OPC UA Part 4 §7.27 requires a NumericRange component for every array dimension.
    @Test
    void multidimensionalRangesRequireEveryDimensionWithoutMutation() throws Exception {
      NodeId nodeId = nodeId("HR<int16[2][2]>" + ARRAY_OFFSET);
      Short[] initial = shorts(0, 1, 2, 3);
      assertGood(
          writeValue(
              nodeId,
              new Matrix(initial, new int[] {2, 2}, OpcUaDataType.Int16)));

      assertStatus(StatusCodes.Bad_IndexRangeNoData, readValue(nodeId, "1").getStatusCode());
      assertStatus(
          StatusCodes.Bad_IndexRangeNoData,
          writeValue(nodeId, "1", new Short[] {8, 9}));

      Matrix actual = assertInstanceOf(Matrix.class, readValue(nodeId).getValue().getValue());
      assertArrayEquals(initial, (Short[]) actual.getElements());
    }

    // NumericRange applies only to indexed values; a scalar read must report that no indexed data
    // exists instead of silently ignoring the range.
    @Test
    void scalarRangeReadsReturnBadIndexRangeNoData() throws Exception {
      NodeId nodeId = nodeId("HR<int16>" + (ARRAY_OFFSET + 20));
      assertGood(writeValue(nodeId, (short) 7));

      assertStatus(StatusCodes.Bad_IndexRangeNoData, readValue(nodeId, "0").getStatusCode());
    }

    // A rejected scalar range write must leave the scalar value unchanged.
    @Test
    void scalarRangeWritesReturnBadIndexRangeNoDataWithoutMutation() throws Exception {
      NodeId nodeId = nodeId("HR<int16>" + (ARRAY_OFFSET + 20));
      assertGood(writeValue(nodeId, (short) 7));

      assertStatus(StatusCodes.Bad_IndexRangeNoData, writeValue(nodeId, "0", (short) 8));

      assertEquals((short) 7, readValue(nodeId).getValue().getValue());
    }

    // OPC UA Part 4 §5.11.2 returns one result per requested node; processing a ranged item must
    // not skip later items in the batch.
    @Test
    void rangedItemsDoNotShortCircuitReadBatches() throws Exception {
      NodeId first = nodeId("HR<int16>" + ARRAY_OFFSET);
      NodeId ranged = nodeId("HR<int16[4]>" + (ARRAY_OFFSET + 10));
      NodeId last = nodeId("C" + (ARRAY_OFFSET + 20));
      assertGood(writeValue(first, (short) 11));
      assertGood(writeValue(ranged, shorts(1, 2, 3, 4)));
      assertGood(writeValue(last, true));

      DataValue[] results =
          opcUaClient
              .read(
                  0.0,
                  TimestampsToReturn.Both,
                  List.of(
                      readValueId(first, AttributeId.Value, ""),
                      readValueId(ranged, AttributeId.Value, "1:2"),
                      readValueId(last, AttributeId.Value, "")))
              .getResults();

      assertNotNull(results);
      assertEquals(3, results.length);
      assertTrue(Arrays.stream(results).allMatch(value -> value.getStatusCode().isGood()));
      assertArrayEquals(shorts(2, 3), (Short[]) results[1].getValue().getValue());
    }

    // OPC UA Part 4 §5.11.4 returns one result per requested write; a ranged write must not skip
    // the scalar writes around it.
    @Test
    void rangedItemsDoNotShortCircuitWriteBatches() throws Exception {
      NodeId first = nodeId("HR<int16>" + ARRAY_OFFSET);
      NodeId ranged = nodeId("HR<int16[4]>" + (ARRAY_OFFSET + 10));
      NodeId last = nodeId("C" + (ARRAY_OFFSET + 20));
      assertGood(writeValue(first, (short) 11));
      assertGood(writeValue(ranged, shorts(1, 2, 3, 4)));
      assertGood(writeValue(last, true));

      StatusCode[] results =
          opcUaClient
              .write(
                  List.of(
                      writeRequest(first, "", (short) 12),
                      writeRequest(ranged, "1:2", shorts(20, 30)),
                      writeRequest(last, "", false)))
              .getResults();

      assertNotNull(results);
      assertEquals(3, results.length);
      assertTrue(Arrays.stream(results).allMatch(StatusCode::isGood));
      assertEquals((short) 12, readValue(first).getValue().getValue());
      assertArrayEquals(
          shorts(1, 20, 30, 4), (Short[]) readValue(ranged).getValue().getValue());
      assertEquals(Boolean.FALSE, readValue(last).getValue().getValue());
    }

    static Stream<OneDimensionalRangeCase> oneDimensionalRangeCases() {
      return ArrayModbusToOpcUaIT.oneDimensionalRangeCases();
    }

    static Stream<TwoDimensionalRangeCase> twoDimensionalRangeCases() {
      return ArrayModbusToOpcUaIT.twoDimensionalRangeCases();
    }

    static Stream<String> registerAreas() {
      return ArrayModbusToOpcUaIT.registerAreas();
    }

    static Stream<InvalidRangeCase> invalidReadRangeCases() {
      return ArrayModbusToOpcUaIT.invalidReadRangeCases();
    }

    static Stream<InvalidRangeCase> invalidWriteRangeCases() {
      return ArrayModbusToOpcUaIT.invalidWriteRangeCases();
    }
  }

  @Nested
  class ValidationAndBoundaries {

    // Fixed-length OPC UA array Variables reject a shorter value so an incomplete device update
    // cannot be reported as successful.
    @Test
    void oneDimensionalArrayLengthMismatchesAreRejectedWithoutMutation() throws Exception {
      NodeId nodeId = nodeId("HR<int16[2]>" + ARRAY_OFFSET);
      Short[] initial = shorts(11, 22);
      assertGood(writeValue(nodeId, initial));

      assertStatus(StatusCodes.Bad_TypeMismatch, writeValue(nodeId, new Short[] {1}));

      assertArrayEquals(initial, (Short[]) readValue(nodeId).getValue().getValue());
    }

    // OPC UA Part 4 §5.11.4 requires Bad_TypeMismatch when the written element type does not match
    // the Variable's DataType, and the failed write must not change device state.
    @Test
    void oneDimensionalArrayElementTypeMismatchesAreRejectedWithoutMutation() throws Exception {
      NodeId nodeId = nodeId("HR<int16[2]>" + ARRAY_OFFSET);
      Short[] initial = shorts(11, 22);
      assertGood(writeValue(nodeId, initial));

      assertStatus(StatusCodes.Bad_TypeMismatch, writeValue(nodeId, new Integer[] {1, 2}));

      assertArrayEquals(initial, (Short[]) readValue(nodeId).getValue().getValue());
    }

    // A multi-dimensional Variable requires Matrix shape information; accepting a flat array would
    // lose the rank contract advertised by ValueRank and ArrayDimensions.
    @Test
    void matrixWritesRejectFlatArraysWithoutMutation() throws Exception {
      NodeId nodeId = nodeId("HR<int16[2][2]>" + (ARRAY_OFFSET + 10));
      Matrix initial =
          new Matrix(shorts(1, 2, 3, 4), new int[] {2, 2}, OpcUaDataType.Int16);
      assertGood(writeValue(nodeId, initial));

      assertStatus(StatusCodes.Bad_TypeMismatch, writeValue(nodeId, shorts(1, 2, 3, 4)));

      Matrix actual =
          assertInstanceOf(Matrix.class, readValue(nodeId).getValue().getValue());
      assertNotNull(actual);
      Object actualElements = actual.getElements();
      assertNotNull(actualElements);
      assertArrayEquals(shorts(1, 2, 3, 4), (Short[]) actualElements);
    }

    // Matrix dimensions are part of the Variable's type contract even when the flattened element
    // count matches, so a reshaped payload must be rejected atomically.
    @Test
    void matrixWritesRejectDifferentDimensionsWithoutMutation() throws Exception {
      NodeId nodeId = nodeId("HR<int16[2][2]>" + (ARRAY_OFFSET + 10));
      Matrix initial =
          new Matrix(shorts(1, 2, 3, 4), new int[] {2, 2}, OpcUaDataType.Int16);
      assertGood(writeValue(nodeId, initial));

      Matrix reshaped =
          new Matrix(shorts(1, 2, 3, 4), new int[] {1, 4}, OpcUaDataType.Int16);
      assertStatus(StatusCodes.Bad_TypeMismatch, writeValue(nodeId, reshaped));

      Matrix actual =
          assertInstanceOf(Matrix.class, readValue(nodeId).getValue().getValue());
      assertNotNull(actual);
      Object actualElements = actual.getElements();
      assertNotNull(actualElements);
      assertArrayEquals(shorts(1, 2, 3, 4), (Short[]) actualElements);
    }

    // OPC UA Part 4 §5.11.2 requires Bad_NodeIdUnknown for syntactically addressable nodes that the
    // server cannot expose safely.
    @ParameterizedTest(name = "invalid array read {0}")
    @MethodSource("invalidArrayAddresses")
    void invalidArrayAddressesAreNotReadableNodes(String address) throws Exception {
      assertStatus(StatusCodes.Bad_NodeIdUnknown, readValue(nodeId(address)).getStatusCode());
    }

    // OPC UA Part 4 §5.11.4 applies the same unknown-node contract to writes, preventing invalid
    // array extents or modifiers from reaching the Modbus server.
    @ParameterizedTest(name = "invalid array write {0}")
    @MethodSource("invalidArrayAddresses")
    void invalidArrayAddressesAreNotWritableNodes(String address) throws Exception {
      assertStatus(StatusCodes.Bad_NodeIdUnknown, writeValue(nodeId(address), (short) 1));
    }

    // An array ending exactly at register 65535 is valid; rejecting it would be an off-by-one loss
    // of the final legal Modbus address.
    @Test
    void registerArraysMayEndAtTheAddressSpaceBoundary() throws Exception {
      DataValue value = readValue(nodeId("HR<int16[36]>65500"));
      Short[] expected = new Short[36];
      Arrays.fill(expected, (short) 0);

      assertGood(value.getStatusCode());
      assertArrayEquals(expected, (Short[]) value.getValue().getValue());
    }

    // A one-element Boolean array beginning at coil 65535 proves the inclusive boundary for bit
    // areas independently of register width calculations.
    @Test
    void booleanArraysMayEndAtTheAddressSpaceBoundary() throws Exception {
      DataValue value = readValue(nodeId("C<bool[1]>65535"));

      assertGood(value.getStatusCode());
      assertArrayEquals(new Boolean[] {false}, (Boolean[]) value.getValue().getValue());
    }

    static Stream<String> invalidArrayAddresses() {
      return ArrayModbusToOpcUaIT.invalidArrayAddresses();
    }
  }

  private DataValue readValue(NodeId nodeId) throws Exception {
    return readValue(nodeId, "");
  }

  private DataValue readValue(NodeId nodeId, String indexRange) throws Exception {
    DataValue[] results =
        opcUaClient
            .read(
                0.0,
                TimestampsToReturn.Both,
                List.of(readValueId(nodeId, AttributeId.Value, indexRange)))
            .getResults();
    assertNotNull(results);
    assertEquals(1, results.length);
    assertNotNull(results[0]);
    return results[0];
  }

  private DataValue readAttribute(NodeId nodeId, AttributeId attributeId) throws Exception {
    DataValue[] results =
        opcUaClient
            .read(
                0.0,
                TimestampsToReturn.Neither,
                List.of(readValueId(nodeId, attributeId, "")))
            .getResults();
    assertNotNull(results);
    assertEquals(1, results.length);
    assertNotNull(results[0]);
    return results[0];
  }

  private StatusCode writeValue(NodeId nodeId, Object value) throws Exception {
    return writeValue(nodeId, "", value);
  }

  private StatusCode writeValue(NodeId nodeId, String indexRange, Object value) throws Exception {
    StatusCode[] results =
        opcUaClient.write(List.of(writeRequest(nodeId, indexRange, value))).getResults();
    assertNotNull(results);
    assertEquals(1, results.length);
    assertNotNull(results[0]);
    return results[0];
  }

  private static ReadValueId readValueId(
      NodeId nodeId, AttributeId attributeId, String indexRange) {
    return new ReadValueId(nodeId, attributeId.uid(), indexRange, QualifiedName.NULL_VALUE);
  }

  private static WriteValue writeRequest(NodeId nodeId, String indexRange, Object value) {
    return new WriteValue(
        nodeId,
        AttributeId.Value.uid(),
        indexRange,
        DataValue.valueOnly(new Variant(value)));
  }

  private static NodeId nodeId(String address) {
    return NodeId.parse("ns=1;s=[modbus-server]" + address);
  }

  private void assertArrayMetadata(
      NodeId nodeId, OpcUaDataType dataType, int[] dimensions) throws Exception {
    assertEquals(
        dataType.getNodeId(),
        readAttribute(nodeId, AttributeId.DataType).getValue().getValue());
    assertEquals(
        dimensions.length, readAttribute(nodeId, AttributeId.ValueRank).getValue().getValue());

    UInteger[] expectedDimensions =
        Arrays.stream(dimensions).mapToObj(Unsigned::uint).toArray(UInteger[]::new);
    assertArrayEquals(
        expectedDimensions,
        (UInteger[])
            readAttribute(nodeId, AttributeId.ArrayDimensions).getValue().getValue());
  }

  private void assertScalarMetadata(NodeId nodeId, OpcUaDataType dataType) throws Exception {
    assertEquals(
        dataType.getNodeId(),
        readAttribute(nodeId, AttributeId.DataType).getValue().getValue());
    assertEquals(-1, readAttribute(nodeId, AttributeId.ValueRank).getValue().getValue());
    assertNull(readAttribute(nodeId, AttributeId.ArrayDimensions).getValue().getValue());
  }

  private static void assertValueShape(Object actual, Object expectedElements, int[] dimensions) {
    if (dimensions.length == 1) {
      assertEquals(expectedElements.getClass(), actual.getClass());
      assertArrayEquals((Object[]) expectedElements, (Object[]) actual);
    } else {
      Matrix matrix = assertInstanceOf(Matrix.class, actual);
      assertNotNull(matrix);
      int[] actualDimensions = matrix.getDimensions();
      Object actualElements = matrix.getElements();
      assertNotNull(actualDimensions);
      assertNotNull(actualElements);
      assertArrayEquals(dimensions, actualDimensions);
      assertEquals(expectedElements.getClass(), actualElements.getClass());
      assertArrayEquals((Object[]) expectedElements, (Object[]) actualElements);
    }
  }

  private static void assertGood(StatusCode statusCode) {
    assertEquals(StatusCode.GOOD, statusCode);
  }

  private static void assertStatus(long expected, StatusCode actual) {
    assertEquals(expected, actual.getValue());
  }

  private byte[] readRegisters(String area, int offset, int quantity) throws Exception {
    if (area.equals("HR")) {
      return modbusClient
          .readHoldingRegisters(0, new ReadHoldingRegistersRequest(offset, quantity))
          .registers();
    }
    if (area.equals("IR")) {
      return modbusClient
          .readInputRegisters(0, new ReadInputRegistersRequest(offset, quantity))
          .registers();
    }
    throw new IllegalArgumentException("register area: " + area);
  }

  private byte[] readBits(String address, int quantity) throws Exception {
    if (address.startsWith("C")) {
      return modbusClient.readCoils(0, new ReadCoilsRequest(ARRAY_OFFSET, quantity)).coils();
    }
    if (address.startsWith("DI")) {
      return modbusClient
          .readDiscreteInputs(0, new ReadDiscreteInputsRequest(ARRAY_OFFSET, quantity))
          .inputs();
    }
    throw new IllegalArgumentException("Boolean address: " + address);
  }

  private byte[] readModbusArray(String address, int elementCount) throws Exception {
    if (address.startsWith("C") || address.startsWith("DI")) {
      return readBits(address, elementCount);
    }
    return readRegisters(address.substring(0, 2), ARRAY_OFFSET, elementCount);
  }

  private static Stream<RegisterArrayCase> registerArrayCases() {
    int[][] dimensions = {{2}, {2, 2}, {2, 1, 2}};
    List<RegisterArrayCase> cases = new ArrayList<>();

    for (String area : List.of("HR", "IR")) {
      for (RegisterType type : registerTypes()) {
        for (int[] shape : dimensions) {
          int elementCount = Arrays.stream(shape).reduce(1, Math::multiplyExact);
          Object elements = repeatElements(type.seedElements(), elementCount);
          Object value =
              shape.length == 1
                  ? elements
                  : new Matrix(elements, shape, type.dataType());
          byte[] registers = repeatBytes(type.encodedSeed(), elementCount / 2);
          String address =
              area + "<" + type.syntax() + formatDimensions(shape) + ">" + ARRAY_OFFSET;
          String caseName = area + " " + type.syntax() + " rank " + shape.length;

          cases.add(
              new RegisterArrayCase(
                  caseName,
                  address,
                  type.dataType(),
                  shape,
                  value,
                  elements,
                  registers));
        }
      }
    }

    return cases.stream();
  }

  private static List<RegisterType> registerTypes() {
    return List.of(
        new RegisterType(
            "int16",
            OpcUaDataType.Int16,
            shorts(0x1234, (short) 0xFEDC),
            hex("1234 FEDC")),
        new RegisterType(
            "uint16",
            OpcUaDataType.UInt16,
            new UShort[] {ushort(0x1234), ushort(0xFEDC)},
            hex("1234 FEDC")),
        new RegisterType(
            "int32",
            OpcUaDataType.Int32,
            new Integer[] {0x12345678, 0x90ABCDEF},
            hex("12345678 90ABCDEF")),
        new RegisterType(
            "uint32",
            OpcUaDataType.UInt32,
            new UInteger[] {uint(0x12345678L), uint(0x90ABCDEFL)},
            hex("12345678 90ABCDEF")),
        new RegisterType(
            "int64",
            OpcUaDataType.Int64,
            new Long[] {0x0123456789ABCDEFL, 0xFEDCBA9876543210L},
            hex("0123456789ABCDEF FEDCBA9876543210")),
        new RegisterType(
            "uint64",
            OpcUaDataType.UInt64,
            new ULong[] {
              ulong(0x0123456789ABCDEFL), ulong(0xFEDCBA9876543210L)
            },
            hex("0123456789ABCDEF FEDCBA9876543210")),
        new RegisterType(
            "float",
            OpcUaDataType.Float,
            new Float[] {1.25f, -2.5f},
            hex("3FA00000 C0200000")),
        new RegisterType(
            "double",
            OpcUaDataType.Double,
            new Double[] {1.25, -2.5},
            hex("3FF4000000000000 C004000000000000")),
        new RegisterType(
            "string8",
            OpcUaDataType.String,
            new String[] {"ABCD", "WXYZ"},
            hex("4142434400000000 5758595A00000000")));
  }

  private static Stream<ArrayMetadataCase> arrayMetadataCases() {
    return Stream.of(
        metadataCase("HR int16 rank 1", "HR", "int16", OpcUaDataType.Int16, 2),
        metadataCase("IR uint16 rank 2", "IR", "uint16", OpcUaDataType.UInt16, 2, 2),
        metadataCase("HR int32 rank 3", "HR", "int32", OpcUaDataType.Int32, 2, 1, 2),
        metadataCase("IR uint32 rank 1", "IR", "uint32", OpcUaDataType.UInt32, 2),
        metadataCase("HR int64 rank 2", "HR", "int64", OpcUaDataType.Int64, 2, 2),
        metadataCase("IR uint64 rank 3", "IR", "uint64", OpcUaDataType.UInt64, 2, 1, 2),
        metadataCase("HR float rank 1", "HR", "float", OpcUaDataType.Float, 2),
        metadataCase("IR double rank 2", "IR", "double", OpcUaDataType.Double, 2, 2),
        metadataCase("HR string rank 3", "HR", "string8", OpcUaDataType.String, 2, 1, 2),
        metadataCase("C Boolean rank 1", "C", "bool", OpcUaDataType.Boolean, 2),
        metadataCase("DI Boolean rank 2", "DI", "bool", OpcUaDataType.Boolean, 2, 2),
        metadataCase("C Boolean rank 3", "C", "bool", OpcUaDataType.Boolean, 2, 1, 2));
  }

  private static ArrayMetadataCase metadataCase(
      String name,
      String area,
      String syntax,
      OpcUaDataType dataType,
      int... dimensions) {

    String address = area + "<" + syntax + formatDimensions(dimensions) + ">" + ARRAY_OFFSET;
    return new ArrayMetadataCase(name, address, dataType, dimensions);
  }

  private static Stream<BooleanArrayCase> booleanArrayCases() {
    int[][] dimensions = {{2}, {2, 2}, {2, 1, 2}};
    List<BooleanArrayCase> cases = new ArrayList<>();

    for (String area : List.of("C", "DI")) {
      for (int[] shape : dimensions) {
        int elementCount = Arrays.stream(shape).reduce(1, Math::multiplyExact);
        Boolean[] elements =
            (Boolean[]) repeatElements(new Boolean[] {true, false}, elementCount);
        Object value =
            shape.length == 1
                ? elements
                : new Matrix(elements, shape, OpcUaDataType.Boolean);
        String address = area + "<bool" + formatDimensions(shape) + ">" + ARRAY_OFFSET;
        cases.add(
            new BooleanArrayCase(
                area + " bool rank " + shape.length,
                address,
                shape,
                value,
                elements,
                packBooleans(elements)));
      }
    }

    return cases.stream();
  }

  private static Stream<RegisterModifierCase> registerModifierCases() {
    Integer[] values = {0x12345678, 0x90ABCDEF};
    return Stream.of(
        new RegisterModifierCase(
            "HR @LE", "HR<int32[2]@LE>" + ARRAY_OFFSET, values, hex("78563412 EFCDAB90")),
        new RegisterModifierCase(
            "HR @LH", "HR<int32[2]@LH>" + ARRAY_OFFSET, values, hex("56781234 CDEF90AB")),
        new RegisterModifierCase(
            "HR @LE@LH",
            "HR<int32[2]@LE@LH>" + ARRAY_OFFSET,
            values,
            hex("34127856 AB90EFCD")),
        new RegisterModifierCase(
            "IR @LE", "IR<int32[2]@LE>" + ARRAY_OFFSET, values, hex("78563412 EFCDAB90")),
        new RegisterModifierCase(
            "IR @LH", "IR<int32[2]@LH>" + ARRAY_OFFSET, values, hex("56781234 CDEF90AB")),
        new RegisterModifierCase(
            "IR @LE@LH",
            "IR<int32[2]@LE@LH>" + ARRAY_OFFSET,
            values,
            hex("34127856 AB90EFCD")));
  }

  private static Stream<IndexedMetadataCase> indexedMetadataCases() {
    return Stream.of(
        new IndexedMetadataCase(
            "indexed register element",
            "HR<int32[2][3]>" + ARRAY_OFFSET + "[1][2]",
            OpcUaDataType.Int32),
        new IndexedMetadataCase(
            "indexed Boolean element",
            "C<bool[2][2][2]>" + ARRAY_OFFSET + "[1][0][1]",
            OpcUaDataType.Boolean));
  }

  private static Stream<OneDimensionalRangeCase> oneDimensionalRangeCases() {
    Boolean[] booleans = {true, false, true, false, true, false};
    Boolean[] booleanSlice = {true, false, true};
    Boolean[] booleanUpdate = {true, true};
    Boolean[] expectedBooleans = {true, true, true, false, true, false};
    Short[] registers = shorts(0, 1, 2, 3, 4, 5);
    Short[] registerSlice = shorts(2, 3, 4);
    Short[] registerUpdate = shorts(20, 30);
    Short[] expectedRegisters = shorts(0, 20, 30, 3, 4, 5);

    return Stream.of(
        new OneDimensionalRangeCase(
            "C range",
            "C<bool[6]>" + ARRAY_OFFSET,
            booleans,
            booleanSlice,
            booleanUpdate,
            expectedBooleans,
            packBooleans(expectedBooleans)),
        new OneDimensionalRangeCase(
            "DI range",
            "DI<bool[6]>" + ARRAY_OFFSET,
            booleans,
            booleanSlice,
            booleanUpdate,
            expectedBooleans,
            packBooleans(expectedBooleans)),
        new OneDimensionalRangeCase(
            "HR range",
            "HR<int16[6]>" + ARRAY_OFFSET,
            registers,
            registerSlice,
            registerUpdate,
            expectedRegisters,
            hex("0000 0014 001E 0003 0004 0005")),
        new OneDimensionalRangeCase(
            "IR range",
            "IR<int16[6]>" + ARRAY_OFFSET,
            registers,
            registerSlice,
            registerUpdate,
            expectedRegisters,
            hex("0000 0014 001E 0003 0004 0005")));
  }

  private static Stream<TwoDimensionalRangeCase> twoDimensionalRangeCases() {
    Boolean[] booleans = {true, false, true, false, true, false, true, false, true};
    Boolean[] booleanSlice = {false, true, true, false};
    Boolean[] booleanUpdate = {true, true, false, false};
    Boolean[] expectedBooleans = {true, true, true, false, false, false, true, false, true};
    Short[] registers = shorts(0, 1, 2, 3, 4, 5, 6, 7, 8);
    Short[] registerSlice = shorts(3, 4, 6, 7);
    Short[] registerUpdate = shorts(20, 21, 30, 31);
    Short[] expectedRegisters = shorts(0, 20, 21, 3, 30, 31, 6, 7, 8);

    return Stream.of(
        new TwoDimensionalRangeCase(
            "C matrix range",
            "C<bool[3][3]>" + ARRAY_OFFSET,
            booleans,
            OpcUaDataType.Boolean,
            booleanSlice,
            booleanUpdate,
            expectedBooleans,
            packBooleans(expectedBooleans)),
        new TwoDimensionalRangeCase(
            "DI matrix range",
            "DI<bool[3][3]>" + ARRAY_OFFSET,
            booleans,
            OpcUaDataType.Boolean,
            booleanSlice,
            booleanUpdate,
            expectedBooleans,
            packBooleans(expectedBooleans)),
        new TwoDimensionalRangeCase(
            "HR matrix range",
            "HR<int16[3][3]>" + ARRAY_OFFSET,
            registers,
            OpcUaDataType.Int16,
            registerSlice,
            registerUpdate,
            expectedRegisters,
            hex("0000 0014 0015 0003 001E 001F 0006 0007 0008")),
        new TwoDimensionalRangeCase(
            "IR matrix range",
            "IR<int16[3][3]>" + ARRAY_OFFSET,
            registers,
            OpcUaDataType.Int16,
            registerSlice,
            registerUpdate,
            expectedRegisters,
            hex("0000 0014 0015 0003 001E 001F 0006 0007 0008")));
  }

  private static Stream<InvalidRangeCase> invalidReadRangeCases() {
    return Stream.of(
        new InvalidRangeCase(
            "malformed read range", "1::2", null, StatusCodes.Bad_IndexRangeInvalid),
        new InvalidRangeCase(
            "trailing colon in read range", "1:", null, StatusCodes.Bad_IndexRangeInvalid),
        new InvalidRangeCase(
            "trailing comma in read range", "1,", null, StatusCodes.Bad_IndexRangeInvalid),
        new InvalidRangeCase(
            "out-of-bounds read range", "99", null, StatusCodes.Bad_IndexRangeNoData));
  }

  private static Stream<InvalidRangeCase> invalidWriteRangeCases() {
    return Stream.of(
        new InvalidRangeCase(
            "malformed write range",
            "1::2",
            new Short[] {9, 9},
            StatusCodes.Bad_IndexRangeInvalid),
        new InvalidRangeCase(
            "trailing colon in write range",
            "1:",
            new Short[] {9},
            StatusCodes.Bad_IndexRangeInvalid),
        new InvalidRangeCase(
            "trailing comma in write range",
            "1,",
            new Short[] {9},
            StatusCodes.Bad_IndexRangeInvalid),
        new InvalidRangeCase(
            "write value does not fill range",
            "2:4",
            new Short[] {9, 9},
            StatusCodes.Bad_IndexRangeDataMismatch),
        new InvalidRangeCase(
            "write value has wrong element type",
            "2:3",
            new Integer[] {9, 9},
            StatusCodes.Bad_TypeMismatch));
  }

  private static Stream<String> registerAreas() {
    return Stream.of("HR", "IR");
  }

  private static Stream<String> invalidArrayAddresses() {
    return Stream.of(
        "HR<int16[37]>65500",
        "HR<int64[99999][99999][99999]>0",
        "HR<int16[10]>0.5",
        "HR0[5]",
        "HR<int16[2]>0[2]",
        "HR<int16@LE[2]>0");
  }

  private static Object repeatElements(Object seedElements, int elementCount) {
    int seedLength = Array.getLength(seedElements);
    Object elements =
        Array.newInstance(seedElements.getClass().getComponentType(), elementCount);
    for (int i = 0; i < elementCount; i++) {
      Array.set(elements, i, Array.get(seedElements, i % seedLength));
    }
    return elements;
  }

  private static byte[] repeatBytes(byte[] seed, int repetitions) {
    byte[] bytes = new byte[seed.length * repetitions];
    for (int i = 0; i < repetitions; i++) {
      System.arraycopy(seed, 0, bytes, i * seed.length, seed.length);
    }
    return bytes;
  }

  private static String formatDimensions(int[] dimensions) {
    StringBuilder builder = new StringBuilder();
    for (int dimension : dimensions) {
      builder.append('[').append(dimension).append(']');
    }
    return builder.toString();
  }

  private static byte[] packBooleans(Boolean[] values) {
    byte[] bytes = new byte[(values.length + 7) / 8];
    for (int i = 0; i < values.length; i++) {
      if (values[i]) {
        bytes[i / 8] |= (byte) (1 << (i % 8));
      }
    }
    return bytes;
  }

  private static Short[] shorts(int... values) {
    Short[] shorts = new Short[values.length];
    for (int i = 0; i < values.length; i++) {
      shorts[i] = (short) values[i];
    }
    return shorts;
  }

  private static byte[] hex(String value) {
    return HexFormat.of().parseHex(value.replace(" ", ""));
  }

  private record RegisterArrayCase(
      String name,
      String address,
      OpcUaDataType dataType,
      int[] dimensions,
      Object value,
      Object expectedElements,
      byte[] expectedRegisters) {

    @Override
    @NotNull
    public String toString() {
      return name;
    }
  }

  private record BooleanArrayCase(
      String name,
      String address,
      int[] dimensions,
      Object value,
      Boolean[] expectedElements,
      byte[] expectedBits) {

    @Override
    @NotNull
    public String toString() {
      return name;
    }
  }

  private record ArrayMetadataCase(
      String name, String address, OpcUaDataType dataType, int[] dimensions) {

    @Override
    @NotNull
    public String toString() {
      return name;
    }
  }

  private record RegisterModifierCase(
      String name, String address, Object value, byte[] expectedRegisters) {

    @Override
    @NotNull
    public String toString() {
      return name;
    }
  }

  private record IndexedMetadataCase(String name, String address, OpcUaDataType dataType) {

    @Override
    @NotNull
    public String toString() {
      return name;
    }
  }

  private record OneDimensionalRangeCase(
      String name,
      String address,
      Object initial,
      Object expectedSlice,
      Object update,
      Object expectedFullValue,
      byte[] expectedModbusValue) {

    @Override
    @NotNull
    public String toString() {
      return name;
    }
  }

  private record TwoDimensionalRangeCase(
      String name,
      String address,
      Object initialElements,
      OpcUaDataType dataType,
      Object expectedSliceElements,
      Object updateElements,
      Object expectedFullElements,
      byte[] expectedModbusValue) {

    @Override
    @NotNull
    public String toString() {
      return name;
    }
  }

  private record InvalidRangeCase(
      String name, String indexRange, Object writeValue, long expectedStatus) {

    @Override
    @NotNull
    public String toString() {
      return name;
    }
  }

  private record RegisterType(
      String syntax, OpcUaDataType dataType, Object seedElements, byte[] encodedSeed) {}
}
