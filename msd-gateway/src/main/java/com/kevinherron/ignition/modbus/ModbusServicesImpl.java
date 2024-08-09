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
import com.digitalpetri.modbus.pdu.WriteMultipleCoilsRequest;
import com.digitalpetri.modbus.pdu.WriteMultipleCoilsResponse;
import com.digitalpetri.modbus.pdu.WriteMultipleRegistersRequest;
import com.digitalpetri.modbus.pdu.WriteMultipleRegistersResponse;
import com.digitalpetri.modbus.pdu.WriteSingleCoilRequest;
import com.digitalpetri.modbus.pdu.WriteSingleCoilResponse;
import com.digitalpetri.modbus.pdu.WriteSingleRegisterRequest;
import com.digitalpetri.modbus.pdu.WriteSingleRegisterResponse;
import com.digitalpetri.modbus.server.ModbusRequestContext;
import com.digitalpetri.modbus.server.ModbusServices;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

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
      int unitId,
      ReadCoilsRequest request
  ) {

    coilLock.readLock().lock();
    try {
      int address = request.address();
      int quantity = request.quantity();

      byte[] coils = readBits(address, quantity, coilMap);

      return new ReadCoilsResponse(coils);
    } finally {
      coilLock.readLock().unlock();
    }
  }

  @Override
  public ReadDiscreteInputsResponse readDiscreteInputs(
      ModbusRequestContext context,
      int unitId,
      ReadDiscreteInputsRequest request
  ) {

    discreteInputLock.readLock().lock();
    try {
      int address = request.address();
      int quantity = request.quantity();

      byte[] inputs = readBits(address, quantity, discreteInputMap);

      return new ReadDiscreteInputsResponse(inputs);
    } finally {
      discreteInputLock.readLock().unlock();
    }
  }

  @Override
  public ReadHoldingRegistersResponse readHoldingRegisters(
      ModbusRequestContext context,
      int unitId,
      ReadHoldingRegistersRequest request
  ) {

    holdingRegisterLock.readLock().lock();
    try {
      int address = request.address();
      int quantity = request.quantity();

      var registers = readRegisters(holdingRegisterMap, address, quantity);

      return new ReadHoldingRegistersResponse(registers);
    } finally {
      holdingRegisterLock.readLock().unlock();
    }
  }

  @Override
  public ReadInputRegistersResponse readInputRegisters(
      ModbusRequestContext context,
      int unitId,
      ReadInputRegistersRequest request
  ) {

    inputRegisterLock.readLock().lock();
    try {
      int address = request.address();
      int quantity = request.quantity();

      var registers = readRegisters(inputRegisterMap, address, quantity);

      return new ReadInputRegistersResponse(registers);
    } finally {
      inputRegisterLock.readLock().unlock();
    }
  }

  @Override
  public WriteSingleCoilResponse writeSingleCoil(
      ModbusRequestContext context,
      int unitId,
      WriteSingleCoilRequest request
  ) {

    int address = request.address();
    int value = request.value();

    coilLock.writeLock().lock();
    try {
      if (value == 0) {
        coilMap.remove(address);
      } else {
        coilMap.put(address, true);
      }

      return new WriteSingleCoilResponse(request.address(), request.value());
    } finally {
      coilLock.writeLock().unlock();
    }
  }

  @Override
  public WriteMultipleCoilsResponse writeMultipleCoils(
      ModbusRequestContext context,
      int unitId,
      WriteMultipleCoilsRequest request
  ) {

    int address = request.address();
    int quantity = request.quantity();
    byte[] values = request.values();

    coilLock.writeLock().lock();
    try {
      for (int i = 0; i < quantity; i++) {
        boolean value = (values[i / 8] & (1 << (i % 8))) != 0;
        if (!value) {
          coilMap.remove(address + i);
        } else {
          coilMap.put(address + i, value);
        }
      }

      return new WriteMultipleCoilsResponse(request.address(), request.quantity());
    } finally {
      coilLock.writeLock().unlock();
    }
  }

  @Override
  public WriteSingleRegisterResponse writeSingleRegister(
      ModbusRequestContext context,
      int unitId,
      WriteSingleRegisterRequest request
  ) {

    int address = request.address();
    int value = request.value();

    holdingRegisterLock.writeLock().lock();
    try {
      if (value == 0) {
        holdingRegisterMap.remove(address);
      } else {
        byte high = (byte) ((value >> 8) & 0xFF);
        byte low = (byte) (value & 0xFF);
        byte[] bs = new byte[]{high, low};
        holdingRegisterMap.put(address, bs);
      }

      return new WriteSingleRegisterResponse(request.address(), request.value());
    } finally {
      holdingRegisterLock.writeLock().unlock();
    }
  }

  @Override
  public WriteMultipleRegistersResponse writeMultipleRegisters(
      ModbusRequestContext context,
      int unitId,
      WriteMultipleRegistersRequest request
  ) {

    holdingRegisterLock.writeLock().lock();
    try {
      int address = request.address();
      int quantity = request.quantity();
      byte[] values = request.values();

      for (int i = 0; i < quantity; i++) {
        byte high = values[i * 2];
        byte low = values[i * 2 + 1];

        if (high == 0 && low == 0) {
          holdingRegisterMap.remove(address + i);
        } else {
          byte[] value = new byte[]{high, low};
          holdingRegisterMap.put(address + i, value);
        }
      }
      return new WriteMultipleRegistersResponse(request.address(), request.quantity());
    } finally {
      holdingRegisterLock.writeLock().unlock();
    }
  }

  @Override
  public MaskWriteRegisterResponse maskWriteRegister(
      ModbusRequestContext context,
      int unitId,
      MaskWriteRegisterRequest request
  ) {

    // Result = (Current Contents AND And_Mask) OR (Or_Mask AND (NOT And_Mask))
    int address = request.address();
    int andMask = request.andMask();
    int orMask = request.orMask();

    holdingRegisterLock.writeLock().lock();
    try {
      byte[] value = holdingRegisterMap.getOrDefault(address, new byte[2]);
      int currentValue = (value[0] << 8) | (value[1] & 0xFF);
      int result = (currentValue & andMask) | (orMask & ~andMask);

      if (result == 0) {
        holdingRegisterMap.remove(address);
      } else {
        byte high = (byte) ((result >> 8) & 0xFF);
        byte low = (byte) (result & 0xFF);
        byte[] bs = new byte[]{high, low};
        holdingRegisterMap.put(address, bs);
      }

      return new MaskWriteRegisterResponse(request.address(), request.andMask(), request.orMask());
    } finally {
      holdingRegisterLock.writeLock().unlock();
    }
  }

  static byte[] readBits(int address, int quantity, Map<Integer, Boolean> bitMap) {
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

}
