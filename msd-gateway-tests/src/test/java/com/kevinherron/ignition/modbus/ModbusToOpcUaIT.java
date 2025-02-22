package com.kevinherron.ignition.modbus;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ulong;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ushort;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.digitalpetri.modbus.client.ModbusClient;
import com.digitalpetri.modbus.client.ModbusTcpClient;
import com.digitalpetri.modbus.internal.util.Hex;
import com.digitalpetri.modbus.pdu.ReadHoldingRegistersRequest;
import com.digitalpetri.modbus.pdu.ReadHoldingRegistersResponse;
import com.digitalpetri.modbus.pdu.ReadInputRegistersRequest;
import com.digitalpetri.modbus.pdu.ReadInputRegistersResponse;
import com.digitalpetri.modbus.pdu.WriteMultipleCoilsRequest;
import com.digitalpetri.modbus.pdu.WriteMultipleRegistersRequest;
import com.digitalpetri.modbus.pdu.WriteSingleCoilRequest;
import com.digitalpetri.modbus.pdu.WriteSingleRegisterRequest;
import com.digitalpetri.modbus.tcp.client.NettyTcpClientTransport;
import com.mussonindustrial.testcontainers.ignition.GatewayEdition;
import com.mussonindustrial.testcontainers.ignition.IgnitionContainer;
import com.mussonindustrial.testcontainers.ignition.Module;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.stream.Stream;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.OpcUaClientConfigBuilder;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.util.EndpointUtil;
import org.eclipse.milo.opcua.stack.transport.client.tcp.OpcTcpClientTransportConfigBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

