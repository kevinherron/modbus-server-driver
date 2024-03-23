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
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode;
import org.eclipse.milo.opcua.sdk.server.util.SubscriptionModel;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;

public class DeviceAddressSpace extends ManagedAddressSpaceFragmentWithLifecycle {

  private final Pattern areaFolderPattern;

  private final AddressSpaceFilter filter =
      SimpleAddressSpaceFilter.create(getNodeManager()::containsNode);

  private final ModbusServerDevice device;
  private final SubscriptionModel subscriptionModel;

  public DeviceAddressSpace(OpcUaServer server, ModbusServerDevice device) {
    super(server, device);

    this.device = device;

    areaFolderPattern = Pattern.compile(
        "[%s]".formatted(device.deviceContext.getName()) + "_(C|DI|HR|IR)(\\d+)_");

    subscriptionModel = new SubscriptionModel(server, this);
    getLifecycleManager().addLifecycle(subscriptionModel);

    getLifecycleManager().addStartupTask(this::addNodes);
  }

  @Override
  public AddressSpaceFilter getFilter() {
    return filter;
  }

  @Override
  public void browse(BrowseContext context, NodeId nodeId) {
    if (nodeId.equals(device.deviceContext.nodeId("Coils"))) {
      context.success(createReferences(nodeId, "_C%d_"));
    } else if (nodeId.equals(device.deviceContext.nodeId("DiscreteInputs"))) {
      context.success(createReferences(nodeId, "_DI%d_"));
    } else if (nodeId.equals(device.deviceContext.nodeId("HoldingRegisters"))) {
      context.success(createReferences(nodeId, "_HR%d_"));
    } else if (nodeId.equals(device.deviceContext.nodeId("InputRegisters"))) {
      context.success(createReferences(nodeId, "_IR%d_"));
    } else {
      String id = nodeId.getIdentifier().toString();
      Matcher matcher = areaFolderPattern.matcher(id);
      if (matcher.matches()) {
        String area = matcher.group(1);
        int address = Integer.parseInt(matcher.group(2));
        // TODO create a reference to each datatype variation for the area/address
      } else {
        super.browse(context, nodeId);
      }
    }
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
        device.deviceContext.nodeId(device.deviceContext.getName()),
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
    for (int i = 0; i < 9999; i++) {
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
