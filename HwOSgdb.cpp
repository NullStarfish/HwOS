/*
 type: uploaded file
 fileName: HwOSgdb.cpp
*/
#include <iostream>
#include <vector>
#include <string>
#include <sstream>
#include <map>
#include <iomanip>
#include <fstream>
#include <deque>
#include <algorithm>
#include <ncurses.h> 
#include <functional>
#include <cctype>

#include "verilated.h"
#include "VSimpleTop.h"
#include "svdpi.h"

// ==========================================
// 1. 数据结构
// ==========================================

struct ThreadSnapshot {
    int id;
    std::string name; 
    uint32_t pc;
    bool active;
    bool done;
    std::string step_name;
};

struct CycleSnapshot {
    uint64_t cycle;
    std::vector<ThreadSnapshot> threads;
};

static std::deque<CycleSnapshot> g_history;
static const int MAX_HISTORY = 2000;

// 线程元数据
static std::vector<std::string> g_all_thread_names; // index = TID
static std::map<std::string, int> g_thread_name_to_id; // Name -> TID
static std::map<std::string, std::map<int, std::string>> g_symbols; 

// UI 状态
static uint64_t g_cycle_count = 0;
static std::vector<int> g_pinned_tids; 
static int g_sidebar_cursor = 0;      
static int g_main_cursor = 0;          
static bool g_focus_sidebar = true;    

// 断点触发状态
static bool g_breakpoint_hit = false;
static std::string g_hit_reason = "";

// ==========================================
// 2. 表达式解析器 (Expression Parser)
// ==========================================

// 简单的 AST 节点基类
struct ExprNode {
    virtual ~ExprNode() = default;
    virtual bool evaluate(const std::vector<ThreadSnapshot>& threads) = 0;
};

// 相等比较节点: ThreadName.pc == Value
struct PcEqNode : ExprNode {
    int tid;
    uint32_t target_pc;
    PcEqNode(int id, uint32_t pc) : tid(id), target_pc(pc) {}
    bool evaluate(const std::vector<ThreadSnapshot>& threads) override {
        if (tid >= (int)threads.size()) return false;
        // 只有当线程 active 或 done 时才检查 PC (或者你希望 idle 时也检查？通常 idle pc=0)
        return threads[tid].pc == target_pc; 
    }
};

// 逻辑与节点: Left && Right
struct AndNode : ExprNode {
    ExprNode *left, *right;
    AndNode(ExprNode* l, ExprNode* r) : left(l), right(r) {}
    ~AndNode() { delete left; delete right; }
    bool evaluate(const std::vector<ThreadSnapshot>& threads) override {
        return left->evaluate(threads) && right->evaluate(threads);
    }
};

// 逻辑或节点: Left || Right
struct OrNode : ExprNode {
    ExprNode *left, *right;
    OrNode(ExprNode* l, ExprNode* r) : left(l), right(r) {}
    ~OrNode() { delete left; delete right; }
    bool evaluate(const std::vector<ThreadSnapshot>& threads) override {
        return left->evaluate(threads) || right->evaluate(threads);
    }
};

class BreakpointParser {
    std::string input;
    size_t pos = 0;

    char peek() {
        while (pos < input.size() && std::isspace(input[pos])) pos++;
        if (pos == input.size()) return 0;
        return input[pos];
    }

    char get() {
        char c = peek();
        if (pos < input.size()) pos++;
        return c;
    }

    bool match(const std::string& token) {
        size_t saved_pos = pos;
        while (pos < input.size() && std::isspace(input[pos])) pos++;
        
        if (input.substr(pos, token.size()) == token) {
            pos += token.size();
            return true;
        }
        pos = saved_pos;
        return false;
    }

    // 解析数字
    uint32_t parseNumber() {
        while (pos < input.size() && std::isspace(input[pos])) pos++;
        size_t start = pos;
        while (pos < input.size() && std::isdigit(input[pos])) pos++;
        return std::stoi(input.substr(start, pos - start));
    }

    // 解析标识符 (线程名)
    std::string parseIdentifier() {
        while (pos < input.size() && std::isspace(input[pos])) pos++;
        size_t start = pos;
        while (pos < input.size() && (std::isalnum(input[pos]) || input[pos] == '_' || input[pos] == '/')) pos++;
        return input.substr(start, pos - start);
    }

public:
    BreakpointParser(const std::string& s) : input(s) {}

    // Grammar:
    // Expression -> Term { "||" Term }
    // Term       -> Factor { "&&" Factor }
    // Factor     -> Identifier ".pc" "==" Number | "(" Expression ")"

