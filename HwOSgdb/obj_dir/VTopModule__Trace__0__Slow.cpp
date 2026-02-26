// Verilated -*- C++ -*-
// DESCRIPTION: Verilator output: Tracing implementation internals
#include "verilated_vcd_c.h"
#include "VTopModule__Syms.h"


VL_ATTR_COLD void VTopModule___024root__trace_init_sub__TOP__0(VTopModule___024root* vlSelf, VerilatedVcd* tracep) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VTopModule___024root__trace_init_sub__TOP__0\n"); );
    VTopModule__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    const int c = vlSymsp->__Vm_baseCode;
    tracep->pushPrefix("$rootio", VerilatedTracePrefixType::SCOPE_MODULE);
    tracep->declBit(c+5,0,"clock",-1, VerilatedTraceSigDirection::INPUT, VerilatedTraceSigKind::WIRE, VerilatedTraceSigType::LOGIC, false,-1);
    tracep->declBit(c+6,0,"reset",-1, VerilatedTraceSigDirection::INPUT, VerilatedTraceSigKind::WIRE, VerilatedTraceSigType::LOGIC, false,-1);
    tracep->declBit(c+7,0,"io_start",-1, VerilatedTraceSigDirection::INPUT, VerilatedTraceSigKind::WIRE, VerilatedTraceSigType::LOGIC, false,-1);
    tracep->declBit(c+8,0,"io_done",-1, VerilatedTraceSigDirection::OUTPUT, VerilatedTraceSigKind::WIRE, VerilatedTraceSigType::LOGIC, false,-1);
    tracep->popPrefix();
    tracep->pushPrefix("TopModule", VerilatedTracePrefixType::SCOPE_MODULE);
    tracep->declBit(c+5,0,"clock",-1, VerilatedTraceSigDirection::INPUT, VerilatedTraceSigKind::WIRE, VerilatedTraceSigType::LOGIC, false,-1);
    tracep->declBit(c+6,0,"reset",-1, VerilatedTraceSigDirection::INPUT, VerilatedTraceSigKind::WIRE, VerilatedTraceSigType::LOGIC, false,-1);
    tracep->declBit(c+7,0,"io_start",-1, VerilatedTraceSigDirection::INPUT, VerilatedTraceSigKind::WIRE, VerilatedTraceSigType::LOGIC, false,-1);
    tracep->declBit(c+8,0,"io_done",-1, VerilatedTraceSigDirection::OUTPUT, VerilatedTraceSigKind::WIRE, VerilatedTraceSigType::LOGIC, false,-1);
    tracep->declBit(c+1,0,"activeReg",-1, VerilatedTraceSigDirection::NONE, VerilatedTraceSigKind::VAR, VerilatedTraceSigType::LOGIC, false,-1);
    tracep->declBit(c+1,0,"activeReg_0",-1, VerilatedTraceSigDirection::NONE, VerilatedTraceSigKind::WIRE, VerilatedTraceSigType::LOGIC, false,-1);
    tracep->declBit(c+2,0,"doneReg",-1, VerilatedTraceSigDirection::NONE, VerilatedTraceSigKind::VAR, VerilatedTraceSigType::LOGIC, false,-1);
    tracep->declBus(c+3,0,"pcReg",-1, VerilatedTraceSigDirection::NONE, VerilatedTraceSigKind::VAR, VerilatedTraceSigType::LOGIC, false,-1, 2,0);
    tracep->declBus(c+3,0,"pcReg_0",-1, VerilatedTraceSigDirection::NONE, VerilatedTraceSigKind::WIRE, VerilatedTraceSigType::LOGIC, false,-1, 2,0);
    tracep->pushPrefix("monitor", VerilatedTracePrefixType::SCOPE_MODULE);
    tracep->declBit(c+5,0,"clock",-1, VerilatedTraceSigDirection::INPUT, VerilatedTraceSigKind::WIRE, VerilatedTraceSigType::LOGIC, false,-1);
    tracep->declBit(c+6,0,"reset",-1, VerilatedTraceSigDirection::INPUT, VerilatedTraceSigKind::WIRE, VerilatedTraceSigType::LOGIC, false,-1);
    tracep->declBus(c+4,0,"pcs",-1, VerilatedTraceSigDirection::INPUT, VerilatedTraceSigKind::WIRE, VerilatedTraceSigType::LOGIC, false,-1, 31,0);
    tracep->declBus(c+1,0,"actives",-1, VerilatedTraceSigDirection::INPUT, VerilatedTraceSigKind::WIRE, VerilatedTraceSigType::LOGIC, false,-1, 0,0);
    tracep->declBus(c+2,0,"dones",-1, VerilatedTraceSigDirection::INPUT, VerilatedTraceSigKind::WIRE, VerilatedTraceSigType::LOGIC, false,-1, 0,0);
    tracep->popPrefix();
    tracep->popPrefix();
}

