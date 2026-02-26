#pragma once
#include <ncurses.h>
#include "DataTypes.hpp"
#include "SymbolParser.hpp"
#include "SimEngine.hpp"

class UIManager {
public:
    UIManager(SymbolParser* parser, SimEngine* engine);
    ~UIManager();

    void render_all();
    void process_input();
    bool is_running() const { return running; }

private:
    void render_sidebar();
    void render_main();
    void render_callstack_view();
    void render_cmd();
    void toggle_pin(int tid);

    SymbolParser* sym_parser;
    SimEngine* sim_engine;

    WINDOW *win_side, *win_main, *win_bt, *win_cmd;
    
    bool running = true;
    bool focus_sidebar = true;
    int sidebar_cursor = 0;
    int main_cursor = 0;
    int scroll_offset = 0;
    std::vector<int> pinned_tids;
};