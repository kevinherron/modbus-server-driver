package com.kevinherron.ignition.modbus;

import com.digitalpetri.modbus.server.ModbusTcpServer;
import com.digitalpetri.modbus.server.ProcessImage;
import com.digitalpetri.modbus.server.ReadWriteModbusServices;
import com.digitalpetri.modbus.tcp.server.NettyServerTransportConfig;
import com.digitalpetri.modbus.tcp.server.NettyTcpServerTransport;
import com.inductiveautomation.ignition.gateway.opcua.server.api.Device;
import com.inductiveautomation.ignition.gateway.opcua.server.api.DeviceContext;
import com.inductiveautomation.ignition.gateway.opcua.server.api.DeviceSettingsRecord;
import com.inductiveautomation.ignition.gateway.opcua.server.api.OpcUa;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.eclipse.milo.opcua.sdk.server.api.AddressSpaceComposite;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModbusServerDevice extends AddressSpaceComposite implements Device {

  final Logger logger = LoggerFactory.getLogger(getClass());

  private ModbusTcpServer server;
  private volatile String status = "";

  final ProcessImage processImage = new ProcessImage();

  final ReadWriteModbusServices services = new ReadWriteModbusServices() {
    @Override
    protected Optional<ProcessImage> getProcessImage(int unitId) {
      return Optional.of(processImage);
    }
  };

  private BrowsableAddressSpace browsableAddressSpace;
  private ModbusAddressSpace modbusAddressSpace;

  final DeviceContext deviceContext;
  final DeviceSettingsRecord deviceSettings;
  final ModbusServerDeviceSettings modbusServerSettings;

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
    var transport = new NettyTcpServerTransport(
        NettyServerTransportConfig.create(cfg -> {
          cfg.bindAddress = modbusServerSettings.getBindAddress();
          cfg.port = modbusServerSettings.getPort();
          cfg.executor = OpcUa.SHARED_EXECUTOR;
          cfg.eventLoopGroup = OpcUa.SHARED_EVENT_LOOP;
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

      browsableAddressSpace = new BrowsableAddressSpace(deviceContext.getServer(), this);
      browsableAddressSpace.startup();

      modbusAddressSpace = new ModbusAddressSpace(this);
      modbusAddressSpace.startup();

      onDataItemsCreated(deviceContext.getSubscriptionModel().getDataItems(getName()));
    } catch (ExecutionException e) {
      status = "Error";
      logger.error("Error starting Modbus server", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      status = "Error";
      logger.error("Error starting Modbus server", e);
    }
  }

  @Override
  public void shutdown() {
    if (browsableAddressSpace != null) {
      browsableAddressSpace.shutdown();
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
