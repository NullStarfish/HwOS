// Verilated -*- C++ -*-
// DESCRIPTION: Verilator output: Design implementation internals
// See VSimpleTop.h for the primary calling header

#include "VSimpleTop__pch.h"

VL_ATTR_COLD void VSimpleTop___024root___eval_static(VSimpleTop___024root* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VSimpleTop___024root___eval_static\n"); );
    VSimpleTop__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__Vtrigprevexpr___TOP__clock__0 = vlSelfRef.clock;
}

VL_ATTR_COLD void VSimpleTop___024root___eval_initial(VSimpleTop___024root* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VSimpleTop___024root___eval_initial\n"); );
    VSimpleTop__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
}

VL_ATTR_COLD void VSimpleTop___024root___eval_final(VSimpleTop___024root* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VSimpleTop___024root___eval_final\n"); );
    VSimpleTop__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
}

#ifdef VL_DEBUG
VL_ATTR_COLD void VSimpleTop___024root___dump_triggers__stl(const VlUnpacked<QData/*63:0*/, 1> &triggers, const std::string &tag);
#endif  // VL_DEBUG
VL_ATTR_COLD bool VSimpleTop___024root___eval_phase__stl(VSimpleTop___024root* vlSelf);

VL_ATTR_COLD void VSimpleTop___024root___eval_settle(VSimpleTop___024root* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VSimpleTop___024root___eval_settle\n"); );
    VSimpleTop__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Locals
    IData/*31:0*/ __VstlIterCount;
    // Body
    __VstlIterCount = 0U;
    vlSelfRef.__VstlFirstIteration = 1U;
    do {
        if (VL_UNLIKELY(((0x00000064U < __VstlIterCount)))) {
#ifdef VL_DEBUG
            VSimpleTop___024root___dump_triggers__stl(vlSelfRef.__VstlTriggered, "stl"s);
#endif
            VL_FATAL_MT("generated/SimpleTop.sv", 46, "", "Settle region did not converge after 100 tries");
        }
        __VstlIterCount = ((IData)(1U) + __VstlIterCount);
    } while (VSimpleTop___024root___eval_phase__stl(vlSelf));
}

VL_ATTR_COLD void VSimpleTop___024root___eval_triggers__stl(VSimpleTop___024root* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VSimpleTop___024root___eval_triggers__stl\n"); );
    VSimpleTop__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__VstlTriggered[0U] = ((0xfffffffffffffffeULL 
                                      & vlSelfRef.__VstlTriggered
                                      [0U]) | (IData)((IData)(vlSelfRef.__VstlFirstIteration)));
    vlSelfRef.__VstlFirstIteration = 0U;
#ifdef VL_DEBUG
    if (VL_UNLIKELY(vlSymsp->_vm_contextp__->debug())) {
        VSimpleTop___024root___dump_triggers__stl(vlSelfRef.__VstlTriggered, "stl"s);
    }
#endif
}

VL_ATTR_COLD bool VSimpleTop___024root___trigger_anySet__stl(const VlUnpacked<QData/*63:0*/, 1> &in);

#ifdef VL_DEBUG
VL_ATTR_COLD void VSimpleTop___024root___dump_triggers__stl(const VlUnpacked<QData/*63:0*/, 1> &triggers, const std::string &tag) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VSimpleTop___024root___dump_triggers__stl\n"); );
    // Body
    if ((1U & (~ (IData)(VSimpleTop___024root___trigger_anySet__stl(triggers))))) {
        VL_DBG_MSGS("         No '" + tag + "' region triggers active\n");
    }
    if ((1U & (IData)(triggers[0U]))) {
        VL_DBG_MSGS("         '" + tag + "' region trigger index 0 is active: Internal 'stl' trigger - first iteration\n");
    }
}
#endif  // VL_DEBUG

VL_ATTR_COLD bool VSimpleTop___024root___trigger_anySet__stl(const VlUnpacked<QData/*63:0*/, 1> &in) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VSimpleTop___024root___trigger_anySet__stl\n"); );
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

