# --- Makefile ---
VERILATOR = verilator
VERILATOR_FLAGS = -cc --exe -O3 --build --trace

# 替换为你的 Verilog 顶层文件和模块名
V_TOP_FILE = ../generated/TopModule.sv
V_TOP_MOD  = TopModule

# C++ 源文件
CPP_SRCS = main.cpp SymbolParser.cpp SimEngine.cpp UIManager.cpp

all: hwosgdb

hwosgdb: $(CPP_SRCS) $(V_TOP_FILE)
	$(VERILATOR) $(VERILATOR_FLAGS) $(V_TOP_FILE) $(CPP_SRCS) \
	-LDFLAGS "-lncurses" \
	--top-module $(V_TOP_MOD)
	cp obj_dir/V$(V_TOP_MOD) ./hwosgdb

clean:
	rm -rf obj_dir hwosgdb