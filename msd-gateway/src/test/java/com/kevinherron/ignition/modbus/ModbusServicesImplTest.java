package com.kevinherron.ignition.modbus;

import static org.joou.Unsigned.ubyte;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.digitalpetri.modbus.pdu.ReadCoilsRequest;
import org.junit.jupiter.api.Test;

class ModbusServicesImplTest {

  ModbusServicesImpl services = new ModbusServicesImpl();

  @Test
  void readCoilPatterns() {
    for (int i = 0; i < 32; i++) {
      setCoilPattern(i, 8, 0b00000000);
      assertCoilPattern(i, 0b00000000);
      setCoilPattern(i, 8, 0b11111111);
      assertCoilPattern(i, 0b11111111);
      setCoilPattern(i, 8, 0b00001111);
      assertCoilPattern(i, 0b00001111);
      setCoilPattern(i, 8, 0b11110000);
      assertCoilPattern(i, 0b11110000);
      setCoilPattern(i, 8, 0b00110011);
      assertCoilPattern(i, 0b00110011);
      setCoilPattern(i, 8, 0b11001100);
      assertCoilPattern(i, 0b11001100);
      setCoilPattern(i, 8, 0b01010101);
      assertCoilPattern(i, 0b01010101);
      setCoilPattern(i, 8, 0b10101010);
      assertCoilPattern(i, 0b10101010);
    }
  }

  private void assertCoilPattern(int address, int pattern) {
    var response = services.readCoils(
        null,
        ubyte(0),
        new ReadCoilsRequest(address, 8)
    );

    byte[] coils = response.coils();

    assertEquals(1, coils.length);
    assertEquals(pattern, coils[0] & 0xFF);
  }

  private void setCoilPattern(int start, int quantity, int pattern) {
    for (int i = 0; i < quantity; i++) {
      services.coilMap.put(start + i, (pattern & (1 << i)) != 0);
    }
  }

}