#pragma once
#include "DataTypes.hpp"
#include "SymbolParser.hpp"
#include <deque>
#include <memory>

class VTopModule; // 前向声明你的 Verilator Top 类

class SimEngine {
public:
    SimEngine(VTopModule* top, SymbolParser* parser);
    
    void step_cycle();
    void run_continuous();
    void run_until_thread_change(int tid);
    
    // 供 DPI 调用的记录函数
    void record_snapshot(int n_threads, const uint32_t* pcs, const uint32_t* actives, const uint32_t* dones);

    const std::deque<CycleSnapshot>& get_history() const { return history; }
    uint64_t get_cycle_count() const { return cycle_count; }

private:
    VTopModule* dut;
    SymbolParser* sym_parser;
    std::deque<CycleSnapshot> history;
    uint64_t cycle_count = 0;
    const int MAX_HISTORY = 2000;
};

// 全局指针，供 DPI C 函数回调使用
extern SimEngine* g_engine;