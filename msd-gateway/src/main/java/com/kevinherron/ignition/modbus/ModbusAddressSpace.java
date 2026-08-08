package com.kevinherron.ignition.modbus;

import com.digitalpetri.modbus.server.ProcessImage;
import com.kevinherron.ignition.modbus.address.ModbusAddress;
import com.kevinherron.ignition.modbus.address.ModbusAddress.ArrayAddress;
import com.kevinherron.ignition.modbus.address.ModbusAddressParser;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.milo.opcua.sdk.core.AccessLevel;
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
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.UaRuntimeException;
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

/**
 * Presents a {@link ModbusServerDevice} process image as OPC UA variable nodes.
 *
 * <p>The fragment resolves Modbus address strings as variable nodes, exposes their value and
 * metadata attributes, and supplies monitored values through the OPC UA subscription model. Value
 * operations select images through {@link ProcessImageManager}; unqualified addresses select unit
 * 0 in separate mode, and mixed-unit batches retain their original result order.
 *
 * <p>Its {@link Lifecycle} must be started before use and shut down with the owning device.
 */
public class ModbusAddressSpace implements AddressSpaceFragment, Lifecycle {

  private static final Logger logger = LoggerFactory.getLogger(ModbusAddressSpace.class);

  private final AddressSpaceFilter filter;
  private final SubscriptionModel subscriptionModel;

  private final ModbusServerDevice device;

