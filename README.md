# Modbus Server Driver

A module for Ignition's OPC UA server that acts as a Modbus server, creating a bidirectional
"scratchpad" where data can be exchanged between Modbus clients and Ignition or other OPC UA
clients.

The Modbus process image is accessible through both interfaces:

- Modbus TCP clients
- OPC UA clients connected to Ignition's OPC UA server

Modbus clients can read or write as expected using any of the standard Modbus function codes.

OPC UA clients can additionally write to Discrete Input and Input Register areas, which are
read-only from Modbus clients.

## Development

The repository pins its Java and Maven toolchain with `mise`:

```shell
mise install
```

Run the full build through the pinned toolchain:

```shell
mise exec -- mvn clean verify
```

Apply Google Java Format with Spotless:

```shell
mise exec -- mvn spotless:apply
```

## Connection Security

The per-device `security.allowedIpAddresses` setting, labeled **Allowed IP Addresses** in the
Gateway, restricts which remote addresses may establish Modbus TCP connections. Its default value,
`*`, allows every remote address. Existing and migrated devices whose setting is missing or null
also use `*`.

### Syntax

To restrict access, provide a comma-separated allow list. A remote address is allowed when it
matches any entry; entry order does not matter.

| Entry form | Example | Meaning |
| --- | --- | --- |
| Any address | `*` | No address restriction |
| IPv4 literal | `192.168.1.50` | One address |
| IPv4 CIDR block | `192.168.1.0/24` | Every address in the block |
| IPv4 trailing wildcard | `192.168.1.*`, `192.168.*`, `10.*` | An octet-aligned address block |
| IPv4 short range | `192.168.1.10-20` | `.10` through `.20`, inclusive |
| IPv4 full range | `192.168.1.10-192.168.2.5` | The inclusive address interval |

Each list may contain at most 256 entries and 4,096 characters. IPv4 literals must contain four
decimal octets from `0` through `255`, without leading zeros. CIDR prefix lengths must be from `/0`
through `/32`. Wildcards must replace whole trailing octets;
`192.168.*.*` is also accepted and means the same `/16` as `192.168.*`. CIDR entries must use the
network address, with all host bits zero.

Whitespace is ignored around comma-delimited entries but is invalid inside an entry. Empty entries,
including a trailing comma, are invalid. DNS names, IPv6 addresses, regular expressions, and
negated deny rules are not supported.

### Enforcement and boundaries

The source address is checked when each TCP connection is accepted. Saving a device configuration
change restarts and rebinds the server, closes existing connections, and applies the new allow list.

Blank values are rejected when the configuration is saved. If a malformed nonblank value reaches
runtime, the device reports `Error: invalid allowed IP addresses` and does not bind the Modbus
listener. A blank runtime value preserves unrestricted access and produces a warning.

This setting restricts Modbus TCP access only. It does not restrict OPC UA access to the same
process image; secure that surface through Ignition's endpoint, user authentication, and
certificate trust settings.

Restrictive entries match IPv4 addresses only and do not match IPv6 peers. For loopback access, use
`127.0.0.1` over an IPv4 connection. If a device intentionally uses a non-IPv4 bind address, leave
`security.allowedIpAddresses` set to `*`.

The allow list complements `bindAddress`; it does not replace it. With the default `0.0.0.0`, the
server listens on all IPv4 interfaces and the allow list determines which IPv4 peers may connect.
Behind NAT, a Docker bridge, or a reverse proxy, multiple clients may share one visible source
address, so the allow list cannot distinguish those clients from one another.

## OPC UA Address Syntax

The syntax used in NodeIds has the following components:

- Unit ID (optional `0` to `255`, followed by `.`)
- Area (`C`, `DI`, `HR`, `IR`)
- DataType (`bool`, `int16`, `int32`, `int64`, `uint16`, `uint32`, `uint64`, `float`, `double`,
  `stringN`). The `C` and `DI` areas only support `bool`; register-only data types are rejected.
- Array dimensions (optional positive sizes in square brackets inside the angle brackets, after the
  DataType and before any modifiers)
- Offset (`0` to `65535`)
- Element indices (optional zero-based indices in square brackets after the offset)
- Bit (optional `.<index>` after the offset; for a register scalar value, use `0` through `N-1`
  where `N` is the number of bits in the underlying data type). A bit index outside that width or
  a bit selection on a non-integer data type is rejected.

Additionally, the DataType can have "modifiers" applied to influence the byte order and word order:

- `@BE` (big-endian, default)
- `@LE` (little-endian)
- `@HL` (high-low, default)
- `@LH` (low-high)

Examples:

- `C0` (coil area, offset 0)
- `7.C0` (unit ID 7, coil area, offset 0)
- `DI0` (discrete input area, offset 0)
- `HR<int16>0` (holding register area, offset 0)
- `7.HR<int16>0` (unit ID 7, holding register area, offset 0)
- `HR<int32>0.5` (holding register area, offset 0, bit 5 within a 32-bit signed integer (2
  registers))
- `HR<string10>0` (holding register area, offset 0, string of length 10 (5 registers))
- `IR<int32@LE>0` (input register area, offset 0, 32-bit signed integer (2 registers),
  little-endian byte order)
- `IR<float@LH>0` (input register area, offset 0, 32-bit floating point number (2 registers),
  low-high word order)

## Process Image Modes

