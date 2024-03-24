package com.kevinherron.ignition.modbus;

import com.digitalpetri.modbus.server.ModbusTcpServer;
import com.digitalpetri.modbus.server.NettyServerTransport;
import com.digitalpetri.modbus.server.NettyServerTransportConfig;
import com.inductiveautomation.ignition.gateway.opcua.server.api.Device;
import com.inductiveautomation.ignition.gateway.opcua.server.api.DeviceContext;
import com.inductiveautomation.ignition.gateway.opcua.server.api.DeviceSettingsRecord;
import java.util.concurrent.ExecutionException;
import org.eclipse.milo.opcua.sdk.server.api.AddressSpaceComposite;
import org.jetbrains.annotations.NotNull;
import org.joou.UShort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModbusServerDevice extends AddressSpaceComposite implements Device {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  private ModbusTcpServer server;
  private volatile String status = "";

  final ModbusServicesImpl services = new ModbusServicesImpl();

  private DeviceAddressSpace deviceAddressSpace;
  private ModbusAddressSpace modbusAddressSpace;

  final DeviceContext deviceContext;
  private final DeviceSettingsRecord deviceSettings;
  private final ModbusServerDeviceSettings modbusServerSettings;

  public ModbusServerDevice(
      DeviceContext deviceContext,
      DeviceSettingsRecord deviceSettings,
      ModbusServerDeviceSettings modbusServerSettings
  ) {

    super(deviceContext.getServer());

    this.deviceContext = deviceContext;
    this.deviceSettings = deviceSettings;
    this.modbusServerSettings = modbusServerSettings;
  }

  @Override
  public @NotNull String getName() {
    return deviceSettings.getName();
  }

  @Override
  public @NotNull String getStatus() {
    return status;
  }

  @Override
  public @NotNull String getTypeId() {
    return deviceSettings.getType();
  }

  @Override
  public void startup() {
    var transport = new NettyServerTransport(
        NettyServerTransportConfig.create(cfg -> {
          cfg.bindAddress = modbusServerSettings.getBindAddress();
          cfg.port = UShort.valueOf(modbusServerSettings.getPort());
        })
    );

    server = ModbusTcpServer.create(transport, services);

    try {
      server.start();

      status = "Listening";

      logger.info(
          "Modbus server listening on {}:{}",
          modbusServerSettings.getBindAddress(),
          modbusServerSettings.getPort()
      );

      deviceAddressSpace = new DeviceAddressSpace(deviceContext.getServer(), this);
      deviceAddressSpace.startup();

      modbusAddressSpace = new ModbusAddressSpace(this);
      modbusAddressSpace.startup();
    } catch (ExecutionException e) {
      status = "Error";
      logger.error("Error starting Modbus server", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      status = "Error";
      logger.error("Error starting Modbus server", e);
    }

    onDataItemsCreated(deviceContext.getSubscriptionModel().getDataItems(getName()));
  }

  @Override
  public void shutdown() {
    if (deviceAddressSpace != null) {
      deviceAddressSpace.shutdown();
    }
    if (modbusAddressSpace != null) {
      modbusAddressSpace.shutdown();
    }

    if (server != null) {
      try {
        server.stop();
      } catch (ExecutionException e) {
        logger.error("Error stopping Modbus server", e);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        logger.error("Error stopping Modbus server", e);
      }
    }
  }

}
