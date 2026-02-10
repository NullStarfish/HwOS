// ThreadTestModule_Baseline.sv
module ThreadTestModule_Baseline (
    input  logic        clk,
    input  logic        reset,
    input  logic        startTrigger,
    input  logic        flagA,
    input  logic        flagB,
    output logic [31:0] counterValue,
    output logic [31:0] pcValue,
    output logic        isRunning,
    output logic        isDone
);

    // 状态定义 (对应你代码中的 Step Index)
    typedef enum logic [2:0] {
        IDLE       = 3'd0,
        INIT       = 3'd1,
        WAIT_A     = 3'd2,
        ADD_ACT    = 3'd3,
        JUMP_CHECK = 3'd4,
        EXIT       = 3'd5
    } state_t;

    state_t state;
    logic [31:0] counter;
    logic active_reg;

    assign counterValue = counter;
    assign pcValue      = {29'b0, state}; // 暴露状态作为 PC
    assign isRunning    = active_reg;
    assign isDone       = (state == EXIT);

    always_ff @(posedge clk) begin
        if (reset) begin
            state <= IDLE;
            counter <= 32'd0;
            active_reg <= 1'b0;
        end else begin
            case (state)
                IDLE: begin
                    if (startTrigger) begin
                        state <= INIT;
                        active_reg <= 1'b1;
                    end
                end

                INIT: begin // Index 0
                    counter <= 32'd0;
                    state <= WAIT_A;
                end

                WAIT_A: begin // Index 1
                    // waitCondition(flagA)
                    if (flagA) begin
                        state <= ADD_ACT;
                    end
                end

                ADD_ACT: begin // Index 2
                    // waitAndAct(flagB)
                    if (flagB) begin
                        counter <= counter + 1'b1;
                        state <= JUMP_CHECK;
                    end
                    // 隐含：!flagB 则留在当前状态
                end

                JUMP_CHECK: begin // Index 3
                    // Loop 逻辑
                    if (counter < 32'd3) begin
                        state <= ADD_ACT;
                    end else begin
                        state <= EXIT;
                    end
                end

                EXIT: begin // Index 4
                    active_reg <= 1'b0;
                    state <= IDLE;
                end

                default: state <= IDLE;
            endcase
        end
    end

endmodule