// Verilated -*- C++ -*-
// DESCRIPTION: Verilator output: Design implementation internals
// See VSimpleTop.h for the primary calling header

#include "VSimpleTop__pch.h"

extern "C" void kernel_monitor_tick(int n_threads, const svBitVecVal* pcs, const svBitVecVal* actives, const svBitVecVal* starts, const svBitVecVal* aborts, const svBitVecVal* dones);

void VSimpleTop___024root____Vdpiimwrap_SimpleTop__DOT__monitor__DOT__kernel_monitor_tick_TOP(IData/*31:0*/ n_threads, QData/*63:0*/ pcs, CData/*1:0*/ actives, CData/*1:0*/ starts, CData/*1:0*/ aborts, CData/*1:0*/ dones) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VSimpleTop___024root____Vdpiimwrap_SimpleTop__DOT__monitor__DOT__kernel_monitor_tick_TOP\n"); );
    // Body
    int n_threads__Vcvt;
    n_threads__Vcvt = n_threads;
    svBitVecVal pcs__Vcvt[2];
    VL_SET_SVBV_Q(64, pcs__Vcvt, pcs);
    svBitVecVal actives__Vcvt[1];
    VL_SET_SVBV_I(2, actives__Vcvt, actives);
    svBitVecVal starts__Vcvt[1];
    VL_SET_SVBV_I(2, starts__Vcvt, starts);
    svBitVecVal aborts__Vcvt[1];
    VL_SET_SVBV_I(2, aborts__Vcvt, aborts);
    svBitVecVal dones__Vcvt[1];
    VL_SET_SVBV_I(2, dones__Vcvt, dones);
    kernel_monitor_tick(n_threads__Vcvt, pcs__Vcvt, actives__Vcvt, starts__Vcvt, aborts__Vcvt, dones__Vcvt);
}

#ifdef VL_DEBUG
VL_ATTR_COLD void VSimpleTop___024root___dump_triggers__act(const VlUnpacked<QData/*63:0*/, 1> &triggers, const std::string &tag);
#endif  // VL_DEBUG

void VSimpleTop___024root___eval_triggers__act(VSimpleTop___024root* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VSimpleTop___024root___eval_triggers__act\n"); );
    VSimpleTop__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__VactTriggered[0U] = (QData)((IData)(
                                                    ((IData)(vlSelfRef.clock) 
                                                     & (~ (IData)(vlSelfRef.__Vtrigprevexpr___TOP__clock__0)))));
    vlSelfRef.__Vtrigprevexpr___TOP__clock__0 = vlSelfRef.clock;
#ifdef VL_DEBUG
    if (VL_UNLIKELY(vlSymsp->_vm_contextp__->debug())) {
        VSimpleTop___024root___dump_triggers__act(vlSelfRef.__VactTriggered, "act"s);
    }
#endif
}

bool VSimpleTop___024root___trigger_anySet__act(const VlUnpacked<QData/*63:0*/, 1> &in) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VSimpleTop___024root___trigger_anySet__act\n"); );
    // Locals
    IData/*31:0*/ n;
    // Body
    n = 0U;
    do {
        if (in[n]) {
            return (1U);
        }
        n = ((IData)(1U) + n);
    } while ((1U > n));
    return (0U);
}