VL_ATTR_COLD void VTopModule___024root__trace_init_top(VTopModule___024root* vlSelf, VerilatedVcd* tracep) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VTopModule___024root__trace_init_top\n"); );
    VTopModule__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    VTopModule___024root__trace_init_sub__TOP__0(vlSelf, tracep);
}

VL_ATTR_COLD void VTopModule___024root__trace_const_0(void* voidSelf, VerilatedVcd::Buffer* bufp);
VL_ATTR_COLD void VTopModule___024root__trace_full_0(void* voidSelf, VerilatedVcd::Buffer* bufp);
void VTopModule___024root__trace_chg_0(void* voidSelf, VerilatedVcd::Buffer* bufp);
void VTopModule___024root__trace_cleanup(void* voidSelf, VerilatedVcd* /*unused*/);

VL_ATTR_COLD void VTopModule___024root__trace_register(VTopModule___024root* vlSelf, VerilatedVcd* tracep) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VTopModule___024root__trace_register\n"); );
    VTopModule__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    tracep->addConstCb(&VTopModule___024root__trace_const_0, 0, vlSelf);
    tracep->addFullCb(&VTopModule___024root__trace_full_0, 0, vlSelf);
    tracep->addChgCb(&VTopModule___024root__trace_chg_0, 0, vlSelf);
    tracep->addCleanupCb(&VTopModule___024root__trace_cleanup, vlSelf);
}

VL_ATTR_COLD void VTopModule___024root__trace_const_0(void* voidSelf, VerilatedVcd::Buffer* bufp) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VTopModule___024root__trace_const_0\n"); );
    // Body
    VTopModule___024root* const __restrict vlSelf VL_ATTR_UNUSED = static_cast<VTopModule___024root*>(voidSelf);
    VTopModule__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
}

VL_ATTR_COLD void VTopModule___024root__trace_full_0_sub_0(VTopModule___024root* vlSelf, VerilatedVcd::Buffer* bufp);

VL_ATTR_COLD void VTopModule___024root__trace_full_0(void* voidSelf, VerilatedVcd::Buffer* bufp) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VTopModule___024root__trace_full_0\n"); );
    // Body
    VTopModule___024root* const __restrict vlSelf VL_ATTR_UNUSED = static_cast<VTopModule___024root*>(voidSelf);
    VTopModule__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    VTopModule___024root__trace_full_0_sub_0((&vlSymsp->TOP), bufp);
}

VL_ATTR_COLD void VTopModule___024root__trace_full_0_sub_0(VTopModule___024root* vlSelf, VerilatedVcd::Buffer* bufp) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VTopModule___024root__trace_full_0_sub_0\n"); );
    VTopModule__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    uint32_t* const oldp VL_ATTR_UNUSED = bufp->oldp(vlSymsp->__Vm_baseCode);
    bufp->fullBit(oldp+1,(vlSelfRef.TopModule__DOT__activeReg));
    bufp->fullBit(oldp+2,(vlSelfRef.TopModule__DOT__doneReg));
    bufp->fullCData(oldp+3,(vlSelfRef.TopModule__DOT__pcReg),3);
    bufp->fullIData(oldp+4,(vlSelfRef.TopModule__DOT__pcReg),32);
    bufp->fullBit(oldp+5,(vlSelfRef.clock));
    bufp->fullBit(oldp+6,(vlSelfRef.reset));
    bufp->fullBit(oldp+7,(vlSelfRef.io_start));
    bufp->fullBit(oldp+8,(vlSelfRef.io_done));
}
