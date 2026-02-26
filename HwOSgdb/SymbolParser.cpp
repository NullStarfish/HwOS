#include "SymbolParser.hpp"
#include <fstream>
#include <sstream>

std::vector<std::string> SymbolParser::split(const std::string& s, char delimiter) const {
    std::vector<std::string> tokens;
    std::string token;
    std::istringstream tokenStream(s);
    while (std::getline(tokenStream, token, delimiter)) {
        tokens.push_back(token);
    }
    return tokens;
}

void SymbolParser::load_symbols(const std::string& filename) {
    std::ifstream infile(filename);
    if (!infile.good()) return;
    
    std::string line;
    while (std::getline(infile, line)) {
        std::istringstream iss(line);
        std::string t_name, s_name, t_stack_str, a_tree_str;
        int pc, t_depth, a_count;

        // 格式: Thread PC Step T_Depth T_Stack A_Count A_Tree
        if (!(iss >> t_name >> pc >> s_name >> t_depth >> t_stack_str >> a_count >> a_tree_str)) continue;

        if (!seen_threads[t_name]) {
            all_thread_names.push_back(t_name);
            thread_name_to_id[t_name] = all_thread_names.size() - 1;
            seen_threads[t_name] = true;
        }

        StepInfo info;
        info.step_name = s_name;
        info.temporal_depth = t_depth;

        if (t_stack_str != "None") {
            info.temporal_stack = split(t_stack_str, ',');
        }

        if (a_tree_str != "None") {
            std::vector<std::string> paths = split(a_tree_str, ';');
            for (const auto& p : paths) {
                info.atomic_tree.push_back(split(p, ','));
            }
        }
        symbols[t_name][pc] = info;
    }
}

StepInfo SymbolParser::get_step_info(const std::string& thread_name, int pc) const {
    auto t_it = symbols.find(thread_name);
    if (t_it != symbols.end()) {
        auto p_it = t_it->second.find(pc);
        if (p_it != t_it->second.end()) return p_it->second;
    }
    return {"???", 0, {}, {}};
}