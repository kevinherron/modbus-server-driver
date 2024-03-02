package com.kevinherron.ignition.modbus;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.digitalpetri.modbus.pdu.*;
import com.digitalpetri.modbus.server.ModbusServices;
import com.digitalpetri.modbus.server.ModbusTcpServer;
import com.digitalpetri.modbus.server.NettyServerTransport;
import com.digitalpetri.modbus.server.NettyServerTransportConfig;
import com.inductiveautomation.ignition.gateway.opcua.server.api.Device;
import com.inductiveautomation.ignition.gateway.opcua.server.api.DeviceContext;
import com.inductiveautomation.ignition.gateway.opcua.server.api.DeviceSettingsRecord;
import com.kevinherron.ignition.modbus.address.ModbusAddress;
import com.kevinherron.ignition.modbus.address.ModbusAddressParser;
import com.kevinherron.ignition.modbus.address.ModbusDataType;
import com.kevinherron.ignition.modbus.address.ModbusDataType.Modifier;
import com.kevinherron.ignition.modbus.util.ByteArrayByteOps;
import org.eclipse.milo.opcua.sdk.core.AccessLevel;
import org.eclipse.milo.opcua.sdk.core.ValueRank;
import org.eclipse.milo.opcua.sdk.server.api.DataItem;
import org.eclipse.milo.opcua.sdk.server.api.MonitoredItem;
import org.eclipse.milo.opcua.sdk.server.util.SubscriptionModel;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.types.builtin.*;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.ULong;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned;
import org.eclipse.milo.opcua.stack.core.types.enumerated.NodeClass;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId;
import org.eclipse.milo.opcua.stack.core.types.structured.ViewDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.WriteValue;
import org.jetbrains.annotations.NotNull;
import org.joou.UByte;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.joou.Unsigned.ushort;


public class ModbusServerDevice implements Device {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  private ModbusTcpServer server;
  private volatile String status = "";

  private final ModbusServicesImpl services = new ModbusServicesImpl();

  private final SubscriptionModel subscriptionModel;

  private final DeviceContext deviceContext;
  private final DeviceSettingsRecord deviceSettings;
  private final ModbusServerDeviceSettings modbusServerSettings;

  public ModbusServerDevice(
      DeviceContext deviceContext,
      DeviceSettingsRecord deviceSettings,
      ModbusServerDeviceSettings modbusServerSettings
  ) {

    this.deviceContext = deviceContext;
    this.deviceSettings = deviceSettings;
    this.modbusServerSettings = modbusServerSettings;

    subscriptionModel = new SubscriptionModel(deviceContext.getServer(), this);
  }

  @Override
  public @NotNull String getName() {
    return deviceSettings.getName();
  }

  @Override
  public @NotNull String getStatus() {
    return status;
  }

  @Override
  public @NotNull String getTypeId() {
    return deviceSettings.getType();
  }

