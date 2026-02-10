// Verilated -*- C++ -*-
// DESCRIPTION: Verilator output: Design internal header
// See VSimpleTop.h for the primary calling header

#ifndef VERILATED_VSIMPLETOP___024ROOT_H_
#define VERILATED_VSIMPLETOP___024ROOT_H_  // guard

#include "verilated.h"


class VSimpleTop__Syms;

class alignas(VL_CACHE_LINE_BYTES) VSimpleTop___024root final : public VerilatedModule {
  public:

    // DESIGN SPECIFIC STATE
    VL_IN8(clock,0,0);
    VL_IN8(reset,0,0);
    VL_IN8(io_start,0,0);
    VL_OUT8(io_done,0,0);
    CData/*0:0*/ SimpleTop__DOT__activeReg;
    CData/*0:0*/ SimpleTop__DOT__activeReg_1;
    CData/*3:0*/ SimpleTop__DOT__pcReg;
    CData/*0:0*/ SimpleTop__DOT___GEN;
    CData/*0:0*/ SimpleTop__DOT__doneWire;
    CData/*1:0*/ SimpleTop__DOT__pcReg_1;
    CData/*0:0*/ SimpleTop__DOT___GEN_0;
    CData/*0:0*/ SimpleTop__DOT__doneWire_1;
    CData/*0:0*/ __VstlFirstIteration;
    CData/*0:0*/ __Vtrigprevexpr___TOP__clock__0;
    IData/*31:0*/ SimpleTop__DOT__sharedCounter;
    IData/*31:0*/ __VactIterCount;
    VlUnpacked<QData/*63:0*/, 1> __VstlTriggered;
    VlUnpacked<QData/*63:0*/, 1> __VactTriggered;
    VlUnpacked<QData/*63:0*/, 1> __VnbaTriggered;

    // INTERNAL VARIABLES
    VSimpleTop__Syms* const vlSymsp;

    // CONSTRUCTORS
    VSimpleTop___024root(VSimpleTop__Syms* symsp, const char* v__name);
    ~VSimpleTop___024root();
    VL_UNCOPYABLE(VSimpleTop___024root);

    // INTERNAL METHODS
    void __Vconfigure(bool first);
};


#endif  // guard