void VSimpleTop___024root___nba_sequent__TOP__0(VSimpleTop___024root* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VSimpleTop___024root___nba_sequent__TOP__0\n"); );
    VSimpleTop__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Locals
    CData/*0:0*/ __Vdly__SimpleTop__DOT__activeReg;
    __Vdly__SimpleTop__DOT__activeReg = 0;
    CData/*0:0*/ __Vdly__SimpleTop__DOT__activeReg_1;
    __Vdly__SimpleTop__DOT__activeReg_1 = 0;
    IData/*31:0*/ __Vdly__SimpleTop__DOT__sharedCounter;
    __Vdly__SimpleTop__DOT__sharedCounter = 0;
    CData/*3:0*/ __Vdly__SimpleTop__DOT__pcReg;
    __Vdly__SimpleTop__DOT__pcReg = 0;
    CData/*1:0*/ __Vdly__SimpleTop__DOT__pcReg_1;
    __Vdly__SimpleTop__DOT__pcReg_1 = 0;
    // Body
    if ((1U & (~ (IData)(vlSelfRef.reset)))) {
        VSimpleTop___024root____Vdpiimwrap_SimpleTop__DOT__monitor__DOT__kernel_monitor_tick_TOP(2U, 
                                                                                (((QData)((IData)(vlSelfRef.SimpleTop__DOT__pcReg_1)) 
                                                                                << 0x00000020U) 
                                                                                | (QData)((IData)(vlSelfRef.SimpleTop__DOT__pcReg))), 
                                                                                (((IData)(vlSelfRef.SimpleTop__DOT__activeReg_1) 
                                                                                << 1U) 
                                                                                | (IData)(vlSelfRef.SimpleTop__DOT__activeReg)), 
                                                                                (3U 
                                                                                & (- (IData)((IData)(vlSelfRef.io_start)))), 0U, 
                                                                                (((IData)(vlSelfRef.SimpleTop__DOT__doneWire_1) 
                                                                                << 1U) 
                                                                                | (IData)(vlSelfRef.SimpleTop__DOT__doneWire)));
    }
    __Vdly__SimpleTop__DOT__sharedCounter = vlSelfRef.SimpleTop__DOT__sharedCounter;
    __Vdly__SimpleTop__DOT__activeReg = vlSelfRef.SimpleTop__DOT__activeReg;
    __Vdly__SimpleTop__DOT__activeReg_1 = vlSelfRef.SimpleTop__DOT__activeReg_1;
    __Vdly__SimpleTop__DOT__pcReg = vlSelfRef.SimpleTop__DOT__pcReg;
    __Vdly__SimpleTop__DOT__pcReg_1 = vlSelfRef.SimpleTop__DOT__pcReg_1;
    if (vlSelfRef.reset) {
        __Vdly__SimpleTop__DOT__activeReg = 0U;
        __Vdly__SimpleTop__DOT__activeReg_1 = 0U;
        __Vdly__SimpleTop__DOT__sharedCounter = 0U;
        __Vdly__SimpleTop__DOT__pcReg = 0U;
        __Vdly__SimpleTop__DOT__pcReg_1 = 0U;
    } else {
        if (vlSelfRef.SimpleTop__DOT__activeReg) {
            __Vdly__SimpleTop__DOT__activeReg = (1U 
                                                 & (~ (IData)(vlSelfRef.SimpleTop__DOT___GEN)));
            if ((8U == (IData)(vlSelfRef.SimpleTop__DOT__pcReg))) {
                __Vdly__SimpleTop__DOT__sharedCounter 
                    = ((IData)(1U) + vlSelfRef.SimpleTop__DOT__sharedCounter);
            } else if ((7U == (IData)(vlSelfRef.SimpleTop__DOT__pcReg))) {
                __Vdly__SimpleTop__DOT__sharedCounter 
                    = ((IData)(1U) + vlSelfRef.SimpleTop__DOT__sharedCounter);
            } else if ((6U == (IData)(vlSelfRef.SimpleTop__DOT__pcReg))) {
                __Vdly__SimpleTop__DOT__sharedCounter 
                    = ((IData)(1U) + vlSelfRef.SimpleTop__DOT__sharedCounter);
            } else if ((5U == (IData)(vlSelfRef.SimpleTop__DOT__pcReg))) {
                __Vdly__SimpleTop__DOT__sharedCounter 
                    = ((IData)(1U) + vlSelfRef.SimpleTop__DOT__sharedCounter);
            } else if ((4U == (IData)(vlSelfRef.SimpleTop__DOT__pcReg))) {
                __Vdly__SimpleTop__DOT__sharedCounter 
                    = ((IData)(1U) + vlSelfRef.SimpleTop__DOT__sharedCounter);
            } else if ((3U == (IData)(vlSelfRef.SimpleTop__DOT__pcReg))) {
                __Vdly__SimpleTop__DOT__sharedCounter 
                    = ((IData)(1U) + vlSelfRef.SimpleTop__DOT__sharedCounter);
            } else if ((2U == (IData)(vlSelfRef.SimpleTop__DOT__pcReg))) {
                __Vdly__SimpleTop__DOT__sharedCounter 
                    = ((IData)(1U) + vlSelfRef.SimpleTop__DOT__sharedCounter);
            } else if ((1U == (IData)(vlSelfRef.SimpleTop__DOT__pcReg))) {
                __Vdly__SimpleTop__DOT__sharedCounter 
                    = ((IData)(1U) + vlSelfRef.SimpleTop__DOT__sharedCounter);
            } else if ((0U == (IData)(vlSelfRef.SimpleTop__DOT__pcReg))) {
                __Vdly__SimpleTop__DOT__sharedCounter = 0U;
            }
            __Vdly__SimpleTop__DOT__pcReg = ((((IData)(vlSelfRef.io_start) 
                                               & (IData)(vlSelfRef.SimpleTop__DOT__doneWire)) 
                                              | (IData)(vlSelfRef.SimpleTop__DOT___GEN))
                                              ? 0U : 
                                             (0x0000000fU 
                                              & ((IData)(1U) 
                                                 + (IData)(vlSelfRef.SimpleTop__DOT__pcReg))));
        } else if (vlSelfRef.io_start) {
            __Vdly__SimpleTop__DOT__activeReg = 1U;
            __Vdly__SimpleTop__DOT__pcReg = 0U;
        } else {
            __Vdly__SimpleTop__DOT__activeReg = 0U;
        }
        if (vlSelfRef.SimpleTop__DOT__activeReg_1) {
            __Vdly__SimpleTop__DOT__activeReg_1 = (1U 
                                                   & (~ (IData)(vlSelfRef.SimpleTop__DOT___GEN_0)));
            if ((((IData)(vlSelfRef.io_start) & (IData)(vlSelfRef.SimpleTop__DOT__doneWire_1)) 
                 | (IData)(vlSelfRef.SimpleTop__DOT___GEN_0))) {
                __Vdly__SimpleTop__DOT__pcReg_1 = 0U;
            } else if ((1U & (~ ((0U == (IData)(vlSelfRef.SimpleTop__DOT__pcReg_1)) 
                                 & (5U > vlSelfRef.SimpleTop__DOT__sharedCounter))))) {
                __Vdly__SimpleTop__DOT__pcReg_1 = (3U 
                                                   & ((IData)(1U) 
                                                      + (IData)(vlSelfRef.SimpleTop__DOT__pcReg_1)));
            }
        } else if (vlSelfRef.io_start) {
            __Vdly__SimpleTop__DOT__activeReg_1 = 1U;
            __Vdly__SimpleTop__DOT__pcReg_1 = 0U;
        } else {
            __Vdly__SimpleTop__DOT__activeReg_1 = 0U;
        }
    }
    vlSelfRef.SimpleTop__DOT__sharedCounter = __Vdly__SimpleTop__DOT__sharedCounter;
    vlSelfRef.SimpleTop__DOT__activeReg = __Vdly__SimpleTop__DOT__activeReg;
    vlSelfRef.SimpleTop__DOT__activeReg_1 = __Vdly__SimpleTop__DOT__activeReg_1;
    vlSelfRef.SimpleTop__DOT__pcReg = __Vdly__SimpleTop__DOT__pcReg;
    vlSelfRef.SimpleTop__DOT__pcReg_1 = __Vdly__SimpleTop__DOT__pcReg_1;
    vlSelfRef.SimpleTop__DOT___GEN = (9U == (IData)(vlSelfRef.SimpleTop__DOT__pcReg));
    vlSelfRef.SimpleTop__DOT__doneWire = ((IData)(vlSelfRef.SimpleTop__DOT__activeReg) 
                                          & (9U == (IData)(vlSelfRef.SimpleTop__DOT__pcReg)));
    vlSelfRef.SimpleTop__DOT___GEN_0 = (2U == (IData)(vlSelfRef.SimpleTop__DOT__pcReg_1));
    vlSelfRef.SimpleTop__DOT__doneWire_1 = ((IData)(vlSelfRef.SimpleTop__DOT__activeReg_1) 
                                            & (2U == (IData)(vlSelfRef.SimpleTop__DOT__pcReg_1)));
    vlSelfRef.io_done = ((IData)(vlSelfRef.SimpleTop__DOT__doneWire) 
                         & (IData)(vlSelfRef.SimpleTop__DOT__doneWire_1));
}

