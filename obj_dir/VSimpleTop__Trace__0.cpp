// Verilated -*- C++ -*-
// DESCRIPTION: Verilator output: Tracing implementation internals
#include "verilated_vcd_c.h"
#include "VSimpleTop__Syms.h"


void VSimpleTop___024root__trace_chg_0_sub_0(VSimpleTop___024root* vlSelf, VerilatedVcd::Buffer* bufp);

void VSimpleTop___024root__trace_chg_0(void* voidSelf, VerilatedVcd::Buffer* bufp) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VSimpleTop___024root__trace_chg_0\n"); );
    // Body
    VSimpleTop___024root* const __restrict vlSelf VL_ATTR_UNUSED = static_cast<VSimpleTop___024root*>(voidSelf);
    VSimpleTop__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    if (VL_UNLIKELY(!vlSymsp->__Vm_activity)) return;
    VSimpleTop___024root__trace_chg_0_sub_0((&vlSymsp->TOP), bufp);
}

void VSimpleTop___024root__trace_chg_0_sub_0(VSimpleTop___024root* vlSelf, VerilatedVcd::Buffer* bufp) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VSimpleTop___024root__trace_chg_0_sub_0\n"); );
    VSimpleTop__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Locals
    VlWide<10>/*319:0*/ __Vtemp_8;
    // Body
    uint32_t* const oldp VL_ATTR_UNUSED = bufp->oldp(vlSymsp->__Vm_baseCode + 1);
    if (VL_UNLIKELY((vlSelfRef.__Vm_traceActivity[1U]))) {
        bufp->chgIData(oldp+0,(vlSelfRef.SimpleTop__DOT__phyRegs_1),32);
        bufp->chgIData(oldp+1,(vlSelfRef.SimpleTop__DOT__phyRegs_2),32);
        bufp->chgIData(oldp+2,(vlSelfRef.SimpleTop__DOT__phyRegs_3),32);
        bufp->chgIData(oldp+3,(vlSelfRef.SimpleTop__DOT__phyRegs_4),32);
        bufp->chgIData(oldp+4,(vlSelfRef.SimpleTop__DOT__mem_0),32);
        bufp->chgIData(oldp+5,(vlSelfRef.SimpleTop__DOT__mem_1),32);
        bufp->chgIData(oldp+6,(vlSelfRef.SimpleTop__DOT__mem_2),32);
        bufp->chgIData(oldp+7,(vlSelfRef.SimpleTop__DOT__mem_3),32);
        bufp->chgIData(oldp+8,(vlSelfRef.SimpleTop__DOT__mem_4),32);
        bufp->chgIData(oldp+9,(vlSelfRef.SimpleTop__DOT__mem_5),32);
        bufp->chgIData(oldp+10,(vlSelfRef.SimpleTop__DOT__mem_6),32);
        bufp->chgIData(oldp+11,(vlSelfRef.SimpleTop__DOT__mem_7),32);
        bufp->chgIData(oldp+12,(vlSelfRef.SimpleTop__DOT__mem_8),32);
        bufp->chgIData(oldp+13,(vlSelfRef.SimpleTop__DOT__mem_9),32);
        bufp->chgIData(oldp+14,(vlSelfRef.SimpleTop__DOT__mem_10),32);
        bufp->chgIData(oldp+15,(vlSelfRef.SimpleTop__DOT__mem_11),32);
        bufp->chgIData(oldp+16,(vlSelfRef.SimpleTop__DOT__mem_12),32);
        bufp->chgIData(oldp+17,(vlSelfRef.SimpleTop__DOT__mem_13),32);
        bufp->chgIData(oldp+18,(vlSelfRef.SimpleTop__DOT__mem_14),32);
        bufp->chgIData(oldp+19,(vlSelfRef.SimpleTop__DOT__mem_15),32);
        bufp->chgIData(oldp+20,(vlSelfRef.SimpleTop__DOT__mem_16),32);
        bufp->chgIData(oldp+21,(vlSelfRef.SimpleTop__DOT__mem_17),32);
        bufp->chgIData(oldp+22,(vlSelfRef.SimpleTop__DOT__mem_18),32);
        bufp->chgIData(oldp+23,(vlSelfRef.SimpleTop__DOT__mem_19),32);
        bufp->chgIData(oldp+24,(vlSelfRef.SimpleTop__DOT__mem_20),32);
        bufp->chgIData(oldp+25,(vlSelfRef.SimpleTop__DOT__mem_21),32);
        bufp->chgIData(oldp+26,(vlSelfRef.SimpleTop__DOT__mem_22),32);
        bufp->chgIData(oldp+27,(vlSelfRef.SimpleTop__DOT__mem_23),32);
        bufp->chgIData(oldp+28,(vlSelfRef.SimpleTop__DOT__mem_24),32);
        bufp->chgIData(oldp+29,(vlSelfRef.SimpleTop__DOT__mem_25),32);
        bufp->chgIData(oldp+30,(vlSelfRef.SimpleTop__DOT__mem_26),32);
        bufp->chgIData(oldp+31,(vlSelfRef.SimpleTop__DOT__mem_27),32);
        bufp->chgIData(oldp+32,(vlSelfRef.SimpleTop__DOT__mem_28),32);
        bufp->chgIData(oldp+33,(vlSelfRef.SimpleTop__DOT__mem_29),32);
        bufp->chgIData(oldp+34,(vlSelfRef.SimpleTop__DOT__mem_30),32);
        bufp->chgIData(oldp+35,(vlSelfRef.SimpleTop__DOT__mem_31),32);
        bufp->chgIData(oldp+36,(vlSelfRef.SimpleTop__DOT__mem_32),32);
        bufp->chgIData(oldp+37,(vlSelfRef.SimpleTop__DOT__mem_33),32);
        bufp->chgIData(oldp+38,(vlSelfRef.SimpleTop__DOT__mem_34),32);
        bufp->chgIData(oldp+39,(vlSelfRef.SimpleTop__DOT__mem_35),32);
        bufp->chgIData(oldp+40,(vlSelfRef.SimpleTop__DOT__mem_36),32);
        bufp->chgIData(oldp+41,(vlSelfRef.SimpleTop__DOT__mem_37),32);
        bufp->chgIData(oldp+42,(vlSelfRef.SimpleTop__DOT__mem_38),32);
        bufp->chgIData(oldp+43,(vlSelfRef.SimpleTop__DOT__mem_39),32);
        bufp->chgIData(oldp+44,(vlSelfRef.SimpleTop__DOT__mem_40),32);
        bufp->chgIData(oldp+45,(vlSelfRef.SimpleTop__DOT__mem_41),32);
        bufp->chgIData(oldp+46,(vlSelfRef.SimpleTop__DOT__mem_42),32);
        bufp->chgIData(oldp+47,(vlSelfRef.SimpleTop__DOT__mem_43),32);
        bufp->chgIData(oldp+48,(vlSelfRef.SimpleTop__DOT__mem_44),32);
        bufp->chgIData(oldp+49,(vlSelfRef.SimpleTop__DOT__mem_45),32);
        bufp->chgIData(oldp+50,(vlSelfRef.SimpleTop__DOT__mem_46),32);
        bufp->chgIData(oldp+51,(vlSelfRef.SimpleTop__DOT__mem_47),32);
        bufp->chgIData(oldp+52,(vlSelfRef.SimpleTop__DOT__mem_48),32);
        bufp->chgIData(oldp+53,(vlSelfRef.SimpleTop__DOT__mem_49),32);
        bufp->chgIData(oldp+54,(vlSelfRef.SimpleTop__DOT__mem_50),32);
        bufp->chgIData(oldp+55,(vlSelfRef.SimpleTop__DOT__mem_51),32);
        bufp->chgIData(oldp+56,(vlSelfRef.SimpleTop__DOT__mem_52),32);
        bufp->chgIData(oldp+57,(vlSelfRef.SimpleTop__DOT__mem_53),32);
        bufp->chgIData(oldp+58,(vlSelfRef.SimpleTop__DOT__mem_54),32);
        bufp->chgIData(oldp+59,(vlSelfRef.SimpleTop__DOT__mem_55),32);
        bufp->chgIData(oldp+60,(vlSelfRef.SimpleTop__DOT__mem_56),32);
        bufp->chgIData(oldp+61,(vlSelfRef.SimpleTop__DOT__mem_57),32);
        bufp->chgIData(oldp+62,(vlSelfRef.SimpleTop__DOT__mem_58),32);
        bufp->chgIData(oldp+63,(vlSelfRef.SimpleTop__DOT__mem_59),32);
        bufp->chgIData(oldp+64,(vlSelfRef.SimpleTop__DOT__mem_60),32);
        bufp->chgIData(oldp+65,(vlSelfRef.SimpleTop__DOT__mem_61),32);
        bufp->chgIData(oldp+66,(vlSelfRef.SimpleTop__DOT__mem_62),32);
        bufp->chgIData(oldp+67,(vlSelfRef.SimpleTop__DOT__mem_63),32);
        bufp->chgIData(oldp+68,(vlSelfRef.SimpleTop__DOT__mem_64),32);
        bufp->chgIData(oldp+69,(vlSelfRef.SimpleTop__DOT__mem_65),32);
        bufp->chgIData(oldp+70,(vlSelfRef.SimpleTop__DOT__mem_66),32);
        bufp->chgIData(oldp+71,(vlSelfRef.SimpleTop__DOT__mem_67),32);
        bufp->chgIData(oldp+72,(vlSelfRef.SimpleTop__DOT__mem_68),32);
        bufp->chgIData(oldp+73,(vlSelfRef.SimpleTop__DOT__mem_69),32);
        bufp->chgIData(oldp+74,(vlSelfRef.SimpleTop__DOT__mem_70),32);
        bufp->chgIData(oldp+75,(vlSelfRef.SimpleTop__DOT__mem_71),32);
        bufp->chgIData(oldp+76,(vlSelfRef.SimpleTop__DOT__mem_72),32);
        bufp->chgIData(oldp+77,(vlSelfRef.SimpleTop__DOT__mem_73),32);
        bufp->chgIData(oldp+78,(vlSelfRef.SimpleTop__DOT__mem_74),32);
        bufp->chgIData(oldp+79,(vlSelfRef.SimpleTop__DOT__mem_75),32);
        bufp->chgIData(oldp+80,(vlSelfRef.SimpleTop__DOT__mem_76),32);
        bufp->chgIData(oldp+81,(vlSelfRef.SimpleTop__DOT__mem_77),32);
        bufp->chgIData(oldp+82,(vlSelfRef.SimpleTop__DOT__mem_78),32);
        bufp->chgIData(oldp+83,(vlSelfRef.SimpleTop__DOT__mem_79),32);
        bufp->chgIData(oldp+84,(vlSelfRef.SimpleTop__DOT__mem_80),32);
        bufp->chgIData(oldp+85,(vlSelfRef.SimpleTop__DOT__mem_81),32);
        bufp->chgIData(oldp+86,(vlSelfRef.SimpleTop__DOT__mem_82),32);
        bufp->chgIData(oldp+87,(vlSelfRef.SimpleTop__DOT__mem_83),32);
        bufp->chgIData(oldp+88,(vlSelfRef.SimpleTop__DOT__mem_84),32);
        bufp->chgIData(oldp+89,(vlSelfRef.SimpleTop__DOT__mem_85),32);
        bufp->chgIData(oldp+90,(vlSelfRef.SimpleTop__DOT__mem_86),32);
        bufp->chgIData(oldp+91,(vlSelfRef.SimpleTop__DOT__mem_87),32);
        bufp->chgIData(oldp+92,(vlSelfRef.SimpleTop__DOT__mem_88),32);
        bufp->chgIData(oldp+93,(vlSelfRef.SimpleTop__DOT__mem_89),32);
        bufp->chgIData(oldp+94,(vlSelfRef.SimpleTop__DOT__mem_90),32);
        bufp->chgIData(oldp+95,(vlSelfRef.SimpleTop__DOT__mem_91),32);
        bufp->chgIData(oldp+96,(vlSelfRef.SimpleTop__DOT__mem_92),32);
        bufp->chgIData(oldp+97,(vlSelfRef.SimpleTop__DOT__mem_93),32);
        bufp->chgIData(oldp+98,(vlSelfRef.SimpleTop__DOT__mem_94),32);
        bufp->chgIData(oldp+99,(vlSelfRef.SimpleTop__DOT__mem_95),32);
        bufp->chgIData(oldp+100,(vlSelfRef.SimpleTop__DOT__mem_96),32);
        bufp->chgIData(oldp+101,(vlSelfRef.SimpleTop__DOT__mem_97),32);
        bufp->chgIData(oldp+102,(vlSelfRef.SimpleTop__DOT__mem_98),32);
        bufp->chgIData(oldp+103,(vlSelfRef.SimpleTop__DOT__mem_99),32);
        bufp->chgIData(oldp+104,(vlSelfRef.SimpleTop__DOT__mem_100),32);
        bufp->chgIData(oldp+105,(vlSelfRef.SimpleTop__DOT__mem_101),32);
        bufp->chgIData(oldp+106,(vlSelfRef.SimpleTop__DOT__mem_102),32);
        bufp->chgIData(oldp+107,(vlSelfRef.SimpleTop__DOT__mem_103),32);
        bufp->chgIData(oldp+108,(vlSelfRef.SimpleTop__DOT__mem_104),32);
        bufp->chgIData(oldp+109,(vlSelfRef.SimpleTop__DOT__mem_105),32);
        bufp->chgIData(oldp+110,(vlSelfRef.SimpleTop__DOT__mem_106),32);
        bufp->chgIData(oldp+111,(vlSelfRef.SimpleTop__DOT__mem_107),32);
        bufp->chgIData(oldp+112,(vlSelfRef.SimpleTop__DOT__mem_108),32);
        bufp->chgIData(oldp+113,(vlSelfRef.SimpleTop__DOT__mem_109),32);
        bufp->chgIData(oldp+114,(vlSelfRef.SimpleTop__DOT__mem_110),32);
        bufp->chgIData(oldp+115,(vlSelfRef.SimpleTop__DOT__mem_111),32);
        bufp->chgIData(oldp+116,(vlSelfRef.SimpleTop__DOT__mem_112),32);
        bufp->chgIData(oldp+117,(vlSelfRef.SimpleTop__DOT__mem_113),32);
        bufp->chgIData(oldp+118,(vlSelfRef.SimpleTop__DOT__mem_114),32);
        bufp->chgIData(oldp+119,(vlSelfRef.SimpleTop__DOT__mem_115),32);
        bufp->chgIData(oldp+120,(vlSelfRef.SimpleTop__DOT__mem_116),32);
        bufp->chgIData(oldp+121,(vlSelfRef.SimpleTop__DOT__mem_117),32);
        bufp->chgIData(oldp+122,(vlSelfRef.SimpleTop__DOT__mem_118),32);
        bufp->chgIData(oldp+123,(vlSelfRef.SimpleTop__DOT__mem_119),32);
        bufp->chgIData(oldp+124,(vlSelfRef.SimpleTop__DOT__mem_120),32);
        bufp->chgIData(oldp+125,(vlSelfRef.SimpleTop__DOT__mem_121),32);
        bufp->chgIData(oldp+126,(vlSelfRef.SimpleTop__DOT__mem_122),32);
        bufp->chgIData(oldp+127,(vlSelfRef.SimpleTop__DOT__mem_123),32);
        bufp->chgIData(oldp+128,(vlSelfRef.SimpleTop__DOT__mem_124),32);
        bufp->chgIData(oldp+129,(vlSelfRef.SimpleTop__DOT__mem_125),32);
        bufp->chgIData(oldp+130,(vlSelfRef.SimpleTop__DOT__mem_126),32);
        bufp->chgIData(oldp+131,(vlSelfRef.SimpleTop__DOT__mem_127),32);
        bufp->chgCData(oldp+132,(vlSelfRef.SimpleTop__DOT__state),2);
        bufp->chgCData(oldp+133,(vlSelfRef.SimpleTop__DOT__timer),4);
        bufp->chgIData(oldp+134,(vlSelfRef.SimpleTop__DOT__rdataBuffer),32);
        bufp->chgIData(oldp+135,(vlSelfRef.SimpleTop__DOT__addrLatch),32);
        bufp->chgBit(oldp+136,(vlSelfRef.SimpleTop__DOT__busyTable_0));
        bufp->chgBit(oldp+137,(vlSelfRef.SimpleTop__DOT__busyTable_1));
        bufp->chgBit(oldp+138,(vlSelfRef.SimpleTop__DOT__busyTable_2));
        bufp->chgBit(oldp+139,(vlSelfRef.SimpleTop__DOT__busyTable_3));
        bufp->chgBit(oldp+140,(vlSelfRef.SimpleTop__DOT__busyTable_4));
        bufp->chgCData(oldp+141,(vlSelfRef.SimpleTop__DOT__nextIssueId),5);
        bufp->chgBit(oldp+142,(vlSelfRef.SimpleTop__DOT__activeReg));
        bufp->chgBit(oldp+143,(vlSelfRef.SimpleTop__DOT__activeReg_1));
        bufp->chgBit(oldp+144,(vlSelfRef.SimpleTop__DOT__activeReg_2));
        bufp->chgBit(oldp+145,(vlSelfRef.SimpleTop__DOT__activeReg_3));
        bufp->chgBit(oldp+146,(vlSelfRef.SimpleTop__DOT__activeReg_4));
        bufp->chgBit(oldp+147,(vlSelfRef.SimpleTop__DOT__activeReg_5));
        bufp->chgIData(oldp+148,(vlSelfRef.SimpleTop__DOT__res),32);
        bufp->chgCData(oldp+149,(vlSelfRef.SimpleTop__DOT__pcReg),3);
        bufp->chgBit(oldp+150,(vlSelfRef.SimpleTop__DOT___GEN));
        bufp->chgBit(oldp+151,(vlSelfRef.SimpleTop__DOT__intents_0_release));
        bufp->chgBit(oldp+152,(vlSelfRef.SimpleTop__DOT__doneWire_5));
        bufp->chgCData(oldp+153,(vlSelfRef.SimpleTop__DOT__pcReg_1),2);
        bufp->chgBit(oldp+154,(vlSelfRef.SimpleTop__DOT__stall_2));
        bufp->chgBit(oldp+155,(vlSelfRef.SimpleTop__DOT__intents_0_acquire));
        bufp->chgBit(oldp+156,(vlSelfRef.SimpleTop__DOT__intents_0_reg));
        bufp->chgBit(oldp+157,(vlSelfRef.SimpleTop__DOT__startWire_5));
        bufp->chgBit(oldp+158,(vlSelfRef.SimpleTop__DOT__doneWire));
        bufp->chgBit(oldp+159,(vlSelfRef.SimpleTop__DOT__activeReg_6));
        bufp->chgIData(oldp+160,(vlSelfRef.SimpleTop__DOT__op1_1),32);
        bufp->chgIData(oldp+161,(vlSelfRef.SimpleTop__DOT__res_1),32);
        bufp->chgCData(oldp+162,(vlSelfRef.SimpleTop__DOT__pcReg_2),3);
        bufp->chgBit(oldp+163,(vlSelfRef.SimpleTop__DOT___GEN_4));
        bufp->chgBit(oldp+164,(vlSelfRef.SimpleTop__DOT__intents_1_release));
        bufp->chgBit(oldp+165,(vlSelfRef.SimpleTop__DOT__doneWire_6));
        bufp->chgCData(oldp+166,(vlSelfRef.SimpleTop__DOT__pcReg_3),2);
        bufp->chgBit(oldp+167,(vlSelfRef.SimpleTop__DOT__intents_1_acquire));
        bufp->chgBit(oldp+168,(vlSelfRef.SimpleTop__DOT__intents_1_reg));
        bufp->chgBit(oldp+169,(vlSelfRef.SimpleTop__DOT__startWire_6));
        bufp->chgBit(oldp+170,(vlSelfRef.SimpleTop__DOT__doneWire_1));
        bufp->chgBit(oldp+171,(vlSelfRef.SimpleTop__DOT__activeReg_7));
        bufp->chgIData(oldp+172,(vlSelfRef.SimpleTop__DOT__addrVal),32);
        bufp->chgIData(oldp+173,(vlSelfRef.SimpleTop__DOT__dataVal),32);
        bufp->chgCData(oldp+174,(vlSelfRef.SimpleTop__DOT__pcReg_4),4);
        bufp->chgBit(oldp+175,(vlSelfRef.SimpleTop__DOT__intents_2_release));
        bufp->chgBit(oldp+176,(vlSelfRef.SimpleTop__DOT__doneWire_7));
        bufp->chgCData(oldp+177,(vlSelfRef.SimpleTop__DOT__pcReg_5),2);
        bufp->chgBit(oldp+178,(vlSelfRef.SimpleTop__DOT__stall_8));
        bufp->chgBit(oldp+179,(vlSelfRef.SimpleTop__DOT__intents_2_acquire));
        bufp->chgBit(oldp+180,(vlSelfRef.SimpleTop__DOT__startWire_7));
        bufp->chgBit(oldp+181,(vlSelfRef.SimpleTop__DOT__doneWire_2));
        bufp->chgBit(oldp+182,(vlSelfRef.SimpleTop__DOT__activeReg_8));
        bufp->chgIData(oldp+183,(vlSelfRef.SimpleTop__DOT__addrVal_1),32);
        bufp->chgIData(oldp+184,(vlSelfRef.SimpleTop__DOT__memData),32);
        bufp->chgCData(oldp+185,(vlSelfRef.SimpleTop__DOT__pcReg_6),4);
        bufp->chgBit(oldp+186,(vlSelfRef.SimpleTop__DOT__io_1_req));
        bufp->chgBit(oldp+187,(vlSelfRef.SimpleTop__DOT__io_1_isWr));
        bufp->chgIData(oldp+188,(vlSelfRef.SimpleTop__DOT__io_1_addr),32);
        bufp->chgBit(oldp+189,(vlSelfRef.SimpleTop__DOT___GEN_18));
        bufp->chgCData(oldp+190,(((IData)(vlSelfRef.SimpleTop__DOT___GEN_18)
                                   ? 3U : 0U)),2);
        bufp->chgBit(oldp+191,(vlSelfRef.SimpleTop__DOT__intents_3_release));
        bufp->chgBit(oldp+192,(vlSelfRef.SimpleTop__DOT__doneWire_8));
        bufp->chgCData(oldp+193,(vlSelfRef.SimpleTop__DOT__pcReg_7),2);
        bufp->chgBit(oldp+194,(vlSelfRef.SimpleTop__DOT__intents_3_acquire));
        bufp->chgCData(oldp+195,(vlSelfRef.SimpleTop__DOT__intents_3_reg),2);
        bufp->chgBit(oldp+196,(vlSelfRef.SimpleTop__DOT__startWire_8));
        bufp->chgBit(oldp+197,(vlSelfRef.SimpleTop__DOT__doneWire_3));
        bufp->chgBit(oldp+198,(vlSelfRef.SimpleTop__DOT__activeReg_9));
        bufp->chgIData(oldp+199,(vlSelfRef.SimpleTop__DOT__op1_2),32);
        bufp->chgIData(oldp+200,(vlSelfRef.SimpleTop__DOT__op2),32);
        bufp->chgIData(oldp+201,(vlSelfRef.SimpleTop__DOT__res_2),32);
        bufp->chgCData(oldp+202,(vlSelfRef.SimpleTop__DOT__pcReg_8),4);
        bufp->chgBit(oldp+203,(vlSelfRef.SimpleTop__DOT__intents_4_release));
        bufp->chgBit(oldp+204,(vlSelfRef.SimpleTop__DOT__doneWire_9));
        bufp->chgCData(oldp+205,(vlSelfRef.SimpleTop__DOT__pcReg_9),2);
        bufp->chgBit(oldp+206,(vlSelfRef.SimpleTop__DOT__intents_4_acquire));
        bufp->chgBit(oldp+207,(vlSelfRef.SimpleTop__DOT__intents_4_reg));
        bufp->chgBit(oldp+208,(vlSelfRef.SimpleTop__DOT__startWire_9));
        __Vtemp_8[0U] = vlSelfRef.SimpleTop__DOT__pcReg_1;
        __Vtemp_8[1U] = vlSelfRef.SimpleTop__DOT__pcReg_3;
        __Vtemp_8[2U] = vlSelfRef.SimpleTop__DOT__pcReg_5;
        __Vtemp_8[3U] = vlSelfRef.SimpleTop__DOT__pcReg_7;
        __Vtemp_8[4U] = vlSelfRef.SimpleTop__DOT__pcReg_9;
        __Vtemp_8[5U] = vlSelfRef.SimpleTop__DOT__pcReg;
        __Vtemp_8[6U] = vlSelfRef.SimpleTop__DOT__pcReg_2;
        __Vtemp_8[7U] = vlSelfRef.SimpleTop__DOT__pcReg_4;
        __Vtemp_8[8U] = (IData)((((QData)((IData)(vlSelfRef.SimpleTop__DOT__pcReg_8)) 
                                  << 0x00000020U) | (QData)((IData)(vlSelfRef.SimpleTop__DOT__pcReg_6))));
        __Vtemp_8[9U] = (IData)(((((QData)((IData)(vlSelfRef.SimpleTop__DOT__pcReg_8)) 
                                   << 0x00000020U) 
                                  | (QData)((IData)(vlSelfRef.SimpleTop__DOT__pcReg_6))) 
                                 >> 0x00000020U));
        bufp->chgWData(oldp+209,(__Vtemp_8),320);
        bufp->chgSData(oldp+219,(((((((IData)(vlSelfRef.SimpleTop__DOT__activeReg_9) 
                                      << 4U) | (((IData)(vlSelfRef.SimpleTop__DOT__activeReg_8) 
                                                 << 3U) 
                                                | ((IData)(vlSelfRef.SimpleTop__DOT__activeReg_7) 
                                                   << 2U))) 
                                    | (((IData)(vlSelfRef.SimpleTop__DOT__activeReg_6) 
                                        << 1U) | (IData)(vlSelfRef.SimpleTop__DOT__activeReg_5))) 
                                   << 5U) | ((((IData)(vlSelfRef.SimpleTop__DOT__activeReg_4) 
                                               << 4U) 
                                              | (((IData)(vlSelfRef.SimpleTop__DOT__activeReg_3) 
                                                  << 3U) 
                                                 | ((IData)(vlSelfRef.SimpleTop__DOT__activeReg_2) 
                                                    << 2U))) 
                                             | (((IData)(vlSelfRef.SimpleTop__DOT__activeReg_1) 
                                                 << 1U) 
                                                | (IData)(vlSelfRef.SimpleTop__DOT__activeReg))))),10);
        bufp->chgBit(oldp+220,(vlSelfRef.SimpleTop__DOT__unnamedblk1__DOT__unnamedblk2__DOT__stall_6));
        bufp->chgBit(oldp+221,(vlSelfRef.SimpleTop__DOT__unnamedblk1__DOT__unnamedblk2__DOT__stall_7));
        bufp->chgIData(oldp+222,(vlSelfRef.SimpleTop__DOT__unnamedblk1__DOT__unnamedblk2__DOT__io_1_wdata),32);
        bufp->chgBit(oldp+223,(vlSelfRef.SimpleTop__DOT__unnamedblk1__DOT__unnamedblk2__DOT__stall_9));
        bufp->chgBit(oldp+224,(vlSelfRef.SimpleTop__DOT__unnamedblk1__DOT__unnamedblk2__DOT__stall_12));
        bufp->chgBit(oldp+225,(vlSelfRef.SimpleTop__DOT__unnamedblk1__DOT__unnamedblk2__DOT__stall_13));
    }
    bufp->chgBit(oldp+226,(vlSelfRef.clock));
    bufp->chgBit(oldp+227,(vlSelfRef.reset));
    bufp->chgBit(oldp+228,(vlSelfRef.io_start));
    bufp->chgBit(oldp+229,(vlSelfRef.io_done));
    bufp->chgIData(oldp+230,(vlSelfRef.io_r1),32);
    bufp->chgIData(oldp+231,(vlSelfRef.io_r2),32);
    bufp->chgIData(oldp+232,(vlSelfRef.io_r3),32);
    bufp->chgIData(oldp+233,(vlSelfRef.io_r4),32);
    bufp->chgSData(oldp+234,(((((IData)(vlSelfRef.SimpleTop__DOT__startWire_9) 
                                << 9U) | (((IData)(vlSelfRef.SimpleTop__DOT__startWire_8) 
                                           << 8U) | 
                                          ((IData)(vlSelfRef.SimpleTop__DOT__startWire_7) 
                                           << 7U))) 
                              | (((IData)(vlSelfRef.SimpleTop__DOT__startWire_6) 
                                  << 6U) | (((IData)(vlSelfRef.SimpleTop__DOT__startWire_5) 
                                             << 5U) 
                                            | (0x0000001fU 
                                               & (- (IData)((IData)(vlSelfRef.io_start)))))))),10);
    bufp->chgSData(oldp+235,(((((((IData)(vlSelfRef.SimpleTop__DOT__doneWire_9) 
                                  << 4U) | (((IData)(vlSelfRef.SimpleTop__DOT__doneWire_8) 
                                             << 3U) 
                                            | ((IData)(vlSelfRef.SimpleTop__DOT__doneWire_7) 
                                               << 2U))) 
                                | (((IData)(vlSelfRef.SimpleTop__DOT__doneWire_6) 
                                    << 1U) | (IData)(vlSelfRef.SimpleTop__DOT__doneWire_5))) 
                               << 5U) | ((((IData)(vlSelfRef.io_done) 
                                           << 4U) | 
                                          (((IData)(vlSelfRef.SimpleTop__DOT__doneWire_3) 
                                            << 3U) 
                                           | ((IData)(vlSelfRef.SimpleTop__DOT__doneWire_2) 
                                              << 2U))) 
                                         | (((IData)(vlSelfRef.SimpleTop__DOT__doneWire_1) 
                                             << 1U) 
                                            | (IData)(vlSelfRef.SimpleTop__DOT__doneWire))))),10);
}

void VSimpleTop___024root__trace_cleanup(void* voidSelf, VerilatedVcd* /*unused*/) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VSimpleTop___024root__trace_cleanup\n"); );
    // Body
    VSimpleTop___024root* const __restrict vlSelf VL_ATTR_UNUSED = static_cast<VSimpleTop___024root*>(voidSelf);
    VSimpleTop__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    vlSymsp->__Vm_activity = false;
    vlSymsp->TOP.__Vm_traceActivity[0U] = 0U;
    vlSymsp->TOP.__Vm_traceActivity[1U] = 0U;
}
