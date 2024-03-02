package com.kevinherron.ignition.modbus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.digitalpetri.modbus.pdu.*;
import com.digitalpetri.modbus.server.ModbusServices;
import com.digitalpetri.modbus.server.ModbusTcpServer;
import com.digitalpetri.modbus.server.NettyServerTransport;
import com.digitalpetri.modbus.server.NettyServerTransportConfig;
import com.inductiveautomation.ignition.gateway.opcua.server.api.Device;
import com.inductiveautomation.ignition.gateway.opcua.server.api.DeviceSettingsRecord;
import org.eclipse.milo.opcua.sdk.server.api.DataItem;
import org.eclipse.milo.opcua.sdk.server.api.MonitoredItem;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId;
import org.eclipse.milo.opcua.stack.core.types.structured.ViewDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.WriteValue;
import org.jetbrains.annotations.NotNull;
import org.joou.UByte;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.joou.Unsigned.ushort;

public class ModbusServerDevice implements Device {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  private ModbusTcpServer server;
  private volatile String status = "";

  private final ModbusServicesImpl services = new ModbusServicesImpl();

  private final DeviceSettingsRecord deviceSettings;
  private final ModbusServerDeviceSettings modbusServerSettings;

  public ModbusServerDevice(
      DeviceSettingsRecord deviceSettings,
      ModbusServerDeviceSettings modbusServerSettings
  ) {

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
    NettyServerTransport transport = new NettyServerTransport(
        NettyServerTransportConfig.create(cfg -> {
          cfg.bindAddress = modbusServerSettings.getBindAddress();
          cfg.port = ushort(modbusServerSettings.getPort());
        })
    );

    server = ModbusTcpServer.create(transport, services, cfg -> {});

    try {
      server.start();

      status = "Listening";

      logger.info(
          "Modbus server listening on {}:{}",
          modbusServerSettings.getBindAddress(),
          modbusServerSettings.getPort()
      );
    } catch (ExecutionException e) {
      status = "Error";

      logger.error("Error starting Modbus server", e);
    } catch (InterruptedException e) {
      status = "Error";

      Thread.currentThread().interrupt();

      logger.error("Error starting Modbus server", e);
    }
  }

