// Verilated -*- C++ -*-
// DESCRIPTION: Verilator output: Model implementation (design independent parts)

#include "VSimpleTop__pch.h"
#include "verilated_vcd_c.h"

//============================================================
// Constructors

VSimpleTop::VSimpleTop(VerilatedContext* _vcontextp__, const char* _vcname__)
    : VerilatedModel{*_vcontextp__}
    , vlSymsp{new VSimpleTop__Syms(contextp(), _vcname__, this)}
    , clock{vlSymsp->TOP.clock}
    , reset{vlSymsp->TOP.reset}
    , io_start{vlSymsp->TOP.io_start}
    , io_done{vlSymsp->TOP.io_done}
    , io_r1{vlSymsp->TOP.io_r1}
    , io_r2{vlSymsp->TOP.io_r2}
    , io_r3{vlSymsp->TOP.io_r3}
    , io_r4{vlSymsp->TOP.io_r4}
    , rootp{&(vlSymsp->TOP)}
{
    // Register model with the context
    contextp()->addModel(this);
    contextp()->traceBaseModelCbAdd(
        [this](VerilatedTraceBaseC* tfp, int levels, int options) { traceBaseModel(tfp, levels, options); });
}

VSimpleTop::VSimpleTop(const char* _vcname__)
    : VSimpleTop(Verilated::threadContextp(), _vcname__)
{
}

//============================================================
// Destructor

VSimpleTop::~VSimpleTop() {
    delete vlSymsp;
}

//============================================================
// Evaluation function

#ifdef VL_DEBUG
void VSimpleTop___024root___eval_debug_assertions(VSimpleTop___024root* vlSelf);
#endif  // VL_DEBUG
void VSimpleTop___024root___eval_static(VSimpleTop___024root* vlSelf);
void VSimpleTop___024root___eval_initial(VSimpleTop___024root* vlSelf);
void VSimpleTop___024root___eval_settle(VSimpleTop___024root* vlSelf);
void VSimpleTop___024root___eval(VSimpleTop___024root* vlSelf);

void VSimpleTop::eval_step() {
    VL_DEBUG_IF(VL_DBG_MSGF("+++++TOP Evaluate VSimpleTop::eval_step\n"); );
#ifdef VL_DEBUG
    // Debug assertions
    VSimpleTop___024root___eval_debug_assertions(&(vlSymsp->TOP));
#endif  // VL_DEBUG
    vlSymsp->__Vm_activity = true;
    vlSymsp->__Vm_deleter.deleteAll();
    if (VL_UNLIKELY(!vlSymsp->__Vm_didInit)) {
        vlSymsp->__Vm_didInit = true;
        VL_DEBUG_IF(VL_DBG_MSGF("+ Initial\n"););
        VSimpleTop___024root___eval_static(&(vlSymsp->TOP));
        VSimpleTop___024root___eval_initial(&(vlSymsp->TOP));
        VSimpleTop___024root___eval_settle(&(vlSymsp->TOP));
    }
    VL_DEBUG_IF(VL_DBG_MSGF("+ Eval\n"););
    VSimpleTop___024root___eval(&(vlSymsp->TOP));
    // Evaluate cleanup
    Verilated::endOfEval(vlSymsp->__Vm_evalMsgQp);
}

//============================================================
// Events and timing
bool VSimpleTop::eventsPending() { return false; }

uint64_t VSimpleTop::nextTimeSlot() {
    VL_FATAL_MT(__FILE__, __LINE__, "", "No delays in the design");
    return 0;
}

//============================================================
// Utilities

const char* VSimpleTop::name() const {
    return vlSymsp->name();
}

//============================================================
// Invoke final blocks

void VSimpleTop___024root___eval_final(VSimpleTop___024root* vlSelf);

VL_ATTR_COLD void VSimpleTop::final() {
    VSimpleTop___024root___eval_final(&(vlSymsp->TOP));
}

//============================================================
// Implementations of abstract methods from VerilatedModel

const char* VSimpleTop::hierName() const { return vlSymsp->name(); }
const char* VSimpleTop::modelName() const { return "VSimpleTop"; }
unsigned VSimpleTop::threads() const { return 1; }
void VSimpleTop::prepareClone() const { contextp()->prepareClone(); }
void VSimpleTop::atClone() const {
    contextp()->threadPoolpOnClone();
}
std::unique_ptr<VerilatedTraceConfig> VSimpleTop::traceConfig() const {
    return std::unique_ptr<VerilatedTraceConfig>{new VerilatedTraceConfig{false, false, false}};
};

//============================================================
// Trace configuration

void VSimpleTop___024root__trace_decl_types(VerilatedVcd* tracep);

void VSimpleTop___024root__trace_init_top(VSimpleTop___024root* vlSelf, VerilatedVcd* tracep);

VL_ATTR_COLD static void trace_init(void* voidSelf, VerilatedVcd* tracep, uint32_t code) {
    // Callback from tracep->open()
    VSimpleTop___024root* const __restrict vlSelf VL_ATTR_UNUSED = static_cast<VSimpleTop___024root*>(voidSelf);
    VSimpleTop__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    if (!vlSymsp->_vm_contextp__->calcUnusedSigs()) {
        VL_FATAL_MT(__FILE__, __LINE__, __FILE__,
            "Turning on wave traces requires Verilated::traceEverOn(true) call before time 0.");
    }
    vlSymsp->__Vm_baseCode = code;
    tracep->pushPrefix(std::string{vlSymsp->name()}, VerilatedTracePrefixType::SCOPE_MODULE);
    VSimpleTop___024root__trace_decl_types(tracep);
    VSimpleTop___024root__trace_init_top(vlSelf, tracep);
    tracep->popPrefix();
}

VL_ATTR_COLD void VSimpleTop___024root__trace_register(VSimpleTop___024root* vlSelf, VerilatedVcd* tracep);

VL_ATTR_COLD void VSimpleTop::traceBaseModel(VerilatedTraceBaseC* tfp, int levels, int options) {
    (void)levels; (void)options;
    VerilatedVcdC* const stfp = dynamic_cast<VerilatedVcdC*>(tfp);
    if (VL_UNLIKELY(!stfp)) {
        vl_fatal(__FILE__, __LINE__, __FILE__,"'VSimpleTop::trace()' called on non-VerilatedVcdC object;"
            " use --trace-fst with VerilatedFst object, and --trace-vcd with VerilatedVcd object");
    }
    stfp->spTrace()->addModel(this);
    stfp->spTrace()->addInitCb(&trace_init, &(vlSymsp->TOP));
    VSimpleTop___024root__trace_register(&(vlSymsp->TOP), stfp->spTrace());
}