    ExprNode* parseExpression() {
        ExprNode* left = parseTerm();
        while (match("||")) {
            ExprNode* right = parseTerm();
            left = new OrNode(left, right);
        }
        return left;
    }

    ExprNode* parseTerm() {
        ExprNode* left = parseFactor();
        while (match("&&")) {
            ExprNode* right = parseFactor();
            left = new AndNode(left, right);
        }
        return left;
    }

    ExprNode* parseFactor() {
        if (match("(")) {
            ExprNode* node = parseExpression();
            if (!match(")")) throw std::runtime_error("Missing ')'");
            return node;
        }

        std::string name = parseIdentifier();
        if (match(".pc")) {
            if (match("==")) {
                uint32_t val = parseNumber();
                if (g_thread_name_to_id.find(name) == g_thread_name_to_id.end()) {
                    throw std::runtime_error("Unknown thread: " + name);
                }
                return new PcEqNode(g_thread_name_to_id[name], val);
            }
        }
        throw std::runtime_error("Syntax error at: " + name);
    }
};

struct Breakpoint {
    std::string raw;
    ExprNode* expr;
    bool enabled;
};

static std::vector<Breakpoint> g_breakpoints;

void add_breakpoint(const std::string& cmd) {
    try {
        BreakpointParser parser(cmd);
        ExprNode* root = parser.parseExpression();
        g_breakpoints.push_back({cmd, root, true});
    } catch (std::exception& e) {
        // UI should handle error printing, but here we can just log or ignore
    }
}

bool check_breakpoints(const std::vector<ThreadSnapshot>& threads) {
    for (const auto& bp : g_breakpoints) {
        if (bp.enabled && bp.expr->evaluate(threads)) {
            g_hit_reason = "Hit: " + bp.raw;
            return true;
        }
    }
    return false;
}

// ==========================================
// 3. 辅助逻辑
// ==========================================

void load_symbols(const std::string& filename) {
    std::ifstream infile(filename);
    if (!infile.good()) return;
    std::string t_name, s_name;
    int pc;
    while (infile >> t_name >> pc >> s_name) {
        g_symbols[t_name][pc] = s_name;
    }
}

void scan_thread_names(const std::string& filename) {
    std::ifstream infile(filename);
    if (!infile.good()) return;
    std::string line;
    std::map<std::string, bool> seen;
    while(std::getline(infile, line)) {
        std::stringstream ss(line);
        std::string t_name;
        ss >> t_name;
        if (!seen[t_name]) {
            g_all_thread_names.push_back(t_name);
            g_thread_name_to_id[t_name] = g_all_thread_names.size() - 1;
            seen[t_name] = true;
        }
    }
}

std::string get_step_name(const std::string& t_name, int pc) {
    if (g_symbols[t_name].count(pc)) return g_symbols[t_name][pc];
    return "???";
}

bool is_pinned(int tid) {
    return std::find(g_pinned_tids.begin(), g_pinned_tids.end(), tid) != g_pinned_tids.end();
}

void toggle_pin(int tid) {
    auto it = std::find(g_pinned_tids.begin(), g_pinned_tids.end(), tid);
    if (it != g_pinned_tids.end()) {
        g_pinned_tids.erase(it);
        if (g_main_cursor >= (int)g_pinned_tids.size()) g_main_cursor = std::max(0, (int)g_pinned_tids.size() - 1);
    } else {
        g_pinned_tids.push_back(tid);
    }
}

// ==========================================
// 4. DPI 回调
// ==========================================
extern "C" void kernel_monitor_tick(
    int n_threads, const svBitVecVal* pcs, const svBitVecVal* actives, 
    const svBitVecVal* starts, const svBitVecVal* aborts, const svBitVecVal* dones
) {
    CycleSnapshot snap;
    snap.cycle = g_cycle_count;
    
    // Auto-discover threads
    if (g_all_thread_names.size() < (size_t)n_threads) {
        for (size_t i = g_all_thread_names.size(); i < (size_t)n_threads; i++) {
            std::string name = "Thread_" + std::to_string(i);
            g_all_thread_names.push_back(name);
            g_thread_name_to_id[name] = i;
        }
    }

    for (int i = 0; i < n_threads; i++) {
        ThreadSnapshot t;
        t.id = i;
        t.name = g_all_thread_names[i];
        t.pc = pcs[i];
        t.active = (actives[i/32] >> (i%32)) & 1;
        t.done = (dones[i/32] >> (i%32)) & 1;
        t.step_name = get_step_name(t.name, t.pc);
        snap.threads.push_back(t);
    }
    g_history.push_back(snap);
    if (g_history.size() > MAX_HISTORY) g_history.pop_front();
}

