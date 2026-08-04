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
import com.kevinherron.ignition.modbus.address.ModbusAddress.ArrayAddress;
import com.kevinherron.ignition.modbus.address.ModbusAddress.ScalarAddress;
import com.kevinherron.ignition.modbus.address.ModbusAddressParser;
import com.kevinherron.ignition.modbus.address.ModbusDataType;
import com.kevinherron.ignition.modbus.util.ModbusByteUtil;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.eclipse.milo.opcua.sdk.core.AccessLevel;
import org.eclipse.milo.opcua.sdk.core.NumericRange;
import org.eclipse.milo.opcua.sdk.core.Reference;
import org.eclipse.milo.opcua.sdk.core.Reference.Direction;
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
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.OpcUaDataType;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.UaRuntimeException;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.Matrix;
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
import org.eclipse.milo.opcua.stack.core.util.ArrayUtil;
import org.eclipse.milo.opcua.stack.core.util.ExecutionQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModbusAddressSpace implements AddressSpaceFragment, Lifecycle {

  /** Shared default for absent register entries; read-only, never mutated. */
  private static final byte[] EMPTY_REGISTER = new byte[2];

  private static final Logger logger = LoggerFactory.getLogger(ModbusAddressSpace.class);

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

    // Give each of the Nodes in ModbusAddressSpace a HasTypeDefinition reference pointing to
    // BaseDataVariableType.

    var results = new ArrayList<ReferenceResult>();

    for (NodeId nodeId : nodeIds) {
      var result =
          ReferenceResult.of(
              List.of(
                  new Reference(
                      nodeId,
                      NodeIds.HasTypeDefinition,
                      NodeIds.BaseDataVariableType.expanded(),
                      Direction.FORWARD)));

      results.add(result);
    }

    return results;
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
    var pendingValueReads = new ArrayList<PendingValueRead>();

    for (PendingRead pending : pendingReads) {
      ReadValueId readValueId = pending.readValueId;

      String id = readValueId.getNodeId().getIdentifier().toString();
      String name = "[%s]".formatted(device.deviceContext.getName());
      String addr = id.substring(id.indexOf(name) + name.length());

      try {
        ModbusAddress address = ModbusAddressParser.parse(addr);
        AttributeId attributeId = AttributeId.from(readValueId.getAttributeId()).orElse(null);

        if (attributeId == null) {
          pending.value = new DataValue(StatusCodes.Bad_AttributeIdInvalid);
        } else if (attributeId == AttributeId.Value) {
          pendingValueReads.add(
              new PendingValueRead(pending, new ValueRead(address, readValueId.getIndexRange())));
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

    List<DataValue> values =
        readValueAttributes(
            device.processImage, pendingValueReads.stream().map(PendingValueRead::read).toList());
    for (int i = 0; i < pendingValueReads.size(); i++) {
      pendingValueReads.get(i).pending().value = values.get(i);
    }

    return pendingReads.stream().map(p -> p.value).toList();
  }

  static List<DataValue> readValueAttributes(ProcessImage processImage, List<ValueRead> reads) {

    var values = new ArrayList<DataValue>(reads.size());
    for (ValueRead read : reads) {
      try {
        values.add(
            new DataValue(readValueAttribute(processImage, read.address(), read.indexRange())));
      } catch (UaException e) {
        values.add(new DataValue(e.getStatusCode()));
      } catch (RuntimeException e) {
        logger.error("Error reading value: address={}", read.address(), e);
        values.add(new DataValue(StatusCodes.Bad_InternalError));
      }
    }
    return values;
  }

  static Variant readValueAttribute(
      ProcessImage processImage, ModbusAddress address, String indexRange) throws UaException {

    NumericRange range = null;
    if (indexRange != null && !indexRange.isEmpty()) {
      range = NumericRange.parse(indexRange);
      // OPC UA Part 4 defines IndexRange on scalar String values as sub-string selection.
      if (address instanceof ScalarAddress
          && !(address.getDataType() instanceof ModbusDataType.String)) {
        throw new UaException(StatusCodes.Bad_IndexRangeNoData);
      }
    }

    Variant fullValue =
        switch (address.getArea()) {
          case COILS -> readBooleanValue(processImage, address, false);
          case DISCRETE_INPUTS -> readBooleanValue(processImage, address, true);
          case HOLDING_REGISTERS -> {
            byte[] bs = processImage.get(tx -> readHoldingRegisters(tx, address));

            yield new Variant(ModbusByteUtil.getValueForBytes(bs, address));
          }
          case INPUT_REGISTERS -> {
            byte[] bs = processImage.get(tx -> readInputRegisters(tx, address));

            yield new Variant(ModbusByteUtil.getValueForBytes(bs, address));
          }
        };

    if (range != null) {
      return new Variant(readValueAtRange(fullValue.getValue(), range));
    }
    return fullValue;
  }

  private static Variant readBooleanValue(
      ProcessImage processImage, ModbusAddress address, boolean discreteInputs) {

    if (address instanceof ArrayAddress array) {
      boolean[] values =
          processImage.get(
              tx ->
                  discreteInputs
                      ? tx.readDiscreteInputs(map -> readBooleans(map, array))
                      : tx.readCoils(map -> readBooleans(map, array)));

      return new Variant(shapeBooleanArray(values, array));
    } else if (address instanceof ScalarAddress scalar) {
      boolean value =
          processImage.get(
              tx ->
                  discreteInputs
                      ? tx.readDiscreteInputs(map -> map.getOrDefault(scalar.getOffset(), false))
                      : tx.readCoils(map -> map.getOrDefault(scalar.getOffset(), false)));

      return new Variant(value);
    } else {
      throw new IllegalArgumentException("address: " + address);
    }
  }

  static Object readValueAtRange(Object value, NumericRange range) throws UaException {
    Object valueAtRange;
    if (value instanceof Matrix matrix) {
      valueAtRange = NumericRange.readFromValueAtRange(matrix.nestedArrayValue(), range);
      if (ArrayUtil.getValueRank(valueAtRange) > 1) {
        valueAtRange = new Matrix(valueAtRange);
      }
    } else {
      valueAtRange = NumericRange.readFromValueAtRange(value, range);
    }

    return valueAtRange;
  }

  static boolean[] readBooleans(Map<Integer, Boolean> booleans, ArrayAddress address) {
    int totalElements = address.getElementCount();

    boolean[] values = new boolean[totalElements];

    for (int elementIndex = 0; elementIndex < totalElements; elementIndex++) {
      int elementOffset = address.getOffset() + elementIndex;
      values[elementIndex] = booleans.getOrDefault(elementOffset, false);
    }

    return values;
  }

  static Object shapeBooleanArray(boolean[] values, ArrayAddress address) {
    Boolean[] boxedValues = new Boolean[values.length];
    for (int i = 0; i < values.length; i++) {
      boxedValues[i] = values[i];
    }

    int[] dimensions = address.getDimensions();

    if (dimensions.length == 1) {
      return boxedValues;
    } else {
      return new Matrix(boxedValues, dimensions, OpcUaDataType.Boolean);
    }
  }

  private static byte[] readHoldingRegisters(Transaction tx, ModbusAddress address) {
    return tx.readHoldingRegisters(registers -> readRegisters(registers, address));
  }

  private static byte[] readInputRegisters(Transaction tx, ModbusAddress address) {
    return tx.readInputRegisters(registers -> readRegisters(registers, address));
  }

  static byte[] readRegisters(Map<Integer, byte[]> registers, ModbusAddress address) {
    int elementCount =
        address instanceof ArrayAddress arrayAddress ? arrayAddress.getElementCount() : 1;
    int registerCount = address.getDataType().getRegisterCount();

    var value = new byte[elementCount * registerCount * 2];

    for (int i = 0; i < value.length / 2; i++) {
      byte[] bs = registers.getOrDefault(address.getOffset() + i, EMPTY_REGISTER);
      value[i * 2] = bs[0];
      value[i * 2 + 1] = bs[1];
    }

    return value;
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
          case DataType, ValueRank, ArrayDimensions, AccessLevel, UserAccessLevel ->
              readAddressAttribute(attributeId, address).getValue();

          case Value ->
              throw new UaException(StatusCodes.Bad_InternalError, "attributeId: " + attributeId);

          default ->
              throw new UaException(
                  StatusCodes.Bad_AttributeIdInvalid, "attributeId: " + attributeId);
        };

    return new Variant(o);
  }

  static Variant readAddressAttribute(AttributeId attributeId, ModbusAddress address)
      throws UaException {

    Object value =
        switch (attributeId) {
          case DataType -> address.getDataType().getOpcUaDataType().getNodeId();
          case ValueRank -> {
            if (address instanceof ArrayAddress array) {
              yield array.getDimensions().length;
            } else {
              yield ValueRank.Scalar.getValue();
            }
          }
          case ArrayDimensions -> {
            if (address instanceof ArrayAddress array) {
              yield Arrays.stream(array.getDimensions())
                  .mapToObj(Unsigned::uint)
                  .toArray(UInteger[]::new);
            } else {
              yield null;
            }
          }

          // All areas are Read/Write from the OPC UA side, otherwise nothing would be able to
          // update IR and DI values!
          case AccessLevel, UserAccessLevel -> AccessLevel.toValue(AccessLevel.READ_WRITE);

          default ->
              throw new UaException(
                  StatusCodes.Bad_AttributeIdInvalid, "attributeId: " + attributeId);
        };

    return new Variant(value);
  }

  // endregion

  // region Write

  @Override
  public List<StatusCode> write(WriteContext context, List<WriteValue> writeValues) {
    var pendingWrites = writeValues.stream().map(PendingWrite::new).toList();

    var pendingValueWrites = new ArrayList<PendingValueWrite>();

    for (PendingWrite pending : pendingWrites) {
      WriteValue writeValue = pending.writeValue;

      AttributeId attributeId = AttributeId.from(writeValue.getAttributeId()).orElse(null);

      if (attributeId == null) {
        pending.statusCode = new StatusCode(StatusCodes.Bad_AttributeIdInvalid);
      } else if (attributeId == AttributeId.Value) {
        String id = writeValue.getNodeId().getIdentifier().toString();
        String name = "[%s]".formatted(device.deviceContext.getName());
        String addr = id.substring(id.indexOf(name) + name.length());
        try {
          ModbusAddress address = ModbusAddressParser.parse(addr);
          pendingValueWrites.add(
              new PendingValueWrite(
                  pending,
                  new ValueWrite(
                      address, writeValue.getValue().getValue(), writeValue.getIndexRange())));
        } catch (Exception e) {
          pending.statusCode = new StatusCode(StatusCodes.Bad_ConfigurationError);
        }
      } else {
        pending.statusCode = new StatusCode(StatusCodes.Bad_NotWritable);
      }
    }

    List<StatusCode> statuses =
        writeValueAttributes(
            device.processImage,
            pendingValueWrites.stream().map(PendingValueWrite::write).toList());
    for (int i = 0; i < pendingValueWrites.size(); i++) {
      pendingValueWrites.get(i).pending().statusCode = statuses.get(i);
    }

    return pendingWrites.stream().map(p -> p.statusCode).toList();
  }

  static List<StatusCode> writeValueAttributes(ProcessImage processImage, List<ValueWrite> writes) {

    var statuses = new ArrayList<StatusCode>(writes.size());
    for (ValueWrite write : writes) {
      try {
        writeValueAttribute(processImage, write.address(), write.variant(), write.indexRange());
        statuses.add(StatusCode.GOOD);
      } catch (UaException e) {
        statuses.add(e.getStatusCode());
      } catch (RuntimeException e) {
        statuses.add(
            UaException.extract(e)
                .map(UaException::getStatusCode)
                .orElse(new StatusCode(StatusCodes.Bad_InternalError)));
      }
    }
    return statuses;
  }

  static void writeValueAttribute(
      ProcessImage processImage, ModbusAddress address, Variant variant, String indexRange)
      throws UaException {

    try {
      processImage.with(tx -> writeValueAttribute(tx, address, variant, indexRange));
    } catch (Exception e) {
      throw UaException.extract(e).orElse(new UaException(StatusCodes.Bad_InternalError));
    }
  }

  private static void writeValueAttribute(
      Transaction tx, ModbusAddress address, Variant variant, String indexRange) {

    switch (address.getArea()) {
      case COILS ->
          tx.writeCoils(coilMap -> writeBooleanValue(coilMap, address, variant, indexRange));
      case DISCRETE_INPUTS ->
          tx.writeDiscreteInputs(
              discreteInputMap ->
                  writeBooleanValue(discreteInputMap, address, variant, indexRange));
      case HOLDING_REGISTERS ->
          tx.writeHoldingRegisters(
              holdingRegisterMap ->
                  writeRegisterValue(holdingRegisterMap, address, variant, indexRange));
      case INPUT_REGISTERS ->
          tx.writeInputRegisters(
              inputRegisterMap ->
                  writeRegisterValue(inputRegisterMap, address, variant, indexRange));
    }
  }

  /**
   * Resolve the full value to write, merging {@code variant} into the current value when an index
   * range is present.
   */
  private static Variant resolveWriteValue(
      ModbusAddress address, Variant variant, String indexRange, CurrentValueReader currentValue)
      throws UaException {

    if (indexRange == null || indexRange.isEmpty()) {
      return variant;
    }

    NumericRange range = NumericRange.parse(indexRange);
    // Arrays support element ranges; scalar String values support sub-string ranges.
    boolean rangeSupported =
        address instanceof ArrayAddress || address.getDataType() instanceof ModbusDataType.String;
    if (!rangeSupported) {
      throw new UaException(StatusCodes.Bad_IndexRangeNoData);
    }

    return new Variant(writeValueAtRange(currentValue.read(address), variant.getValue(), range));
  }

  @FunctionalInterface
  private interface CurrentValueReader {
    Object read(ModbusAddress address) throws UaException;
  }

  private static void writeBooleanValue(
      Map<Integer, Boolean> booleanMap, ModbusAddress address, Variant variant, String indexRange) {

    try {
      Variant fullVariant =
          resolveWriteValue(
              address,
              variant,
              indexRange,
              addr -> {
                // Only ArrayAddress reaches here: scalar coils are Bool, so
                // resolveWriteValue rejects their index-range writes.
                ArrayAddress array = (ArrayAddress) addr;
                return shapeBooleanArray(readBooleans(booleanMap, array), array);
              });

      if (address instanceof ArrayAddress array) {
        writeBooleanArray(booleanMap, fullVariant, array);
      } else if (address instanceof ScalarAddress scalar) {
        if (fullVariant.getValue() instanceof Boolean b) {
          booleanMap.put(scalar.getOffset(), b);
        } else {
          throw new UaException(StatusCodes.Bad_TypeMismatch);
        }
      } else {
        throw new IllegalArgumentException("address: " + address);
      }
    } catch (UaException e) {
      throw new UaRuntimeException(e);
    }
  }

  private static void writeRegisterValue(
      Map<Integer, byte[]> registerMap, ModbusAddress address, Variant variant, String indexRange) {

    try {
      Variant fullVariant =
          resolveWriteValue(
              address,
              variant,
              indexRange,
              addr -> ModbusByteUtil.getValueForBytes(readRegisters(registerMap, addr), addr));

      if (address.getDataType() instanceof ModbusDataType.Bit dataType) {
        writeBitToRegister(address, fullVariant, dataType, registerMap);
      } else {
        byte[] registers = getRegisterWriteBytes(address, fullVariant);
        for (int i = 0; i < registers.length / 2; i++) {
          byte[] value = new byte[] {registers[i * 2], registers[i * 2 + 1]};
          registerMap.put(address.getOffset() + i, value);
        }
      }
    } catch (UaException e) {
      throw new UaRuntimeException(e);
    }
  }

  static Object writeValueAtRange(Object currentValue, Object updateValue, NumericRange range)
      throws UaException {

    if (currentValue == null || updateValue == null) {
      throw new UaException(StatusCodes.Bad_IndexRangeNoData);
    }

    if (currentValue instanceof Matrix matrix) {
      currentValue = matrix.nestedArrayValue();
    }
    if (updateValue instanceof Matrix matrix) {
      updateValue = matrix.nestedArrayValue();
    }

    int[] updateDimensions = ArrayUtil.getDimensions(updateValue);
    NumericRange.Bounds[] bounds = range.getBounds();
    // For String values an extra final range dimension selects characters within an
    // element, so the update value has one fewer dimension than the range has bounds.
    boolean subStringRange =
        updateDimensions.length == bounds.length - 1 && hasStringLeaf(updateValue);
    if (!subStringRange && updateDimensions.length != bounds.length) {
      throw new UaException(StatusCodes.Bad_IndexRangeNoData);
    }
    for (int i = 0; i < updateDimensions.length; i++) {
      int expectedLength = bounds[i].getHigh() - bounds[i].getLow() + 1;
      if (updateDimensions[i] != expectedLength) {
        throw new UaException(StatusCodes.Bad_IndexRangeNoData);
      }
    }

    Object valueAtRange = NumericRange.writeToValueAtRange(currentValue, updateValue, range);
    if (ArrayUtil.getValueRank(valueAtRange) > 1) {
      valueAtRange = new Matrix(valueAtRange);
    }

    return valueAtRange;
  }

  private static boolean hasStringLeaf(Object value) {
    Class<?> type = value.getClass();
    while (type.isArray()) {
      type = type.getComponentType();
    }
    return type == String.class;
  }

  static byte[] getRegisterWriteBytes(ModbusAddress address, Variant variant) throws UaException {
    return ModbusByteUtil.getBytesForValue(variant.getValue(), address);
  }

  static void writeBooleanArray(
      Map<Integer, Boolean> booleanMap, Variant variant, ArrayAddress array) throws UaException {

    Object value = variant.getValue();

    if (array.getDimensions().length > 1) {
      if (!(value instanceof Matrix matrix)
          || matrix.isNull()
          || !Arrays.equals(matrix.getDimensions(), array.getDimensions())) {
        throw new UaException(StatusCodes.Bad_TypeMismatch);
      }
      value = matrix.getElements();
    }

    if (!(value instanceof Boolean[] booleans) || booleans.length != array.getElementCount()) {
      throw new UaException(StatusCodes.Bad_TypeMismatch);
    }

    for (int i = 0; i < booleans.length; i++) {
      booleanMap.put(array.getOffset() + i, booleans[i]);
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
      byte[] value = registerMap.getOrDefault(address.getOffset() + i, EMPTY_REGISTER);
      bytes[i * 2] = value[0];
      bytes[i * 2 + 1] = value[1];
    }

    Object underlyingValue =
        ModbusByteUtil.getScalarValueForBytes(
            bytes, underlyingType, address.getDataTypeModifiers());

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
            ModbusByteUtil.getBytesForScalarValue(
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

    try (FileChannel channel = openFileChannel(path)) {
      channel.truncate(65535);
      ByteBuffer coils = ByteBuffer.allocate(65535);
      readChannelIntoBuffer(coils, channel);
      coils.flip();

      tx.writeCoils(
          coilMap -> {
            for (int i = 0; i < coils.limit(); i++) {
              if (coils.get(i) != 0) {
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

    try (FileChannel channel = openFileChannel(path)) {
      channel.truncate(65535);
      ByteBuffer discreteInputs = ByteBuffer.allocate(65535);
      readChannelIntoBuffer(discreteInputs, channel);
      discreteInputs.flip();

      tx.writeDiscreteInputs(
          discreteInputMap -> {
            for (int i = 0; i < discreteInputs.limit(); i++) {
              if (discreteInputs.get(i) != 0) {
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

    try (FileChannel channel = openFileChannel(path)) {
      int size = 65535 * 2;
      channel.truncate(size);
      ByteBuffer holdingRegisters = ByteBuffer.allocate(size);
      readChannelIntoBuffer(holdingRegisters, channel);
      holdingRegisters.flip();

      tx.writeHoldingRegisters(
          holdingRegisterMap -> {
            for (int i = 0; i < holdingRegisters.limit(); i += 2) {
              byte high = holdingRegisters.get(i);
              byte low = holdingRegisters.get(i + 1);
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

    try (FileChannel channel = openFileChannel(path)) {
      int size = 65535 * 2;
      channel.truncate(size);
      ByteBuffer inputRegisters = ByteBuffer.allocate(size);
      readChannelIntoBuffer(inputRegisters, channel);
      inputRegisters.flip();

      tx.writeInputRegisters(
          inputRegisterMap -> {
            for (int i = 0; i < inputRegisters.limit(); i += 2) {
              byte high = inputRegisters.get(i);
              byte low = inputRegisters.get(i + 1);
              if (high != 0 || low != 0) {
                inputRegisterMap.put(i / 2, new byte[] {high, low});
              }
            }
          });
    } catch (IOException e) {
      logger.error("Error reading inputRegisters.bin", e);
    }
  }

  /**
   * Opens a {@link FileChannel} for the specified file path with read, write, and create options.
   *
   * @param path the {@link Path} representing the file to be opened.
   * @return the {@link FileChannel} instance associated with the specified file.
   * @throws IOException if an I/O error occurs while opening the file.
   */
  private static FileChannel openFileChannel(Path path) throws IOException {
    return FileChannel.open(
        path, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE);
  }

  /**
   * Reads data from the given {@link FileChannel} into the provided {@link ByteBuffer} until the
   * buffer is fully filled or the end of the file is reached.
   *
   * @param buffer the {@link ByteBuffer} into which data will be read.
   * @param channel the {@link FileChannel} from which data will be read.
   * @throws IOException if an I/O error occurs during reading from the file channel.
   */
  private static void readChannelIntoBuffer(ByteBuffer buffer, FileChannel channel)
      throws IOException {

    while (buffer.remaining() > 0) {
      int bytesRead = channel.read(buffer);
      if (bytesRead == -1) {
        break; // EOF
      }
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

            try (FileChannel channel = openFileChannel(path)) {
              ByteBuffer buffer = ByteBuffer.allocate(1);

              for (CoilModification m : modifications) {
                buffer.clear();
                buffer.put((byte) (m.value() ? 1 : 0));
                buffer.flip();
                channel.position(m.address());
                int bytesWritten = channel.write(buffer);
                if (bytesWritten != buffer.capacity()) {
                  throw new IOException(
                      "failed to write all bytes to coils.bin: wrote %s of %s"
                          .formatted(bytesWritten, buffer.capacity()));
                }
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

            try (FileChannel channel = openFileChannel(path)) {
              ByteBuffer buffer = ByteBuffer.allocate(1);

              for (DiscreteInputModification m : modifications) {
                buffer.clear();
                buffer.put((byte) (m.value() ? 1 : 0));
                buffer.flip();
                channel.position(m.address());
                int bytesWritten = channel.write(buffer);
                if (bytesWritten != buffer.capacity()) {
                  throw new IOException(
                      "failed to write all bytes to discreteInputs.bin: wrote %s of %s"
                          .formatted(bytesWritten, buffer.capacity()));
                }
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

            try (FileChannel channel = openFileChannel(path)) {
              ByteBuffer buffer = ByteBuffer.allocate(2);

              for (HoldingRegisterModification m : modifications) {
                buffer.clear();
                buffer.put(m.value()[0]);
                buffer.put(m.value()[1]);
                buffer.flip();
                channel.position(m.address() * 2L);
                int bytesWritten = channel.write(buffer);
                if (bytesWritten != buffer.capacity()) {
                  throw new IOException(
                      "failed to write all bytes to holdingRegisters.bin: wrote %s of %s"
                          .formatted(bytesWritten, buffer.capacity()));
                }
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

            try (FileChannel channel = openFileChannel(path)) {
              ByteBuffer buffer = ByteBuffer.allocate(2);

              for (InputRegisterModification m : modifications) {
                buffer.clear();
                buffer.put(m.value()[0]);
                buffer.put(m.value()[1]);
                buffer.flip();
                channel.position(m.address() * 2L);
                int bytesWritten = channel.write(buffer);
                if (bytesWritten != buffer.capacity()) {
                  throw new IOException(
                      "failed to write all bytes to inputRegisters.bin: wrote %s of %s"
                          .formatted(bytesWritten, buffer.capacity()));
                }
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

  record ValueRead(ModbusAddress address, String indexRange) {}

  record ValueWrite(ModbusAddress address, Variant variant, String indexRange) {}

  private record PendingValueRead(PendingRead pending, ValueRead read) {}

  private static class PendingWrite {

    volatile StatusCode statusCode;
    final WriteValue writeValue;

    private PendingWrite(WriteValue writeValue) {
      this.writeValue = writeValue;
    }
  }

  private record PendingValueWrite(PendingWrite pending, ValueWrite write) {}

  private static class ModbusAddressFilter extends SimpleAddressSpaceFilter {

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
      return ModbusAddressParser.isValidAddress(id);
    }
  }
}
