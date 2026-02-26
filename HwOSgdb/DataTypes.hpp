#pragma once
#include <string>
#include <vector>
#include <map>
#include <cstdint>

// 微观组合逻辑树的一条路径
using AtomicPath = std::vector<std::string>;

// 符号表信息 (双轨调用栈)
struct StepInfo {
    std::string step_name;
    int temporal_depth = 0;
    std::vector<std::string> temporal_stack; // 宏观时序栈
    std::vector<AtomicPath> atomic_tree;     // 微观组合调用树
};

// 单个线程在一拍中的快照
struct ThreadSnapshot {
    int id;
    std::string name; 
    uint32_t pc;
    bool active;
    bool done;
    std::string step_name;
};

// 全局单拍快照
struct CycleSnapshot {
    uint64_t cycle;
    std::vector<ThreadSnapshot> threads;
};