package com.kevinherron.ignition.modbus;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.milo.opcua.sdk.core.Reference;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.api.AddressSpaceFilter;
import org.eclipse.milo.opcua.sdk.server.api.DataItem;
import org.eclipse.milo.opcua.sdk.server.api.ManagedAddressSpaceFragmentWithLifecycle;
import org.eclipse.milo.opcua.sdk.server.api.MonitoredItem;
import org.eclipse.milo.opcua.sdk.server.api.SimpleAddressSpaceFilter;
import org.eclipse.milo.opcua.sdk.server.nodes.AttributeContext;
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaServerNode;
import org.eclipse.milo.opcua.sdk.server.util.SubscriptionModel;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.enumerated.NodeClass;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId;
import org.eclipse.milo.opcua.stack.core.types.structured.ViewDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BrowsableAddressSpace extends ManagedAddressSpaceFragmentWithLifecycle {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  private final Pattern enumeratedAreaPattern = Pattern.compile("_(C|DI|HR|IR)(\\d+)_");

  private final AddressSpaceFilter filter;

  private final ModbusServerDevice device;
  private final SubscriptionModel subscriptionModel;

  public BrowsableAddressSpace(OpcUaServer server, ModbusServerDevice device) {
    super(server, device);

    this.device = device;

    filter = SimpleAddressSpaceFilter.create(nodeId -> {
      if (logger.isDebugEnabled()) {
        logger.debug("filtering: {}", nodeId);
      }

      if (getNodeManager().containsNode(nodeId)) {
        return true;
      } else {
        String id = nodeId.getIdentifier().toString();
        id = id.substring(device.deviceContext.getName().length() + 2);
        return switch (id) {
          case "Coils", "DiscreteInputs", "HoldingRegisters", "InputRegisters" -> true;
          default -> enumeratedAreaPattern.matcher(id).matches();
        };
      }
    });

    subscriptionModel = new SubscriptionModel(server, this);
    getLifecycleManager().addLifecycle(subscriptionModel);

    getLifecycleManager().addStartupTask(this::addNodes);
  }

  @Override
  public AddressSpaceFilter getFilter() {
    return filter;
  }

  @Override
  public void browse(BrowseContext context, ViewDescription viewDescription, NodeId nodeId) {
    String id = nodeId.getIdentifier().toString();
    id = id.substring(device.deviceContext.getName().length() + 2);

    switch (id) {
      case "Coils" -> {
        String coilBrowseRanges = device.modbusServerSettings.getCoilBrowseRanges();

        if (coilBrowseRanges != null && !coilBrowseRanges.isEmpty()) {
          var references = new ArrayList<Reference>();

          List<Range> ranges = parseRanges(coilBrowseRanges);
          for (Range range : ranges) {
            for (int i = range.start; i <= range.end; i++) {
              references.add(new Reference(
                  nodeId,
                  Identifiers.HasComponent,
                  device.deviceContext.nodeId("C%d".formatted(i)).expanded(),
                  Reference.Direction.FORWARD
              ));
            }
          }

          context.success(references);
        }
      }
      case "DiscreteInputs" -> {
        String discreteInputBrowseRanges =
            device.modbusServerSettings.getDiscreteInputBrowseRanges();

        if (discreteInputBrowseRanges != null && !discreteInputBrowseRanges.isEmpty()) {
          var references = new ArrayList<Reference>();

          List<Range> ranges = parseRanges(discreteInputBrowseRanges);
          for (Range range : ranges) {
            for (int i = range.start; i <= range.end; i++) {
              references.add(new Reference(
                  nodeId,
                  Identifiers.HasComponent,
                  device.deviceContext.nodeId("DI%d".formatted(i)).expanded(),
                  Reference.Direction.FORWARD
              ));
            }
          }

          context.success(references);
        }
      }
      case "HoldingRegisters" -> {
        String holdingRegisterBrowseRanges =
            device.modbusServerSettings.getHoldingRegisterBrowseRanges();

        if (holdingRegisterBrowseRanges != null && !holdingRegisterBrowseRanges.isEmpty()) {
          List<Reference> references = createRegisterFolderReferences(
              nodeId,
              "_HR%d_",
              parseRanges(holdingRegisterBrowseRanges)
          );
          context.success(references);
        }
      }
      case "InputRegisters" -> {
        String inputRegisterBrowseRanges =
            device.modbusServerSettings.getInputRegisterBrowseRanges();

        if (inputRegisterBrowseRanges != null && !inputRegisterBrowseRanges.isEmpty()) {
          List<Reference> references = createRegisterFolderReferences(
              nodeId,
              "_IR%d_",
              parseRanges(inputRegisterBrowseRanges)
          );
          context.success(references);
        }
      }
      default -> {
        Matcher matcher = enumeratedAreaPattern.matcher(id);
        if (matcher.matches()) {
          String area = matcher.group(1);
          int address = Integer.parseInt(matcher.group(2));
          context.success(createRegisterAddressReferences(nodeId, area, address));
        } else {
          if (logger.isDebugEnabled()) {
            logger.debug("Browsing super with: {}", nodeId);
          }
          super.browse(context, viewDescription, nodeId);
        }
      }
    }
  }

  private List<Reference> createRegisterFolderReferences(
      NodeId nodeId,
      String formatString,
      List<Range> ranges
  ) {

    var references = new ArrayList<Reference>();

    for (Range range : ranges) {
      for (int i = range.start; i <= range.end; i++) {
        NodeId childNodeId = device.deviceContext.nodeId(formatString.formatted(i));
        references.add(new Reference(
            nodeId,
            Identifiers.HasComponent,
            childNodeId.expanded(),
            Reference.Direction.FORWARD
        ));
      }
    }

    return references;
  }

  private List<Reference> createRegisterAddressReferences(
      NodeId parentNodeId,
      String area,
      int address
  ) {

    var references = new ArrayList<Reference>();

    switch (area) {
      case "HR", "IR" -> {
        List<String> dataTypes =
            List.of("int16", "uint16", "int32", "uint32", "int64", "uint64", "float", "double");

        for (String dataType : dataTypes) {
          NodeId targetNodeId = device.deviceContext.nodeId(
              "%s<%s>%d".formatted(area, dataType, address)
          );

          references.add(new Reference(
              parentNodeId,
              Identifiers.HasComponent,
              targetNodeId.expanded(),
              Reference.Direction.FORWARD
          ));
        }
      }
      default -> {
        // intentional fall-through
      }
    }

    return references;
  }

  @Override
  public void read(
      ReadContext context,
      Double maxAge,
      TimestampsToReturn timestamps,
      List<ReadValueId> readValueIds
  ) {

    List<DataValue> results = new ArrayList<>();

    for (ReadValueId readValueId : readValueIds) {
      UaServerNode node = getNodeManager().get(readValueId.getNodeId());

      if (node != null) {
        DataValue value = node.readAttribute(
            new AttributeContext(context),
            readValueId.getAttributeId(),
            timestamps,
            readValueId.getIndexRange(),
            readValueId.getDataEncoding()
        );
        results.add(value);
      } else {
        String id = readValueId.getNodeId().getIdentifier().toString();
        id = id.substring(device.deviceContext.getName().length() + 2);

        switch (id) {
          case "Coils", "DiscreteInputs", "HoldingRegisters", "InputRegisters" -> {
            DataValue value = AttributeId.from(readValueId.getAttributeId())
                .map(attributeId -> {
                  Variant variant = readAttribute(readValueId.getNodeId(), attributeId);
                  return new DataValue(variant);
                })
                .orElseGet(() -> new DataValue(StatusCodes.Bad_AttributeIdInvalid));

            results.add(value);
          }
          default -> {
            if (enumeratedAreaPattern.matcher(id).matches()) {
              DataValue value = AttributeId.from(readValueId.getAttributeId())
                  .map(attributeId -> {
                    Variant variant = readAttribute(readValueId.getNodeId(), attributeId);
                    return new DataValue(variant);
                  })
                  .orElseGet(() -> new DataValue(StatusCodes.Bad_AttributeIdInvalid));

              results.add(value);
            } else {
              results.add(new DataValue(StatusCodes.Bad_NodeIdUnknown));
            }
          }
        }
      }
    }

    context.success(results);
  }

  private Variant readAttribute(NodeId nodeId, AttributeId attributeId) {
    Object o = switch (attributeId) {
      case NodeId -> nodeId;
      case NodeClass -> NodeClass.Object;
      case BrowseName -> {
        String id = nodeId.getIdentifier().toString();
        String addr = id.substring(device.getName().length() + 2);
        addr = addr.replace("_", "");
        yield device.deviceContext.qualifiedName(addr);
      }
      case DisplayName, Description -> {
        String id = nodeId.getIdentifier().toString();
        String addr = id.substring(device.getName().length() + 2);
        addr = addr.replace("_", "");
        yield LocalizedText.english(addr);
      }
      default -> null;
    };

    return o == null ? Variant.NULL_VALUE : new Variant(o);
  }

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

  private void addNodes() {
    // create a folder node for our configured device
    var deviceNode = new UaFolderNode(
        getNodeContext(),
        device.deviceContext.nodeId(""),
        device.deviceContext.qualifiedName(String.format("[%s]", device.deviceContext.getName())),
        new LocalizedText(String.format("[%s]", device.deviceContext.getName()))
    );

    // add the folder node to the server
    getNodeManager().addNode(deviceNode);

    // add a reference to the root "Devices" folder node
    deviceNode.addReference(new Reference(
        deviceNode.getNodeId(),
        Identifiers.Organizes,
        device.deviceContext.getRootNodeId().expanded(),
        Reference.Direction.INVERSE
    ));

    addCoilsNode(deviceNode);
    addDiscreteInputsNode(deviceNode);
    addHoldingRegistersNode(deviceNode);
    addInputRegistersNode(deviceNode);
  }

  private void addCoilsNode(UaFolderNode deviceNode) {
    var coilsNode = new UaFolderNode(
        getNodeContext(),
        device.deviceContext.nodeId("Coils"),
        device.deviceContext.qualifiedName("Coils"),
        new LocalizedText("Coils")
    );

    getNodeManager().addNode(coilsNode);

    deviceNode.addOrganizes(coilsNode);
  }

  private void addDiscreteInputsNode(UaFolderNode deviceNode) {
    var discreteInputsNode = new UaFolderNode(
        getNodeContext(),
        device.deviceContext.nodeId("DiscreteInputs"),
        device.deviceContext.qualifiedName("DiscreteInputs"),
        new LocalizedText("DiscreteInputs")
    );

    getNodeManager().addNode(discreteInputsNode);

    deviceNode.addOrganizes(discreteInputsNode);
  }

  private void addHoldingRegistersNode(UaFolderNode deviceNode) {
    var holdingRegistersNode = new UaFolderNode(
        getNodeContext(),
        device.deviceContext.nodeId("HoldingRegisters"),
        device.deviceContext.qualifiedName("HoldingRegisters"),
        new LocalizedText("HoldingRegisters")
    );

    getNodeManager().addNode(holdingRegistersNode);

    deviceNode.addOrganizes(holdingRegistersNode);
  }

  private void addInputRegistersNode(UaFolderNode deviceNode) {
    var inputRegistersNode = new UaFolderNode(
        getNodeContext(),
        device.deviceContext.nodeId("InputRegisters"),
        device.deviceContext.qualifiedName("InputRegisters"),
        new LocalizedText("InputRegisters")
    );

    getNodeManager().addNode(inputRegistersNode);

    deviceNode.addOrganizes(inputRegistersNode);
  }

  record Range(int start, int end) {}

  static List<Range> parseRanges(String ranges) {
    var rangeList = new ArrayList<Range>();
    for (String range : ranges.split(",")) {
      String[] parts = range.split("-");
      if (parts.length == 1) {
        int start = Math.min(Integer.parseInt(parts[0]), 65535);
        rangeList.add(new Range(start, start));
      } else if (parts.length == 2) {
        int start = Math.min(Integer.parseInt(parts[0]), 65535);
        int end = Math.min(Integer.parseInt(parts[1]), 65535);
        rangeList.add(new Range(start, end));
      }
    }
    return rangeList;
  }

}
