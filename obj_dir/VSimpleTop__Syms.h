// Verilated -*- C++ -*-
// DESCRIPTION: Verilator output: Symbol table internal header
//
// Internal details; most calling programs do not need this header,
// unless using verilator public meta comments.

#ifndef VERILATED_VSIMPLETOP__SYMS_H_
#define VERILATED_VSIMPLETOP__SYMS_H_  // guard

#include "verilated.h"

// INCLUDE MODEL CLASS

#include "VSimpleTop.h"

// INCLUDE MODULE CLASSES
#include "VSimpleTop___024root.h"

// DPI TYPES for DPI Export callbacks (Internal use)

// SYMS CLASS (contains all model state)
class alignas(VL_CACHE_LINE_BYTES) VSimpleTop__Syms final : public VerilatedSyms {
  public:
    // INTERNAL STATE
    VSimpleTop* const __Vm_modelp;
    VlDeleter __Vm_deleter;
    bool __Vm_didInit = false;

    // MODULE INSTANCE STATE
    VSimpleTop___024root           TOP;

    // CONSTRUCTORS
    VSimpleTop__Syms(VerilatedContext* contextp, const char* namep, VSimpleTop* modelp);
    ~VSimpleTop__Syms();

    // METHODS
    const char* name() { return TOP.name(); }
};

#endif  // guard