VL_ATTR_COLD void VSimpleTop___024root___stl_sequent__TOP__0(VSimpleTop___024root* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VSimpleTop___024root___stl_sequent__TOP__0\n"); );
    VSimpleTop__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.SimpleTop__DOT___GEN = (9U == (IData)(vlSelfRef.SimpleTop__DOT__pcReg));
    vlSelfRef.SimpleTop__DOT___GEN_0 = (2U == (IData)(vlSelfRef.SimpleTop__DOT__pcReg_1));
    vlSelfRef.SimpleTop__DOT__doneWire = ((IData)(vlSelfRef.SimpleTop__DOT__activeReg) 
                                          & (9U == (IData)(vlSelfRef.SimpleTop__DOT__pcReg)));
    vlSelfRef.SimpleTop__DOT__doneWire_1 = ((IData)(vlSelfRef.SimpleTop__DOT__activeReg_1) 
                                            & (2U == (IData)(vlSelfRef.SimpleTop__DOT__pcReg_1)));
    vlSelfRef.io_done = ((IData)(vlSelfRef.SimpleTop__DOT__doneWire) 
                         & (IData)(vlSelfRef.SimpleTop__DOT__doneWire_1));
}

VL_ATTR_COLD void VSimpleTop___024root___eval_stl(VSimpleTop___024root* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VSimpleTop___024root___eval_stl\n"); );
    VSimpleTop__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    if ((1ULL & vlSelfRef.__VstlTriggered[0U])) {
        VSimpleTop___024root___stl_sequent__TOP__0(vlSelf);
    }
}

VL_ATTR_COLD bool VSimpleTop___024root___eval_phase__stl(VSimpleTop___024root* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VSimpleTop___024root___eval_phase__stl\n"); );
    VSimpleTop__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Locals
    CData/*0:0*/ __VstlExecute;
    // Body
    VSimpleTop___024root___eval_triggers__stl(vlSelf);
    __VstlExecute = VSimpleTop___024root___trigger_anySet__stl(vlSelfRef.__VstlTriggered);
    if (__VstlExecute) {
        VSimpleTop___024root___eval_stl(vlSelf);
    }
    return (__VstlExecute);
}

bool VSimpleTop___024root___trigger_anySet__act(const VlUnpacked<QData/*63:0*/, 1> &in);

#ifdef VL_DEBUG
VL_ATTR_COLD void VSimpleTop___024root___dump_triggers__act(const VlUnpacked<QData/*63:0*/, 1> &triggers, const std::string &tag) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VSimpleTop___024root___dump_triggers__act\n"); );
    // Body
    if ((1U & (~ (IData)(VSimpleTop___024root___trigger_anySet__act(triggers))))) {
        VL_DBG_MSGS("         No '" + tag + "' region triggers active\n");
    }
    if ((1U & (IData)(triggers[0U]))) {
        VL_DBG_MSGS("         '" + tag + "' region trigger index 0 is active: @(posedge clock)\n");
    }
}
#endif  // VL_DEBUG

VL_ATTR_COLD void VSimpleTop___024root___ctor_var_reset(VSimpleTop___024root* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VSimpleTop___024root___ctor_var_reset\n"); );
    VSimpleTop__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    const uint64_t __VscopeHash = VL_MURMUR64_HASH(vlSelf->name());
    vlSelf->clock = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 5452235342940299466ull);
    vlSelf->reset = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 9928399931838511862ull);
    vlSelf->io_start = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 9567792102730658101ull);
    vlSelf->io_done = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 3995589108428058558ull);
    vlSelf->SimpleTop__DOT__activeReg = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 10949871311228714848ull);
    vlSelf->SimpleTop__DOT__activeReg_1 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 5120620063286244826ull);
    vlSelf->SimpleTop__DOT__sharedCounter = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 12202768701550327904ull);
    vlSelf->SimpleTop__DOT__pcReg = VL_SCOPED_RAND_RESET_I(4, __VscopeHash, 10919699830290249434ull);
    vlSelf->SimpleTop__DOT___GEN = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 11376989429897011889ull);
    vlSelf->SimpleTop__DOT__doneWire = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 17561030463944021505ull);
    vlSelf->SimpleTop__DOT__pcReg_1 = VL_SCOPED_RAND_RESET_I(2, __VscopeHash, 11227526637556543659ull);
    vlSelf->SimpleTop__DOT___GEN_0 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 15113702268735630379ull);
    vlSelf->SimpleTop__DOT__doneWire_1 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 7855858399527319761ull);
    for (int __Vi0 = 0; __Vi0 < 1; ++__Vi0) {
        vlSelf->__VstlTriggered[__Vi0] = 0;
    }
    for (int __Vi0 = 0; __Vi0 < 1; ++__Vi0) {
        vlSelf->__VactTriggered[__Vi0] = 0;
    }
    vlSelf->__Vtrigprevexpr___TOP__clock__0 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 13272892335938733197ull);
    for (int __Vi0 = 0; __Vi0 < 1; ++__Vi0) {
        vlSelf->__VnbaTriggered[__Vi0] = 0;
    }
}
