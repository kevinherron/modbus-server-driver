package com.kevinherron.ignition.modbus;

import java.util.ArrayList;
import java.util.List;
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

public class DeviceAddressSpace extends ManagedAddressSpaceFragmentWithLifecycle {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  private final Pattern areaFolderPattern;

  private final AddressSpaceFilter filter;

  private final ModbusServerDevice device;
  private final SubscriptionModel subscriptionModel;

  public DeviceAddressSpace(OpcUaServer server, ModbusServerDevice device) {
    super(server, device);

    this.device = device;

    areaFolderPattern = Pattern.compile("_(C|DI|HR|IR)(\\d+)_");

    filter = SimpleAddressSpaceFilter.create(nodeId -> {
      logger.info("filtering: {}", nodeId);

      if (getNodeManager().containsNode(nodeId)) {
        return true;
      } else {
        String id = nodeId.getIdentifier().toString();
        id = id.substring(device.deviceContext.getName().length() + 2);
        return switch (id) {
          case "Coils", "DiscreteInputs", "HoldingRegisters", "InputRegisters" -> true;
          default -> areaFolderPattern.matcher(id).matches();
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
    if (nodeId.equals(device.deviceContext.nodeId("Coils"))) {
      context.success(createReferences(nodeId, "_C%05d_"));
    } else if (nodeId.equals(device.deviceContext.nodeId("DiscreteInputs"))) {
      context.success(createReferences(nodeId, "_DI%05d_"));
    } else if (nodeId.equals(device.deviceContext.nodeId("HoldingRegisters"))) {
      context.success(createReferences(nodeId, "_HR%05d_"));
    } else if (nodeId.equals(device.deviceContext.nodeId("InputRegisters"))) {
      context.success(createReferences(nodeId, "_IR%05d_"));
    } else {
      String id = nodeId.getIdentifier().toString();
      id = id.substring(device.deviceContext.getName().length() + 2);

      if (areaFolderPattern.matcher(id).matches()) {
        context.success(List.of());
      } else {
        logger.info("Browsing super with: {}", nodeId);
        super.browse(context, viewDescription, nodeId);
      }
    }
  }

  @Override
  public void read(ReadContext context, Double maxAge, TimestampsToReturn timestamps,
      List<ReadValueId> readValueIds) {

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
            Variant variant = readAttribute(readValueId.getNodeId(),
                AttributeId.from(readValueId.getAttributeId()).orElseThrow());
            results.add(new DataValue(variant));
          }
          default -> {
            if (areaFolderPattern.matcher(id).matches()) {
              Variant variant = readAttribute(readValueId.getNodeId(),
                  AttributeId.from(readValueId.getAttributeId()).orElseThrow());
              results.add(new DataValue(variant));
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
        yield device.deviceContext.qualifiedName(addr);
      }
      case DisplayName, Description -> {
        String id = nodeId.getIdentifier().toString();
        String addr = id.substring(device.getName().length() + 2);
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

  private List<Reference> createReferences(NodeId nodeId, String formatString) {
    var references = new ArrayList<Reference>();
    for (int i = 0; i < 10_000; i++) {
      NodeId childNodeId = device.deviceContext.nodeId(formatString.formatted(i));
      references.add(new Reference(
          nodeId,
          Identifiers.HasComponent,
          childNodeId.expanded(),
          Reference.Direction.FORWARD
      ));
    }
    return references;
  }

}
