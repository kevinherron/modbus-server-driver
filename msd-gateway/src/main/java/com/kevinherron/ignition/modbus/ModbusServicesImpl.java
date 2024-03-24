package com.kevinherron.ignition.modbus;

import com.digitalpetri.modbus.pdu.MaskWriteRegisterRequest;
import com.digitalpetri.modbus.pdu.MaskWriteRegisterResponse;
import com.digitalpetri.modbus.pdu.ReadCoilsRequest;
import com.digitalpetri.modbus.pdu.ReadCoilsResponse;
import com.digitalpetri.modbus.pdu.ReadDiscreteInputsRequest;
import com.digitalpetri.modbus.pdu.ReadDiscreteInputsResponse;
import com.digitalpetri.modbus.pdu.ReadHoldingRegistersRequest;
import com.digitalpetri.modbus.pdu.ReadHoldingRegistersResponse;
import com.digitalpetri.modbus.pdu.ReadInputRegistersRequest;
import com.digitalpetri.modbus.pdu.ReadInputRegistersResponse;
import com.digitalpetri.modbus.pdu.WriteMultipleRegistersRequest;
import com.digitalpetri.modbus.pdu.WriteMultipleRegistersResponse;
import com.digitalpetri.modbus.pdu.WriteSingleRegisterRequest;
import com.digitalpetri.modbus.pdu.WriteSingleRegisterResponse;
import com.digitalpetri.modbus.server.ModbusRequestContext;
import com.digitalpetri.modbus.server.ModbusServices;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.joou.UByte;

class ModbusServicesImpl implements ModbusServices {

  final ReadWriteLock coilLock = new ReentrantReadWriteLock();
  final Map<Integer, Boolean> coilMap = new HashMap<>();

  final ReadWriteLock discreteInputLock = new ReentrantReadWriteLock();
  final Map<Integer, Boolean> discreteInputMap = new HashMap<>();

  final ReadWriteLock holdingRegisterLock = new ReentrantReadWriteLock();
  final Map<Integer, byte[]> holdingRegisterMap = new HashMap<>();

  final ReadWriteLock inputRegisterLock = new ReentrantReadWriteLock();
  final Map<Integer, byte[]> inputRegisterMap = new HashMap<>();

  @Override
  public ReadCoilsResponse readCoils(
      ModbusRequestContext context,
      UByte unitId,
      ReadCoilsRequest request
  ) {

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
  public ReadDiscreteInputsResponse readDiscreteInputs(
      ModbusRequestContext context,
      UByte unitId,
      ReadDiscreteInputsRequest request
  ) {

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
  public ReadHoldingRegistersResponse readHoldingRegisters(
      ModbusRequestContext context,
      UByte unitId,
      ReadHoldingRegistersRequest request
  ) {

    holdingRegisterLock.readLock().lock();
    try {
      int address = request.address().intValue();
      int quantity = request.quantity().intValue();

      var registers = readRegisters(holdingRegisterMap, address, quantity);

      return new ReadHoldingRegistersResponse(registers);
    } finally {
      holdingRegisterLock.readLock().unlock();
    }
  }

  @Override
  public ReadInputRegistersResponse readInputRegisters(
      ModbusRequestContext context,
      UByte unitId,
      ReadInputRegistersRequest request
  ) {

    inputRegisterLock.readLock().lock();
    try {
      int address = request.address().intValue();
      int quantity = request.quantity().intValue();

      var registers = readRegisters(inputRegisterMap, address, quantity);

      return new ReadInputRegistersResponse(registers);
    } finally {
      inputRegisterLock.readLock().unlock();
    }
  }

  static byte[] readRegisters(Map<Integer, byte[]> registerMap, int address, int quantity) {
    var registers = new byte[quantity * 2];

    for (int i = 0; i < quantity; i++) {
      byte[] value = registerMap.getOrDefault(address + i, new byte[2]);

      registers[i * 2] = value[0];
      registers[i * 2 + 1] = value[1];
    }

    return registers;
  }

  static void writeRegisters(Map<Integer, byte[]> registerMap, int address, byte[] registers) {
    for (int i = 0; i < registers.length / 2; i++) {
      byte[] value = new byte[]{registers[i * 2], registers[i * 2 + 1]};
      registerMap.put(address + i, value);
    }
  }

  @Override
  public WriteMultipleRegistersResponse writeMultipleRegisters(
      ModbusRequestContext context,
      UByte unitId,
      WriteMultipleRegistersRequest request
  ) {

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
  public WriteSingleRegisterResponse writeSingleRegister(
      ModbusRequestContext context,
      UByte unitId,
      WriteSingleRegisterRequest request
  ) {

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

  @Override
  public MaskWriteRegisterResponse maskWriteRegister(
      ModbusRequestContext context,
      UByte unitId,
      MaskWriteRegisterRequest request
  ) {

    // Result = (Current Contents AND And_Mask) OR (Or_Mask AND (NOT And_Mask))
    int address = request.address().intValue();
    int andMask = request.andMask().intValue();
    int orMask = request.orMask().intValue();

    holdingRegisterLock.writeLock().lock();
    try {
      byte[] value = holdingRegisterMap.getOrDefault(address, new byte[2]);
      int currentValue = (value[0] << 8) | (value[1] & 0xFF);
      int result = (currentValue & andMask) | (orMask & ~andMask);

      byte b0 = (byte) ((result >> 8) & 0xFF);
      byte b1 = (byte) (result & 0xFF);
      byte[] bs = new byte[]{b0, b1};
      holdingRegisterMap.put(address, bs);

      return new MaskWriteRegisterResponse(request.address(), request.andMask(), request.orMask());
    } finally {
      holdingRegisterLock.writeLock().unlock();
    }
  }

}
