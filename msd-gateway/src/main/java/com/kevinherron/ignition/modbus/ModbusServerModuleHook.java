package com.kevinherron.ignition.modbus;

import com.inductiveautomation.ignition.common.BundleUtil;
import com.inductiveautomation.ignition.common.licensing.LicenseState;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import com.inductiveautomation.ignition.gateway.opcua.server.api.AbstractDeviceModuleHook;
import com.inductiveautomation.ignition.gateway.opcua.server.api.DeviceType;
import java.util.List;
import org.jetbrains.annotations.NotNull;


public class ModbusServerModuleHook extends AbstractDeviceModuleHook {

  @Override
  public void setup(@NotNull GatewayContext context) {
    BundleUtil.get().addBundle("ModbusServer", ModbusServerDevice.class, "ModbusServer");

    super.setup(context);
  }

  @Override
  public void startup(@NotNull LicenseState activationState) {
    super.startup(activationState);
  }

  @Override
  public void shutdown() {
    BundleUtil.get().removeBundle(ModbusServerDevice.class);

    super.shutdown();
  }

  @Override
  protected @NotNull List<DeviceType> getDeviceTypes() {
    return List.of(new ModbusServerDeviceType());
  }

}
