package com.kevinherron.ignition.modbus;

import com.digitalpetri.modbus.server.ModbusTcpServer;
import com.digitalpetri.modbus.server.ProcessImage;
import com.digitalpetri.modbus.server.ReadWriteModbusServices;
import com.digitalpetri.modbus.tcp.server.NettyServerTransportConfig;
import com.digitalpetri.modbus.tcp.server.NettyTcpServerTransport;
import com.inductiveautomation.ignition.gateway.opcua.server.api.Device;
import com.inductiveautomation.ignition.gateway.opcua.server.api.DeviceContext;
import com.inductiveautomation.ignition.gateway.opcua.server.api.OpcUa;
import com.kevinherron.ignition.modbus.security.AllowedIpAddressFilter;
import com.kevinherron.ignition.modbus.security.AllowedIpAddressHandler;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.eclipse.milo.opcua.sdk.server.AddressSpaceComposite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs one Ignition Modbus server device and exposes its shared {@link ProcessImage} through Modbus
 * TCP and OPC UA.
 *
 * <p>Each instance owns its process image, TCP server, and OPC UA address spaces. The Ignition
 * device lifecycle starts and stops those resources as a unit.
 */
public class ModbusServerDevice extends AddressSpaceComposite implements Device {

  final Logger logger = LoggerFactory.getLogger(getClass());

  private ModbusTcpServer server;
  private volatile String status = "";

  final ProcessImage processImage = new ProcessImage();

  final ReadWriteModbusServices services =
      new ReadWriteModbusServices() {
        @Override
        protected Optional<ProcessImage> getProcessImage(int unitId) {
          return Optional.of(processImage);
        }
      };

  private BrowsableAddressSpace browsableAddressSpace;
  private ModbusAddressSpace modbusAddressSpace;

  final DeviceContext deviceContext;
  final ModbusServerDeviceConfig deviceConfig;

  /**
   * Creates an unstarted device for the supplied Gateway context and configuration.
   *
   * @param deviceContext the Gateway context that supplies the OPC UA server and subscription
   *     model.
   * @param deviceConfig the network, browsing, persistence, and connection-security settings.
   */
  public ModbusServerDevice(DeviceContext deviceContext, ModbusServerDeviceConfig deviceConfig) {

    super(deviceContext.getServer());

    this.deviceContext = deviceContext;
    this.deviceConfig = deviceConfig;
  }

  @Override
  public String getStatus() {
    return status;
  }

  /**
   * Starts the Modbus TCP listener and OPC UA address spaces after validating connection admission.
   *
   * <p>If the configured allow list is malformed, the listener remains unbound and {@link
   * #getStatus()} reports an error.
   */
  @Override
  public void startup() {
    String allowedIpAddresses = deviceConfig.security().allowedIpAddresses();
    if (allowedIpAddresses.isBlank()) {
      logger.warn("Allowed IP addresses is blank; preserving unrestricted access");
    }

    final AllowedIpAddressFilter ipFilter;
    try {
      ipFilter = AllowedIpAddressFilter.parse(allowedIpAddresses);
    } catch (IllegalArgumentException e) {
      status = "Error: invalid allowed IP addresses";
      logger.error("Invalid allowed IP addresses; not binding Modbus server", e);
      return;
    }

    final AllowedIpAddressHandler ipHandler =
        ipFilter.allowsAll() ? null : new AllowedIpAddressHandler(ipFilter);

    var transport =
        new NettyTcpServerTransport(
            NettyServerTransportConfig.create(
                cfg -> {
                  cfg.bindAddress = deviceConfig.connectivity().bindAddress();
                  cfg.port = deviceConfig.connectivity().port();
                  cfg.executor = OpcUa.SHARED_EXECUTOR;
                  cfg.eventLoopGroup = OpcUa.SHARED_EVENT_LOOP;
                  if (ipHandler != null) {
                    cfg.pipelineCustomizer = pipeline -> pipeline.addFirst("ipFilter", ipHandler);
                  }
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
