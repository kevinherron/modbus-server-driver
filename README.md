# Modbus Server Driver

A module for Ignition's OPC UA server that acts as a Modbus server, creating a bidirectional "
scratchpad" where data can be exchanged between Modbus clients and Ignition or other OPC UA clients.

The process image in the Modbus server is accessible from either sides:

- from a Modbus TCP client/master
- from an OPC UA client connected to Ignition's OPC UA server

Modbus clients can read or write as expected using any of the standard Modbus function codes.

OPC UA clients can additionally write to Discrete Input and Input Register areas, which are
read-only from Modbus clients.

## OPC UA Address Syntax

The syntax used in NodeIds has the following components:

- Area (`C`, `DI`, `HR`, `IR`)
- DataType (`bool`, `int16`, `int32`, `int64`, `uint16`, `uint32`, `uint64`, `float`, `double`,
  `stringN`)
- Offset (`0` to `65535`)
- Bit (`.0` to `.N` where `N` is the number of bits in the underlying data type)

Additionally, the DataType can have "modifiers" applied to influence the byte order and word order:

- `@BE` (big-endian, default)
- `@LE` (little-endian)
- `@HL` (high-low, default)
- `@LH` (low-high)

Examples:

- `C0` (coil area, offset 0)
- `DI0` (discrete input area, offset 0)
- `HR<int16>0` (holding register area, offset 0)
- `HR<int32>0.5` (holding register area, offset 0, bit 5 within a 32-bit signed integer (2
  registers))
- `HR<string10>0` (holding register area, offset 0, string of length 10 (5 registers))
- `IR<int32@LE>0` (input register area, offset 0, 32-byte signed integer (2 registers),
  little-endian byte order)
- `IR<float@LH>0` (input register area, offset 0, 32-byte floating point number (2 registers),
  low-high word order)