// ==========================================
// 5. 仿真运行逻辑
// ==========================================
void step_simulation_cycle(VSimpleTop* top) {
     top->clock=1; top->eval(); top->clock=0; top->eval(); 
    g_cycle_count++;
}

// 智能步进：直到线程状态改变 或 断点触发
void run_until_thread_change(VSimpleTop* top, int pinned_idx) {
    if (g_history.empty() || pinned_idx >= (int)g_pinned_tids.size()) return;
    int tid = g_pinned_tids[pinned_idx];
    const auto& last_snap = g_history.back();
    if (tid >= (int)last_snap.threads.size()) return;
    const auto& t_start = last_snap.threads[tid];
    uint32_t start_pc = t_start.pc;
    bool start_active = t_start.active;

    if (t_start.done) { step_simulation_cycle(top); return; }

    int max_cycles = 5000;
    int cycles = 0;
    nodelay(stdscr, TRUE);
    bool changed = false;
    g_breakpoint_hit = false;

    while (!changed && cycles < max_cycles && !Verilated::gotFinish()) {
        step_simulation_cycle(top);
        cycles++;

        if (!g_history.empty()) {
            const auto& curr = g_history.back();
            
            // Check Breakpoints
            if (check_breakpoints(curr.threads)) {
                g_breakpoint_hit = true;
                break;
            }

            if (tid < (int)curr.threads.size()) {
                const auto& t_curr = curr.threads[tid];
                if (start_active) {
                    if (t_curr.pc != start_pc || t_curr.done || !t_curr.active) changed = true;
                } else {
                    if (t_curr.active) changed = true;
                }
            }
        }
        if (getch() == 'q') break; 
    }
    nodelay(stdscr, FALSE);
}

// 连续运行：直到断点触发
void run_continuous(VSimpleTop* top) {
    nodelay(stdscr, TRUE);
    g_breakpoint_hit = false;
    
    while (!Verilated::gotFinish()) {
        step_simulation_cycle(top);

        if (!g_history.empty()) {
             if (check_breakpoints(g_history.back().threads)) {
                g_breakpoint_hit = true;
                break;
            }
        }

        // Check user input to pause
        int ch = getch();
        if (ch == 'p' || ch == ' ') break; 

        // Update UI every ~50ms
        if (g_cycle_count % 100 == 0) {
             napms(1); // yield CPU
             // Note: We are not rendering here to keep speed up, rendering happens in main loop
        }
    }
    nodelay(stdscr, FALSE);
}


// ==========================================
// 6. UI 渲染模块
// ==========================================
WINDOW *win_side, *win_main, *win_cmd;
int scroll_offset = 0; 

bool is_driver_step(const std::string& name) {
    if (name.find("Reg_") == 0) return true;
    if (name.find("Mem_") == 0) return true;
    if (name.find("Driver") != std::string::npos) return true;
    return false;
}

void render_sidebar() {
    werase(win_side);
    box(win_side, 0, 0);
    mvwprintw(win_side, 0, 2, " Thread Repo ");

    int max_y, max_x;
    getmaxyx(win_side, max_y, max_x);
    int visible_lines = max_y - 2;
    int start_idx = 0;
    if (g_sidebar_cursor >= visible_lines) start_idx = g_sidebar_cursor - visible_lines + 1;

    for (int i = 0; i < visible_lines; i++) {
        int idx = start_idx + i;
        if (idx >= (int)g_all_thread_names.size()) break;

        std::string name = g_all_thread_names[idx];
        if (name.length() > (size_t)max_x - 6) name = name.substr(0, max_x - 6) + "..";
        bool pinned = is_pinned(idx);
        char marker = pinned ? '*' : ' ';
        if (g_focus_sidebar && idx == g_sidebar_cursor) wattron(win_side, A_REVERSE);
        if (pinned) wattron(win_side, COLOR_PAIR(3)); 
        mvwprintw(win_side, i + 1, 1, "%c %s", marker, name.c_str());
        if (pinned) wattroff(win_side, COLOR_PAIR(3));
        if (g_focus_sidebar && idx == g_sidebar_cursor) wattroff(win_side, A_REVERSE);
    }
    wrefresh(win_side);
}

