// Verilated -*- C++ -*-
// DESCRIPTION: Verilator output: Design implementation internals
// See VTopModule.h for the primary calling header

#include "VTopModule__pch.h"

extern "C" void kernel_monitor_tick(int n_threads, const svBitVecVal* pcs, const svBitVecVal* actives, const svBitVecVal* dones);

void VTopModule___024root____Vdpiimwrap_TopModule__DOT__monitor__DOT__kernel_monitor_tick_TOP(IData/*31:0*/ n_threads, IData/*31:0*/ pcs, CData/*0:0*/ actives, CData/*0:0*/ dones) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VTopModule___024root____Vdpiimwrap_TopModule__DOT__monitor__DOT__kernel_monitor_tick_TOP\n"); );
    // Body
    int n_threads__Vcvt;
    n_threads__Vcvt = n_threads;
    svBitVecVal pcs__Vcvt[1];
    VL_SET_SVBV_I(32, pcs__Vcvt, pcs);
    svBitVecVal actives__Vcvt[1];
    VL_SET_SVBV_I(1, actives__Vcvt, actives);
    svBitVecVal dones__Vcvt[1];
    VL_SET_SVBV_I(1, dones__Vcvt, dones);
    kernel_monitor_tick(n_threads__Vcvt, pcs__Vcvt, actives__Vcvt, dones__Vcvt);
}

#ifdef VL_DEBUG
VL_ATTR_COLD void VTopModule___024root___dump_triggers__act(const VlUnpacked<QData/*63:0*/, 1> &triggers, const std::string &tag);
#endif  // VL_DEBUG

void VTopModule___024root___eval_triggers__act(VTopModule___024root* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VTopModule___024root___eval_triggers__act\n"); );
    VTopModule__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__VactTriggered[0U] = (QData)((IData)(
                                                    ((IData)(vlSelfRef.clock) 
                                                     & (~ (IData)(vlSelfRef.__Vtrigprevexpr___TOP__clock__0)))));
    vlSelfRef.__Vtrigprevexpr___TOP__clock__0 = vlSelfRef.clock;
#ifdef VL_DEBUG
    if (VL_UNLIKELY(vlSymsp->_vm_contextp__->debug())) {
        VTopModule___024root___dump_triggers__act(vlSelfRef.__VactTriggered, "act"s);
    }
#endif
}

bool VTopModule___024root___trigger_anySet__act(const VlUnpacked<QData/*63:0*/, 1> &in) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VTopModule___024root___trigger_anySet__act\n"); );
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

void VTopModule___024root___nba_sequent__TOP__0(VTopModule___024root* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VTopModule___024root___nba_sequent__TOP__0\n"); );
    VTopModule__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Locals
    CData/*0:0*/ __Vdly__TopModule__DOT__activeReg;
    __Vdly__TopModule__DOT__activeReg = 0;
    CData/*2:0*/ __Vdly__TopModule__DOT__pcReg;
    __Vdly__TopModule__DOT__pcReg = 0;
    // Body
    if ((1U & (~ (IData)(vlSelfRef.reset)))) {
        VTopModule___024root____Vdpiimwrap_TopModule__DOT__monitor__DOT__kernel_monitor_tick_TOP(1U, (IData)(vlSelfRef.TopModule__DOT__pcReg), vlSelfRef.TopModule__DOT__activeReg, (IData)(vlSelfRef.TopModule__DOT__doneReg));
    }
    __Vdly__TopModule__DOT__activeReg = vlSelfRef.TopModule__DOT__activeReg;
    __Vdly__TopModule__DOT__pcReg = vlSelfRef.TopModule__DOT__pcReg;
    vlSelfRef.TopModule__DOT__doneReg = ((1U & (~ (IData)(vlSelfRef.reset))) 
                                         && ((~ (IData)(vlSelfRef.io_start)) 
                                             & ((5U 
                                                 == (IData)(vlSelfRef.TopModule__DOT__pcReg)) 
                                                | (IData)(vlSelfRef.TopModule__DOT__doneReg))));
    if (vlSelfRef.reset) {
        __Vdly__TopModule__DOT__activeReg = 0U;
        __Vdly__TopModule__DOT__pcReg = 0U;
    } else {
        __Vdly__TopModule__DOT__activeReg = ((IData)(vlSelfRef.io_start) 
                                             | ((~ (IData)(vlSelfRef.TopModule__DOT___layer_probe_4)) 
                                                & (IData)(vlSelfRef.TopModule__DOT__activeReg)));
        if (((IData)(vlSelfRef.io_start) | (IData)(vlSelfRef.TopModule__DOT___layer_probe_4))) {
            __Vdly__TopModule__DOT__pcReg = 0U;
        } else if (((((((IData)(vlSelfRef.TopModule__DOT___layer_probe_3) 
                        | (IData)(vlSelfRef.TopModule__DOT___layer_probe_2)) 
                       | (IData)(vlSelfRef.TopModule__DOT___layer_probe_1)) 
                      | (IData)(vlSelfRef.TopModule__DOT___layer_probe_0)) 
                     | (IData)(vlSelfRef.TopModule__DOT___layer_probe)) 
                    & (IData)(vlSelfRef.TopModule__DOT__activeReg))) {
            __Vdly__TopModule__DOT__pcReg = (7U & ((IData)(1U) 
                                                   + (IData)(vlSelfRef.TopModule__DOT__pcReg)));
        }
    }
    vlSelfRef.TopModule__DOT__activeReg = __Vdly__TopModule__DOT__activeReg;
    vlSelfRef.TopModule__DOT__pcReg = __Vdly__TopModule__DOT__pcReg;
    vlSelfRef.io_done = vlSelfRef.TopModule__DOT__doneReg;
    vlSelfRef.TopModule__DOT___layer_probe = (0U == (IData)(vlSelfRef.TopModule__DOT__pcReg));
    vlSelfRef.TopModule__DOT___layer_probe_0 = (1U 
                                                == (IData)(vlSelfRef.TopModule__DOT__pcReg));
    vlSelfRef.TopModule__DOT___layer_probe_1 = (2U 
                                                == (IData)(vlSelfRef.TopModule__DOT__pcReg));
    vlSelfRef.TopModule__DOT___layer_probe_2 = (3U 
                                                == (IData)(vlSelfRef.TopModule__DOT__pcReg));
    vlSelfRef.TopModule__DOT___layer_probe_3 = (4U 
                                                == (IData)(vlSelfRef.TopModule__DOT__pcReg));
    vlSelfRef.TopModule__DOT___layer_probe_4 = (5U 
                                                == (IData)(vlSelfRef.TopModule__DOT__pcReg));
}