void VSimpleTop___024root___eval_nba(VSimpleTop___024root* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VSimpleTop___024root___eval_nba\n"); );
    VSimpleTop__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    if ((1ULL & vlSelfRef.__VnbaTriggered[0U])) {
        VSimpleTop___024root___nba_sequent__TOP__0(vlSelf);
    }
}

void VSimpleTop___024root___trigger_orInto__act(VlUnpacked<QData/*63:0*/, 1> &out, const VlUnpacked<QData/*63:0*/, 1> &in) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VSimpleTop___024root___trigger_orInto__act\n"); );
    // Locals
    IData/*31:0*/ n;
    // Body
    n = 0U;
    do {
        out[n] = (out[n] | in[n]);
        n = ((IData)(1U) + n);
    } while ((1U > n));
}

bool VSimpleTop___024root___eval_phase__act(VSimpleTop___024root* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VSimpleTop___024root___eval_phase__act\n"); );
    VSimpleTop__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    VSimpleTop___024root___eval_triggers__act(vlSelf);
    VSimpleTop___024root___trigger_orInto__act(vlSelfRef.__VnbaTriggered, vlSelfRef.__VactTriggered);
    return (0U);
}

void VSimpleTop___024root___trigger_clear__act(VlUnpacked<QData/*63:0*/, 1> &out) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VSimpleTop___024root___trigger_clear__act\n"); );
    // Locals
    IData/*31:0*/ n;
    // Body
    n = 0U;
    do {
        out[n] = 0ULL;
        n = ((IData)(1U) + n);
    } while ((1U > n));
}