void render_main() {
    werase(win_main);
    int max_y, max_x;
    getmaxyx(win_main, max_y, max_x);

    if (g_pinned_tids.empty()) {
        mvwprintw(win_main, max_y/2, max_x/2 - 15, "No threads monitored.");
        mvwprintw(win_main, max_y/2 + 1, max_x/2 - 20, "Select from Sidebar and press ENTER.");
        wrefresh(win_main); return;
    }

    const int COL_WIDTH = 20;
    const int TIMESTAMP_WIDTH = 8;
    wattron(win_main, A_BOLD | COLOR_PAIR(1));
    mvwprintw(win_main, 0, 0, "%-*s", TIMESTAMP_WIDTH, "CYCLE");
    for (size_t i = 0; i < g_pinned_tids.size(); i++) {
        int tid = g_pinned_tids[i];
        std::string name = (tid < (int)g_all_thread_names.size()) ? g_all_thread_names[tid] : "???";
        if (name.length() > COL_WIDTH-2) name = name.substr(0, COL_WIDTH-2);
        int x_pos = TIMESTAMP_WIDTH + i * COL_WIDTH;
        if (!g_focus_sidebar && (int)i == g_main_cursor) {
            wattron(win_main, A_REVERSE | COLOR_PAIR(6)); 
            mvwprintw(win_main, 0, x_pos, "| %-*s", COL_WIDTH-2, name.c_str());
            wattroff(win_main, A_REVERSE | COLOR_PAIR(6));
            wattron(win_main, A_BOLD | COLOR_PAIR(1)); 
        } else {
            mvwprintw(win_main, 0, x_pos, "| %-*s", COL_WIDTH-2, name.c_str());
        }
    }
    wattroff(win_main, A_BOLD | COLOR_PAIR(1));

    int history_idx = g_history.size() - 1 - scroll_offset;
    int screen_y = max_y - 1;
    while (history_idx >= 0 && screen_y >= 1) {
        const auto& snap = g_history[history_idx];
        const CycleSnapshot* prev_snap = (history_idx > 0) ? &g_history[history_idx-1] : nullptr;

        wattron(win_main, COLOR_PAIR(2));
        mvwprintw(win_main, screen_y, 0, "%-7lu", snap.cycle);
        wattroff(win_main, COLOR_PAIR(2));

        for (size_t i = 0; i < g_pinned_tids.size(); i++) {
            int tid = g_pinned_tids[i];
            int x_pos = TIMESTAMP_WIDTH + i * COL_WIDTH;
            mvwprintw(win_main, screen_y, x_pos, "| ");
            if (tid >= (int)snap.threads.size()) continue;
            const auto& t = snap.threads[tid];
            if (!t.active && !t.done) {
                wattron(win_main, A_DIM); wprintw(win_main, "."); wattroff(win_main, A_DIM);
            } else if (t.done) {
                wattron(win_main, COLOR_PAIR(5)|A_BOLD); wprintw(win_main, "DONE"); wattroff(win_main, COLOR_PAIR(5)|A_BOLD);
            } else {
                bool is_stalled = false;
                if (prev_snap && tid < (int)prev_snap->threads.size()) {
                    if (prev_snap->threads[tid].active && prev_snap->threads[tid].pc == t.pc) is_stalled = true;
                }
                if (is_stalled) {
                    wattron(win_main, A_DIM|COLOR_PAIR(4)); wprintw(win_main, ":"); wattroff(win_main, A_DIM|COLOR_PAIR(4));
                } else {
                    std::string dname = t.step_name.substr(0, COL_WIDTH-3);
                    if (is_driver_step(t.step_name)) {
                        wattron(win_main, COLOR_PAIR(3)); wprintw(win_main, "%s", dname.c_str()); wattroff(win_main, COLOR_PAIR(3));
                    } else {
                        wattron(win_main, A_BOLD); wprintw(win_main, "%s", dname.c_str()); wattroff(win_main, A_BOLD);
                    }
                }
            }
        }
        screen_y--; history_idx--;
    }
    wrefresh(win_main);
}

