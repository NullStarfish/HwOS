verilator -j 8 --cc --exe --build \
  -LDFLAGS "-lncurses" \
  -I./generated \
  ./generated/SimpleTop.sv \
  ./generated/KernelStateMonitorDPI.sv \
  HwOSgdb.cpp