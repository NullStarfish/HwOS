#include "SimEngine.hpp"
#include "VTopModule.h"
#include <ncurses.h>

SimEngine* g_engine = nullptr;

SimEngine::SimEngine(VSimpleTop* top, SymbolParser* parser) 
    : dut(top), sym_parser(parser) {
    g_engine = this;
}

void SimEngine::step_cycle() {
    dut->clock = 1; dut->eval(); 
    dut->clock = 0; dut->eval();
    cycle_count++;
}

void SimEngine::run_continuous() {
    nodelay(stdscr, TRUE);
    while (!Verilated::gotFinish()) {
        step_cycle();
        int ch = getch();
        if (ch == 'p' || ch == ' ') break; 
        if (cycle_count % 100 == 0) napms(1);
    }
    nodelay(stdscr, FALSE);
}

void SimEngine::run_until_thread_change(int tid) {
    if (history.empty()) return;
    const auto& last_snap = history.back();
    if (tid >= (int)last_snap.threads.size()) return;
    
    uint32_t start_pc = last_snap.threads[tid].pc;
    bool start_active = last_snap.threads[tid].active;
    if (last_snap.threads[tid].done) { step_cycle(); return; }

    nodelay(stdscr, TRUE);
    bool changed = false;
    int timeout = 5000;
    
    while (!changed && timeout-- > 0 && !Verilated::gotFinish()) {
        step_cycle();
        if (!history.empty() && tid < (int)history.back().threads.size()) {
            const auto& t_curr = history.back().threads[tid];
            if (start_active) {
                if (t_curr.pc != start_pc || t_curr.done || !t_curr.active) changed = true;
            } else {
                if (t_curr.active) changed = true;
            }
        }
        if (getch() == 'q') break;
    }
    nodelay(stdscr, FALSE);
}

void SimEngine::record_snapshot(int n_threads, const uint32_t* pcs, const uint32_t* actives, const uint32_t* dones) {
    CycleSnapshot snap;
    snap.cycle = cycle_count;
    
    for (int i = 0; i < n_threads; i++) {
        ThreadSnapshot t;
        t.id = i;
        t.name = (i < sym_parser->all_thread_names.size()) ? sym_parser->all_thread_names[i] : "Thread_" + std::to_string(i);
        t.pc = pcs[i];
        t.active = (actives[i/32] >> (i%32)) & 1;
        t.done = (dones[i/32] >> (i%32)) & 1;
        
        StepInfo info = sym_parser->get_step_info(t.name, t.pc);
        t.step_name = info.step_name;
        snap.threads.push_back(t);
    }
    history.push_back(snap);
    if (history.size() > MAX_HISTORY) history.pop_front();
}