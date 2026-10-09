package codegen;

import cfg.Cfg;
import util.Id;
import util.Label;
import util.Property;
import util.Todo;
import util.Trace;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

public class Munch {
    // all parameters and locals in a function
    private HashMap<Id, X64.Type.T> allVars;
    // current locals, we will append freshly generated locals into it
    private List<X64.Dec.T> currentLocals;
    // points to the current instruction list in a x64 block
    private List<X64.Instr.T> currentInstrs;

    // Layout 的结果：类大小，以及字段/方法槽位的字节偏移。
    private Layout layout;
    private Property<Id, Integer> sizeOfClassProp;
    private Property<Id, Integer> offsetProp;
    // 当前函数中，CFG 的标签对应哪个 X64 块。
    private HashMap<Label, X64.Block.Singleton> blockMap;

    // /////////////////////////////////////////////////////////
    // type and declaration: 保留类型和原有 Id，只更换 IR 节点类型。
    private X64.Type.T doitType(Cfg.Type.T type) {
        return switch (type) {
            case Cfg.Type.Int() -> new X64.Type.Int();
            case Cfg.Type.ClassType(var id) -> new X64.Type.ClassType(id);
            case Cfg.Type.IntArray() -> new X64.Type.IntArray();
            case Cfg.Type.CodePtr() -> new X64.Type.CodePtr();
        };
    }

    private X64.Dec.T doitDec(Cfg.Dec.T dec) {
        return switch (dec) {
            case Cfg.Dec.Singleton(var type, var id) ->
                    new X64.Dec.Singleton(doitType(type), id);
        };
    }

    private List<X64.Dec.T> doitDecList(List<Cfg.Dec.T> decs) {
        return new LinkedList<>(decs.stream().map(this::doitDec).toList());
    }

    private void registerVars(List<X64.Dec.T> decs) {
        for (var dec : decs) {
            switch (dec) {
                case X64.Dec.Singleton(var type, var id) -> allVars.put(id, type);
            }
        }
    }

    // /////////////////////////////////////////////////////////
    // instruction helpers: 类似 cfg.Translate 的 emit/newTemp。
    private void emit(X64.Instr.T instr) {
        currentInstrs.add(instr);
    }

    private X64.VirtualReg.T vid(Id id) {
        X64.Type.T type = allVars.get(id);
        if (type == null)
            throw new IllegalStateException("undeclared variable: " + id);
        return new X64.VirtualReg.Vid(id, type);
    }

    private X64.VirtualReg.T newTemp(X64.Type.T type) {
        Id id = Id.newNoname();
        currentLocals.add(new X64.Dec.Singleton(type, id));
        allVars.put(id, type);
        return vid(id);
    }

    // /////////////////////////////////////////////////////////
    // expression: 向 currentInstrs 发射指令，将表达式结果写入 dst。
    private void doitExp(Cfg.Exp.T exp, X64.VirtualReg.T dst) {
        switch (exp) {
            case Cfg.Exp.Int(var n) ->
                    throw new Todo("Int: move immediate to dst");
            case Cfg.Exp.Eid(var id, _) ->
                    throw new Todo("Eid: move vid(id) to dst");
            case Cfg.Exp.Bop(var op, var operands, _) ->
                    throw new Todo("Bop: select arithmetic/comparison instructions");
            case Cfg.Exp.GetField(var object, var field) ->
                    throw new Todo("GetField: load from object + offsetProp.get(field)");
            case Cfg.Exp.GetMethod(var object, _, var method) ->
                    throw new Todo("GetMethod: load vptr, then load method slot");
            case Cfg.Exp.Call(var func, var args, _) ->
                    throw new Todo("Call: pass arguments, indirect call, copy return value");
            case Cfg.Exp.New(var classId) ->
                    throw new Todo("New: call Tiger_new with class size and vtable address");
            case Cfg.Exp.NewIntArray(var size) ->
                    throw new Todo("NewIntArray: call Tiger_newIntArray");
            case Cfg.Exp.ArraySelect(var array, var index) ->
                    throw new Todo("ArraySelect: load element using the runtime array layout");
            case Cfg.Exp.Length(var array) ->
                    throw new Todo("Length: load array length using the runtime array layout");
            case Cfg.Exp.Print(var value) ->
                    throw new Todo("Print: call Tiger_print and define the unused dst");
        }
    }

    // statement: Assign 交给表达式翻译；内存写入单独处理。
    private void doitStm(Cfg.Stm.T stm) {
        switch (stm) {
            case Cfg.Stm.Assign(var id, var exp) -> doitExp(exp, vid(id));
            case Cfg.Stm.PutField(var object, var field, var value) ->
                    throw new Todo("PutField: store at object + offsetProp.get(field)");
            case Cfg.Stm.AssignArray(var array, var index, var value) ->
                    throw new Todo("AssignArray: store using the runtime array layout");
        }
    }

