package com.kevinherron.ignition.modbus;

import java.util.List;

import com.inductiveautomation.ignition.gateway.opcua.server.api.DeviceContext;
import org.eclipse.milo.opcua.sdk.core.Reference;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.api.*;
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;

public class DeviceAddressSpace extends ManagedAddressSpaceFragmentWithLifecycle {

  private final AddressSpaceFilter filter =
      SimpleAddressSpaceFilter.create(getNodeManager()::containsNode);

  private final DeviceContext deviceContext;

  public DeviceAddressSpace(OpcUaServer server, DeviceContext deviceContext) {
    super(server);

    this.deviceContext = deviceContext;

    getLifecycleManager().addStartupTask(this::onStartup);
  }

  private void onStartup() {
    // create a folder node for our configured device
    var rootNode = new UaFolderNode(
        getNodeContext(),
        deviceContext.nodeId(deviceContext.getName()),
        deviceContext.qualifiedName(String.format("[%s]", deviceContext.getName())),
        new LocalizedText(String.format("[%s]", deviceContext.getName()))
    );

    // add the folder node to the server
    getNodeManager().addNode(rootNode);

    // add a reference to the root "Devices" folder node
    rootNode.addReference(new Reference(
        rootNode.getNodeId(),
        Identifiers.Organizes,
        deviceContext.getRootNodeId().expanded(),
        Reference.Direction.INVERSE
    ));
  }

  @Override
  public AddressSpaceFilter getFilter() {
    return filter;
  }

  @Override
  public void onDataItemsCreated(List<DataItem> list) {}

  @Override
  public void onDataItemsModified(List<DataItem> list) {}

  @Override
  public void onDataItemsDeleted(List<DataItem> list) {}

  @Override
  public void onMonitoringModeChanged(List<MonitoredItem> list) {}

}
