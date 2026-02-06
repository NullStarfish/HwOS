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
    vlSelfRef.SimpleTop__DOT___GEN_1 = (5U == (IData)(vlSelfRef.SimpleTop__DOT__pcReg));
    vlSelfRef.SimpleTop__DOT___GEN_2 = (0U == (IData)(vlSelfRef.SimpleTop__DOT__pcReg_1));
    vlSelfRef.SimpleTop__DOT___GEN_6 = (5U == (IData)(vlSelfRef.SimpleTop__DOT__pcReg_2));
    vlSelfRef.SimpleTop__DOT___GEN_7 = (0U == (IData)(vlSelfRef.SimpleTop__DOT__pcReg_3));
    vlSelfRef.SimpleTop__DOT___GEN_13 = (7U == (IData)(vlSelfRef.SimpleTop__DOT__pcReg_4));
    vlSelfRef.SimpleTop__DOT___GEN_14 = (0U == (IData)(vlSelfRef.SimpleTop__DOT__pcReg_5));
    vlSelfRef.SimpleTop__DOT___GEN_17 = (5U == (IData)(vlSelfRef.SimpleTop__DOT__pcReg_6));
    vlSelfRef.SimpleTop__DOT___GEN_20 = (7U == (IData)(vlSelfRef.SimpleTop__DOT__pcReg_6));
    vlSelfRef.SimpleTop__DOT___GEN_21 = (0U == (IData)(vlSelfRef.SimpleTop__DOT__pcReg_7));
    vlSelfRef.SimpleTop__DOT___GEN_23 = (5U == (IData)(vlSelfRef.SimpleTop__DOT__pcReg_8));
    vlSelfRef.SimpleTop__DOT___GEN_26 = (7U == (IData)(vlSelfRef.SimpleTop__DOT__pcReg_8));
    vlSelfRef.SimpleTop__DOT___GEN_27 = (0U == (IData)(vlSelfRef.SimpleTop__DOT__pcReg_9));
    vlSelfRef.io_r1 = vlSelfRef.SimpleTop__DOT__phyRegs_1;
    vlSelfRef.io_r2 = vlSelfRef.SimpleTop__DOT__phyRegs_2;
    vlSelfRef.io_r3 = vlSelfRef.SimpleTop__DOT__phyRegs_3;
    vlSelfRef.io_r4 = vlSelfRef.SimpleTop__DOT__phyRegs_4;
    vlSelfRef.SimpleTop__DOT___GEN = ((IData)(vlSelfRef.SimpleTop__DOT__activeReg_5) 
                                      & (3U == (IData)(vlSelfRef.SimpleTop__DOT__pcReg)));
    vlSelfRef.SimpleTop__DOT__startWire_5 = ((IData)(vlSelfRef.SimpleTop__DOT__activeReg) 
                                             & (1U 
                                                == (IData)(vlSelfRef.SimpleTop__DOT__pcReg_1)));
    vlSelfRef.SimpleTop__DOT___GEN_4 = ((IData)(vlSelfRef.SimpleTop__DOT__activeReg_6) 
                                        & (3U == (IData)(vlSelfRef.SimpleTop__DOT__pcReg_2)));
    vlSelfRef.SimpleTop__DOT__startWire_6 = ((IData)(vlSelfRef.SimpleTop__DOT__activeReg_1) 
                                             & (1U 
                                                == (IData)(vlSelfRef.SimpleTop__DOT__pcReg_3)));
    vlSelfRef.SimpleTop__DOT__intents_2_release = ((IData)(vlSelfRef.SimpleTop__DOT__activeReg_7) 
                                                   & (6U 
                                                      == (IData)(vlSelfRef.SimpleTop__DOT__pcReg_4)));
    vlSelfRef.SimpleTop__DOT__startWire_7 = ((IData)(vlSelfRef.SimpleTop__DOT__activeReg_2) 
                                             & (1U 
                                                == (IData)(vlSelfRef.SimpleTop__DOT__pcReg_5)));
    vlSelfRef.SimpleTop__DOT___GEN_18 = ((IData)(vlSelfRef.SimpleTop__DOT__activeReg_8) 
                                         & (5U == (IData)(vlSelfRef.SimpleTop__DOT__pcReg_6)));
    vlSelfRef.SimpleTop__DOT__startWire_8 = ((IData)(vlSelfRef.SimpleTop__DOT__activeReg_3) 
                                             & (1U 
                                                == (IData)(vlSelfRef.SimpleTop__DOT__pcReg_7)));
    vlSelfRef.SimpleTop__DOT___GEN_24 = ((IData)(vlSelfRef.SimpleTop__DOT__activeReg_9) 
                                         & (5U == (IData)(vlSelfRef.SimpleTop__DOT__pcReg_8)));
    vlSelfRef.SimpleTop__DOT__startWire_9 = ((IData)(vlSelfRef.SimpleTop__DOT__activeReg_4) 
                                             & (1U 
                                                == (IData)(vlSelfRef.SimpleTop__DOT__pcReg_9)));
    vlSelfRef.SimpleTop__DOT__doneWire_5 = ((IData)(vlSelfRef.SimpleTop__DOT__activeReg_5) 
                                            & (5U == (IData)(vlSelfRef.SimpleTop__DOT__pcReg)));
    vlSelfRef.SimpleTop__DOT__doneWire_6 = ((IData)(vlSelfRef.SimpleTop__DOT__activeReg_6) 
                                            & (5U == (IData)(vlSelfRef.SimpleTop__DOT__pcReg_2)));
    vlSelfRef.SimpleTop__DOT__doneWire_7 = ((IData)(vlSelfRef.SimpleTop__DOT__activeReg_7) 
                                            & (7U == (IData)(vlSelfRef.SimpleTop__DOT__pcReg_4)));
    vlSelfRef.SimpleTop__DOT__doneWire_8 = ((IData)(vlSelfRef.SimpleTop__DOT__activeReg_8) 
                                            & (7U == (IData)(vlSelfRef.SimpleTop__DOT__pcReg_6)));
    vlSelfRef.SimpleTop__DOT__doneWire_9 = ((IData)(vlSelfRef.SimpleTop__DOT__activeReg_9) 
                                            & (7U == (IData)(vlSelfRef.SimpleTop__DOT__pcReg_8)));
    vlSelfRef.SimpleTop__DOT__intents_3_release = ((IData)(vlSelfRef.SimpleTop__DOT__activeReg_8) 
                                                   & (6U 
                                                      == (IData)(vlSelfRef.SimpleTop__DOT__pcReg_6)));
    vlSelfRef.SimpleTop__DOT__intents_4_release = ((IData)(vlSelfRef.SimpleTop__DOT__activeReg_9) 
                                                   & (6U 
                                                      == (IData)(vlSelfRef.SimpleTop__DOT__pcReg_8)));
    vlSelfRef.SimpleTop__DOT___stall_T_7 = ((3U != (IData)(vlSelfRef.SimpleTop__DOT__nextIssueId)) 
                                            | (IData)(vlSelfRef.SimpleTop__DOT__busyTable_3));
    vlSelfRef.SimpleTop__DOT___GEN_22 = ((IData)(vlSelfRef.SimpleTop__DOT__activeReg_3) 
                                         & (0U == (IData)(vlSelfRef.SimpleTop__DOT__pcReg_7)));
    vlSelfRef.SimpleTop__DOT___stall_T_9 = ((4U != (IData)(vlSelfRef.SimpleTop__DOT__nextIssueId)) 
                                            | (IData)(vlSelfRef.SimpleTop__DOT__busyTable_4));
    vlSelfRef.SimpleTop__DOT___GEN_28 = ((IData)(vlSelfRef.SimpleTop__DOT__activeReg_4) 
                                         & (0U == (IData)(vlSelfRef.SimpleTop__DOT__pcReg_9)));
    vlSelfRef.SimpleTop__DOT___GEN_16 = ((IData)(vlSelfRef.SimpleTop__DOT__activeReg_8) 
                                         & (3U == (IData)(vlSelfRef.SimpleTop__DOT__pcReg_6)));
    vlSelfRef.SimpleTop__DOT___GEN_10 = ((IData)(vlSelfRef.SimpleTop__DOT__activeReg_7) 
                                         & (4U == (IData)(vlSelfRef.SimpleTop__DOT__pcReg_4)));
    vlSelfRef.SimpleTop__DOT__intents_0_release = ((IData)(vlSelfRef.SimpleTop__DOT__activeReg_5) 
                                                   & (4U 
                                                      == (IData)(vlSelfRef.SimpleTop__DOT__pcReg)));
    vlSelfRef.SimpleTop__DOT__intents_1_release = ((IData)(vlSelfRef.SimpleTop__DOT__activeReg_6) 
                                                   & (4U 
                                                      == (IData)(vlSelfRef.SimpleTop__DOT__pcReg_2)));
    vlSelfRef.SimpleTop__DOT__stall_2 = ((0U != (IData)(vlSelfRef.SimpleTop__DOT__nextIssueId)) 
                                         | (IData)(vlSelfRef.SimpleTop__DOT__busyTable_1));
    vlSelfRef.SimpleTop__DOT___GEN_3 = ((IData)(vlSelfRef.SimpleTop__DOT__activeReg) 
                                        & (0U == (IData)(vlSelfRef.SimpleTop__DOT__pcReg_1)));
    vlSelfRef.SimpleTop__DOT___stall_T_3 = ((1U != (IData)(vlSelfRef.SimpleTop__DOT__nextIssueId)) 
                                            | (IData)(vlSelfRef.SimpleTop__DOT__busyTable_2));
    vlSelfRef.SimpleTop__DOT___GEN_8 = ((IData)(vlSelfRef.SimpleTop__DOT__activeReg_1) 
                                        & (0U == (IData)(vlSelfRef.SimpleTop__DOT__pcReg_3)));
    vlSelfRef.SimpleTop__DOT__doneWire = ((IData)(vlSelfRef.SimpleTop__DOT__activeReg) 
                                          & (IData)(vlSelfRef.SimpleTop__DOT__doneWire_5));
    vlSelfRef.SimpleTop__DOT__doneWire_1 = ((IData)(vlSelfRef.SimpleTop__DOT__activeReg_1) 
                                            & (IData)(vlSelfRef.SimpleTop__DOT__doneWire_6));
    vlSelfRef.SimpleTop__DOT__doneWire_2 = ((IData)(vlSelfRef.SimpleTop__DOT__activeReg_2) 
                                            & (IData)(vlSelfRef.SimpleTop__DOT__doneWire_7));
    vlSelfRef.SimpleTop__DOT__doneWire_3 = ((IData)(vlSelfRef.SimpleTop__DOT__activeReg_3) 
                                            & (IData)(vlSelfRef.SimpleTop__DOT__doneWire_8));
    vlSelfRef.io_done = ((IData)(vlSelfRef.SimpleTop__DOT__activeReg_4) 
                         & (IData)(vlSelfRef.SimpleTop__DOT__doneWire_9));
    vlSelfRef.SimpleTop__DOT__intents_3_acquire = (
                                                   (~ (IData)(vlSelfRef.SimpleTop__DOT___stall_T_7)) 
                                                   & (IData)(vlSelfRef.SimpleTop__DOT___GEN_22));
    vlSelfRef.SimpleTop__DOT__intents_3_reg = ((1U 
                                                & ((~ (IData)(vlSelfRef.SimpleTop__DOT__intents_3_release)) 
                                                   & ((~ (IData)(vlSelfRef.SimpleTop__DOT___GEN_22)) 
                                                      | (IData)(vlSelfRef.SimpleTop__DOT___stall_T_7))))
                                                ? 0U
                                                : 3U);
    vlSelfRef.SimpleTop__DOT__intents_4_acquire = (
                                                   (~ (IData)(vlSelfRef.SimpleTop__DOT___stall_T_9)) 
                                                   & (IData)(vlSelfRef.SimpleTop__DOT___GEN_28));
    vlSelfRef.SimpleTop__DOT__intents_4_reg = (1U & 
                                               ((~ 
                                                 ((~ (IData)(vlSelfRef.SimpleTop__DOT___GEN_28)) 
                                                  | (IData)(vlSelfRef.SimpleTop__DOT___stall_T_9))) 
                                                | (IData)(vlSelfRef.SimpleTop__DOT__intents_4_release)));
    vlSelfRef.SimpleTop__DOT__io_1_req = ((IData)(vlSelfRef.SimpleTop__DOT___GEN_16) 
                                          | (IData)(vlSelfRef.SimpleTop__DOT___GEN_10));
    vlSelfRef.SimpleTop__DOT__io_1_isWr = ((IData)(vlSelfRef.SimpleTop__DOT__activeReg_8)
                                            ? ((3U 
                                                != (IData)(vlSelfRef.SimpleTop__DOT__pcReg_6)) 
                                               & (IData)(vlSelfRef.SimpleTop__DOT___GEN_10))
                                            : (IData)(vlSelfRef.SimpleTop__DOT___GEN_10));
    vlSelfRef.SimpleTop__DOT__io_1_addr = ((IData)(vlSelfRef.SimpleTop__DOT___GEN_16)
                                            ? vlSelfRef.SimpleTop__DOT__addrVal_1
                                            : ((IData)(vlSelfRef.SimpleTop__DOT___GEN_10)
                                                ? vlSelfRef.SimpleTop__DOT__addrVal
                                                : 0U));
    vlSelfRef.SimpleTop__DOT__intents_0_acquire = (
                                                   (~ (IData)(vlSelfRef.SimpleTop__DOT__stall_2)) 
                                                   & (IData)(vlSelfRef.SimpleTop__DOT___GEN_3));
    vlSelfRef.SimpleTop__DOT__intents_0_reg = (1U & 
                                               ((~ 
                                                 ((~ (IData)(vlSelfRef.SimpleTop__DOT___GEN_3)) 
                                                  | (IData)(vlSelfRef.SimpleTop__DOT__stall_2))) 
                                                | (IData)(vlSelfRef.SimpleTop__DOT__intents_0_release)));
    vlSelfRef.SimpleTop__DOT__intents_1_acquire = (
                                                   (~ (IData)(vlSelfRef.SimpleTop__DOT___stall_T_3)) 
                                                   & (IData)(vlSelfRef.SimpleTop__DOT___GEN_8));
    vlSelfRef.SimpleTop__DOT__intents_1_reg = (1U & 
                                               ((~ 
                                                 ((~ (IData)(vlSelfRef.SimpleTop__DOT___GEN_8)) 
                                                  | (IData)(vlSelfRef.SimpleTop__DOT___stall_T_3))) 
                                                | (IData)(vlSelfRef.SimpleTop__DOT__intents_1_release)));
    vlSelfRef.SimpleTop__DOT__stall_8 = ((2U != (IData)(vlSelfRef.SimpleTop__DOT__nextIssueId)) 
                                         | ((IData)(vlSelfRef.SimpleTop__DOT__busyTable_0) 
                                            | (((~ (IData)(vlSelfRef.SimpleTop__DOT__intents_0_reg)) 
                                                & (IData)(vlSelfRef.SimpleTop__DOT__intents_0_acquire)) 
                                               | ((~ (IData)(vlSelfRef.SimpleTop__DOT__intents_1_reg)) 
                                                  & (IData)(vlSelfRef.SimpleTop__DOT__intents_1_acquire)))));
    vlSelfRef.SimpleTop__DOT__intents_2_acquire = ((IData)(vlSelfRef.SimpleTop__DOT__activeReg_2) 
                                                   & ((~ (IData)(vlSelfRef.SimpleTop__DOT__stall_8)) 
                                                      & (0U 
                                                         == (IData)(vlSelfRef.SimpleTop__DOT__pcReg_5))));
}