bool VSimpleTop___024root___eval_phase__nba(VSimpleTop___024root* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VSimpleTop___024root___eval_phase__nba\n"); );
    VSimpleTop__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Locals
    CData/*0:0*/ __VnbaExecute;
    // Body
    __VnbaExecute = VSimpleTop___024root___trigger_anySet__act(vlSelfRef.__VnbaTriggered);
    if (__VnbaExecute) {
        VSimpleTop___024root___eval_nba(vlSelf);
        VSimpleTop___024root___trigger_clear__act(vlSelfRef.__VnbaTriggered);
    }
    return (__VnbaExecute);
}

void VSimpleTop___024root___eval(VSimpleTop___024root* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VSimpleTop___024root___eval\n"); );
    VSimpleTop__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Locals
    IData/*31:0*/ __VnbaIterCount;
    // Body
    __VnbaIterCount = 0U;
    do {
        if (VL_UNLIKELY(((0x00000064U < __VnbaIterCount)))) {
#ifdef VL_DEBUG
            VSimpleTop___024root___dump_triggers__act(vlSelfRef.__VnbaTriggered, "nba"s);
#endif
            VL_FATAL_MT("generated/SimpleTop.sv", 46, "", "NBA region did not converge after 100 tries");
        }
        __VnbaIterCount = ((IData)(1U) + __VnbaIterCount);
        vlSelfRef.__VactIterCount = 0U;
        do {
            if (VL_UNLIKELY(((0x00000064U < vlSelfRef.__VactIterCount)))) {
#ifdef VL_DEBUG
                VSimpleTop___024root___dump_triggers__act(vlSelfRef.__VactTriggered, "act"s);
#endif
                VL_FATAL_MT("generated/SimpleTop.sv", 46, "", "Active region did not converge after 100 tries");
            }
            vlSelfRef.__VactIterCount = ((IData)(1U) 
                                         + vlSelfRef.__VactIterCount);
        } while (VSimpleTop___024root___eval_phase__act(vlSelf));
    } while (VSimpleTop___024root___eval_phase__nba(vlSelf));
}

#ifdef VL_DEBUG
void VSimpleTop___024root___eval_debug_assertions(VSimpleTop___024root* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VSimpleTop___024root___eval_debug_assertions\n"); );
    VSimpleTop__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    if (VL_UNLIKELY(((vlSelfRef.clock & 0xfeU)))) {
        Verilated::overWidthError("clock");
    }
    if (VL_UNLIKELY(((vlSelfRef.reset & 0xfeU)))) {
        Verilated::overWidthError("reset");
    }
    if (VL_UNLIKELY(((vlSelfRef.io_start & 0xfeU)))) {
        Verilated::overWidthError("io_start");
    }
}
#endif  // VL_DEBUG