  /**
   * Creates an unstarted address space for a Modbus server device.
   *
   * @param device the device whose process images and OPC UA context back this address space.
   */
  public ModbusAddressSpace(ModbusServerDevice device) {
    this.device = device;

    filter = new ModbusAddressFilter(device.deviceContext.getName());

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
            String indexRange = readValueId.getIndexRange();
            if (indexRange != null && !indexRange.isEmpty()) {
              // OPC UA Part 4: an IndexRange applies to any attribute; non-array attributes
              // must return Bad_IndexRangeNoData rather than the full value.
              var range = ModbusValueAccess.parseNumericRange(indexRange);
              Object value = v.getValue();
              if (value == null) {
                throw new UaException(StatusCodes.Bad_IndexRangeNoData);
              }
              v = new Variant(ModbusValueAccess.readValueAtRange(value, range));
            }
            pending.value = new DataValue(v);
          } catch (UaException e) {
            pending.value = new DataValue(e.getStatusCode());
          }
        }
      } catch (Exception e) {
        logger.error("Error reading value: id={}, addr={}", id, addr, e);
        pending.value = new DataValue(StatusCodes.Bad_NodeIdInvalid);
      }
    }

    List<DataValue> values =
        readValueAttributes(
            device.processImageManager,
            pendingValueReads.stream().map(PendingValueRead::read).toList());
    for (int i = 0; i < pendingValueReads.size(); i++) {
      pendingValueReads.get(i).pending().value = values.get(i);
    }

    return pendingReads.stream().map(p -> p.value).toList();
  }

  static List<DataValue> readValueAttributes(ProcessImage processImage, List<ValueRead> reads) {
    if (reads.isEmpty()) {
      return List.of();
    }

    // One transaction for the whole batch: a single lock acquisition and a consistent
    // snapshot across items.
    return processImage.get(
        tx -> {
          var values = new ArrayList<DataValue>(reads.size());
          for (ValueRead read : reads) {
            try {
              values.add(
                  new DataValue(
                      ModbusValueAccess.readValueAttribute(
                          tx, read.address(), read.indexRange())));
            } catch (UaException e) {
              values.add(new DataValue(e.getStatusCode()));
            } catch (RuntimeException e) {
              logger.error("Error reading value: address={}", read.address(), e);
              values.add(new DataValue(StatusCodes.Bad_InternalError));
            }
          }
          return values;
        });
  }

  /**
   * Reads a batch through the manager-selected process images.
   *
   * <p>Each image is read in one sequential transaction. Returned values correspond positionally
   * to {@code reads}, including when the batch contains several unit IDs. A request racing device
   * shutdown returns {@code Bad_Shutdown}; a persistence initialization failure returns {@code
   * Bad_InternalError}.
   *
   * @param processImageManager the manager that resolves each address to an image.
   * @param reads the value requests in OPC UA request order.
   * @return the values and statuses in the same order as {@code reads}.
   */
  static List<DataValue> readValueAttributes(
      ProcessImageManager processImageManager, List<ValueRead> reads) {
    if (reads.isEmpty()) {
      return List.of();
    }

    Map<ProcessImage, List<IndexedValueRead>> groups = new LinkedHashMap<>();
    DataValue[] values = new DataValue[reads.size()];
    for (int i = 0; i < reads.size(); i++) {
      ValueRead read = reads.get(i);
      try {
        ProcessImage processImage = processImageManager.get(read.address());
        groups.computeIfAbsent(processImage, ignored -> new ArrayList<>())
            .add(new IndexedValueRead(i, read));
      } catch (IllegalStateException e) {
        values[i] = new DataValue(StatusCodes.Bad_Shutdown);
      } catch (UncheckedIOException e) {
        // A persistent image that cannot be initialized is an internal device failure.
        values[i] = new DataValue(StatusCodes.Bad_InternalError);
      }
    }

    for (Map.Entry<ProcessImage, List<IndexedValueRead>> entry : groups.entrySet()) {
      List<IndexedValueRead> indexedReads = entry.getValue();
      List<DataValue> groupValues =
          readValueAttributes(
              entry.getKey(), indexedReads.stream().map(IndexedValueRead::read).toList());
      for (int i = 0; i < indexedReads.size(); i++) {
        values[indexedReads.get(i).index()] = groupValues.get(i);
      }
    }

    return Arrays.asList(values);
  }

  static Variant readValueAttribute(
      ProcessImage processImage, ModbusAddress address, String indexRange) throws UaException {

    try {
      return processImage.get(
          tx -> {
            try {
              return ModbusValueAccess.readValueAttribute(tx, address, indexRange);
            } catch (UaException e) {
              throw new UaRuntimeException(e);
            }
          });
    } catch (UaRuntimeException e) {
      throw UaException.extract(e).orElse(new UaException(StatusCodes.Bad_InternalError, e));
    }
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
          pending.statusCode = new StatusCode(StatusCodes.Bad_NodeIdInvalid);
        }
      } else {
        pending.statusCode = new StatusCode(StatusCodes.Bad_NotWritable);
      }
    }

    List<StatusCode> statuses =
        writeValueAttributes(
            device.processImageManager,
            pendingValueWrites.stream().map(PendingValueWrite::write).toList());
    for (int i = 0; i < pendingValueWrites.size(); i++) {
      pendingValueWrites.get(i).pending().statusCode = statuses.get(i);
    }

    return pendingWrites.stream().map(p -> p.statusCode).toList();
  }

  static List<StatusCode> writeValueAttributes(ProcessImage processImage, List<ValueWrite> writes) {
    if (writes.isEmpty()) {
      return List.of();
    }

    // One transaction for the whole batch: a single lock acquisition instead of one per item.
    var statuses = new ArrayList<StatusCode>(writes.size());
    processImage.with(
        tx -> {
          for (ValueWrite write : writes) {
            try {
              ModbusValueAccess.writeValueAttribute(
                  tx, write.address(), write.variant(), write.indexRange());
              statuses.add(StatusCode.GOOD);
            } catch (Exception e) {
              statuses.add(
                  UaException.extract(e)
                      .map(UaException::getStatusCode)
                      .orElseGet(
                          () -> {
                            logger.error("Error writing value: address={}", write.address(), e);
                            return new StatusCode(StatusCodes.Bad_InternalError);
                          }));
            }
          }
        });
    return statuses;
  }

  /**
   * Writes a batch through the manager-selected process images.
   *
   * <p>Each image is updated in one sequential transaction, preserving the order of writes that
   * target that image. Returned statuses correspond positionally to {@code writes}. A request
   * racing device shutdown returns {@code Bad_Shutdown}; a persistence initialization failure
   * returns {@code Bad_InternalError}.
   *
   * @param processImageManager the manager that resolves each address to an image.
   * @param writes the value requests in OPC UA request order.
   * @return the write statuses in the same order as {@code writes}.
   */
  static List<StatusCode> writeValueAttributes(
      ProcessImageManager processImageManager, List<ValueWrite> writes) {
    if (writes.isEmpty()) {
      return List.of();
    }

    Map<ProcessImage, List<IndexedValueWrite>> groups = new LinkedHashMap<>();
    StatusCode[] statuses = new StatusCode[writes.size()];
    for (int i = 0; i < writes.size(); i++) {
      ValueWrite write = writes.get(i);
      try {
        ProcessImage processImage = processImageManager.get(write.address());
        groups.computeIfAbsent(processImage, ignored -> new ArrayList<>())
            .add(new IndexedValueWrite(i, write));
      } catch (IllegalStateException e) {
        statuses[i] = new StatusCode(StatusCodes.Bad_Shutdown);
      } catch (UncheckedIOException e) {
        // A persistent image that cannot be initialized is an internal device failure.
        statuses[i] = new StatusCode(StatusCodes.Bad_InternalError);
      }
    }

    for (Map.Entry<ProcessImage, List<IndexedValueWrite>> entry : groups.entrySet()) {
      List<IndexedValueWrite> indexedWrites = entry.getValue();
      List<StatusCode> groupStatuses =
          writeValueAttributes(
              entry.getKey(), indexedWrites.stream().map(IndexedValueWrite::write).toList());
      for (int i = 0; i < indexedWrites.size(); i++) {
        statuses[indexedWrites.get(i).index()] = groupStatuses.get(i);
      }
    }

    return Arrays.asList(statuses);
  }

  static void writeValueAttribute(
      ProcessImage processImage, ModbusAddress address, Variant variant, String indexRange)
      throws UaException {

    try {
      processImage.with(
          tx -> ModbusValueAccess.writeValueAttribute(tx, address, variant, indexRange));
    } catch (Exception e) {
      throw UaException.extract(e)
          .orElseGet(
              () -> {
                logger.error("Error writing value: address={}", address, e);
                return new UaException(StatusCodes.Bad_InternalError, e);
              });
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

  private static class PendingRead {

    volatile DataValue value;
    final ReadValueId readValueId;

    private PendingRead(ReadValueId readValueId) {
      this.readValueId = readValueId;
    }
  }

  record ValueRead(ModbusAddress address, String indexRange) {}

  record ValueWrite(ModbusAddress address, Variant variant, String indexRange) {}

  private record IndexedValueRead(int index, ValueRead read) {}

  private record IndexedValueWrite(int index, ValueWrite write) {}

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
