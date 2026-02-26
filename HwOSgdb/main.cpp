#include "verilated.h"
#include "./obj_dir/VTopModule.h" // 请替换为你的实际 Top 头文件
#include "svdpi.h"
#include "SymbolParser.hpp"
#include "SimEngine.hpp"
#include "UIManager.hpp"



// DPI C 回调函数 (剔除了 starts 和 aborts)
extern "C" void kernel_monitor_tick(
    int n_threads, const svBitVecVal* pcs, const svBitVecVal* actives, const svBitVecVal* dones
) {
    if (g_engine) {
        g_engine->record_snapshot(n_threads, pcs, actives, dones);
    }
} 

int main(int argc, char** argv) {
    Verilated::commandArgs(argc, argv);
    VTopModule* top = new VTopModule;

    // 初始化核心组件
    SymbolParser parser;
    parser.load_symbols("../generated/hwos.symbols");

    SimEngine engine(top, &parser);
    UIManager ui(&parser, &engine);

    // 初始步进与复位 (根据你的 TB 设计调整)
    top->reset = 1; top->io_start = 0;
    for(int i = 0; i < 5; i++) engine.step_cycle();
    top->reset = 0;
    top->io_start = 1;
    engine.step_cycle();
    top->io_start = 0;

    // 主事件循环
    while (ui.is_running() && !Verilated::gotFinish()) {
        ui.render_all();
        
        // 替换掉原来的 int ch = getch(); ui.handle_input(ch);
        ui.process_input(); 
    }

    delete top;
    return 0;
}