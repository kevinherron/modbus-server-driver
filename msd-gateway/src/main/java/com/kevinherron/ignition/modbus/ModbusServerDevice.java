package com.kevinherron.ignition.modbus;

import com.digitalpetri.modbus.server.ModbusTcpServer;
import com.digitalpetri.modbus.server.ProcessImage;
import com.digitalpetri.modbus.server.ReadWriteModbusServices;
import com.digitalpetri.modbus.tcp.server.NettyServerTransportConfig;
import com.digitalpetri.modbus.tcp.server.NettyTcpServerTransport;
import com.inductiveautomation.ignition.gateway.opcua.server.api.Device;
import com.inductiveautomation.ignition.gateway.opcua.server.api.DeviceContext;
import com.inductiveautomation.ignition.gateway.opcua.server.api.OpcUa;
import java.io.UncheckedIOException;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.eclipse.milo.opcua.sdk.server.AddressSpaceComposite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs the Modbus TCP endpoint and OPC UA address spaces for one Ignition device configuration.
 *
 * <p>The Ignition device framework creates this type through {@link
 * ModbusServerDeviceExtensionPoint} and owns its lifecycle. Construction prepares process-image
 * routing and persistent state before the network endpoint can accept requests. {@link #startup()}
 * then starts the Modbus server and registers the browse and variable address spaces.
 *
 * <p>{@link #shutdown()} must be allowed to complete so protocol requests stop before persistence
 * listeners are detached and queued writes are drained. A shut-down instance is not reusable.
 */
public class ModbusServerDevice extends AddressSpaceComposite implements Device {

  final Logger logger = LoggerFactory.getLogger(getClass());

  private ModbusTcpServer server;
  private volatile String status = "";

  final ProcessImageManager processImageManager;
  final ReadWriteModbusServices services;

  private BrowsableAddressSpace browsableAddressSpace;
  private ModbusAddressSpace modbusAddressSpace;

  final DeviceContext deviceContext;
  final ModbusServerDeviceConfig deviceConfig;

  /**
   * Creates an unstarted device from validated Ignition settings.
   *
   * @param deviceContext the Ignition runtime context that owns the device and its OPC UA nodes.
   * @param deviceConfig the decoded connection, browsing, process-image, and persistence settings.
   */
  public ModbusServerDevice(DeviceContext deviceContext, ModbusServerDeviceConfig deviceConfig) {

    super(deviceContext.getServer());

    this.deviceContext = deviceContext;
    this.deviceConfig = deviceConfig;

    processImageManager =
        new ProcessImageManager(
            deviceConfig.processImage().separatePerUnitId(),
            deviceConfig.processImage().persistData(),
            deviceContext.getDeviceFolderPath(),
            OpcUa.SHARED_EXECUTOR);

    services =
        new ReadWriteModbusServices() {
          @Override
          protected Optional<ProcessImage> getProcessImage(int unitId) {
            try {
              return Optional.of(processImageManager.get(unitId));
            } catch (IllegalStateException | UncheckedIOException e) {
              // Shutdown and lazy persistence failures make this unit temporarily unavailable.
              return Optional.empty();
            }
          }
        };
  }

  @Override
  public String getStatus() {
    return status;
  }

  /**
   * Starts the configured Modbus TCP listener and registers both OPC UA address spaces.
   *
   * <p>If the listener cannot start, the device status becomes {@code Error} and the failure is
   * logged. Any resources that started successfully are rolled back, and interruption is restored
   * on the calling thread.
   */
  @Override
  public void startup() {
    var transport =
        new NettyTcpServerTransport(
            NettyServerTransportConfig.create(
                cfg -> {
                  cfg.bindAddress = deviceConfig.connectivity().bindAddress();
                  cfg.port = deviceConfig.connectivity().port();
                  cfg.executor = OpcUa.SHARED_EXECUTOR;
                  cfg.eventLoopGroup = OpcUa.SHARED_EVENT_LOOP;
                }));

    server = ModbusTcpServer.create(transport, services);

    try {
      server.start();

      status = "Listening";

      logger.info(
          "Modbus server listening on {}:{}",
          deviceConfig.connectivity().bindAddress(),
          deviceConfig.connectivity().port());

      browsableAddressSpace = new BrowsableAddressSpace(deviceContext.getServer(), this);
      browsableAddressSpace.startup();

      modbusAddressSpace = new ModbusAddressSpace(this);
      modbusAddressSpace.startup();

      onDataItemsCreated(
          deviceContext.getSubscriptionModel().getDataItems(deviceContext.getName()));
    } catch (ExecutionException e) {
      status = "Error";
      logger.error("Error starting Modbus server", e);
      rollbackStartup();
    } catch (InterruptedException e) {
      status = "Error";
      logger.error("Error starting Modbus server", e);
      rollbackStartup();
      Thread.currentThread().interrupt();
    } catch (RuntimeException e) {
      status = "Error";
      logger.error("Error starting Modbus server device", e);
      rollbackStartup();
    }
  }

  private void rollbackStartup() {
    shutdownAddressSpaces();
    stopServer();
    closeProcessImageManager();
  }

  /**
   * Unregisters OPC UA address spaces, stops the Modbus listener, and closes process-image storage.
   *
   * <p>Closing storage removes modification listeners and waits for accepted persistence writes to
   * finish. This method is idempotent, and interruption while stopping the server is restored on
   * the calling thread.
   */
  @Override
  public void shutdown() {
    shutdownAddressSpaces();
    stopServer();
    closeProcessImageManager();
  }

  private void shutdownAddressSpaces() {
    ModbusAddressSpace localModbusAddressSpace = modbusAddressSpace;
    modbusAddressSpace = null;
    if (localModbusAddressSpace != null) {
      try {
        localModbusAddressSpace.shutdown();
      } catch (RuntimeException e) {
        logger.error("Error shutting down Modbus address space", e);
      }
    }

    BrowsableAddressSpace localBrowsableAddressSpace = browsableAddressSpace;
    browsableAddressSpace = null;
    if (localBrowsableAddressSpace != null) {
      try {
        localBrowsableAddressSpace.shutdown();
      } catch (RuntimeException e) {
        logger.error("Error shutting down browsable address space", e);
      }
    }
  }

  private void stopServer() {
    ModbusTcpServer localServer = server;
    server = null;
    if (localServer != null) {
      try {
        localServer.stop();
      } catch (ExecutionException e) {
        logger.error("Error stopping Modbus server", e);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        logger.error("Error stopping Modbus server", e);
      }
    }
  }

  private void closeProcessImageManager() {
    try {
      processImageManager.close();
    } catch (RuntimeException e) {
      logger.error("Error closing process image manager", e);
    }
  }
}
