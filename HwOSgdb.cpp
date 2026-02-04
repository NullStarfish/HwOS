#include <iostream>
#include <vector>
#include <string>
#include <sstream>
#include <map>
#include <iomanip>
#include <algorithm> // for std::all_of

#include "verilated.h"
#include "VSimpleTop.h"
#include "svdpi.h"
#include "VSimpleTop__Dpi.h"

// --- 全局状态 ---
struct ThreadState {
    int id;
    uint32_t pc;
    bool active;
    bool start;
    bool abort;
    bool done;
};

static std::vector<ThreadState> g_threads;
// 【新增】软件侧的记分板：记录每个线程是否已经完成过
static std::vector<bool> g_threads_finished_mask;

static uint64_t g_cycle_count = 0;
static std::map<int, uint32_t> g_breakpoints;
static bool g_breakpoint_hit = false;

// --- DPI 回调 ---
extern "C" void kernel_monitor_tick(
    int n_threads,
    const svBitVecVal* pcs,
    const svBitVecVal* actives,
    const svBitVecVal* starts,
    const svBitVecVal* aborts,
    const svBitVecVal* dones
) {
    // 初始化或调整大小
    if (g_threads.size() != n_threads) {
        g_threads.resize(n_threads);
        g_threads_finished_mask.resize(n_threads, false);
    }
    
    g_breakpoint_hit = false;

    for (int i = 0; i < n_threads; i++) {
        uint32_t current_pc = pcs[i];
        bool is_active = (actives[i / 32] >> (i % 32)) & 1;
        bool is_start  = (starts[i / 32]  >> (i % 32)) & 1;
        bool is_abort  = (aborts[i / 32]  >> (i % 32)) & 1;
        bool is_done   = (dones[i / 32]   >> (i % 32)) & 1;

        g_threads[i] = {i, current_pc, is_active, is_start, is_abort, is_done};

        // 【新增】软件锁存：如果监测到 done 脉冲，永久标记该线程为已完成
        if (is_done) {
            g_threads_finished_mask[i] = true;
        }

        // 断点检查
        if (g_breakpoints.count(i)) {
            if (is_active && current_pc == g_breakpoints[i]) {
                if (!g_breakpoint_hit) {
                    printf("\n[DEBUG] Breakpoint HIT! Thread %d at PC %d (Cycle %lu)\n", i, current_pc, g_cycle_count);
                    g_breakpoint_hit = true;
                }
            }
        }
    }
}

// --- 辅助函数 ---
void print_status() {
    std::cout << "\n--- Cycle " << g_cycle_count << " Status ---\n";
    std::cout << std::left << std::setw(5) << "TID" << std::setw(10) << "State" << std::setw(10) << "PC" << "Flags" << std::endl;
    std::cout << "--------------------------------\n";
    for (const auto& t : g_threads) {
        std::string state = t.active ? "RUNNING" : "IDLE";
        
        // 优先显示当前瞬间的状态，如果没有，则显示历史完成状态
        if (t.done) state = "DONE (Pulse)";
        else if (g_threads_finished_mask[t.id]) state = "FINISHED"; // 历史状态
        
        std::string flags = "";
        if (t.start) flags += "[START] ";
        std::cout << std::left << std::setw(5) << t.id << std::setw(12) << state << std::setw(10) << t.pc << flags << std::endl;
    }
    std::cout << std::endl;
}

void step_cycle(VSimpleTop* top) {
    top->clock = 1; top->eval();
    top->clock = 0; top->eval();
    g_cycle_count++;
}

bool check_all_threads_done() {
    if (g_threads_finished_mask.empty()) return false;
    // 检查 mask 中是否全为 true
    return std::all_of(g_threads_finished_mask.begin(), g_threads_finished_mask.end(), [](bool v){ return v; });
}

// --- 主函数 ---
int main(int argc, char** argv) {
    Verilated::commandArgs(argc, argv);
    VSimpleTop* top = new VSimpleTop;

    // Reset
    top->reset = 1; top->io_start = 0;
    for (int i = 0; i < 5; i++) step_cycle(top);
    top->reset = 0;
    
    // Start (Reset software latches on start)
    // 注意：如果是多次 start，这里需要清空 g_threads_finished_mask
    std::fill(g_threads_finished_mask.begin(), g_threads_finished_mask.end(), false);
    
    top->io_start = 1; step_cycle(top); top->io_start = 0;

    std::cout << "HwOS Interactive Debugger. Type 'help' for commands.\n";
    std::string line;
    bool running = true;

    while (running && !Verilated::gotFinish()) {
        std::cout << "(HwOS-GDB) > ";
        if (!std::getline(std::cin, line)) break;
        std::stringstream ss(line);
        std::string cmd; ss >> cmd;

        if (cmd == "quit") running = false;
        else if (cmd == "info") print_status();
        else if (cmd == "step") {
            step_cycle(top);
            print_status();
        } 
        else if (cmd == "break") {
            int tid, pc;
            if (ss >> tid >> pc) { g_breakpoints[tid] = pc; std::cout << "Breakpoint set.\n"; }
        }
        else if (cmd == "run") {
            std::cout << "Running... \n";
            while (!Verilated::gotFinish()) {
                step_cycle(top);

                // 断点退出
                if (g_breakpoint_hit) {
                    print_status();
                    break; 
                }

                // 【修改】软件侧判断结束：检查所有线程是否都已标记为 Finish
                if (check_all_threads_done()) {
                    std::cout << "All threads finished (Software Detected) at Cycle " << g_cycle_count << ".\n";
                    print_status();
                    break;
                }
            }
        }
        else if (cmd == "help") {
            std::cout << "Commands: info, step, run, break <tid> <pc>, quit\n";
        }
    }
    delete top;
    return 0;
}