// ==========================================
// 7. 主循环与输入
// ==========================================
int main(int argc, char** argv) {
    Verilated::commandArgs(argc, argv);
    VSimpleTop* top = new VSimpleTop;

    scan_thread_names("generated/hwos.symbols");
    load_symbols("generated/hwos.symbols");
    if (g_all_thread_names.empty()) { 
        scan_thread_names("hwos.symbols"); load_symbols("hwos.symbols");
    }

    initscr(); cbreak(); noecho(); keypad(stdscr, TRUE);
    start_color(); use_default_colors(); curs_set(0);
    init_pair(1, COLOR_WHITE, COLOR_BLUE);
    init_pair(2, COLOR_YELLOW, -1);
    init_pair(3, COLOR_CYAN, -1);
    init_pair(4, COLOR_BLUE, -1);
    init_pair(5, COLOR_GREEN, -1);
    init_pair(6, COLOR_BLACK, COLOR_CYAN);
    init_pair(7, COLOR_RED, COLOR_WHITE); // Breakpoint Alert

    int max_y, max_x;
    getmaxyx(stdscr, max_y, max_x);
    int side_w = 35;
    win_side = newwin(max_y - 3, side_w, 0, 0);
    win_main = newwin(max_y - 3, max_x - side_w, 0, side_w);
    win_cmd  = newwin(3, max_x, max_y - 3, 0);

    top->reset = 1; top->io_start = 0;
    for(int i=0; i<5; i++) { top->clock=1; top->eval(); top->clock=0; top->eval(); }
    top->reset = 0;

    bool running = true;
    char cmd_buf[256] = {0};
    top->io_start = 1;
    step_simulation_cycle(top);
    top->io_start = 0;

    while(running) {
        render_sidebar();
        render_main();

        // --- Status Bar ---
        werase(win_cmd);
        if (g_breakpoint_hit) {
            wattron(win_cmd, A_BOLD | COLOR_PAIR(7));
            wprintw(win_cmd, "[BREAKPOINT] %s", g_hit_reason.c_str());
            wattroff(win_cmd, A_BOLD | COLOR_PAIR(7));
            wprintw(win_cmd, "  (Press Space to continue)");
        } else {
            wattron(win_cmd, A_BOLD);
            if (g_focus_sidebar) wprintw(win_cmd, "[SIDEBAR]");
            else wprintw(win_cmd, "[SCOPE]");
            wattroff(win_cmd, A_BOLD);
            wprintw(win_cmd, " | [b]:Break | [c]:Clear | [r]:Run | [s]:StepTh | [Spc]:Step");
            if (!g_breakpoints.empty()) wprintw(win_cmd, " | BPs: %lu", g_breakpoints.size());
        }
        wrefresh(win_cmd);

        int ch = getch();

        if (g_breakpoint_hit) {
            // 只允许特定键解除断点状态
            if (ch == ' ' || ch == 'r' || ch == 's') g_breakpoint_hit = false;
        }

        if (ch == 'q') running = false;
        else if (ch == '\t') g_focus_sidebar = !g_focus_sidebar;
        else if (ch == 'r') {
             run_continuous(top);
             scroll_offset = 0;
        }
        else if (ch == ' ') { 
            step_simulation_cycle(top);
            // Check breakpoints for single step too
            if (!g_history.empty() && check_breakpoints(g_history.back().threads)) {
                 g_breakpoint_hit = true;
            }
            scroll_offset = 0;
        }
        else if (ch == 'b') { // Add Breakpoint
            echo();
            wmove(win_cmd, 1, 0); wclrtobot(win_cmd);
            wprintw(win_cmd, "Expression (e.g. Thread1.pc == 2): ");
            wgetnstr(win_cmd, cmd_buf, 255);
            noecho();
            if (cmd_buf[0] != 0) add_breakpoint(std::string(cmd_buf));
        }
        else if (ch == 'c') { // Clear Breakpoints
            g_breakpoints.clear();
        }

        if (g_focus_sidebar) {
            if (ch == KEY_UP) { if (g_sidebar_cursor > 0) g_sidebar_cursor--; }
            else if (ch == KEY_DOWN) { if (g_sidebar_cursor < (int)g_all_thread_names.size()-1) g_sidebar_cursor++; }
            else if (ch == '\n' || ch == KEY_ENTER) { if (!g_all_thread_names.empty()) toggle_pin(g_sidebar_cursor); }
        } else {
            if (ch == KEY_LEFT) { if (g_main_cursor > 0) g_main_cursor--; }
            else if (ch == KEY_RIGHT) { if (g_main_cursor < (int)g_pinned_tids.size()-1) g_main_cursor++; }
            else if (ch == KEY_BACKSPACE || ch == 127 || ch == KEY_DC) {
                if (!g_pinned_tids.empty()) toggle_pin(g_pinned_tids[g_main_cursor]);
            }
            else if (ch == 's') { 
                run_until_thread_change(top, g_main_cursor);
                scroll_offset = 0;
            }
            else if (ch == KEY_UP) { if (scroll_offset < (int)g_history.size()-5) scroll_offset++; }
            else if (ch == KEY_DOWN) { if (scroll_offset > 0) scroll_offset--; }
        }
        
        if (Verilated::gotFinish()) {
            wprintw(win_cmd, " [FINISHED]");
            wrefresh(win_cmd);
        }
    }

    endwin();
    delete top;
    return 0;
}