// TODO refactor and re-enable when there is Ignition 8.3 support
@Disabled
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ModbusToOpcUaIT {

  IgnitionContainer ignitionContainer;
  OpcUaClient opcUaClient;
  ModbusClient modbusClient;

  @BeforeAll
  void setUpContainer() throws Exception {
    ignitionContainer =
        new IgnitionContainer("inductiveautomation/ignition:8.1.43")
            .withCredentials("admin", "password")
            .withEdition(GatewayEdition.STANDARD)
            .withGatewayBackup("./src/test/resources/ignition.gwbk", false)
            .withModules(Module.OPC_UA)
            .withThirdPartyModules("../msd-build/target/Modbus-Server-Driver-Module-unsigned.modl")
            .withAdditionalExposedPort(502)
            .withAdditionalExposedPort(62541);

    ignitionContainer.start();

    String endpointUrl =
        "opc.tcp://%s:%d/discovery"
            .formatted(ignitionContainer.getHost(), ignitionContainer.getMappedPort(62541));

    System.out.println("Endpoint URL: " + endpointUrl);

    opcUaClient =
        OpcUaClient.create(
            endpointUrl,
            endpoints ->
                endpoints.stream()
                    .filter(
                        e -> Objects.equals(e.getSecurityPolicyUri(), SecurityPolicy.None.getUri()))
                    .findFirst()
                    .map(
                        e ->
                            EndpointUtil.updateUrl(
                                e,
                                ignitionContainer.getHost(),
                                ignitionContainer.getMappedPort(62541))),
            OpcTcpClientTransportConfigBuilder::build,
            OpcUaClientConfigBuilder::build);

    opcUaClient.connect();

    modbusClient =
        ModbusTcpClient.create(
            NettyTcpClientTransport.create(
                cfg -> {
                  cfg.hostname = ignitionContainer.getHost();
                  cfg.port = ignitionContainer.getMappedPort(502);
                }));

    modbusClient.connect();
  }

  @AfterAll
  void tearDown() throws Exception {
    if (opcUaClient != null) {
      opcUaClient.disconnect();
    }
    if (ignitionContainer != null) {
      ignitionContainer.stop();
    }
    if (modbusClient != null) {
      modbusClient.disconnect();
    }
  }

  @Test
  void writeSingleRegister() throws Exception {
    var randomValues = new ArrayList<Short>();

    var random = new Random();
    for (int i = 0; i < 65536; i++) {
      short randomValue = (short) random.nextInt();

      modbusClient.writeSingleRegister(0, new WriteSingleRegisterRequest(i, randomValue));

      randomValues.add(randomValue);
    }

    for (int i = 0; i < 65536; i++) {
      DataValue value =
          opcUaClient.readValue(
              0.0, TimestampsToReturn.Both, NodeId.parse("ns=1;s=[modbus-server]HR" + i));

      assertEquals(randomValues.get(i), value.getValue().getValue());
    }
  }

  @Test
  void writeMultipleRegister() throws Exception {
    var random = new Random();
    var randomRegisters = new byte[65536 * 2];
    random.nextBytes(randomRegisters);

    int address = 0;
    int remaining = 65536;

    while (remaining > 0) {
      int quantity = Math.min(remaining, random.nextInt(125) + 1);
      byte[] values = new byte[quantity * 2];
      System.arraycopy(randomRegisters, address * 2, values, 0, values.length);

      modbusClient.writeMultipleRegisters(
          0, new WriteMultipleRegistersRequest(address, quantity, values));

      address += quantity;
      remaining -= quantity;
    }

    for (int i = 0; i < 65536; i++) {
      DataValue value =
          opcUaClient.readValue(
              0.0, TimestampsToReturn.Both, NodeId.parse("ns=1;s=[modbus-server]HR" + i));

      int b0 = randomRegisters[i * 2] & 0xFF;
      int b1 = randomRegisters[i * 2 + 1] & 0xFF;
      short expectedValue = (short) ((b0 << 8) | b1);

      assertEquals(expectedValue, value.getValue().getValue());
    }
  }

  @Test
  void writeSingleCoil() throws Exception {
    var randomValues = new ArrayList<Boolean>();

    var random = new Random();
    for (int i = 0; i < 65536; i++) {
      boolean randomValue = random.nextBoolean();

      modbusClient.writeSingleCoil(0, new WriteSingleCoilRequest(i, randomValue));

      randomValues.add(randomValue);
    }

    for (int i = 0; i < 65536; i++) {
      DataValue value =
          opcUaClient.readValue(
              0.0, TimestampsToReturn.Both, NodeId.parse("ns=1;s=[modbus-server]C" + i));

      assertEquals(randomValues.get(i), value.getValue().getValue());
    }
  }

  @Test
  void writeMultipleCoils() throws Exception {
    var random = new Random();
    boolean[] values = new boolean[65536];
    for (int i = 0; i < 65536; i++) {
      values[i] = random.nextBoolean();
    }

    int address = 0;
    int remaining = 65536;
    int quantity = Math.min(remaining - address, random.nextInt(0x7B0) + 1);

    while (remaining > 0) {
      var coils = new byte[(quantity + 7) / 8];
      for (int i = 0; i < quantity; i++) {
        int ci = i / 8;
        int bi = i % 8;
        if (values[address + i]) {
          coils[ci] |= (byte) (1 << bi);
        }
      }

      modbusClient.writeMultipleCoils(0, new WriteMultipleCoilsRequest(address, quantity, coils));

      address += quantity;
      remaining -= quantity;
      quantity = Math.min(remaining, random.nextInt(0x7B0) + 1);
    }

    for (int i = 0; i < 65536; i++) {
      DataValue value =
          opcUaClient.readValue(
              0.0, TimestampsToReturn.Both, NodeId.parse("ns=1;s=[modbus-server]C" + i));

      assertEquals(values[i], value.getValue().getValue());
    }
  }

  @ParameterizedTest
  @MethodSource("writeHoldingRegistersArguments")
  void writeHoldingRegisters(String address, Variant value, byte[] expected) throws Exception {
    System.out.println("address: " + address);
    System.out.println("value: " + value);
    System.out.println("expected: 0x" + Hex.format(expected));

    NodeId nodeId = NodeId.parse("ns=1;s=[modbus-server]%s".formatted(address));

    List<StatusCode> results =
        opcUaClient.writeValues(List.of(nodeId), List.of(DataValue.valueOnly(value)));

    assertEquals(StatusCode.GOOD, results.get(0));

    // read back same value via OPC UA and compare
    DataValue readValue = opcUaClient.readValue(0.0, TimestampsToReturn.Both, nodeId);
    assertEquals(value, readValue.getValue());

    ReadHoldingRegistersResponse response =
        modbusClient.readHoldingRegisters(
            0, new ReadHoldingRegistersRequest(0, expected.length / 2));

    byte[] registers = response.registers();
    System.out.println("registers: 0x" + Hex.format(registers));

    assertArrayEquals(expected, registers);
  }

  @ParameterizedTest
  @MethodSource("writeInputRegistersArguments")
  void writeInputRegisters(String address, Variant value, byte[] expected) throws Exception {
    System.out.println("address: " + address);
    System.out.println("value: " + value);
    System.out.println("expected: 0x" + Hex.format(expected));

    NodeId nodeId = NodeId.parse("ns=1;s=[modbus-server]%s".formatted(address));

    List<StatusCode> results =
        opcUaClient.writeValues(List.of(nodeId), List.of(DataValue.valueOnly(value)));

    assertEquals(StatusCode.GOOD, results.get(0));

    // read back same value via OPC UA and compare
    DataValue readValue = opcUaClient.readValue(0.0, TimestampsToReturn.Both, nodeId);
    assertEquals(value, readValue.getValue());

    ReadInputRegistersResponse response =
        modbusClient.readInputRegisters(0, new ReadInputRegistersRequest(0, expected.length / 2));

    byte[] registers = response.registers();
    System.out.println("registers: 0x" + Hex.format(registers));

    assertArrayEquals(expected, registers);
  }

  @Test
  void writeHoldingRegisterBits() throws Exception {
    for (int i = 0; i < 32; i++) {
      NodeId nodeId = NodeId.parse("ns=1;s=[modbus-server]HR<int32>0.%d".formatted(i));

      List<StatusCode> results =
          opcUaClient.writeValues(
              List.of(nodeId), List.of(DataValue.valueOnly(Variant.ofBoolean(i % 2 == 0))));

      assertEquals(StatusCode.GOOD, results.get(0));
    }

    // read back via OPC UA and compare
    for (int i = 0; i < 32; i++) {
      NodeId nodeId = NodeId.parse("ns=1;s=[modbus-server]HR<int32>0.%d".formatted(i));

      DataValue value = opcUaClient.readValue(0.0, TimestampsToReturn.Both, nodeId);
      assertEquals(Variant.ofBoolean(i % 2 == 0), value.getValue());
    }

    // read back via Modbus and compare
    ReadHoldingRegistersResponse response =
        modbusClient.readHoldingRegisters(0, new ReadHoldingRegistersRequest(0, 2));

    byte[] registers = response.registers();
    System.out.println("registers: 0x" + Hex.format(registers));

    assertArrayEquals(new byte[] {0x55, 0x55, 0x55, 0x55}, registers);
  }

  @Test
  void readInvalidAddress() throws Exception {
    NodeId nodeId = NodeId.parse("ns=1;s=[modbus-server]HR<float32>0");

    DataValue value = opcUaClient.readValue(0.0, TimestampsToReturn.Both, nodeId);
    assertEquals(new StatusCode(StatusCodes.Bad_NodeIdUnknown), value.getStatusCode());
  }

  @Test
  void writeInvalidAddress() throws Exception {
    NodeId nodeId = NodeId.parse("ns=1;s=[modbus-server]HR<float32>0");

    List<StatusCode> results =
        opcUaClient.writeValues(
            List.of(nodeId), List.of(DataValue.valueOnly(Variant.ofFloat(1.234f))));

    assertEquals(new StatusCode(StatusCodes.Bad_NodeIdUnknown), results.get(0));
  }

  private static Stream<Arguments> writeHoldingRegistersArguments() {
    return Stream.of(
        Arguments.of("HR<int16>0", Variant.ofInt16((short) 0x1234), new byte[] {0x12, 0x34}),
        Arguments.of(
            "HR<int32>0", Variant.ofInt32(0x12345678), new byte[] {0x12, 0x34, 0x56, 0x78}),
        Arguments.of(
            "HR<int64>0",
            Variant.ofInt64(0x1234567890ABCDEFL),
            new byte[] {
              0x12, 0x34, 0x56, 0x78, (byte) 0x90, (byte) 0xAB, (byte) 0xCD, (byte) 0xEF
            }),
        Arguments.of("HR<uint16>0", Variant.ofUInt16(ushort(0x1234)), new byte[] {0x12, 0x34}),
        Arguments.of(
            "HR<uint32>0", Variant.ofUInt32(uint(0x12345678)), new byte[] {0x12, 0x34, 0x56, 0x78}),
        Arguments.of(
            "HR<uint64>0",
            Variant.ofUInt64(ulong(0x1234567890ABCDEFL)),
            new byte[] {
              0x12, 0x34, 0x56, 0x78, (byte) 0x90, (byte) 0xAB, (byte) 0xCD, (byte) 0xEF
            }),
        Arguments.of(
            "HR<float>0",
            Variant.ofFloat(1.234f),
            new byte[] {0x3F, (byte) 0x9D, (byte) 0xF3, (byte) 0xB6}),
        Arguments.of(
            "HR<double>0",
            Variant.ofDouble(1.234),
            new byte[] {
              0x3F, (byte) 0xF3, (byte) 0xBE, 0x76, (byte) 0xC8, (byte) 0xB4, 0x39, 0x58
            }));
  }

  private static Stream<Arguments> writeInputRegistersArguments() {
    return Stream.of(
        Arguments.of("IR<int16>0", Variant.ofInt16((short) 0x1234), new byte[] {0x12, 0x34}),
        Arguments.of(
            "IR<int32>0", Variant.ofInt32(0x12345678), new byte[] {0x12, 0x34, 0x56, 0x78}),
        Arguments.of(
            "IR<int64>0",
            Variant.ofInt64(0x1234567890ABCDEFL),
            new byte[] {
              0x12, 0x34, 0x56, 0x78, (byte) 0x90, (byte) 0xAB, (byte) 0xCD, (byte) 0xEF
            }),
        Arguments.of("IR<uint16>0", Variant.ofUInt16(ushort(0x1234)), new byte[] {0x12, 0x34}),
        Arguments.of(
            "IR<uint32>0", Variant.ofUInt32(uint(0x12345678)), new byte[] {0x12, 0x34, 0x56, 0x78}),
        Arguments.of(
            "IR<uint64>0",
            Variant.ofUInt64(ulong(0x1234567890ABCDEFL)),
            new byte[] {
              0x12, 0x34, 0x56, 0x78, (byte) 0x90, (byte) 0xAB, (byte) 0xCD, (byte) 0xEF
            }),
        Arguments.of(
            "IR<float>0",
            Variant.ofFloat(1.234f),
            new byte[] {0x3F, (byte) 0x9D, (byte) 0xF3, (byte) 0xB6}),
        Arguments.of(
            "IR<double>0",
            Variant.ofDouble(1.234),
            new byte[] {
              0x3F, (byte) 0xF3, (byte) 0xBE, 0x76, (byte) 0xC8, (byte) 0xB4, 0x39, 0x58
            }));
  }
}