  @Override
  public void shutdown() {
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

  @Override
  public void read(
      ReadContext context,
      Double maxAge,
      TimestampsToReturn timestamps,
      List<ReadValueId> readValueIds
  ) {

    // parse readValueIds into ModbusAddresses
    // each ModbusAddress will inform us how many bytes to read from which area
    // we'll read the bytes, then use the datatype information interpret those bytes accordingly
  }

  @Override
  public void write(WriteContext context, List<WriteValue> writeValues) {

  }

  @Override
  public void onDataItemsCreated(List<DataItem> dataItems) {

  }

  @Override
  public void onDataItemsModified(List<DataItem> dataItems) {

  }

  @Override
  public void onDataItemsDeleted(List<DataItem> dataItems) {

  }

  @Override
  public void onMonitoringModeChanged(List<MonitoredItem> monitoredItems) {

  }

  @Override
  public void browse(BrowseContext context, ViewDescription viewDescription, NodeId nodeId) {

  }

  @Override
  public void getReferences(BrowseContext context, ViewDescription viewDescription, NodeId nodeId) {

  }

  static class ModbusServicesImpl implements ModbusServices {

    final ReadWriteLock coilLock = new ReentrantReadWriteLock();
    final Map<Integer, Boolean> coilMap = new HashMap<>();

    final ReadWriteLock discreteInputLock = new ReentrantReadWriteLock();
    final Map<Integer, Boolean> discreteInputMap = new HashMap<>();

    final ReadWriteLock holdingRegisterLock = new ReentrantReadWriteLock();
    final Map<Integer, byte[]> holdingRegisterMap = new HashMap<>();

    final ReadWriteLock inputRegisterLock = new ReentrantReadWriteLock();
    final Map<Integer, byte[]> inputRegisterMap = new HashMap<>();

    @Override
    public ReadCoilsResponse readCoils(UByte unitId, ReadCoilsRequest request) {
      coilLock.readLock().lock();
      try {
        int address = request.address().intValue();
        int quantity = request.quantity().intValue();

        var coils = readBits(address, quantity, coilMap);

        return new ReadCoilsResponse(coils);
      } finally {
        coilLock.readLock().unlock();
      }
    }

    @Override
    public ReadDiscreteInputsResponse readDiscreteInputs(UByte unitId, ReadDiscreteInputsRequest request) {
      discreteInputLock.readLock().lock();
      try {
        int address = request.address().intValue();
        int quantity = request.quantity().intValue();

        var inputs = readBits(address, quantity, discreteInputMap);

        return new ReadDiscreteInputsResponse(inputs);
      } finally {
        discreteInputLock.readLock().unlock();
      }
    }

    private static byte[] readBits(int address, int quantity, Map<Integer, Boolean> bitMap) {
      var bytes = new byte[(quantity + 7) / 8];

      for (int i = 0; i < quantity; i++) {
        int byteIndex = i / 8;
        int bitIndex = i % 8;

        boolean value = bitMap.getOrDefault(address + i, false);

        int b = bytes[byteIndex];
        if (value) {
          b |= (1 << bitIndex);
        } else {
          b &= ~(1 << bitIndex);
        }
        bytes[byteIndex] = (byte) (b & 0xFF);
      }

      return bytes;
    }

    @Override
    public ReadHoldingRegistersResponse readHoldingRegisters(UByte unitId, ReadHoldingRegistersRequest request) {
      holdingRegisterLock.readLock().lock();
      try {
        int address = request.address().intValue();
        int quantity = request.quantity().intValue();

        var registers = readRegisters(quantity, address, holdingRegisterMap);

        return new ReadHoldingRegistersResponse(registers);
      } finally {
        holdingRegisterLock.readLock().unlock();
      }
    }

    @Override
    public ReadInputRegistersResponse readInputRegisters(UByte unitId, ReadInputRegistersRequest request) {
      inputRegisterLock.readLock().lock();
      try {
        int address = request.address().intValue();
        int quantity = request.quantity().intValue();

        var registers = readRegisters(quantity, address, inputRegisterMap);

        return new ReadInputRegistersResponse(registers);
      } finally {
        inputRegisterLock.readLock().unlock();
      }
    }

    private static byte[] readRegisters(int quantity, int address, Map<Integer, byte[]> registerMap) {
      var registers = new byte[quantity * 2];

      for (int i = 0; i < quantity; i++) {
        byte[] value = registerMap.getOrDefault(address + i, new byte[2]);

        registers[i * 2] = value[0];
        registers[i * 2 + 1] = value[1];
      }

      return registers;
    }

    @Override
    public WriteMultipleRegistersResponse writeMultipleRegisters(UByte unitId, WriteMultipleRegistersRequest request) {
      holdingRegisterLock.writeLock().lock();
      try {
        int address = request.address().intValue();
        int quantity = request.quantity().intValue();
        byte[] values = request.values();

        for (int i = 0; i < quantity; i++) {
          byte b0 = values[i * 2];
          byte b1 = values[i * 2 + 1];

          if (b0 == 0 && b1 == 0) {
            holdingRegisterMap.remove(address + i);
          } else {
            byte[] value = new byte[]{b0, b1};
            holdingRegisterMap.put(address + i, value);
          }
        }
        return new WriteMultipleRegistersResponse(request.address(), request.quantity());
      } finally {
        holdingRegisterLock.writeLock().unlock();
      }
    }

    @Override
    public WriteSingleRegisterResponse writeSingleRegister(UByte unitId, WriteSingleRegisterRequest request) {
      int address = request.address().intValue();
      int value = request.value().intValue();

      holdingRegisterLock.writeLock().lock();
      try {
        if (value == 0) {
          holdingRegisterMap.remove(address);
        } else {
          byte b0 = (byte) ((value >> 8) & 0xFF);
          byte b1 = (byte) (value & 0xFF);
          byte[] bs = new byte[]{b0, b1};
          holdingRegisterMap.put(address, bs);
        }

        return new WriteSingleRegisterResponse(request.address(), request.value());
      } finally {
        holdingRegisterLock.writeLock().unlock();
      }
    }

  }

}