  @Override
  public void startup() {
    NettyServerTransport transport = new NettyServerTransport(
        NettyServerTransportConfig.create(cfg -> {
          cfg.bindAddress = modbusServerSettings.getBindAddress();
          cfg.port = ushort(modbusServerSettings.getPort());
        })
    );

    server = ModbusTcpServer.create(transport, services, cfg -> {});

    try {
      server.start();

      status = "Listening";

      logger.info(
          "Modbus server listening on {}:{}",
          modbusServerSettings.getBindAddress(),
          modbusServerSettings.getPort()
      );
    } catch (ExecutionException e) {
      status = "Error";
      logger.error("Error starting Modbus server", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      status = "Error";
      logger.error("Error starting Modbus server", e);
    }
  }

  @Override
  public void shutdown() {
    if (server != null) {
      try {
        server.stop();
      } catch (ExecutionException e) {
        logger.error("Error stopping Modbus server", e);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        logger.error("Error stopping Modbus server", e);
      }
    }
  }

  @Override
  public void browse(BrowseContext context, ViewDescription viewDescription, NodeId nodeId) {
    // TODO enumerate data tables?

    // HR
    // -- HR1
    // ---- HR<int16>1
    // ------ HR<int16>1.0
    // ------ HR<int16>1.1
    // ------ HR<int16>1.2
    // ------ HR<int16>1.3
    // ---- HR<int32>1
    // ---- HR<int64>1
    // ---- HR<uint16>1
    // ---- HR<uint32>1
    // ---- HR<uint64>1
    // ---- HR<float>1
    // ---- HR<double>1

    context.success(List.of());
  }

  @Override
  public void getReferences(BrowseContext context, ViewDescription viewDescription, NodeId nodeId) {
    // TODO enumerate data tables?
    context.success(List.of());
  }

  @Override
  public void read(
      ReadContext context,
      Double maxAge,
      TimestampsToReturn timestamps,
      List<ReadValueId> readValueIds
  ) {

    // parse readValueIds into ModbusAddresses
    // each ModbusAddress will inform us how many bytes to read from which area
    // we'll read the bytes, then use the datatype information interpret those bytes accordingly
    List<PendingRead> pendingReads = readValueIds.stream()
        .map(PendingRead::new)
        .toList();

    for (PendingRead pending : pendingReads) {
      ReadValueId readValueId = pending.readValueId;

      if (readValueId.getIndexRange() != null && !readValueId.getIndexRange().isEmpty()) {
        // TODO support index ranges on array values
        pending.value = new DataValue(StatusCodes.Bad_NotSupported);
        break;
      }

      String id = readValueId.getNodeId().getIdentifier().toString();
      String addr = id.substring(id.indexOf("[%s]".formatted(getName())));
      try {
        ModbusAddress address = ModbusAddressParser.parse(addr);
        AttributeId attributeId = AttributeId.from(readValueId.getAttributeId()).orElse(null);

        if (attributeId == null) {
          pending.value = new DataValue(StatusCodes.Bad_AttributeIdInvalid);
        } else if (attributeId == AttributeId.Value) {
          try {
            Variant v = readValueAttribute(address);
            pending.value = new DataValue(v);
          } catch (UaException e) {
            pending.value = new DataValue(e.getStatusCode());
          }
        } else {
          try {
            Variant v = readNonValueAttribute(readValueId.getNodeId(), attributeId, address);
            pending.value = new DataValue(v);
          } catch (UaException e) {
            pending.value = new DataValue(e.getStatusCode());
          }
        }
      } catch (Exception e) {
        pending.value = new DataValue(StatusCodes.Bad_ConfigurationError);
      }
    }

    context.success(pendingReads.stream().map(p -> p.value).toList());
  }

  private Variant readValueAttribute(ModbusAddress address) throws UaException {
    ModbusAddress.ModbusArea area = address.getArea();

    return switch (area) {
      case COILS: {
        services.coilLock.readLock().lock();
        try {
          boolean b = services.coilMap.get(address.getOffset());
          yield new Variant(b);
        } finally {
          services.coilLock.readLock().unlock();
        }
      }
      case DISCRETE_INPUTS: {
        services.discreteInputLock.readLock().lock();
        try {
          boolean b = services.discreteInputMap.get(address.getOffset());
          yield new Variant(b);
        } finally {
          services.discreteInputLock.readLock().unlock();
        }
      }
      case HOLDING_REGISTERS: {
        services.holdingRegisterLock.readLock().lock();
        try {
          byte[] registerBytes = ModbusServicesImpl.readRegisters(
              address.getOffset(),
              address.getDataType().getRegisterCount(),
              services.holdingRegisterMap
          );
          Object value = readRawRegisterValue(
              address.getDataType(),
              address.getDataTypeModifiers(),
              registerBytes
          );
          yield new Variant(value);
        } finally {
          services.holdingRegisterLock.readLock().unlock();
        }
      }
      case INPUT_REGISTERS:
        services.inputRegisterLock.readLock().lock();
        try {
          byte[] registerBytes = ModbusServicesImpl.readRegisters(
              address.getOffset(),
              address.getDataType().getRegisterCount(),
              services.inputRegisterMap
          );
          Object value = readRawRegisterValue(
              address.getDataType(),
              address.getDataTypeModifiers(),
              registerBytes
          );
          yield new Variant(value);
        } finally {
          services.inputRegisterLock.readLock().unlock();
        }
    };
  }

  private Variant readNonValueAttribute(NodeId nodeId, AttributeId attributeId, ModbusAddress address) throws UaException {
    Object o = switch (attributeId) {
      case NodeId -> nodeId;
      case NodeClass -> NodeClass.Variable;
      case BrowseName -> deviceContext.qualifiedName(nodeId.getIdentifier().toString());
      case DisplayName, Description -> LocalizedText.english(nodeId.getIdentifier().toString());
      case WriteMask, UserWriteMask -> UInteger.valueOf(0);
      case DataType -> address.getDataType().getBuiltinDataType().getNodeId();
      case ValueRank -> {
        if (address instanceof ModbusAddress.ArrayAddress a) {
          yield a.getDimensions().length;
        } else {
          yield ValueRank.Scalar;
        }
      }
      case ArrayDimensions -> {
        if (address instanceof ModbusAddress.ArrayAddress a) {
          yield Arrays.stream(a.getDimensions()).mapToObj(Unsigned::uint).toArray();
        } else {
          yield null;
        }
      }
      case AccessLevel, UserAccessLevel -> switch (address.getArea()) {
        case COILS, HOLDING_REGISTERS -> AccessLevel.toValue(AccessLevel.READ_WRITE);
        case DISCRETE_INPUTS, INPUT_REGISTERS -> AccessLevel.toValue(AccessLevel.READ_ONLY);
      };

      case Value -> throw new UaException(StatusCodes.Bad_InternalError, "attributeId: " + attributeId);

      default -> throw new UaException(StatusCodes.Bad_AttributeIdInvalid, "attributeId: " + attributeId);
    };

    return new Variant(o);
  }

  static Object readRawRegisterValue(
      ModbusDataType dataType,
      Set<Modifier> modifiers,
      byte[] registerBytes
  ) throws UaException {

    if (dataType instanceof ModbusDataType.Bit d) {
      // read underlying value, check and return specified bit
      Object value = readRawRegisterValue(d.underlyingType(), modifiers, registerBytes);
      if (value instanceof Number n) {
        return (n.longValue() & (1L << d.bit())) != 0L;
      } else {
        throw new UaException(StatusCodes.Bad_InternalError, "underlying: " + d.underlyingType());
      }
    } else if (dataType instanceof ModbusDataType.Bool) {
      return getByteOps(modifiers).getBoolean(registerBytes, 0);
    } else if (dataType instanceof ModbusDataType.Int16) {
      return getByteOps(modifiers).getShort(registerBytes, 0);
    } else if (dataType instanceof ModbusDataType.UInt16) {
      short v = getByteOps(modifiers).getShort(registerBytes, 0);
      return UShort.valueOf(v);
    } else if (dataType instanceof ModbusDataType.Int32) {
      return getByteOps(modifiers).getInt(registerBytes, 0);
    } else if (dataType instanceof ModbusDataType.UInt32) {
      int v = getByteOps(modifiers).getInt(registerBytes, 0);
      return UInteger.valueOf(v);
    } else if (dataType instanceof ModbusDataType.Int64) {
      return getByteOps(modifiers).getLong(registerBytes, 0);
    } else if (dataType instanceof ModbusDataType.UInt64) {
      long v = getByteOps(modifiers).getLong(registerBytes, 0);
      return ULong.valueOf(v);
    } else if (dataType instanceof ModbusDataType.Float32) {
      return getByteOps(modifiers).getFloat(registerBytes, 0);
    } else if (dataType instanceof ModbusDataType.Double64) {
      return getByteOps(modifiers).getDouble(registerBytes, 0);
    } else if (dataType instanceof ModbusDataType.String d) {
      int length = d.length();
      for (int i = 0; i < length; i++) {
        if (registerBytes[i] == 0) {
          length = i;
          break;
        }
      }
      return new String(registerBytes, 0, length);
    } else {
      throw new UaException(StatusCodes.Bad_InternalError, "dataType: " + dataType);
    }
  }

  @Override
  public void write(WriteContext context, List<WriteValue> writeValues) {
    // TODO
    context.success(Collections.nCopies(writeValues.size(), new StatusCode(StatusCodes.Bad_NotWritable)));
  }

  @Override
  public void onDataItemsCreated(List<DataItem> dataItems) {
    subscriptionModel.onDataItemsCreated(dataItems);
  }

  @Override
  public void onDataItemsModified(List<DataItem> dataItems) {
    subscriptionModel.onDataItemsModified(dataItems);
  }

  @Override
  public void onDataItemsDeleted(List<DataItem> dataItems) {
    subscriptionModel.onDataItemsDeleted(dataItems);
  }

  @Override
  public void onMonitoringModeChanged(List<MonitoredItem> monitoredItems) {
    subscriptionModel.onMonitoringModeChanged(monitoredItems);
  }

  private static ByteArrayByteOps getByteOps(Set<Modifier> modifiers) {
    if (modifiers.contains(Modifier.BYTE_ORDER_BIG_ENDIAN)) {
      if (modifiers.contains(Modifier.WORD_ORDER_LOW_HIGH)) {
        return ByteArrayByteOps.BIG_ENDIAN_WORD_SWAPPED;
      } else {
        return ByteArrayByteOps.BIG_ENDIAN;
      }
    } else if (modifiers.contains(Modifier.BYTE_ORDER_LITTLE_ENDIAN)) {
      if (modifiers.contains(Modifier.WORD_ORDER_LOW_HIGH)) {
        return ByteArrayByteOps.LITTLE_ENDIAN_WORD_SWAPPED;
      } else {
        return ByteArrayByteOps.LITTLE_ENDIAN;
      }
    } else {
      if (modifiers.contains(Modifier.WORD_ORDER_LOW_HIGH)) {
        return ByteArrayByteOps.BIG_ENDIAN_WORD_SWAPPED;
      } else {
        return ByteArrayByteOps.BIG_ENDIAN;
      }
    }
  }

  private static class PendingRead {
    volatile DataValue value;
    final ReadValueId readValueId;

    private PendingRead(ReadValueId readValueId) {this.readValueId = readValueId;}
  }

  static class ModbusServicesImpl implements ModbusServices {

    final ReadWriteLock coilLock = new ReentrantReadWriteLock();
    final Map<Integer, Boolean> coilMap = new HashMap<>();

    final ReadWriteLock discreteInputLock = new ReentrantReadWriteLock();
    final Map<Integer, Boolean> discreteInputMap = new HashMap<>();

    final ReadWriteLock holdingRegisterLock = new ReentrantReadWriteLock();
    final Map<Integer, byte[]> holdingRegisterMap = new HashMap<>();

    final ReadWriteLock inputRegisterLock = new ReentrantReadWriteLock();
    final Map<Integer, byte[]> inputRegisterMap = new HashMap<>();

    @Override
    public ReadCoilsResponse readCoils(UByte unitId, ReadCoilsRequest request) {
      coilLock.readLock().lock();
      try {
        int address = request.address().intValue();
        int quantity = request.quantity().intValue();

        var coils = readBits(address, quantity, coilMap);

        return new ReadCoilsResponse(coils);
      } finally {
        coilLock.readLock().unlock();
      }
    }

    @Override
    public ReadDiscreteInputsResponse readDiscreteInputs(UByte unitId, ReadDiscreteInputsRequest request) {
      discreteInputLock.readLock().lock();
      try {
        int address = request.address().intValue();
        int quantity = request.quantity().intValue();

        var inputs = readBits(address, quantity, discreteInputMap);

        return new ReadDiscreteInputsResponse(inputs);
      } finally {
        discreteInputLock.readLock().unlock();
      }
    }

    private static byte[] readBits(int address, int quantity, Map<Integer, Boolean> bitMap) {
      var bytes = new byte[(quantity + 7) / 8];

      for (int i = 0; i < quantity; i++) {
        int byteIndex = i / 8;
        int bitIndex = i % 8;

        boolean value = bitMap.getOrDefault(address + i, false);

        int b = bytes[byteIndex];
        if (value) {
          b |= (1 << bitIndex);
        } else {
          b &= ~(1 << bitIndex);
        }
        bytes[byteIndex] = (byte) (b & 0xFF);
      }

      return bytes;
    }

    @Override
    public ReadHoldingRegistersResponse readHoldingRegisters(UByte unitId, ReadHoldingRegistersRequest request) {
      holdingRegisterLock.readLock().lock();
      try {
        int address = request.address().intValue();
        int quantity = request.quantity().intValue();

        var registers = readRegisters(address, quantity, holdingRegisterMap);

        return new ReadHoldingRegistersResponse(registers);
      } finally {
        holdingRegisterLock.readLock().unlock();
      }
    }

    @Override
    public ReadInputRegistersResponse readInputRegisters(UByte unitId, ReadInputRegistersRequest request) {
      inputRegisterLock.readLock().lock();
      try {
        int address = request.address().intValue();
        int quantity = request.quantity().intValue();

        var registers = readRegisters(address, quantity, inputRegisterMap);

        return new ReadInputRegistersResponse(registers);
      } finally {
        inputRegisterLock.readLock().unlock();
      }
    }

    static byte[] readRegisters(int address, int quantity, Map<Integer, byte[]> registerMap) {
      var registers = new byte[quantity * 2];

      for (int i = 0; i < quantity; i++) {
        byte[] value = registerMap.getOrDefault(address + i, new byte[2]);

        registers[i * 2] = value[0];
        registers[i * 2 + 1] = value[1];
      }

      return registers;
    }

    @Override
    public WriteMultipleRegistersResponse writeMultipleRegisters(UByte unitId, WriteMultipleRegistersRequest request) {
      holdingRegisterLock.writeLock().lock();
      try {
        int address = request.address().intValue();
        int quantity = request.quantity().intValue();
        byte[] values = request.values();

        for (int i = 0; i < quantity; i++) {
          byte b0 = values[i * 2];
          byte b1 = values[i * 2 + 1];

          if (b0 == 0 && b1 == 0) {
            holdingRegisterMap.remove(address + i);
          } else {
            byte[] value = new byte[]{b0, b1};
            holdingRegisterMap.put(address + i, value);
          }
        }
        return new WriteMultipleRegistersResponse(request.address(), request.quantity());
      } finally {
        holdingRegisterLock.writeLock().unlock();
      }
    }

    @Override
    public WriteSingleRegisterResponse writeSingleRegister(UByte unitId, WriteSingleRegisterRequest request) {
      int address = request.address().intValue();
      int value = request.value().intValue();

      holdingRegisterLock.writeLock().lock();
      try {
        if (value == 0) {
          holdingRegisterMap.remove(address);
        } else {
          byte b0 = (byte) ((value >> 8) & 0xFF);
          byte b1 = (byte) (value & 0xFF);
          byte[] bs = new byte[]{b0, b1};
          holdingRegisterMap.put(address, bs);
        }

        return new WriteSingleRegisterResponse(request.address(), request.value());
      } finally {
        holdingRegisterLock.writeLock().unlock();
      }
    }

  }

}
