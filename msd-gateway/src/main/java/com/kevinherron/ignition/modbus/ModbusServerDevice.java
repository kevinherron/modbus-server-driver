package com.kevinherron.ignition.modbus;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import com.kevinherron.ignition.modbus.util.ModbusByteUtil;
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
    subscriptionModel.startup();

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

    onDataItemsCreated(deviceContext.getSubscriptionModel().getDataItems(getName()));
  }

  @Override
  public void shutdown() {
    subscriptionModel.shutdown();

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

    List<PendingRead> pendingReads = readValueIds.stream()
        .map(PendingRead::new)
        .toList();

    for (PendingRead pending : pendingReads) {
      ReadValueId readValueId = pending.readValueId;

      if (readValueId.getIndexRange() != null && !readValueId.getIndexRange().isEmpty()) {
        // TODO support index ranges on array values
        pending.value = new DataValue(StatusCodes.Bad_WriteNotSupported);
        break;
      }

      String id = readValueId.getNodeId().getIdentifier().toString();
      String name = "[%s]".formatted(getName());
      String addr = id.substring(id.indexOf(name) + name.length());

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
        logger.error("Error reading value: id={}, addr={}", id, addr, e);
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
              services.holdingRegisterMap,
              address.getOffset(),
              address.getDataType().getRegisterCount()
          );
          Object value = ModbusByteUtil.getValueForBytes(
              registerBytes,
              address.getDataType(),
              address.getDataTypeModifiers()
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
              services.inputRegisterMap,
              address.getOffset(),
              address.getDataType().getRegisterCount()
          );
          Object value = ModbusByteUtil.getValueForBytes(
              registerBytes,
              address.getDataType(),
              address.getDataTypeModifiers()
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
      case BrowseName -> {
        String id = nodeId.getIdentifier().toString();
        String addr = id.substring(id.indexOf("[%s]".formatted(getName())));
        yield deviceContext.qualifiedName(addr);
      }
      case DisplayName, Description -> {
        String id = nodeId.getIdentifier().toString();
        String addr = id.substring(id.indexOf("[%s]".formatted(getName())));
        yield LocalizedText.english(addr);
      }
      case WriteMask, UserWriteMask -> UInteger.valueOf(0);
      case DataType -> address.getDataType().getBuiltinDataType().getNodeId();
      case ValueRank -> {
        if (address instanceof ModbusAddress.ArrayAddress a) {
          yield a.getDimensions().length;
        } else {
          yield ValueRank.Scalar.getValue();
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

  @Override
  public void write(WriteContext context, List<WriteValue> writeValues) {
    var pendingWrites = writeValues.stream()
        .map(PendingWrite::new)
        .toList();

    for (PendingWrite pending : pendingWrites) {
      WriteValue writeValue = pending.writeValue;

      if (writeValue.getIndexRange() != null && !writeValue.getIndexRange().isEmpty()) {
        pending.statusCode = new StatusCode(StatusCodes.Bad_WriteNotSupported);
        break;
      }

      String id = writeValue.getNodeId().getIdentifier().toString();
      String name = "[%s]".formatted(getName());
      String addr = id.substring(id.indexOf(name) + name.length());

      try {
        ModbusAddress address = ModbusAddressParser.parse(addr);
        AttributeId attributeId = AttributeId.from(writeValue.getAttributeId()).orElse(null);

        if (attributeId == null) {
          pending.statusCode = new StatusCode(StatusCodes.Bad_AttributeIdInvalid);
        } else if (attributeId == AttributeId.Value) {
          // TODO group all bit writes an apply them atomically?
          try {
            writeValueAttribute(address, writeValue.getValue().getValue());
            pending.statusCode = StatusCode.GOOD;
          } catch (UaException e) {
            pending.statusCode = e.getStatusCode();
          }
        } else {
          pending.statusCode = new StatusCode(StatusCodes.Bad_NotWritable);
        }
      } catch (Exception e) {
        pending.statusCode = new StatusCode(StatusCodes.Bad_ConfigurationError);
      }
    }

    context.success(pendingWrites.stream().map(p -> p.statusCode).toList());
  }

  private void writeValueAttribute(ModbusAddress address, Variant value) throws UaException {
    switch (address.getArea()) {
      case COILS -> {
        if (value.getValue() instanceof Boolean b) {
          services.coilLock.writeLock().lock();
          try {
            services.coilMap.put(address.getOffset(), b);
          } finally {
            services.coilLock.writeLock().unlock();
          }
        } else {
          throw new UaException(StatusCodes.Bad_TypeMismatch);
        }
      }
      case DISCRETE_INPUTS -> {
        if (value.getValue() instanceof Boolean b) {
          services.discreteInputLock.writeLock().lock();
          try {
            services.discreteInputMap.put(address.getOffset(), b);
          } finally {
            services.discreteInputLock.writeLock().unlock();
          }
        } else {
          throw new UaException(StatusCodes.Bad_TypeMismatch);
        }
      }
      case HOLDING_REGISTERS -> {
        services.holdingRegisterLock.writeLock().lock();
        try {
          if (address.getDataType() instanceof ModbusDataType.Bit dataType) {
            writeBitToRegister(address, value, dataType, services.holdingRegisterMap);
          } else {
            byte[] registerBytes = ModbusByteUtil.getBytesForValue(
                value.getValue(),
                address.getDataType(),
                address.getDataTypeModifiers()
            );

            ModbusServicesImpl.writeRegisters(services.holdingRegisterMap, address.getOffset(), registerBytes);
          }
        } finally {
          services.holdingRegisterLock.writeLock().unlock();
        }
      }
      case INPUT_REGISTERS -> {
        services.inputRegisterLock.writeLock().lock();
        try {
          if (address.getDataType() instanceof ModbusDataType.Bit dataType) {
            writeBitToRegister(address, value, dataType, services.inputRegisterMap);
          } else {
            byte[] registerBytes = ModbusByteUtil.getBytesForValue(
                value.getValue(),
                address.getDataType(),
                address.getDataTypeModifiers()
            );

            ModbusServicesImpl.writeRegisters(services.inputRegisterMap, address.getOffset(), registerBytes);
          }
        } finally {
          services.inputRegisterLock.writeLock().unlock();
        }
      }
    }
  }

  private static void writeBitToRegister(
      ModbusAddress address,
      Variant value,
      ModbusDataType.Bit dataType,
      Map<Integer, byte[]> registerMap
  ) throws UaException {

    int bitIndex = dataType.bit();
    ModbusDataType underlyingType = dataType.underlyingType();

    byte[] bytes = ModbusServicesImpl.readRegisters(
        registerMap,
        address.getOffset(),
        underlyingType.getRegisterCount()
    );
    Object underlyingValue = ModbusByteUtil.getValueForBytes(
        bytes,
        underlyingType,
        address.getDataTypeModifiers()
    );

    if (underlyingValue instanceof Number n) {
      long mask = 1L << bitIndex;
      long v = n.longValue();
      if (value.getValue() instanceof Boolean b) {
        if (b) {
          v |= mask;
        } else {
          v &= ~mask;
        }
        byte[] newBytes = ModbusByteUtil.getBytesForValue(v, underlyingType, address.getDataTypeModifiers());
        ModbusServicesImpl.writeRegisters(registerMap, address.getOffset(), newBytes);
      } else {
        throw new UaException(StatusCodes.Bad_TypeMismatch);
      }
    } else {
      throw new UaException(StatusCodes.Bad_InternalError);
    }
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

  private static class PendingRead {
    volatile DataValue value;
    final ReadValueId readValueId;

    private PendingRead(ReadValueId readValueId) {this.readValueId = readValueId;}
  }

  private static class PendingWrite {
    volatile StatusCode statusCode;
    final WriteValue writeValue;

    private PendingWrite(WriteValue writeValue) {this.writeValue = writeValue;}
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

        var registers = readRegisters(holdingRegisterMap, address, quantity);

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

        var registers = readRegisters(inputRegisterMap, address, quantity);

        return new ReadInputRegistersResponse(registers);
      } finally {
        inputRegisterLock.readLock().unlock();
      }
    }

    static byte[] readRegisters(Map<Integer, byte[]> registerMap, int address, int quantity) {
      var registers = new byte[quantity * 2];

      for (int i = 0; i < quantity; i++) {
        byte[] value = registerMap.getOrDefault(address + i, new byte[2]);

        registers[i * 2] = value[0];
        registers[i * 2 + 1] = value[1];
      }

      return registers;
    }

    static void writeRegisters(Map<Integer, byte[]> registerMap, int address, byte[] registers) {
      for (int i = 0; i < registers.length / 2; i++) {
        byte[] value = new byte[]{registers[i * 2], registers[i * 2 + 1]};
        registerMap.put(address + i, value);
      }
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
