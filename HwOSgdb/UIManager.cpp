#include "UIManager.hpp"
#include <algorithm>

UIManager::UIManager(SymbolParser* parser, SimEngine* engine) 
    : sym_parser(parser), sim_engine(engine) {
    initscr(); cbreak(); noecho(); keypad(stdscr, TRUE);
    start_color(); use_default_colors(); curs_set(0);
    
    init_pair(1, COLOR_WHITE, COLOR_BLUE);
    init_pair(2, COLOR_YELLOW, -1);
    init_pair(3, COLOR_CYAN, -1);
    init_pair(4, COLOR_BLUE, -1);
    init_pair(5, COLOR_GREEN, -1);
    init_pair(6, COLOR_BLACK, COLOR_CYAN);

    int max_y, max_x;
    getmaxyx(stdscr, max_y, max_x);
    
    int bt_height = 12; 
    int cmd_height = 3;
    int main_height = max_y - bt_height - cmd_height;
    
    win_side = newwin(main_height, 35, 0, 0);
    win_main = newwin(main_height, max_x - 35, 0, 35);
    win_bt   = newwin(bt_height, max_x, main_height, 0); 
    win_cmd  = newwin(cmd_height, max_x, max_y - cmd_height, 0);
}

UIManager::~UIManager() { endwin(); }

void UIManager::toggle_pin(int tid) {
    auto it = std::find(pinned_tids.begin(), pinned_tids.end(), tid);
    if (it != pinned_tids.end()) {
        pinned_tids.erase(it);
        if (main_cursor >= (int)pinned_tids.size()) main_cursor = std::max(0, (int)pinned_tids.size() - 1);
    } else {
        pinned_tids.push_back(tid);
    }
}

void UIManager::render_sidebar() {
    werase(win_side);
    box(win_side, 0, 0);
    mvwprintw(win_side, 0, 2, " Thread Repo ");

    int max_y, max_x; getmaxyx(win_side, max_y, max_x);
    for (size_t i = 0; i < sym_parser->all_thread_names.size() && i < max_y - 2; i++) {
        bool pinned = std::find(pinned_tids.begin(), pinned_tids.end(), i) != pinned_tids.end();
        if (focus_sidebar && i == sidebar_cursor) wattron(win_side, A_REVERSE);
        if (pinned) wattron(win_side, COLOR_PAIR(3));
        
        std::string name = sym_parser->all_thread_names[i];
        if (name.length() > max_x - 6) name = name.substr(0, max_x - 6) + "..";
        mvwprintw(win_side, i + 1, 1, "%c %s", pinned ? '*' : ' ', name.c_str());
        
        if (pinned) wattroff(win_side, COLOR_PAIR(3));
        if (focus_sidebar && i == sidebar_cursor) wattroff(win_side, A_REVERSE);
    }
    wrefresh(win_side);
}

void UIManager::render_main() {
    werase(win_main);
    if (pinned_tids.empty()) { wrefresh(win_main); return; }

    const int COL_W = 20;
    wattron(win_main, A_BOLD | COLOR_PAIR(1));
    mvwprintw(win_main, 0, 0, "CYCLE   ");
    for (size_t i = 0; i < pinned_tids.size(); i++) {
        std::string name = sym_parser->all_thread_names[pinned_tids[i]];
        if (!focus_sidebar && i == main_cursor) wattron(win_main, A_REVERSE | COLOR_PAIR(6));
        mvwprintw(win_main, 0, 8 + i * COL_W, "| %-*s", COL_W-2, name.substr(0, COL_W-2).c_str());
        if (!focus_sidebar && i == main_cursor) wattroff(win_main, A_REVERSE | COLOR_PAIR(6));
    }
    wattroff(win_main, A_BOLD | COLOR_PAIR(1));

    const auto& history = sim_engine->get_history();
    int h_idx = history.size() - 1 - scroll_offset;
    int max_y, max_x; getmaxyx(win_main, max_y, max_x);
    int screen_y = max_y - 1;

    while (h_idx >= 0 && screen_y >= 1) {
        const auto& snap = history[h_idx];
        wattron(win_main, COLOR_PAIR(2));
        mvwprintw(win_main, screen_y, 0, "%-7lu", snap.cycle);
        wattroff(win_main, COLOR_PAIR(2));

        for (size_t i = 0; i < pinned_tids.size(); i++) {
            int tid = pinned_tids[i];
            mvwprintw(win_main, screen_y, 8 + i * COL_W, "| ");
            if (tid >= snap.threads.size()) continue;
            
            const auto& t = snap.threads[tid];
            if (!t.active && !t.done) { wprintw(win_main, "."); }
            else if (t.done) { wattron(win_main, COLOR_PAIR(5)|A_BOLD); wprintw(win_main, "DONE"); wattroff(win_main, COLOR_PAIR(5)|A_BOLD); }
            else { wattron(win_main, A_BOLD); wprintw(win_main, "%s", t.step_name.substr(0, COL_W-3).c_str()); wattroff(win_main, A_BOLD); }
        }
        screen_y--; h_idx--;
    }
    wrefresh(win_main);
}

