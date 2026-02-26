#pragma once
#include "DataTypes.hpp"
#include <string>

class SymbolParser {
public:
    void load_symbols(const std::string& filename);
    StepInfo get_step_info(const std::string& thread_name, int pc) const;
    
    std::vector<std::string> all_thread_names;
    std::map<std::string, int> thread_name_to_id;

private:
    std::map<std::string, std::map<int, StepInfo>> symbols;
    std::map<std::string, bool> seen_threads;
    
    std::vector<std::string> split(const std::string& s, char delimiter) const;
};