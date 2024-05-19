package com.kevinherron.ignition.modbus;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ulong;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ushort;

import com.kevinherron.ignition.modbus.address.ModbusAddress;
import com.kevinherron.ignition.modbus.address.ModbusAddress.ModbusArea;
import com.kevinherron.ignition.modbus.address.ModbusAddressParser;
import com.kevinherron.ignition.modbus.address.ModbusDataType;
import com.kevinherron.ignition.modbus.util.ModbusByteUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.stream.Collectors;
import org.eclipse.milo.opcua.sdk.core.AccessLevel;
import org.eclipse.milo.opcua.sdk.core.ValueRank;
import org.eclipse.milo.opcua.sdk.server.Lifecycle;
import org.eclipse.milo.opcua.sdk.server.api.AddressSpaceFilter;
import org.eclipse.milo.opcua.sdk.server.api.AddressSpaceFragment;
import org.eclipse.milo.opcua.sdk.server.api.DataItem;
import org.eclipse.milo.opcua.sdk.server.api.MonitoredItem;
import org.eclipse.milo.opcua.sdk.server.api.SimpleAddressSpaceFilter;
import org.eclipse.milo.opcua.sdk.server.util.SubscriptionModel;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned;
import org.eclipse.milo.opcua.stack.core.types.enumerated.NodeClass;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId;
import org.eclipse.milo.opcua.stack.core.types.structured.ViewDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.WriteValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModbusAddressSpace implements AddressSpaceFragment, Lifecycle {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  private final AddressSpaceFilter filter;
  private final SubscriptionModel subscriptionModel;

  private final ModbusServerDevice device;

  public ModbusAddressSpace(ModbusServerDevice device) {
    this.device = device;

    filter = new ModbusAddressFilter(device.getName());

    subscriptionModel = new SubscriptionModel(device.deviceContext.getServer(), this);
  }

  @Override
  public void startup() {
    subscriptionModel.startup();

    device.register(this);
  }

  @Override
  public void shutdown() {
    subscriptionModel.shutdown();

    device.unregister(this);
  }

  @Override
  public AddressSpaceFilter getFilter() {
    return filter;
  }

  //region Browse

  @Override
  public void browse(BrowseContext context, ViewDescription viewDescription, NodeId nodeId) {
    context.success(List.of());
  }

  @Override
  public void getReferences(BrowseContext context, ViewDescription viewDescription,
      NodeId nodeId) {
    context.success(List.of());
  }

  //endregion

  //region Read

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
      String name = "[%s]".formatted(device.getName());
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
    ModbusArea area = address.getArea();

    return switch (area) {
      case COILS: {
        device.services.coilLock.readLock().lock();
        try {
          boolean b = device.services.coilMap.getOrDefault(address.getOffset(), false);
          yield new Variant(b);
        } finally {
          device.services.coilLock.readLock().unlock();
        }
      }
      case DISCRETE_INPUTS: {
        device.services.discreteInputLock.readLock().lock();
        try {
          boolean b = device.services.discreteInputMap.getOrDefault(address.getOffset(), false);
          yield new Variant(b);
        } finally {
          device.services.discreteInputLock.readLock().unlock();
        }
      }
      case HOLDING_REGISTERS: {
        device.services.holdingRegisterLock.readLock().lock();
        try {
          byte[] registerBytes = ModbusServicesImpl.readRegisters(
              device.services.holdingRegisterMap,
              address.getOffset(),
              address.getDataType().getRegisterCount()
          );

          Object value = ModbusByteUtil.getValueForBytes(registerBytes, address);

          yield new Variant(value);
        } finally {
          device.services.holdingRegisterLock.readLock().unlock();
        }
      }
      case INPUT_REGISTERS:
        device.services.inputRegisterLock.readLock().lock();
        try {
          byte[] registerBytes = ModbusServicesImpl.readRegisters(
              device.services.inputRegisterMap,
              address.getOffset(),
              address.getDataType().getRegisterCount()
          );

          Object value = ModbusByteUtil.getValueForBytes(registerBytes, address);

          yield new Variant(value);
        } finally {
          device.services.inputRegisterLock.readLock().unlock();
        }
    };
  }


  private Variant readNonValueAttribute(
      NodeId nodeId,
      AttributeId attributeId,
      ModbusAddress address
  ) throws UaException {

    Object o = switch (attributeId) {
      case NodeId -> nodeId;
      case NodeClass -> NodeClass.Variable;
      case BrowseName -> {
        String id = nodeId.getIdentifier().toString();
        String addr = id.substring(device.getName().length() + 2);
        yield device.deviceContext.qualifiedName(addr);
      }
      case DisplayName, Description -> {
        String id = nodeId.getIdentifier().toString();
        String addr = id.substring(device.getName().length() + 2);
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

      // All areas are Read/Write from the OPC UA side, otherwise nothing would be able to
      // update IR and DI values!
      case AccessLevel, UserAccessLevel -> AccessLevel.toValue(AccessLevel.READ_WRITE);

      case Value ->
          throw new UaException(StatusCodes.Bad_InternalError, "attributeId: " + attributeId);

      default ->
          throw new UaException(StatusCodes.Bad_AttributeIdInvalid, "attributeId: " + attributeId);
    };

    return new Variant(o);
  }

  //endregion

  //region Write

  @Override
  public void write(WriteContext context, List<WriteValue> writeValues) {
    var pendingWrites = writeValues.stream()
        .map(PendingWrite::new)
        .toList();

    var pendingValueWrites = new ArrayList<PendingValueWrite>();

    for (PendingWrite pending : pendingWrites) {
      WriteValue writeValue = pending.writeValue;

      if (writeValue.getIndexRange() != null && !writeValue.getIndexRange().isEmpty()) {
        // TODO support index ranges on array values
        pending.statusCode = new StatusCode(StatusCodes.Bad_WriteNotSupported);
        break;
      }

      AttributeId attributeId = AttributeId.from(writeValue.getAttributeId()).orElse(null);

      if (attributeId == null) {
        pending.statusCode = new StatusCode(StatusCodes.Bad_AttributeIdInvalid);
      } else if (attributeId == AttributeId.Value) {
        String id = writeValue.getNodeId().getIdentifier().toString();
        String name = "[%s]".formatted(device.getName());
        String addr = id.substring(id.indexOf(name) + name.length());
        try {
          ModbusAddress address = ModbusAddressParser.parse(addr);
          pendingValueWrites.add(new PendingValueWrite(writeValue, address));
        } catch (Exception e) {
          pending.statusCode = new StatusCode(StatusCodes.Bad_ConfigurationError);
        }
      } else {
        pending.statusCode = new StatusCode(StatusCodes.Bad_NotWritable);
      }
    }

    // Process PendingValueWrites, grouped by Area
    pendingValueWrites.stream()
        .collect(Collectors.groupingBy(p -> p.address.getArea()))
        .forEach(this::writeValueAttributes);

    context.success(pendingWrites.stream().map(p -> p.statusCode).toList());
  }

  private void writeValueAttributes(ModbusArea area, List<PendingValueWrite> pendingValueWrites) {
    assert pendingValueWrites.stream().allMatch(p -> p.address.getArea() == area);

    ReadWriteLock lock = switch (area) {
      case COILS -> device.services.coilLock;
      case DISCRETE_INPUTS -> device.services.discreteInputLock;
      case HOLDING_REGISTERS -> device.services.holdingRegisterLock;
      case INPUT_REGISTERS -> device.services.inputRegisterLock;
    };

    lock.writeLock().lock();
    try {
      for (PendingValueWrite pvw : pendingValueWrites) {
        try {
          writeValueAttribute(pvw.address, pvw.writeValue.getValue().getValue());
          pvw.statusCode = StatusCode.GOOD;
        } catch (UaException e) {
          pvw.statusCode = e.getStatusCode();
        }
      }
    } finally {
      lock.writeLock().unlock();
    }
  }

  private void writeValueAttribute(ModbusAddress address, Variant variant) throws UaException {
    switch (address.getArea()) {
      case COILS -> {
        if (variant.getValue() instanceof Boolean b) {
          device.services.coilMap.put(address.getOffset(), b);
        } else {
          throw new UaException(StatusCodes.Bad_TypeMismatch);
        }
      }
      case DISCRETE_INPUTS -> {
        if (variant.getValue() instanceof Boolean b) {
          device.services.discreteInputMap.put(address.getOffset(), b);
        } else {
          throw new UaException(StatusCodes.Bad_TypeMismatch);
        }
      }
      case HOLDING_REGISTERS -> {
        checkDataType(address.getDataType(), variant);

        if (address.getDataType() instanceof ModbusDataType.Bit dataType) {
          writeBitToRegister(address, variant, dataType, device.services.holdingRegisterMap);
        } else {
          byte[] registerBytes = ModbusByteUtil.getBytesForValue(variant.getValue(), address);

          ModbusServicesImpl.writeRegisters(
              device.services.holdingRegisterMap,
              address.getOffset(),
              registerBytes
          );
        }
      }
      case INPUT_REGISTERS -> {
        checkDataType(address.getDataType(), variant);

        if (address.getDataType() instanceof ModbusDataType.Bit dataType) {
          writeBitToRegister(address, variant, dataType, device.services.inputRegisterMap);
        } else {
          byte[] registerBytes = ModbusByteUtil.getBytesForValue(variant.getValue(), address);

          ModbusServicesImpl.writeRegisters(
              device.services.inputRegisterMap,
              address.getOffset(),
              registerBytes
          );
        }
      }
    }
  }

  /**
   * Check that a value is of the correct type for the given ModbusDataType.
   *
   * @param dataType the {@link ModbusDataType}.
   * @param variant the {@link Variant} to check.
   * @throws UaException if the value is {@code null}, or not of the correct type.
   */
  private static void checkDataType(ModbusDataType dataType, Variant variant) throws UaException {
    Object value = variant.getValue();
    if (value == null) {
      throw new UaException(StatusCodes.Bad_TypeMismatch);
    }

    Class<?> actualType = variant.getValue().getClass();
    Class<?> expectedType = dataType.getBuiltinDataType().getBackingClass();

    if (!expectedType.isAssignableFrom(actualType)) {
      throw new UaException(StatusCodes.Bad_TypeMismatch);
    }
  }

  private static void writeBitToRegister(
      ModbusAddress address,
      Variant variant,
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
      if (variant.getValue() instanceof Boolean b) {
        if (b) {
          v |= mask;
        } else {
          v &= ~mask;
        }
        byte[] newBytes = ModbusByteUtil.getBytesForValue(
            castToUnderlying(v, underlyingType),
            underlyingType,
            address.getDataTypeModifiers()
        );
        ModbusServicesImpl.writeRegisters(registerMap, address.getOffset(), newBytes);
      } else {
        throw new UaException(StatusCodes.Bad_TypeMismatch);
      }
    } else {
      throw new UaException(StatusCodes.Bad_InternalError);
    }
  }

  private static Number castToUnderlying(long value, ModbusDataType dataType) {
    if (dataType instanceof ModbusDataType.Int16) {
      return (short) value;
    } else if (dataType instanceof ModbusDataType.Int32) {
      return (int) value;
    } else if (dataType instanceof ModbusDataType.Int64) {
      return value;
    } else if (dataType instanceof ModbusDataType.UInt16) {
      return ushort((int) value);
    } else if (dataType instanceof ModbusDataType.UInt32) {
      return uint(value);
    } else if (dataType instanceof ModbusDataType.UInt64) {
      return ulong(value);
    } else {
      throw new IllegalArgumentException("value=" + value + ", dataType=" + dataType);
    }
  }

  //endregion

  //region Subscribe

  @Override
  public void onDataItemsCreated(List<DataItem> items) {
    subscriptionModel.onDataItemsCreated(items);
  }

  @Override
  public void onDataItemsModified(List<DataItem> items) {
    subscriptionModel.onDataItemsModified(items);
  }

  @Override
  public void onDataItemsDeleted(List<DataItem> items) {
    subscriptionModel.onDataItemsDeleted(items);
  }

  @Override
  public void onMonitoringModeChanged(List<MonitoredItem> items) {
    subscriptionModel.onMonitoringModeChanged(items);
  }

  //endregion

  private static class PendingRead {

    volatile DataValue value;
    final ReadValueId readValueId;

    private PendingRead(ReadValueId readValueId) {
      this.readValueId = readValueId;
    }
  }

  private static class PendingWrite {

    volatile StatusCode statusCode;
    final WriteValue writeValue;

    private PendingWrite(WriteValue writeValue) {
      this.writeValue = writeValue;
    }
  }

  private static class PendingValueWrite extends PendingWrite {

    final ModbusAddress address;

    private PendingValueWrite(WriteValue writeValue, ModbusAddress address) {
      super(writeValue);
      this.address = address;
    }
  }

  private class ModbusAddressFilter extends SimpleAddressSpaceFilter {

    private final String deviceName;

    private ModbusAddressFilter(String deviceName) {
      this.deviceName = deviceName;
    }

    @Override
    protected boolean filterNode(NodeId nodeId) {
      return checkAddress(nodeId);
    }

    @Override
    protected boolean filterMonitoredItem(NodeId nodeId) {
      return checkAddress(nodeId);
    }

    private boolean checkAddress(NodeId nodeId) {
      String id = nodeId.getIdentifier().toString();
      // remove the leading "[DeviceName]" prefix
      id = id.substring(deviceName.length() + 2);

      logger.info("checking {}", id);
      try {
        ModbusAddressParser.parse(id);
        return true;
      } catch (Exception e) {
        return false;
      }
    }

  }

}
