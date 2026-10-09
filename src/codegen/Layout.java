package codegen;

import cfg.Cfg;
import util.*;

import java.util.List;

public class Layout {
    public final int vtablePtrOffsetInObject;

    // to record the size of a class
    private final Property<Id, Integer> sizeOfClassProp; // 使用方式类似查表，具体数据分散在各个对象上
    // to record the offset of class fields and methods
    private final Property<Id, Integer> offsetProp;

    Layout() {
        this.vtablePtrOffsetInObject = 0; // 说明 对象开头第一个机器字给了虚表指针，对象里放虚表指针，虚表里放具体函数指针
        this.sizeOfClassProp = new Property<>(Id::getPlist);
        this.offsetProp = new Property<>(Id::getPlist);
    }

    private Object doitProgram0(Cfg.Program.T cfg) {
        // 解构 CFG，取出之前 CFG 解析的 Structs、Vtables
        switch (cfg) {
            case Cfg.Program.Singleton(_, _, var vtables, var structs, _) -> {
                int wordSize = X64.WordSize.bytesOfWord;
                for (var s : structs) {
                    switch (s) {
                        case Cfg.Struct.Singleton(var classId, var fields) -> {
                            int offset = vtablePtrOffsetInObject + wordSize; // 在这里直接加上，预留 vptr 空间
                            for (var field : fields) {
                                switch (field) {
                                    case Cfg.Dec.Singleton(_, var fieldId) ->
                                            offsetProp.put(fieldId, offset);
                                }
                                offset += wordSize;
                            }
                            sizeOfClassProp.put(classId, offset);
                        }
                    }
                }
                for (var vtable : vtables) {
                    switch (vtable) {
                        case Cfg.Vtable.Singleton(_, var entries) -> {
                            int offset = 0;
                            for (var entry : entries) {
                                offsetProp.put(entry.functionId(), offset);
                                offset += wordSize;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    public Tuple.Two<Property<Id, Integer>,
            Property<Id, Integer>> doitProgram(Cfg.Program.T cfg) {
        switch (cfg) {
            case Cfg.Program.Singleton(
                    Id entryClassId,
                    Id entryFuncId,
                    List<Cfg.Vtable.T> vtables,
                    List<Cfg.Struct.T> structs,
                    List<Cfg.Function.T> functions
            ) -> {
                Trace<Cfg.Program.T, Object> trace =
                        new Trace<>("codegen.Layout.doitProgram",
                                this::doitProgram0,
                                cfg,
                                Cfg.Program::pp,
                                (_) -> {
                                    // 调试输出，打印布局
                                    structs.forEach((s) -> {
                                        switch (s) {
                                            case Cfg.Struct.Singleton(
                                                    Id clsId,
                                                    List<Cfg.Dec.T> fields
                                            ) -> {
                                                System.out.println("class " + clsId.toString() + " size = " + sizeOfClassProp.get(clsId).toString());
                                                for (var entry : fields) {
                                                    switch (entry) {
                                                        case Cfg.Dec.Singleton(_, var fieldId) ->
                                                                System.out.println("    field " + fieldId + " offset = " + offsetProp.get(fieldId));
                                                    }
                                                }
                                            }
                                        }
                                    });
                                    //
                                    vtables.forEach((s) -> {
                                        switch (s) {
                                            case Cfg.Vtable.Singleton(var classId, var entries) -> {
                                                System.out.println("vtable " + classId);
                                                for (var entry : entries) {
                                                    System.out.println("    method " + entry.classId() + "_" + entry.functionId()
                                                            + " offset = " + offsetProp.get(entry.functionId()));
                                                }
                                            }
                                        }
                                    });
                                });
                trace.doit();
                return new Tuple.Two<>(sizeOfClassProp, offsetProp);
            }
        }
    }
}