By default, the driver uses one unified process image. All Modbus unit IDs and OPC UA addresses
refer to the same data. For example, `HR0`, `0.HR0`, and `7.HR0` all refer to the same holding
register.

Enable `processImage.separatePerUnitId` to maintain a separate process image for each Modbus unit
ID. In this mode, an OPC UA address without a unit ID refers to unit 0, so `HR0` and `0.HR0` refer
to the same register, while `1.HR0` refers to a different register. Every unit ID from 0 through
255 remains directly accessible from Modbus and OPC UA.

### Browsing separate process images

In separate mode, `browsing.unitIdBrowseRanges` controls which unit folders appear in the OPC UA
browse tree. Its default value is `0`. Enter comma-separated unit IDs and inclusive ranges, such as
`0,2,10-15`; duplicate or overlapping entries are displayed once in ascending order. An empty
value produces no unit folders.

Each selected unit is displayed under a `Unit N` folder containing the normal area folders. For
example, holding registers for unit 7 are browsed under `Unit 7/HoldingRegisters`, while their
variable NodeIds retain the direct address syntax such as `7.HR<int16>0`.

The browse ranges only control discovery. A unit-qualified address remains directly accessible
even when that unit is not included in `browsing.unitIdBrowseRanges`.

### Persistence and mode changes

When persistence is enabled, unified mode continues to use `coils.bin`, `discreteInputs.bin`,
`holdingRegisters.bin`, and `inputRegisters.bin` in the device data directory. Separate mode uses
the same filenames under `units/<unitId>/`, including `units/0/`.

The unified and per-unit datasets are independent. Changing modes does not copy or delete process
image data; changing back restores the data most recently persisted in that mode.

### Arrays

One to three dimensions may follow the DataType inside the angle brackets. Dimensions must appear
before modifiers: `HR<int16[10]>0` declares 10 signed 16-bit values, while
`HR<int32[5][2]@LE>100` declares a 5-by-2 array with little-endian byte order. The placement
`HR<int32@LE[5][2]>100` is rejected.

An array address without indices identifies the whole value. A one-dimensional value reads and
writes as a boxed array. A value with two or three dimensions reads and writes as an OPC UA
`Matrix`; its `ValueRank` and `ArrayDimensions` match the declared rank and dimensions. For
example, `C<bool[2][2]>0` is a 2-by-2 Boolean `Matrix`.

Append one zero-based index per declared dimension after the offset to select a single element.
The resulting node is scalar. For example, `HR<int16[10]>0[5]` selects element 5, and
`HR<int32[5][2]>100[2][1]` selects row 2, column 1.

Elements use row-major ordering: the last index varies fastest. For dimensions `[d0][d1]`, the
linear element index for `[i0][i1]` is `i0 * d1 + i1`. For three dimensions it is
`(i0 * d1 + i1) * d2 + i2`. The physical Modbus offset is the base offset plus the linear index
times the entries used by one element. Thus, the indexed 5-by-2 `int32` example above has linear
index `2 * 2 + 1 = 5`; because `int32` uses two registers, its physical offset is
`100 + 5 * 2 = 110`.

Each Modbus area contains exactly 65,536 entries, addressed from 0 through 65,535. Coils and
discrete inputs use one entry per element. Holding and input register elements use the number of
registers required by their DataType. An address is valid only when
`base offset + element count * entries per element <= 65536`. Therefore,
`HR<int16[36]>65500` exactly reaches the boundary, while `HR<int16[37]>65500` and
`HR<int64[99999][99999][99999]>0` are rejected before any value allocation.

The following combinations are also rejected:

- `HR<int16[10]>0.5` because a bit cannot be selected from an unindexed array; index an element
  first, then select its bit.
- `HR0[5]` because element indices require dimensions in the DataType declaration.

### Stricter address validation

Address validation is stricter than in earlier releases. Forms that previously parsed (and were
silently reinterpreted or partially ignored) are now rejected, and tags that use them show bad
quality after upgrading:

- Register-only DataTypes on coil or discrete input areas, e.g. `C<int16>0`; those areas are
  single bits and support only `bool`.
- Bit specifiers on non-integer DataTypes, e.g. `C0.5` or `HR<float>0.3`, and bit indices past
  the DataType's width.
- Addresses whose extent passes the end of the 65,536-entry area, e.g. `HR<int32>65535`.
- Trailing element subscripts without declared dimensions, e.g. `HR0[2]` (previously the
  subscript was ignored and the address read `HR0`).
- Invalid DataType modifiers, e.g. `HR<int16@EB>0` (previously ignored).

Review existing tags that rely on these forms and update them to the documented syntax when
upgrading.

### OPC UA IndexRange

OPC UA `IndexRange` is supported for both reads and writes of array nodes. Use the standard OPC UA
range text on the Read or Write request, such as `2:5` for one dimension or `1:2,0:1` for two
dimensions. It is request metadata, not part of the Modbus address syntax. Consequently,
`HR<int16[10]>0[2:5]` is rejected; use the one-dimensional array address above and put `2:5` in
the request's `IndexRange` field. A malformed range returns `Bad_IndexRangeInvalid`; a range that
cannot select data from the addressed value returns `Bad_IndexRangeNoData`.

Per the OPC UA rules for String values, `IndexRange` also selects characters: on a scalar
`stringN` node the range selects a sub-string (e.g. `0:4`), and on a `stringN` array node an
additional final dimension selects characters within the selected elements (e.g. `1,0:2` for the
first 3 characters of element 1).