void VTopModule___024root___eval_nba(VTopModule___024root* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VTopModule___024root___eval_nba\n"); );
    VTopModule__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    if ((1ULL & vlSelfRef.__VnbaTriggered[0U])) {
        VTopModule___024root___nba_sequent__TOP__0(vlSelf);
        vlSelfRef.__Vm_traceActivity[1U] = 1U;
    }
}

void VTopModule___024root___trigger_orInto__act(VlUnpacked<QData/*63:0*/, 1> &out, const VlUnpacked<QData/*63:0*/, 1> &in) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VTopModule___024root___trigger_orInto__act\n"); );
    // Locals
    IData/*31:0*/ n;
    // Body
    n = 0U;
    do {
        out[n] = (out[n] | in[n]);
        n = ((IData)(1U) + n);
    } while ((1U > n));
}

bool VTopModule___024root___eval_phase__act(VTopModule___024root* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VTopModule___024root___eval_phase__act\n"); );
    VTopModule__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    VTopModule___024root___eval_triggers__act(vlSelf);
    VTopModule___024root___trigger_orInto__act(vlSelfRef.__VnbaTriggered, vlSelfRef.__VactTriggered);
    return (0U);
}

void VTopModule___024root___trigger_clear__act(VlUnpacked<QData/*63:0*/, 1> &out) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VTopModule___024root___trigger_clear__act\n"); );
    // Locals
    IData/*31:0*/ n;
    // Body
    n = 0U;
    do {
        out[n] = 0ULL;
        n = ((IData)(1U) + n);
    } while ((1U > n));
}

bool VTopModule___024root___eval_phase__nba(VTopModule___024root* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VTopModule___024root___eval_phase__nba\n"); );
    VTopModule__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Locals
    CData/*0:0*/ __VnbaExecute;
    // Body
    __VnbaExecute = VTopModule___024root___trigger_anySet__act(vlSelfRef.__VnbaTriggered);
    if (__VnbaExecute) {
        VTopModule___024root___eval_nba(vlSelf);
        VTopModule___024root___trigger_clear__act(vlSelfRef.__VnbaTriggered);
    }
    return (__VnbaExecute);
}

void VTopModule___024root___eval(VTopModule___024root* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VTopModule___024root___eval\n"); );
    VTopModule__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Locals
    IData/*31:0*/ __VnbaIterCount;
    // Body
    __VnbaIterCount = 0U;
    do {
        if (VL_UNLIKELY(((0x00000064U < __VnbaIterCount)))) {
#ifdef VL_DEBUG
            VTopModule___024root___dump_triggers__act(vlSelfRef.__VnbaTriggered, "nba"s);
#endif
            VL_FATAL_MT("../generated/TopModule.sv", 46, "", "NBA region did not converge after 100 tries");
        }
        __VnbaIterCount = ((IData)(1U) + __VnbaIterCount);
        vlSelfRef.__VactIterCount = 0U;
        do {
            if (VL_UNLIKELY(((0x00000064U < vlSelfRef.__VactIterCount)))) {
#ifdef VL_DEBUG
                VTopModule___024root___dump_triggers__act(vlSelfRef.__VactTriggered, "act"s);
#endif
                VL_FATAL_MT("../generated/TopModule.sv", 46, "", "Active region did not converge after 100 tries");
            }
            vlSelfRef.__VactIterCount = ((IData)(1U) 
                                         + vlSelfRef.__VactIterCount);
        } while (VTopModule___024root___eval_phase__act(vlSelf));
    } while (VTopModule___024root___eval_phase__nba(vlSelf));
}

#ifdef VL_DEBUG
void VTopModule___024root___eval_debug_assertions(VTopModule___024root* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VTopModule___024root___eval_debug_assertions\n"); );
    VTopModule__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
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
