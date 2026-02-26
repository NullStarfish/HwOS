// Verilated -*- C++ -*-
// DESCRIPTION: Verilator output: Design implementation internals
// See VTopModule.h for the primary calling header

#include "VTopModule__pch.h"

VL_ATTR_COLD void VTopModule___024root___eval_static(VTopModule___024root* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VTopModule___024root___eval_static\n"); );
    VTopModule__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__Vtrigprevexpr___TOP__clock__0 = vlSelfRef.clock;
}

VL_ATTR_COLD void VTopModule___024root___eval_initial(VTopModule___024root* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VTopModule___024root___eval_initial\n"); );
    VTopModule__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
}

VL_ATTR_COLD void VTopModule___024root___eval_final(VTopModule___024root* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VTopModule___024root___eval_final\n"); );
    VTopModule__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
}

#ifdef VL_DEBUG
VL_ATTR_COLD void VTopModule___024root___dump_triggers__stl(const VlUnpacked<QData/*63:0*/, 1> &triggers, const std::string &tag);
#endif  // VL_DEBUG
VL_ATTR_COLD bool VTopModule___024root___eval_phase__stl(VTopModule___024root* vlSelf);

VL_ATTR_COLD void VTopModule___024root___eval_settle(VTopModule___024root* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VTopModule___024root___eval_settle\n"); );
    VTopModule__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Locals
    IData/*31:0*/ __VstlIterCount;
    // Body
    __VstlIterCount = 0U;
    vlSelfRef.__VstlFirstIteration = 1U;
    do {
        if (VL_UNLIKELY(((0x00000064U < __VstlIterCount)))) {
#ifdef VL_DEBUG
            VTopModule___024root___dump_triggers__stl(vlSelfRef.__VstlTriggered, "stl"s);
#endif
            VL_FATAL_MT("../generated/TopModule.sv", 46, "", "Settle region did not converge after 100 tries");
        }
        __VstlIterCount = ((IData)(1U) + __VstlIterCount);
    } while (VTopModule___024root___eval_phase__stl(vlSelf));
}

VL_ATTR_COLD void VTopModule___024root___eval_triggers__stl(VTopModule___024root* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VTopModule___024root___eval_triggers__stl\n"); );
    VTopModule__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__VstlTriggered[0U] = ((0xfffffffffffffffeULL 
                                      & vlSelfRef.__VstlTriggered
                                      [0U]) | (IData)((IData)(vlSelfRef.__VstlFirstIteration)));
    vlSelfRef.__VstlFirstIteration = 0U;
#ifdef VL_DEBUG
    if (VL_UNLIKELY(vlSymsp->_vm_contextp__->debug())) {
        VTopModule___024root___dump_triggers__stl(vlSelfRef.__VstlTriggered, "stl"s);
    }
#endif
}

VL_ATTR_COLD bool VTopModule___024root___trigger_anySet__stl(const VlUnpacked<QData/*63:0*/, 1> &in);

#ifdef VL_DEBUG
VL_ATTR_COLD void VTopModule___024root___dump_triggers__stl(const VlUnpacked<QData/*63:0*/, 1> &triggers, const std::string &tag) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VTopModule___024root___dump_triggers__stl\n"); );
    // Body
    if ((1U & (~ (IData)(VTopModule___024root___trigger_anySet__stl(triggers))))) {
        VL_DBG_MSGS("         No '" + tag + "' region triggers active\n");
    }
    if ((1U & (IData)(triggers[0U]))) {
        VL_DBG_MSGS("         '" + tag + "' region trigger index 0 is active: Internal 'stl' trigger - first iteration\n");
    }
}
#endif  // VL_DEBUG

VL_ATTR_COLD bool VTopModule___024root___trigger_anySet__stl(const VlUnpacked<QData/*63:0*/, 1> &in) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VTopModule___024root___trigger_anySet__stl\n"); );
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

VL_ATTR_COLD void VTopModule___024root___stl_sequent__TOP__0(VTopModule___024root* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VTopModule___024root___stl_sequent__TOP__0\n"); );
    VTopModule__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
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

VL_ATTR_COLD void VTopModule___024root___eval_stl(VTopModule___024root* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VTopModule___024root___eval_stl\n"); );
    VTopModule__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    if ((1ULL & vlSelfRef.__VstlTriggered[0U])) {
        VTopModule___024root___stl_sequent__TOP__0(vlSelf);
    }
}

VL_ATTR_COLD bool VTopModule___024root___eval_phase__stl(VTopModule___024root* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VTopModule___024root___eval_phase__stl\n"); );
    VTopModule__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Locals
    CData/*0:0*/ __VstlExecute;
    // Body
    VTopModule___024root___eval_triggers__stl(vlSelf);
    __VstlExecute = VTopModule___024root___trigger_anySet__stl(vlSelfRef.__VstlTriggered);
    if (__VstlExecute) {
        VTopModule___024root___eval_stl(vlSelf);
    }
    return (__VstlExecute);
}

bool VTopModule___024root___trigger_anySet__act(const VlUnpacked<QData/*63:0*/, 1> &in);

#ifdef VL_DEBUG
VL_ATTR_COLD void VTopModule___024root___dump_triggers__act(const VlUnpacked<QData/*63:0*/, 1> &triggers, const std::string &tag) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VTopModule___024root___dump_triggers__act\n"); );
    // Body
    if ((1U & (~ (IData)(VTopModule___024root___trigger_anySet__act(triggers))))) {
        VL_DBG_MSGS("         No '" + tag + "' region triggers active\n");
    }
    if ((1U & (IData)(triggers[0U]))) {
        VL_DBG_MSGS("         '" + tag + "' region trigger index 0 is active: @(posedge clock)\n");
    }
}
#endif  // VL_DEBUG

VL_ATTR_COLD void VTopModule___024root___ctor_var_reset(VTopModule___024root* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VTopModule___024root___ctor_var_reset\n"); );
    VTopModule__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    const uint64_t __VscopeHash = VL_MURMUR64_HASH(vlSelf->name());
    vlSelf->clock = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 5452235342940299466ull);
    vlSelf->reset = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 9928399931838511862ull);
    vlSelf->io_start = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 9567792102730658101ull);
    vlSelf->io_done = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 3995589108428058558ull);
    vlSelf->TopModule__DOT__activeReg = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 1976879737510597966ull);
    vlSelf->TopModule__DOT__doneReg = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 8478835497995247564ull);
    vlSelf->TopModule__DOT__pcReg = VL_SCOPED_RAND_RESET_I(3, __VscopeHash, 17127354050164836563ull);
    vlSelf->TopModule__DOT___layer_probe = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 17071557546017824210ull);
    vlSelf->TopModule__DOT___layer_probe_0 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 6446565904988790657ull);
    vlSelf->TopModule__DOT___layer_probe_1 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 3816387415888149598ull);
    vlSelf->TopModule__DOT___layer_probe_2 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 8044951177544160718ull);
    vlSelf->TopModule__DOT___layer_probe_3 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 5818552817732942420ull);
    vlSelf->TopModule__DOT___layer_probe_4 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 4901378227243582906ull);
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
    for (int __Vi0 = 0; __Vi0 < 2; ++__Vi0) {
        vlSelf->__Vm_traceActivity[__Vi0] = 0;
    }
}
