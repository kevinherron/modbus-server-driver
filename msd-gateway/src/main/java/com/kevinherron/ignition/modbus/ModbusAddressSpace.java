package com.kevinherron.ignition.modbus;

import java.util.List;

import org.eclipse.milo.opcua.sdk.server.api.AddressSpaceFilter;
import org.eclipse.milo.opcua.sdk.server.api.AddressSpaceFragment;
import org.eclipse.milo.opcua.sdk.server.api.DataItem;
import org.eclipse.milo.opcua.sdk.server.api.MonitoredItem;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId;
import org.eclipse.milo.opcua.stack.core.types.structured.ViewDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.WriteValue;

public class ModbusAddressSpace implements AddressSpaceFragment {

  @Override
  public AddressSpaceFilter getFilter() {
    return null;
  }

  @Override
  public void browse(BrowseContext browseContext, ViewDescription viewDescription, NodeId nodeId) {

  }

  @Override
  public void getReferences(BrowseContext browseContext, ViewDescription viewDescription, NodeId nodeId) {

  }

  @Override
  public void read(ReadContext readContext, Double aDouble, TimestampsToReturn timestampsToReturn, List<ReadValueId> list) {

  }

  @Override
  public void write(WriteContext writeContext, List<WriteValue> list) {

  }

  @Override
  public void onDataItemsCreated(List<DataItem> list) {

  }

  @Override
  public void onDataItemsModified(List<DataItem> list) {

  }

  @Override
  public void onDataItemsDeleted(List<DataItem> list) {

  }

  @Override
  public void onMonitoringModeChanged(List<MonitoredItem> list) {

  }

}