    // transfer: 比较和返回值搬运放在 instrs 中，跳转关系放在 Transfer 中。
    private X64.Transfer.T doitTransfer(Cfg.Transfer.T transfer) {
        return switch (transfer) {
            case Cfg.Transfer.Jmp(var target) ->
                    new X64.Transfer.Jmp(blockMap.get(Cfg.Block.getLabel(target)));
            case Cfg.Transfer.If(var condition, var trueBlock, var falseBlock) ->
                    throw new Todo("If: compare condition with zero, then connect both target blocks");
            case Cfg.Transfer.Ret(var value) ->
                    throw new Todo("Ret: move value to %rax, emit function epilogue, then Ret()");
        };
    }

    private void doitBlock(Cfg.Block.T block) {
        switch (block) {
            case Cfg.Block.Singleton(var label, var stms, var transfers) -> {
                X64.Block.Singleton result = blockMap.get(label);
                currentInstrs = result.instrs();
                stms.forEach(this::doitStm);
                result.transfer().add(doitTransfer(transfers.getFirst()));
            }
        }
    }

    // /////////////////////////////////////////////////////////
    // function: 每个函数重新建立变量表和块映射。
    private void emitFunctionEntry(List<X64.Dec.T> formals) {
        // TODO: 函数序言、保存约定要求的寄存器、接收参数。
        // 前六个整数/引用参数来自参数寄存器，其余来自调用者的栈。
        // 序言/尾声与后续寄存器分配阶段的栈帧处理需要统一约定。
        throw new Todo("function entry and parameter passing");
    }

    private X64.Function.T doitFunction(Cfg.Function.T function) {
        return switch (function) {
            case Cfg.Function.Singleton(var retType, var classId, var methodId,
                                        var formals, var locals, var blocks) -> {
                // 处理新函数时候创建：变量 Id → X64 类型, 块的 Label → 对应的 X64 块
                // 做查询，指令中只存在 id
                allVars = new HashMap<>();
                blockMap = new HashMap<>();

                // 参数和局部变量 登记到 allVars
                var newFormals = doitDecList(formals);
                currentLocals = doitDecList(locals);
                registerVars(newFormals);
                registerVars(currentLocals);

                List<X64.Block.T> newBlocks = new LinkedList<>();
                
                // 函数入口保留位置
                var entry = new X64.Block.Singleton(new Label(),
                        new LinkedList<>(), new LinkedList<>());
                newBlocks.add(entry);

                // 第一遍先建空块，使前向跳转和循环回边都能找到目标。
                for (var block : blocks) {
                    Label label = Cfg.Block.getLabel(block);
                    var result = new X64.Block.Singleton(label, new LinkedList<>(), new LinkedList<>());
                    blockMap.put(label, result);
                    newBlocks.add(result);
                }

                // 入口块处理
                currentInstrs = entry.instrs();
                emitFunctionEntry(newFormals);
                entry.transfer().add(new X64.Transfer.Jmp(blockMap.get(Cfg.Block.getLabel(blocks.getFirst()))));

                // 第二遍填充指令和跳转。
                blocks.forEach(this::doitBlock);
                yield new X64.Function.Singleton(doitType(retType), classId, methodId,
                        newFormals, currentLocals, newBlocks);
            }
        };
    }

    private X64.Struct.T doitStruct(Cfg.Struct.T struct) {
        return switch (struct) {
            case Cfg.Struct.Singleton(var classId, var fields) ->
                    new X64.Struct.Singleton(classId, doitDecList(fields));
        };
    }

    private X64.Vtable.T doitVtable(Cfg.Vtable.T vtable) {
        return switch (vtable) {
            case Cfg.Vtable.Singleton(var classId, var entries) ->
                    new X64.Vtable.Singleton(classId, entries.stream().map(e -> e.classId() + "_" + e.functionId()).toList());
        };
    }

    // /////////////////////////////////////////////////////////
    // program: 先计算布局，再逐层构造 X64 节点。
    private X64.Program.T doitProgram0(Cfg.Program.T cfg) {
        layout = new Layout();
        var properties = layout.doitProgram(cfg);
        sizeOfClassProp = properties.first();
        offsetProp = properties.second();

        return switch (cfg) {
            case Cfg.Program.Singleton(var MainClassId, var MainFuncId,
                                       var vtables, var structs, var functions) ->
                    new X64.Program.Singleton(MainClassId, MainFuncId,
                            vtables.stream().map(this::doitVtable).toList(),
                            structs.stream().map(this::doitStruct).toList(),
                            functions.stream().map(this::doitFunction).toList());
        };
    }

    public X64.Program.T doitProgram(Cfg.Program.T cfg) {
        Trace<Cfg.Program.T, X64.Program.T> trace =
                new Trace<>("codegen.Munch.munchProgram",
                        this::doitProgram0,
                        cfg,
                        Cfg.Program::pp, // 打印输入
                        X64.Program::pp); // 打印输出
        return trace.doit();
    }
}
