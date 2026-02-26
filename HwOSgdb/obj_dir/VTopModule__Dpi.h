// Verilated -*- C++ -*-
// DESCRIPTION: Verilator output: Prototypes for DPI import and export functions.
//
// Verilator includes this file in all generated .cpp files that use DPI functions.
// Manually include this file where DPI .c import functions are declared to ensure
// the C functions match the expectations of the DPI imports.

#ifndef VERILATED_VTOPMODULE__DPI_H_
#define VERILATED_VTOPMODULE__DPI_H_  // guard

#include "svdpi.h"

#ifdef __cplusplus
extern "C" {
#endif


    // DPI IMPORTS
    // DPI import at ../generated/KernelStateMonitorDPI.sv:13:32
    extern void kernel_monitor_tick(int n_threads, const svBitVecVal* pcs, const svBitVecVal* actives, const svBitVecVal* dones);

#ifdef __cplusplus
}
#endif

#endif  // guard
