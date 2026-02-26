// Verilated -*- C++ -*-
// DESCRIPTION: Verilator output: Design internal header
// See VTopModule.h for the primary calling header

#ifndef VERILATED_VTOPMODULE___024ROOT_H_
#define VERILATED_VTOPMODULE___024ROOT_H_  // guard

#include "verilated.h"


class VTopModule__Syms;

class alignas(VL_CACHE_LINE_BYTES) VTopModule___024root final : public VerilatedModule {
  public:

    // DESIGN SPECIFIC STATE
    VL_IN8(clock,0,0);
    VL_IN8(reset,0,0);
    VL_IN8(io_start,0,0);
    VL_OUT8(io_done,0,0);
    CData/*0:0*/ TopModule__DOT__activeReg;
    CData/*0:0*/ TopModule__DOT__doneReg;
    CData/*2:0*/ TopModule__DOT__pcReg;
    CData/*0:0*/ TopModule__DOT___layer_probe;
    CData/*0:0*/ TopModule__DOT___layer_probe_0;
    CData/*0:0*/ TopModule__DOT___layer_probe_1;
    CData/*0:0*/ TopModule__DOT___layer_probe_2;
    CData/*0:0*/ TopModule__DOT___layer_probe_3;
    CData/*0:0*/ TopModule__DOT___layer_probe_4;
    CData/*0:0*/ __VstlFirstIteration;
    CData/*0:0*/ __Vtrigprevexpr___TOP__clock__0;
    IData/*31:0*/ __VactIterCount;
    VlUnpacked<QData/*63:0*/, 1> __VstlTriggered;
    VlUnpacked<QData/*63:0*/, 1> __VactTriggered;
    VlUnpacked<QData/*63:0*/, 1> __VnbaTriggered;
    VlUnpacked<CData/*0:0*/, 2> __Vm_traceActivity;

    // INTERNAL VARIABLES
    VTopModule__Syms* const vlSymsp;

    // CONSTRUCTORS
    VTopModule___024root(VTopModule__Syms* symsp, const char* v__name);
    ~VTopModule___024root();
    VL_UNCOPYABLE(VTopModule___024root);

    // INTERNAL METHODS
    void __Vconfigure(bool first);
};


#endif  // guard