void UIManager::render_callstack_view() {
    werase(win_bt);
    box(win_bt, 0, 0);
    
    if (pinned_tids.empty() || sim_engine->get_history().empty()) {
        mvwprintw(win_bt, 1, 2, "No thread selected or simulation not started.");
        wrefresh(win_bt); return;
    }

    int focused_tid = pinned_tids[main_cursor];
    const auto& last_snap = sim_engine->get_history().back();
    if (focused_tid >= last_snap.threads.size()) return;

    const auto& t = last_snap.threads[focused_tid];
    StepInfo info = sym_parser->get_step_info(t.name, t.pc);

    mvwprintw(win_bt, 0, 2, " CallStack (Thread: %s | PC: %d) ", t.name.c_str(), t.pc);

    int row = 1;
    // 1. 渲染宏观时序栈 (Temporal Stack)
    wattron(win_bt, A_BOLD | COLOR_PAIR(3));
    mvwprintw(win_bt, row++, 2, "[Temporal Stack]");
    wattroff(win_bt, A_BOLD | COLOR_PAIR(3));

    int depth = 0;
    for (const auto& frame : info.temporal_stack) {
        mvwprintw(win_bt, row++, 4 + depth * 2, "#%d %s", depth, frame.c_str());
        depth++;
    }
    wattron(win_bt, A_REVERSE);
    mvwprintw(win_bt, row++, 4 + depth * 2, "-> %s (Current Step)", info.step_name.c_str());
    wattroff(win_bt, A_REVERSE);

    row++;
    // 2. 渲染微观组合树 (Atomic Tree)
    wattron(win_bt, A_BOLD | COLOR_PAIR(2));
    mvwprintw(win_bt, row++, 2, "[Atomic Logic Unrolled]");
    wattroff(win_bt, A_BOLD | COLOR_PAIR(2));

    if (info.atomic_tree.empty()) {
        mvwprintw(win_bt, row++, 4, "(No internal HwFunctions called)");
    } else {
        for (size_t i = 0; i < info.atomic_tree.size(); ++i) {
            const auto& path = info.atomic_tree[i];
            char branch = (i == info.atomic_tree.size() - 1) ? '`' : '|';
            mvwprintw(win_bt, row, 4, "%c- ", branch);
            for (size_t j = 0; j < path.size(); ++j) {
                wprintw(win_bt, j == 0 ? "%s" : " -> %s", path[j].c_str());
            }
            row++;
            if (row >= 10) { mvwprintw(win_bt, row, 4, "..."); break; }
        }
    }
    wrefresh(win_bt);
}

void UIManager::render_cmd() {
    werase(win_cmd);
    wattron(win_cmd, A_BOLD);
    wprintw(win_cmd, focus_sidebar ? "[SIDEBAR]" : "[SCOPE]");
    wattroff(win_cmd, A_BOLD);
    wprintw(win_cmd, " | [q]:Quit | [TAB]:Switch Focus | [r]:Run | [s]:StepTh | [Spc]:Step");
    wrefresh(win_cmd);
}

void UIManager::render_all() {
    render_sidebar();
    render_main();
    render_callstack_view();
    render_cmd();
}

void UIManager::handle_input(int ch) {
    if (ch == 'q') running = false;
    else if (ch == '\t') focus_sidebar = !focus_sidebar;
    else if (ch == 'r') { sim_engine->run_continuous(); scroll_offset = 0; }
    else if (ch == ' ') { sim_engine->step_cycle(); scroll_offset = 0; }

    if (focus_sidebar) {
        if (ch == KEY_UP && sidebar_cursor > 0) sidebar_cursor--;
        else if (ch == KEY_DOWN && sidebar_cursor < sym_parser->all_thread_names.size() - 1) sidebar_cursor++;
        else if (ch == '\n' || ch == KEY_ENTER) toggle_pin(sidebar_cursor);
    } else {
        if (ch == KEY_LEFT && main_cursor > 0) main_cursor--;
        else if (ch == KEY_RIGHT && main_cursor < pinned_tids.size() - 1) main_cursor++;
        else if (ch == KEY_BACKSPACE || ch == 127) { if (!pinned_tids.empty()) toggle_pin(pinned_tids[main_cursor]); }
        else if (ch == 's') { if (!pinned_tids.empty()) sim_engine->run_until_thread_change(pinned_tids[main_cursor]); scroll_offset = 0; }
        else if (ch == KEY_UP && scroll_offset < sim_engine->get_history().size() - 5) scroll_offset++;
        else if (ch == KEY_DOWN && scroll_offset > 0) scroll_offset--;
    }
}