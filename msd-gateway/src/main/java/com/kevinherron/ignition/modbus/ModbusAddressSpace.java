package com.kevinherron.ignition.modbus;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ulong;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ushort;

import com.digitalpetri.modbus.server.ProcessImage;
import com.digitalpetri.modbus.server.ProcessImage.Modification.CoilModification;
import com.digitalpetri.modbus.server.ProcessImage.Modification.DiscreteInputModification;
import com.digitalpetri.modbus.server.ProcessImage.Modification.HoldingRegisterModification;
import com.digitalpetri.modbus.server.ProcessImage.Modification.InputRegisterModification;
import com.digitalpetri.modbus.server.ProcessImage.Transaction;
import com.inductiveautomation.ignition.gateway.opcua.server.api.OpcUa;
import com.kevinherron.ignition.modbus.address.ModbusAddress;
import com.kevinherron.ignition.modbus.address.ModbusAddress.ModbusArea;
import com.kevinherron.ignition.modbus.address.ModbusAddressParser;
import com.kevinherron.ignition.modbus.address.ModbusDataType;
import com.kevinherron.ignition.modbus.util.ModbusByteUtil;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.eclipse.milo.opcua.sdk.core.AccessLevel;
import org.eclipse.milo.opcua.sdk.core.ValueRank;
import org.eclipse.milo.opcua.sdk.server.AddressSpace.ReferenceResult.ReferenceList;
import org.eclipse.milo.opcua.sdk.server.AddressSpaceFilter;
import org.eclipse.milo.opcua.sdk.server.AddressSpaceFragment;
import org.eclipse.milo.opcua.sdk.server.Lifecycle;
import org.eclipse.milo.opcua.sdk.server.SimpleAddressSpaceFilter;
import org.eclipse.milo.opcua.sdk.server.items.DataItem;
import org.eclipse.milo.opcua.sdk.server.items.MonitoredItem;
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
import org.eclipse.milo.opcua.stack.core.util.ExecutionQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModbusAddressSpace implements AddressSpaceFragment, Lifecycle {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  private final AddressSpaceFilter filter;
  private final SubscriptionModel subscriptionModel;

  private final ModbusServerDevice device;

  public ModbusAddressSpace(ModbusServerDevice device) {
    this.device = device;

    filter = new ModbusAddressFilter(device.deviceContext.getName());

    subscriptionModel = new SubscriptionModel(device.deviceContext.getServer(), this);
  }

  @Override
  public void startup() {
    if (device.deviceConfig.persistence().persistData()) {
      Path deviceFolderPath = device.deviceContext.getDeviceFolderPath().toAbsolutePath();

      if (!Files.exists(deviceFolderPath)) {
        try {
          Files.createDirectories(deviceFolderPath);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }

      loadProcessImage();

      device.processImage.addModificationListener(new ModificationListener());
    }

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

  // region Browse


  @Override
  public List<ReferenceResult> browse(
      BrowseContext context, ViewDescription viewDescription, List<NodeId> nodeIds) {

    return List.of();
  }

  @Override
  public ReferenceList gather(
      BrowseContext context, ViewDescription viewDescription, NodeId nodeId) {

    return ReferenceResult.of(List.of());
  }

  // endregion

  // region Read

  @Override
  public List<DataValue> read(
      ReadContext context,
      Double maxAge,
      TimestampsToReturn timestamps,
      List<ReadValueId> readValueIds) {

    List<PendingRead> pendingReads = readValueIds.stream().map(PendingRead::new).toList();

    for (PendingRead pending : pendingReads) {
      ReadValueId readValueId = pending.readValueId;

      if (readValueId.getIndexRange() != null && !readValueId.getIndexRange().isEmpty()) {
        // TODO support index ranges on array values
        pending.value = new DataValue(StatusCodes.Bad_WriteNotSupported);
        break;
      }

      String id = readValueId.getNodeId().getIdentifier().toString();
      String name = "[%s]".formatted(device.deviceContext.getName());
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

    return pendingReads.stream().map(p -> p.value).toList();
  }

  private Variant readValueAttribute(ModbusAddress address) throws UaException {
    ModbusArea area = address.getArea();

    return switch (area) {
      case COILS:
        {
          boolean value =
              device.processImage.get(
                  tx -> tx.readCoils(coilMap -> coilMap.getOrDefault(address.getOffset(), false)));

          yield new Variant(value);
        }
      case DISCRETE_INPUTS:
        {
          boolean value =
              device.processImage.get(
                  tx ->
                      tx.readDiscreteInputs(
                          discreteInputMap ->
                              discreteInputMap.getOrDefault(address.getOffset(), false)));

          yield new Variant(value);
        }
      case HOLDING_REGISTERS:
        {
          //noinspection DuplicatedCode
          byte[] bs =
              device.processImage.get(
                  tx ->
                      tx.readHoldingRegisters(
                          holdingRegisterMap -> {
                            var registers = new byte[address.getDataType().getRegisterCount() * 2];

                            for (int i = 0; i < registers.length / 2; i++) {
                              byte[] value =
                                  holdingRegisterMap.getOrDefault(
                                      address.getOffset() + i, new byte[2]);
                              registers[i * 2] = value[0];
                              registers[i * 2 + 1] = value[1];
                            }

                            return registers;
                          }));

          yield new Variant(ModbusByteUtil.getValueForBytes(bs, address));
        }
      case INPUT_REGISTERS:
        //noinspection DuplicatedCode
        byte[] bs =
            device.processImage.get(
                tx ->
                    tx.readInputRegisters(
                        inputRegisterMap -> {
                          var registers = new byte[address.getDataType().getRegisterCount() * 2];

                          for (int i = 0; i < registers.length / 2; i++) {
                            byte[] value =
                                inputRegisterMap.getOrDefault(address.getOffset() + i, new byte[2]);
                            registers[i * 2] = value[0];
                            registers[i * 2 + 1] = value[1];
                          }

                          return registers;
                        }));

        yield new Variant(ModbusByteUtil.getValueForBytes(bs, address));
    };
  }

  private Variant readNonValueAttribute(
      NodeId nodeId, AttributeId attributeId, ModbusAddress address) throws UaException {

    Object o =
        switch (attributeId) {
          case NodeId -> nodeId;
          case NodeClass -> NodeClass.Variable;
          case BrowseName -> {
            String id = nodeId.getIdentifier().toString();
            String addr = id.substring(device.deviceContext.getName().length() + 2);
            yield device.deviceContext.qualifiedName(addr);
          }
          case DisplayName, Description -> {
            String id = nodeId.getIdentifier().toString();
            String addr = id.substring(device.deviceContext.getName().length() + 2);
            yield LocalizedText.english(addr);
          }
          case WriteMask, UserWriteMask -> UInteger.valueOf(0);
          case DataType -> address.getDataType().getOpcUaDataType().getNodeId();
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
              throw new UaException(
                  StatusCodes.Bad_AttributeIdInvalid, "attributeId: " + attributeId);
        };

    return new Variant(o);
  }

  // endregion

  // region Write

  @Override
  public List<StatusCode> write(WriteContext context, List<WriteValue> writeValues) {
    var pendingWrites = writeValues.stream().map(PendingWrite::new).toList();

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
        String name = "[%s]".formatted(device.deviceContext.getName());
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

    for (PendingValueWrite pvw : pendingValueWrites) {
      try {
        writeValueAttribute(pvw.address, pvw.writeValue.getValue().getValue());
        pvw.statusCode = StatusCode.GOOD;
      } catch (UaException e) {
        pvw.statusCode = e.getStatusCode();
      } catch (RuntimeException e) {
        if (e.getCause() instanceof UaException uax) {
          pvw.statusCode = uax.getStatusCode();
        } else {
          pvw.statusCode = new StatusCode(StatusCodes.Bad_InternalError);
        }
      }
    }

    return pendingWrites.stream().map(p -> p.statusCode).toList();
  }

  private void writeValueAttribute(ModbusAddress address, Variant variant) throws UaException {
    switch (address.getArea()) {
      case COILS -> {
        if (variant.getValue() instanceof Boolean b) {
          device.processImage.with(
              tx -> tx.writeCoils(coilMap -> coilMap.put(address.getOffset(), b)));
        } else {
          throw new UaException(StatusCodes.Bad_TypeMismatch);
        }
      }
      case DISCRETE_INPUTS -> {
        if (variant.getValue() instanceof Boolean b) {
          device.processImage.with(
              tx ->
                  tx.writeDiscreteInputs(
                      discreteInputMap -> discreteInputMap.put(address.getOffset(), b)));
        } else {
          throw new UaException(StatusCodes.Bad_TypeMismatch);
        }
      }
      case HOLDING_REGISTERS -> {
        checkDataType(address.getDataType(), variant);

        if (address.getDataType() instanceof ModbusDataType.Bit dataType) {
          device.processImage.with(
              tx ->
                  tx.writeHoldingRegisters(
                      holdingRegisterMap -> {
                        try {
                          writeBitToRegister(address, variant, dataType, holdingRegisterMap);
                        } catch (UaException e) {
                          throw new RuntimeException(e);
                        }
                      }));
        } else {
          byte[] registers = ModbusByteUtil.getBytesForValue(variant.getValue(), address);

          device.processImage.with(
              tx ->
                  tx.writeHoldingRegisters(
                      holdingRegisterMap -> {
                        for (int i = 0; i < registers.length / 2; i++) {
                          byte[] value = new byte[] {registers[i * 2], registers[i * 2 + 1]};
                          holdingRegisterMap.put(address.getOffset() + i, value);
                        }
                      }));
        }
      }
      case INPUT_REGISTERS -> {
        checkDataType(address.getDataType(), variant);

        if (address.getDataType() instanceof ModbusDataType.Bit dataType) {
          device.processImage.with(
              tx ->
                  tx.writeInputRegisters(
                      inputRegisterMap -> {
                        try {
                          writeBitToRegister(address, variant, dataType, inputRegisterMap);
                        } catch (UaException e) {
                          throw new RuntimeException(e);
                        }
                      }));
        } else {
          byte[] registers = ModbusByteUtil.getBytesForValue(variant.getValue(), address);

          device.processImage.with(
              tx ->
                  tx.writeInputRegisters(
                      inputRegisterMap -> {
                        for (int i = 0; i < registers.length / 2; i++) {
                          byte[] value = new byte[] {registers[i * 2], registers[i * 2 + 1]};
                          inputRegisterMap.put(address.getOffset() + i, value);
                        }
                      }));
        }
      }
      default -> throw new IllegalArgumentException("area: " + address.getArea());
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
    Class<?> expectedType = dataType.getOpcUaDataType().getBackingClass();

    if (!expectedType.isAssignableFrom(actualType)) {
      throw new UaException(StatusCodes.Bad_TypeMismatch);
    }
  }

  private static void writeBitToRegister(
      ModbusAddress address,
      Variant variant,
      ModbusDataType.Bit dataType,
      Map<Integer, byte[]> registerMap)
      throws UaException {

    int bitIndex = dataType.bit();
    ModbusDataType underlyingType = dataType.underlyingType();

    var bytes = new byte[underlyingType.getRegisterCount() * 2];

    for (int i = 0; i < bytes.length / 2; i++) {
      byte[] value = registerMap.getOrDefault(address.getOffset() + i, new byte[2]);
      bytes[i * 2] = value[0];
      bytes[i * 2 + 1] = value[1];
    }

    Object underlyingValue =
        ModbusByteUtil.getValueForBytes(bytes, underlyingType, address.getDataTypeModifiers());

    if (underlyingValue instanceof Number n) {
      long mask = 1L << bitIndex;
      long v = n.longValue();
      if (variant.getValue() instanceof Boolean b) {
        if (b) {
          v |= mask;
        } else {
          v &= ~mask;
        }
        byte[] newBytes =
            ModbusByteUtil.getBytesForValue(
                castToUnderlying(v, underlyingType),
                underlyingType,
                address.getDataTypeModifiers());

        for (int i = 0; i < newBytes.length / 2; i++) {
          byte[] value = new byte[] {newBytes[i * 2], newBytes[i * 2 + 1]};
          registerMap.put(address.getOffset() + i, value);
        }
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

  // endregion

  // region Subscribe

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

  // endregion

  // region ProcessImage Load/Save

  private void loadProcessImage() {
    device.processImage.with(
        tx -> {
          loadCoils(tx);
          loadDiscreteInputs(tx);
          loadHoldingRegisters(tx);
          loadInputRegisters(tx);
        });
  }

  private void loadCoils(Transaction tx) {
    Path path = device.deviceContext.getDeviceFolderPath().resolve("coils.bin").toAbsolutePath();

    try (var coilsFile = new RandomAccessFile(path.toFile(), "rw")) {
      coilsFile.setLength(65535);
      byte[] coils = new byte[65535];
      coilsFile.readFully(coils);

      tx.writeCoils(
          coilMap -> {
            for (int i = 0; i < coils.length; i++) {
              if (coils[i] != 0) {
                coilMap.put(i, true);
              }
            }
          });
    } catch (IOException e) {
      logger.error("Error reading coils.bin", e);
    }
  }

  private void loadDiscreteInputs(Transaction tx) {
    Path path =
        device.deviceContext.getDeviceFolderPath().resolve("discreteInputs.bin").toAbsolutePath();

    try (var discreteInputsFile = new RandomAccessFile(path.toFile(), "rw")) {
      discreteInputsFile.setLength(65535);
      byte[] discreteInputs = new byte[65535];
      discreteInputsFile.readFully(discreteInputs);

      tx.writeDiscreteInputs(
          discreteInputMap -> {
            for (int i = 0; i < discreteInputs.length; i++) {
              if (discreteInputs[i] != 0) {
                discreteInputMap.put(i, true);
              }
            }
          });
    } catch (IOException e) {
      logger.error("Error reading discreteInputs.bin", e);
    }
  }

  private void loadHoldingRegisters(Transaction tx) {
    Path path =
        device.deviceContext.getDeviceFolderPath().resolve("holdingRegisters.bin").toAbsolutePath();

    try (var holdingRegistersFile = new RandomAccessFile(path.toFile(), "rw")) {
      holdingRegistersFile.setLength(65535 * 2);
      byte[] holdingRegisters = new byte[65535 * 2];
      holdingRegistersFile.readFully(holdingRegisters);

      tx.writeHoldingRegisters(
          holdingRegisterMap -> {
            for (int i = 0; i < holdingRegisters.length; i += 2) {
              byte high = holdingRegisters[i];
              byte low = holdingRegisters[i + 1];
              if (high != 0 || low != 0) {
                holdingRegisterMap.put(i / 2, new byte[] {high, low});
              }
            }
          });
    } catch (IOException e) {
      logger.error("Error reading holdingRegisters.bin", e);
    }
  }

  private void loadInputRegisters(Transaction tx) {
    Path path =
        device.deviceContext.getDeviceFolderPath().resolve("inputRegisters.bin").toAbsolutePath();

    try (var inputRegistersFile = new RandomAccessFile(path.toFile(), "rw")) {
      inputRegistersFile.setLength(65535 * 2);
      byte[] inputRegisters = new byte[65535 * 2];
      inputRegistersFile.readFully(inputRegisters);

      tx.writeInputRegisters(
          inputRegisterMap -> {
            for (int i = 0; i < inputRegisters.length; i += 2) {
              byte high = inputRegisters[i];
              byte low = inputRegisters[i + 1];
              if (high != 0 || low != 0) {
                inputRegisterMap.put(i / 2, new byte[] {high, low});
              }
            }
          });
    } catch (IOException e) {
      logger.error("Error reading inputRegisters.bin", e);
    }
  }

  private class ModificationListener implements ProcessImage.ModificationListener {

    private final ExecutionQueue modificationQueue = new ExecutionQueue(OpcUa.SHARED_EXECUTOR);

    @Override
    public void onCoilsModified(List<CoilModification> modifications) {
      modificationQueue.submit(
          () -> {
            logger.trace("onCoilsModified: {}", modifications);

            Path path =
                device.deviceContext.getDeviceFolderPath().resolve("coils.bin").toAbsolutePath();

            try (var coilsFile = new RandomAccessFile(path.toFile(), "rw")) {
              for (CoilModification m : modifications) {
                coilsFile.seek(m.address());
                coilsFile.write(m.value() ? 1 : 0);
              }
            } catch (IOException e) {
              logger.error("Error writing coils.bin", e);
            }
          });
    }

    @Override
    public void onDiscreteInputsModified(List<DiscreteInputModification> modifications) {
      modificationQueue.submit(
          () -> {
            logger.trace("onDiscreteInputsModified: {}", modifications);

            Path path =
                device
                    .deviceContext
                    .getDeviceFolderPath()
                    .resolve("discreteInputs.bin")
                    .toAbsolutePath();

            try (var discreteInputsFile = new RandomAccessFile(path.toFile(), "rw")) {
              for (DiscreteInputModification m : modifications) {
                discreteInputsFile.seek(m.address());
                discreteInputsFile.write(m.value() ? 1 : 0);
              }
            } catch (IOException e) {
              logger.error("Error writing discreteInputs.bin", e);
            }
          });
    }

    @Override
    public void onHoldingRegistersModified(List<HoldingRegisterModification> modifications) {
      modificationQueue.submit(
          () -> {
            logger.trace("onHoldingRegistersModified: {}", modifications);

            Path path =
                device
                    .deviceContext
                    .getDeviceFolderPath()
                    .resolve("holdingRegisters.bin")
                    .toAbsolutePath();

            try (var holdingRegistersFile = new RandomAccessFile(path.toFile(), "rw")) {
              for (HoldingRegisterModification m : modifications) {
                holdingRegistersFile.seek(m.address() * 2L);
                holdingRegistersFile.writeByte(m.value()[0]);
                holdingRegistersFile.writeByte(m.value()[1]);
              }
            } catch (IOException e) {
              logger.error("Error writing holdingRegisters.bin", e);
            }
          });
    }

    @Override
    public void onInputRegistersModified(List<InputRegisterModification> modifications) {
      modificationQueue.submit(
          () -> {
            logger.trace("onInputRegistersModified: {}", modifications);

            Path path =
                device
                    .deviceContext
                    .getDeviceFolderPath()
                    .resolve("inputRegisters.bin")
                    .toAbsolutePath();

            try (var inputRegistersFile = new RandomAccessFile(path.toFile(), "rw")) {
              for (InputRegisterModification m : modifications) {
                inputRegistersFile.seek(m.address() * 2L);
                inputRegistersFile.writeByte(m.value()[0]);
                inputRegistersFile.writeByte(m.value()[1]);
              }
            } catch (IOException e) {
              logger.error("Error writing inputRegisters.bin", e);
            }
          });
    }
  }

  // endregion

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

      logger.trace("checking {}", id);
      try {
        ModbusAddressParser.parse(id);
        return true;
      } catch (Exception e) {
        return false;
      }
    }
  }
}