VL_ATTR_COLD void VSimpleTop___024root____Vm_traceActivitySetAll(VSimpleTop___024root* vlSelf);

VL_ATTR_COLD void VSimpleTop___024root___eval_stl(VSimpleTop___024root* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VSimpleTop___024root___eval_stl\n"); );
    VSimpleTop__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    if ((1ULL & vlSelfRef.__VstlTriggered[0U])) {
        VSimpleTop___024root___stl_sequent__TOP__0(vlSelf);
        VSimpleTop___024root____Vm_traceActivitySetAll(vlSelf);
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

VL_ATTR_COLD void VSimpleTop___024root____Vm_traceActivitySetAll(VSimpleTop___024root* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VSimpleTop___024root____Vm_traceActivitySetAll\n"); );
    VSimpleTop__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__Vm_traceActivity[0U] = 1U;
    vlSelfRef.__Vm_traceActivity[1U] = 1U;
}

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
    vlSelf->io_r1 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 9740247222696236908ull);
    vlSelf->io_r2 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 16237672019164212487ull);
    vlSelf->io_r3 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 17161203356971771725ull);
    vlSelf->io_r4 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 2069492201946809824ull);
    vlSelf->SimpleTop__DOT__phyRegs_1 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 9406163295068141480ull);
    vlSelf->SimpleTop__DOT__phyRegs_2 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 3995232194468216167ull);
    vlSelf->SimpleTop__DOT__phyRegs_3 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 451536387341257267ull);
    vlSelf->SimpleTop__DOT__phyRegs_4 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 14728557365201967488ull);
    vlSelf->SimpleTop__DOT__mem_0 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 18283416008082741039ull);
    vlSelf->SimpleTop__DOT__mem_1 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 12690417276186708676ull);
    vlSelf->SimpleTop__DOT__mem_2 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 7581711502387303539ull);
    vlSelf->SimpleTop__DOT__mem_3 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 5123400399464385585ull);
    vlSelf->SimpleTop__DOT__mem_4 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 17000078062413174744ull);
    vlSelf->SimpleTop__DOT__mem_5 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 7839128825105785426ull);
    vlSelf->SimpleTop__DOT__mem_6 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 17398734402472676333ull);
    vlSelf->SimpleTop__DOT__mem_7 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 1240768754380124634ull);
    vlSelf->SimpleTop__DOT__mem_8 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 4013840138093622688ull);
    vlSelf->SimpleTop__DOT__mem_9 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 1387736916633103402ull);
    vlSelf->SimpleTop__DOT__mem_10 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 12601762764326668452ull);
    vlSelf->SimpleTop__DOT__mem_11 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 14708571913111128140ull);
    vlSelf->SimpleTop__DOT__mem_12 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 4674059633031515008ull);
    vlSelf->SimpleTop__DOT__mem_13 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 4581523873765110117ull);
    vlSelf->SimpleTop__DOT__mem_14 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 14242927134962519242ull);
    vlSelf->SimpleTop__DOT__mem_15 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 17032398366629229008ull);
    vlSelf->SimpleTop__DOT__mem_16 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 9609205328675627148ull);
    vlSelf->SimpleTop__DOT__mem_17 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 10780803153542698687ull);
    vlSelf->SimpleTop__DOT__mem_18 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 4647672637384184470ull);
    vlSelf->SimpleTop__DOT__mem_19 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 3768276528337353238ull);
    vlSelf->SimpleTop__DOT__mem_20 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 5823623366330132923ull);
    vlSelf->SimpleTop__DOT__mem_21 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 13603677028114943323ull);
    vlSelf->SimpleTop__DOT__mem_22 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 8202600978973568166ull);
    vlSelf->SimpleTop__DOT__mem_23 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 1149092273801653559ull);
    vlSelf->SimpleTop__DOT__mem_24 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 13738605303392522670ull);
    vlSelf->SimpleTop__DOT__mem_25 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 17312501868947934031ull);
    vlSelf->SimpleTop__DOT__mem_26 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 2945912264953117441ull);
    vlSelf->SimpleTop__DOT__mem_27 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 5979087476311254517ull);
    vlSelf->SimpleTop__DOT__mem_28 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 16334131839871829136ull);
    vlSelf->SimpleTop__DOT__mem_29 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 1513531542497853682ull);
    vlSelf->SimpleTop__DOT__mem_30 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 6606469245694398660ull);
    vlSelf->SimpleTop__DOT__mem_31 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 13260283411988896993ull);
    vlSelf->SimpleTop__DOT__mem_32 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 18028620098540785248ull);
    vlSelf->SimpleTop__DOT__mem_33 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 7719690527075380544ull);
    vlSelf->SimpleTop__DOT__mem_34 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 4344926688401541784ull);
    vlSelf->SimpleTop__DOT__mem_35 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 13106641204154085223ull);
    vlSelf->SimpleTop__DOT__mem_36 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 14160690733191624272ull);
    vlSelf->SimpleTop__DOT__mem_37 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 1754994431528613436ull);
    vlSelf->SimpleTop__DOT__mem_38 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 14469096455171227954ull);
    vlSelf->SimpleTop__DOT__mem_39 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 15765054373666880055ull);
    vlSelf->SimpleTop__DOT__mem_40 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 17876736128570932181ull);
    vlSelf->SimpleTop__DOT__mem_41 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 5413753390378103202ull);
    vlSelf->SimpleTop__DOT__mem_42 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 14274623931944898288ull);
    vlSelf->SimpleTop__DOT__mem_43 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 2717328692835072061ull);
    vlSelf->SimpleTop__DOT__mem_44 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 15049713562127449719ull);
    vlSelf->SimpleTop__DOT__mem_45 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 2222865431955569404ull);
    vlSelf->SimpleTop__DOT__mem_46 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 7176477014526274652ull);
    vlSelf->SimpleTop__DOT__mem_47 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 4622667211597586039ull);
    vlSelf->SimpleTop__DOT__mem_48 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 6116930945150047879ull);
    vlSelf->SimpleTop__DOT__mem_49 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 1052792723727113879ull);
    vlSelf->SimpleTop__DOT__mem_50 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 10598473740700916571ull);
    vlSelf->SimpleTop__DOT__mem_51 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 11029681551825922584ull);
    vlSelf->SimpleTop__DOT__mem_52 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 10463937843003089803ull);
    vlSelf->SimpleTop__DOT__mem_53 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 15414653429073880270ull);
    vlSelf->SimpleTop__DOT__mem_54 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 15339474758906105296ull);
    vlSelf->SimpleTop__DOT__mem_55 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 15607626770933119724ull);
    vlSelf->SimpleTop__DOT__mem_56 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 4618514869318286498ull);
    vlSelf->SimpleTop__DOT__mem_57 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 15008250445712348752ull);
    vlSelf->SimpleTop__DOT__mem_58 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 6494964629591463828ull);
    vlSelf->SimpleTop__DOT__mem_59 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 6651430154155185470ull);
    vlSelf->SimpleTop__DOT__mem_60 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 17049518472123722745ull);
    vlSelf->SimpleTop__DOT__mem_61 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 5862303043544443596ull);
    vlSelf->SimpleTop__DOT__mem_62 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 6794708621340380729ull);
    vlSelf->SimpleTop__DOT__mem_63 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 17418089300585579381ull);
    vlSelf->SimpleTop__DOT__mem_64 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 16680250375905838904ull);
    vlSelf->SimpleTop__DOT__mem_65 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 8295853020078993687ull);
    vlSelf->SimpleTop__DOT__mem_66 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 4272554649566496956ull);
    vlSelf->SimpleTop__DOT__mem_67 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 1361083248626046411ull);
    vlSelf->SimpleTop__DOT__mem_68 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 15705646642807304340ull);
    vlSelf->SimpleTop__DOT__mem_69 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 2804474685091532277ull);
    vlSelf->SimpleTop__DOT__mem_70 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 5886577401349393410ull);
    vlSelf->SimpleTop__DOT__mem_71 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 13291030060161459591ull);
    vlSelf->SimpleTop__DOT__mem_72 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 6641500759303732267ull);
    vlSelf->SimpleTop__DOT__mem_73 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 7491397907593696842ull);
    vlSelf->SimpleTop__DOT__mem_74 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 8602462508499280897ull);
    vlSelf->SimpleTop__DOT__mem_75 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 6545005648925484714ull);
    vlSelf->SimpleTop__DOT__mem_76 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 9130594982210742708ull);
    vlSelf->SimpleTop__DOT__mem_77 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 17381660797892599850ull);
    vlSelf->SimpleTop__DOT__mem_78 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 4433552185494123002ull);
    vlSelf->SimpleTop__DOT__mem_79 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 1841208710370056010ull);
    vlSelf->SimpleTop__DOT__mem_80 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 11842693459618711637ull);
    vlSelf->SimpleTop__DOT__mem_81 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 10318117110177652092ull);
    vlSelf->SimpleTop__DOT__mem_82 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 3276575112496868966ull);
    vlSelf->SimpleTop__DOT__mem_83 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 3466086647453332450ull);
    vlSelf->SimpleTop__DOT__mem_84 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 17275217017475345524ull);
    vlSelf->SimpleTop__DOT__mem_85 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 661027931446098521ull);
    vlSelf->SimpleTop__DOT__mem_86 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 12120039863685193660ull);
    vlSelf->SimpleTop__DOT__mem_87 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 16914385927667579028ull);
    vlSelf->SimpleTop__DOT__mem_88 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 880802539133045934ull);
    vlSelf->SimpleTop__DOT__mem_89 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 15849745829842924822ull);
    vlSelf->SimpleTop__DOT__mem_90 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 7387101483237620423ull);
    vlSelf->SimpleTop__DOT__mem_91 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 8008415634005829723ull);
    vlSelf->SimpleTop__DOT__mem_92 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 5164026656173863620ull);
    vlSelf->SimpleTop__DOT__mem_93 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 14035573374816867034ull);
    vlSelf->SimpleTop__DOT__mem_94 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 9043523784894937353ull);
    vlSelf->SimpleTop__DOT__mem_95 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 1288331420929861975ull);
    vlSelf->SimpleTop__DOT__mem_96 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 10144096751616972619ull);
    vlSelf->SimpleTop__DOT__mem_97 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 17138661400153054185ull);
    vlSelf->SimpleTop__DOT__mem_98 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 4643554011812812342ull);
    vlSelf->SimpleTop__DOT__mem_99 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 8387986640264753782ull);
    vlSelf->SimpleTop__DOT__mem_100 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 1057974998558959431ull);
    vlSelf->SimpleTop__DOT__mem_101 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 4912738863731530054ull);
    vlSelf->SimpleTop__DOT__mem_102 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 14618069153909590048ull);
    vlSelf->SimpleTop__DOT__mem_103 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 448926558733190128ull);
    vlSelf->SimpleTop__DOT__mem_104 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 8360174768749486518ull);
    vlSelf->SimpleTop__DOT__mem_105 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 920528275593355428ull);
    vlSelf->SimpleTop__DOT__mem_106 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 13945796361767017334ull);
    vlSelf->SimpleTop__DOT__mem_107 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 7517390722743427093ull);
    vlSelf->SimpleTop__DOT__mem_108 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 13803550118335035801ull);
    vlSelf->SimpleTop__DOT__mem_109 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 14814081900973459899ull);
    vlSelf->SimpleTop__DOT__mem_110 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 8380454377199012140ull);
    vlSelf->SimpleTop__DOT__mem_111 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 15521406496437287828ull);
    vlSelf->SimpleTop__DOT__mem_112 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 12877264649669790472ull);
    vlSelf->SimpleTop__DOT__mem_113 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 6899650256878029929ull);
    vlSelf->SimpleTop__DOT__mem_114 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 2653284512050946183ull);
    vlSelf->SimpleTop__DOT__mem_115 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 7969302963726596984ull);
    vlSelf->SimpleTop__DOT__mem_116 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 9436386172506386337ull);
    vlSelf->SimpleTop__DOT__mem_117 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 8387471167540726256ull);
    vlSelf->SimpleTop__DOT__mem_118 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 9381039993330664416ull);
    vlSelf->SimpleTop__DOT__mem_119 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 17901718342041651939ull);
    vlSelf->SimpleTop__DOT__mem_120 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 6070010007830609100ull);
    vlSelf->SimpleTop__DOT__mem_121 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 14031934987476473015ull);
    vlSelf->SimpleTop__DOT__mem_122 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 15154563723826026088ull);
    vlSelf->SimpleTop__DOT__mem_123 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 6517331112773635085ull);
    vlSelf->SimpleTop__DOT__mem_124 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 1352738070008657563ull);
    vlSelf->SimpleTop__DOT__mem_125 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 6774563043195337660ull);
    vlSelf->SimpleTop__DOT__mem_126 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 15343303197726378132ull);
    vlSelf->SimpleTop__DOT__mem_127 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 985129485173372408ull);
    vlSelf->SimpleTop__DOT__state = VL_SCOPED_RAND_RESET_I(2, __VscopeHash, 3935921017637599860ull);
    vlSelf->SimpleTop__DOT__timer = VL_SCOPED_RAND_RESET_I(4, __VscopeHash, 4087475450652508325ull);
    vlSelf->SimpleTop__DOT__rdataBuffer = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 11338194042436837766ull);
    vlSelf->SimpleTop__DOT__addrLatch = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 6367066397002383148ull);
    vlSelf->SimpleTop__DOT__busyTable_0 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 11853441416001178856ull);
    vlSelf->SimpleTop__DOT__busyTable_1 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 772767763809274001ull);
    vlSelf->SimpleTop__DOT__busyTable_2 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 12411870188797799328ull);
    vlSelf->SimpleTop__DOT__busyTable_3 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 7927109424505576526ull);
    vlSelf->SimpleTop__DOT__busyTable_4 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 16576406880961234500ull);
    vlSelf->SimpleTop__DOT__nextIssueId = VL_SCOPED_RAND_RESET_I(5, __VscopeHash, 17771195111364796078ull);
    vlSelf->SimpleTop__DOT__activeReg = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 10949871311228714848ull);
    vlSelf->SimpleTop__DOT__activeReg_1 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 5120620063286244826ull);
    vlSelf->SimpleTop__DOT__activeReg_2 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 9355382443713028415ull);
    vlSelf->SimpleTop__DOT__activeReg_3 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 13597437760906198917ull);
    vlSelf->SimpleTop__DOT__activeReg_4 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 5531557942991256603ull);
    vlSelf->SimpleTop__DOT__activeReg_5 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 3841306377232274881ull);
    vlSelf->SimpleTop__DOT__res = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 16496183382770384298ull);
    vlSelf->SimpleTop__DOT__pcReg = VL_SCOPED_RAND_RESET_I(3, __VscopeHash, 10919699830290249434ull);
    vlSelf->SimpleTop__DOT___GEN = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 11376989429897011889ull);
    vlSelf->SimpleTop__DOT__intents_0_release = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 12160935226301652588ull);
    vlSelf->SimpleTop__DOT___GEN_1 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 16302217655418576557ull);
    vlSelf->SimpleTop__DOT__doneWire_5 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 11958176218435133032ull);
    vlSelf->SimpleTop__DOT__pcReg_1 = VL_SCOPED_RAND_RESET_I(2, __VscopeHash, 11227526637556543659ull);
    vlSelf->SimpleTop__DOT___GEN_2 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 6925299607989513005ull);
    vlSelf->SimpleTop__DOT__stall_2 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 4526187347546679351ull);
    vlSelf->SimpleTop__DOT___GEN_3 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 9032405544021702557ull);
    vlSelf->SimpleTop__DOT__intents_0_acquire = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 12476930984954970555ull);
    vlSelf->SimpleTop__DOT__intents_0_reg = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 14737861213041311648ull);
    vlSelf->SimpleTop__DOT__startWire_5 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 2177289543794997653ull);
    vlSelf->SimpleTop__DOT__doneWire = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 17561030463944021505ull);
    vlSelf->SimpleTop__DOT__activeReg_6 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 3443408724414465001ull);
    vlSelf->SimpleTop__DOT__op1_1 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 7685840532538154646ull);
    vlSelf->SimpleTop__DOT__res_1 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 4781591698185296723ull);
    vlSelf->SimpleTop__DOT__pcReg_2 = VL_SCOPED_RAND_RESET_I(3, __VscopeHash, 8823653764362042787ull);
    vlSelf->SimpleTop__DOT___GEN_4 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 18286638223697528396ull);
    vlSelf->SimpleTop__DOT__intents_1_release = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 6024293929198032456ull);
    vlSelf->SimpleTop__DOT___GEN_6 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 11594632748346889213ull);
    vlSelf->SimpleTop__DOT__doneWire_6 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 12339117589041328867ull);
    vlSelf->SimpleTop__DOT__pcReg_3 = VL_SCOPED_RAND_RESET_I(2, __VscopeHash, 7322114389594532890ull);
    vlSelf->SimpleTop__DOT___GEN_7 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 16134075610741952274ull);
    vlSelf->SimpleTop__DOT___stall_T_3 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 16002034781536948965ull);
    vlSelf->SimpleTop__DOT___GEN_8 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 2871819520110251759ull);
    vlSelf->SimpleTop__DOT__intents_1_acquire = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 16460193137455203341ull);
    vlSelf->SimpleTop__DOT__intents_1_reg = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 3934364090841652770ull);
    vlSelf->SimpleTop__DOT__startWire_6 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 15191048816432795221ull);
    vlSelf->SimpleTop__DOT__doneWire_1 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 7855858399527319761ull);
    vlSelf->SimpleTop__DOT__activeReg_7 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 14496453340003509276ull);
    vlSelf->SimpleTop__DOT__addrVal = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 12811115622466716983ull);
    vlSelf->SimpleTop__DOT__dataVal = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 7945678015294766309ull);
    vlSelf->SimpleTop__DOT__pcReg_4 = VL_SCOPED_RAND_RESET_I(4, __VscopeHash, 3856553867561254152ull);
    vlSelf->SimpleTop__DOT___GEN_10 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 2480924271450487256ull);
    vlSelf->SimpleTop__DOT__intents_2_release = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 11403221883853312998ull);
    vlSelf->SimpleTop__DOT___GEN_13 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 8993119919284775459ull);
    vlSelf->SimpleTop__DOT__doneWire_7 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 8956859066598503030ull);
    vlSelf->SimpleTop__DOT__pcReg_5 = VL_SCOPED_RAND_RESET_I(2, __VscopeHash, 1380279355513185617ull);
    vlSelf->SimpleTop__DOT___GEN_14 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 10624753201294934286ull);
    vlSelf->SimpleTop__DOT__stall_8 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 17080898813735660933ull);
    vlSelf->SimpleTop__DOT__intents_2_acquire = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 14124144859881324479ull);
    vlSelf->SimpleTop__DOT__startWire_7 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 12621613867194212754ull);
    vlSelf->SimpleTop__DOT__doneWire_2 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 10604089262857821221ull);
    vlSelf->SimpleTop__DOT__activeReg_8 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 14817211173133854650ull);
    vlSelf->SimpleTop__DOT__addrVal_1 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 14919266978611004637ull);
    vlSelf->SimpleTop__DOT__memData = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 7970549607594465496ull);
    vlSelf->SimpleTop__DOT__pcReg_6 = VL_SCOPED_RAND_RESET_I(4, __VscopeHash, 13620545719462593546ull);
    vlSelf->SimpleTop__DOT___GEN_16 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 12277481823594786142ull);
    vlSelf->SimpleTop__DOT__io_1_req = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 6522362867990182837ull);
    vlSelf->SimpleTop__DOT__io_1_isWr = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 11191522342284958973ull);
    vlSelf->SimpleTop__DOT__io_1_addr = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 7991418255876967757ull);
    vlSelf->SimpleTop__DOT___GEN_17 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 3285263426576424994ull);
    vlSelf->SimpleTop__DOT___GEN_18 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 10119088686003099122ull);
    vlSelf->SimpleTop__DOT__intents_3_release = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 11318038348509148977ull);
    vlSelf->SimpleTop__DOT___GEN_20 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 14787359276690169120ull);
    vlSelf->SimpleTop__DOT__doneWire_8 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 7900018134692261470ull);
    vlSelf->SimpleTop__DOT__pcReg_7 = VL_SCOPED_RAND_RESET_I(2, __VscopeHash, 17625118297270159294ull);
    vlSelf->SimpleTop__DOT___GEN_21 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 2605888911762034822ull);
    vlSelf->SimpleTop__DOT___stall_T_7 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 4509441920386899318ull);
    vlSelf->SimpleTop__DOT___GEN_22 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 12966882917757625645ull);
    vlSelf->SimpleTop__DOT__intents_3_acquire = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 15085555829904434677ull);
    vlSelf->SimpleTop__DOT__intents_3_reg = VL_SCOPED_RAND_RESET_I(2, __VscopeHash, 15839676954046108193ull);
    vlSelf->SimpleTop__DOT__startWire_8 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 15479673357818232485ull);
    vlSelf->SimpleTop__DOT__doneWire_3 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 14102332944386278644ull);
    vlSelf->SimpleTop__DOT__activeReg_9 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 1051863624339229685ull);
    vlSelf->SimpleTop__DOT__op1_2 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 1816698518093101927ull);
    vlSelf->SimpleTop__DOT__op2 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 11487894881407430808ull);
    vlSelf->SimpleTop__DOT__res_2 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 14345013955055549229ull);
    vlSelf->SimpleTop__DOT__pcReg_8 = VL_SCOPED_RAND_RESET_I(4, __VscopeHash, 7528940872419318046ull);
    vlSelf->SimpleTop__DOT___GEN_23 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 17424645278312410575ull);
    vlSelf->SimpleTop__DOT___GEN_24 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 6283650658642018597ull);
    vlSelf->SimpleTop__DOT__intents_4_release = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 4755019116284109581ull);
    vlSelf->SimpleTop__DOT___GEN_26 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 14242790508031004097ull);
    vlSelf->SimpleTop__DOT__doneWire_9 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 12793961387764573721ull);
    vlSelf->SimpleTop__DOT__pcReg_9 = VL_SCOPED_RAND_RESET_I(2, __VscopeHash, 4038280290138664743ull);
    vlSelf->SimpleTop__DOT___GEN_27 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 6605914125546621170ull);
    vlSelf->SimpleTop__DOT___stall_T_9 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 11066618282980583911ull);
    vlSelf->SimpleTop__DOT___GEN_28 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 10203980638966301135ull);
    vlSelf->SimpleTop__DOT__intents_4_acquire = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 17185992497084723929ull);
    vlSelf->SimpleTop__DOT__intents_4_reg = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 17157265403975925010ull);
    vlSelf->SimpleTop__DOT__startWire_9 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 4920110087055117795ull);
    vlSelf->SimpleTop__DOT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_32 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 15703682325326010007ull);
    vlSelf->SimpleTop__DOT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_33 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 12361730464066545685ull);
    vlSelf->SimpleTop__DOT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_34 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 16478091065461654336ull);
    vlSelf->SimpleTop__DOT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_35 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 12441045877599628862ull);
    vlSelf->SimpleTop__DOT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_36 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 16428469905540711636ull);
    vlSelf->SimpleTop__DOT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_37 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 15740941495577405307ull);
    vlSelf->SimpleTop__DOT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_38 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 5228747141856777720ull);
    vlSelf->SimpleTop__DOT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_39 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 11753099452258827642ull);
    vlSelf->SimpleTop__DOT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_40 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 17331354212196517864ull);
    vlSelf->SimpleTop__DOT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_41 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 398028079974930394ull);
    vlSelf->SimpleTop__DOT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_42 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 11057910118303190930ull);
    vlSelf->SimpleTop__DOT__unnamedblk1__DOT__unnamedblk2__DOT___stall_hazard_T_146 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 5958910752433073592ull);
    vlSelf->SimpleTop__DOT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_43 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 12664518556247531432ull);
    vlSelf->SimpleTop__DOT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_44 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 7556765051828334217ull);
    vlSelf->SimpleTop__DOT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_45 = VL_SCOPED_RAND_RESET_I(3, __VscopeHash, 6177076430446268251ull);
    vlSelf->SimpleTop__DOT__unnamedblk1__DOT__unnamedblk2__DOT__stall_6 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 3342813865919011834ull);
    vlSelf->SimpleTop__DOT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_46 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 8005042489836258137ull);
    vlSelf->SimpleTop__DOT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_47 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 8759119855914465692ull);
    vlSelf->SimpleTop__DOT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_48 = VL_SCOPED_RAND_RESET_I(5, __VscopeHash, 6858852725896895748ull);
    vlSelf->SimpleTop__DOT__unnamedblk1__DOT__unnamedblk2__DOT__stall_7 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 4849269911803666633ull);
    vlSelf->SimpleTop__DOT__unnamedblk1__DOT__unnamedblk2__DOT__io_1_wdata = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 1802974795706689631ull);
    vlSelf->SimpleTop__DOT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_49 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 2665465458006996371ull);
    vlSelf->SimpleTop__DOT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_50 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 5804405042510659281ull);
    vlSelf->SimpleTop__DOT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_51 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 17984673691744760180ull);
    vlSelf->SimpleTop__DOT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_52 = VL_SCOPED_RAND_RESET_I(6, __VscopeHash, 4872284787642417868ull);
    vlSelf->SimpleTop__DOT__unnamedblk1__DOT__unnamedblk2__DOT__stall_9 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 925225563415508728ull);
    vlSelf->SimpleTop__DOT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_53 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 2961372228234066340ull);
    vlSelf->SimpleTop__DOT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_54 = VL_SCOPED_RAND_RESET_I(4, __VscopeHash, 7004756556579665666ull);
    vlSelf->SimpleTop__DOT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_55 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 7219870575166755895ull);
    vlSelf->SimpleTop__DOT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_56 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 16772211775955361993ull);
    vlSelf->SimpleTop__DOT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_57 = VL_SCOPED_RAND_RESET_I(7, __VscopeHash, 859350045250238762ull);
    vlSelf->SimpleTop__DOT__unnamedblk1__DOT__unnamedblk2__DOT__stall_12 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 4566117676254177126ull);
    vlSelf->SimpleTop__DOT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_58 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 13458789473345543019ull);
    vlSelf->SimpleTop__DOT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_59 = VL_SCOPED_RAND_RESET_I(9, __VscopeHash, 3849511905003832063ull);
    vlSelf->SimpleTop__DOT__unnamedblk1__DOT__unnamedblk2__DOT__stall_13 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 16740825264318229113ull);
    vlSelf->SimpleTop__DOT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_60 = VL_SCOPED_RAND_RESET_I(8, __VscopeHash, 12207216018438646236ull);
    vlSelf->SimpleTop__DOT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_61 = VL_SCOPED_RAND_RESET_I(8, __VscopeHash, 5080434951977918232ull);
    vlSelf->SimpleTop__DOT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_62 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 805899146985115835ull);
    vlSelf->SimpleTop__DOT__unnamedblk1__DOT__unnamedblk2__DOT__unnamedblk3__DOT___GEN_63 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 9198403371232490993ull);
    vlSelf->SimpleTop__DOT__unnamedblk1__DOT__unnamedblk2__DOT__unnamedblk3__DOT___GEN_64 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 16325209442259566307ull);
    vlSelf->SimpleTop__DOT__unnamedblk1__DOT__unnamedblk2__DOT__unnamedblk4__DOT___GEN_65 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 10061535839174652126ull);
    vlSelf->SimpleTop__DOT__unnamedblk1__DOT__unnamedblk2__DOT__unnamedblk4__DOT___GEN_66 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 12449807401825698764ull);
    vlSelf->SimpleTop__DOT__unnamedblk1__DOT__unnamedblk2__DOT__unnamedblk5__DOT___GEN_67 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 9411187803302312363ull);
    vlSelf->SimpleTop__DOT__unnamedblk1__DOT__unnamedblk2__DOT__unnamedblk5__DOT___GEN_68 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 15229077794483772202ull);
    VL_SCOPED_RAND_RESET_W(4096, vlSelf->SimpleTop__DOT__unnamedblk1__DOT__unnamedblk6__DOT___GEN_69, __VscopeHash, 10836608963948724728ull);
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
