module ForkNonBlockingModule_SV (
    input  logic        clock,
    input  logic        reset,
    input  logic        io_start,
    output logic [31:0] io_parentPC,
    output logic [31:0] io_mainReg,
    output logic [31:0] io_childReg,
    output logic        io_done
);

    // ============================================================
    // 寄存器定义
    // ============================================================
    logic [31:0] mainReg;
    logic [31:0] accReg;
    
    // 状态指针 (PC)
    // Parent States: 0=Fork, 1=OpA, 2=OpB, 3=OpC, 4=Wait, 5=ISR
    logic [2:0]  parent_pc; 
    logic        parent_active;

    // Child States: 0=Idle, 1=Load, 2=Compute, 3=Store
    logic [1:0]  child_pc;
    logic        child_active;
    logic        child_done_pulse; // 子线程完成脉冲

    // ============================================================
    // Child FSM (Accelerator)
    // ============================================================
    always_ff @(posedge clock) begin
        if (reset) begin
            child_pc     <= 0;
            child_active <= 0;
            accReg       <= 0;
            child_done_pulse <= 0;
        end else begin
            // 默认拉低脉冲
            child_done_pulse <= 0;

            if (child_active) begin
                case (child_pc)
                    2'd0: begin // Load Step
                        accReg   <= 32'd10;
                        child_pc <= 2'd1;
                    end
                    2'd1: begin // Compute Step
                        accReg   <= accReg * 32'd2; // 20
                        child_pc <= 2'd2;
                    end
                    2'd2: begin // Store Step & Exit
                        accReg   <= accReg + 32'd5; // 25
                        child_active <= 0; // Exit
                        child_pc     <= 0;
                        child_done_pulse <= 1; // Trigger Callback
                    end
                endcase
            end else begin
                // Fork Trigger Condition
                // 当父线程处于 State 0 且 Active 时触发
                if (parent_active && parent_pc == 3'd0) begin
                    child_active <= 1;
                    child_pc     <= 0; // Start at Load
                end
            end
        end
    end

    // ============================================================
    // Parent FSM (CPU)
    // ============================================================
    always_ff @(posedge clock) begin
        if (reset) begin
            parent_pc     <= 0;
            parent_active <= 0;
            mainReg       <= 0;
        end else begin
            if (parent_active) begin
                // --- Callback Logic (Highest Priority) ---
                // HwOS 中的 Callback 具有注入特性，会覆盖当前逻辑
                if (child_done_pulse) begin
                    mainReg   <= accReg; // Callback payload
                    parent_pc <= 3'd5;   // Force Jump to ISR
                end 
                else begin
                    // --- Normal Execution Flow ---
                    case (parent_pc)
                        3'd0: begin // Step 0: Fork
                            // Child is triggered by monitoring logic above
                            parent_pc <= 3'd1;
                        end
                        3'd1: begin // Step 1: Op A
                            mainReg   <= mainReg + 1;
                            parent_pc <= 3'd2;
                        end
                        3'd2: begin // Step 2: Op B
                            mainReg   <= mainReg + 1;
                            parent_pc <= 3'd3;
                        end
                        3'd3: begin // Step 3: Op C
                            mainReg   <= mainReg + 1;
                            parent_pc <= 3'd4;
                        end
                        3'd4: begin // Step 4: Wait Loop
                            // waitCondition(false.B) -> Stick here
                            // 除非被上面的 child_done_pulse 强制跳转
                            parent_pc <= 3'd4; 
                        end
                        3'd5: begin // Step 5: ISR / Exit
                            parent_active <= 0; // exit()
                            parent_pc     <= 0;
                        end
                        default: parent_pc <= 0;
                    endcase
                end
            end else begin
                // Start Trigger
                if (io_start) begin
                    parent_active <= 1;
                    parent_pc     <= 0;
                    mainReg       <= 0; // Reset mainReg on start logic
                end
            end
        end
    end

    // ============================================================
    // Output Connections
    // ============================================================
    assign io_parentPC = {29'b0, parent_pc};
    assign io_mainReg  = mainReg;
    assign io_childReg = accReg;
    
    // HwOS 的 done 是一个组合逻辑脉冲信号，当线程调用 exit() 的那一拍拉高
    assign io_done     = parent_active && (parent_pc == 3'd5);

endmodule