package codegen;

import cfg.Cfg;
import util.*;

import java.util.List;

public class Layout {
    
    public static final int WORD_BYTES = X64.WordSize.bytesOfWord;

    public static final int VPTR_OFFSET = 0;  // vptr
    public static final int LENGTH_OFFSET = WORD_BYTES; // array length
    public static final int FORWARDING_OFFSET = 2 * WORD_BYTES; // forwarding，新地址
    public static final int HEADER_BYTES = 3 * WORD_BYTES;

    public static final int ARRAY_ELEMENTS_OFFSET = HEADER_BYTES; // 数组元素起始位置和对象一样
    public static final long NON_ARRAY_LENGTH = -1L; // 区分 数组和对象，复用 array length

    // vptr 指向虚表起点：首槽为 GC map 地址，随后才是方法地址。
    public static final int CLASS_GC_MAP_OFFSET = 0;
    public static final int VTABLE_METHODS_OFFSET = WORD_BYTES;

    public final int vtablePtrOffsetInObject;

    // to record the size of a class
    private final Property<Id, Integer> sizeOfClassProp; // 使用方式类似查表，具体数据分散在各个对象上
    // to record the offset of class fields and methods
    private final Property<Id, Integer> offsetProp;
    // 类 Id -> class gc map 字符串
    private final Property<Id, String> classGcMapProp;

    Layout() {
        this.vtablePtrOffsetInObject = VPTR_OFFSET;
        this.sizeOfClassProp = new Property<>(Id::getPlist);
        this.offsetProp = new Property<>(Id::getPlist);
        this.classGcMapProp = new Property<>(Id::getPlist);
    }

    // doitProgram 完成后查询；保留原来的大小/偏移返回接口，供 Munch 使用。
    public String getClassGcMap(Id classId) {
        return classGcMapProp.get(classId);
    }

    // 根据字段类型决定是否追踪
    private char gcMark(Cfg.Type.T type) {
        return switch (type) {
            case Cfg.Type.ClassType(_), Cfg.Type.IntArray() -> 'Y'; // 类引用和数组引用
            case Cfg.Type.Int(), Cfg.Type.CodePtr() -> 'N'; // 布尔和int类型
        };
    }

    private Object doitProgram0(Cfg.Program.T cfg) {
        // 解构 CFG，取出之前 CFG 解析的 Structs、Vtables
        switch (cfg) {
            case Cfg.Program.Singleton(_, _, var vtables, var structs, _) -> {
                int wordSize = WORD_BYTES;
                for (var s : structs) {
                    switch (s) {
                        case Cfg.Struct.Singleton(var classId, var fields) -> {
                            int offset = HEADER_BYTES;
                            StringBuilder gcMap = new StringBuilder(); // 逐步拼接字符串的类
                            for (var field : fields) {
                                switch (field) {
                                    case Cfg.Dec.Singleton(var type, var fieldId) -> {
                                        offsetProp.put(fieldId, offset);
                                        gcMap.append(gcMark(type));
                                    }
                                }
                                offset += wordSize;
                            }
                            sizeOfClassProp.put(classId, offset);
                            classGcMapProp.put(classId, gcMap.toString());
                        }
                    }
                }
                for (var vtable : vtables) {
                    switch (vtable) {
                        case Cfg.Vtable.Singleton(_, var entries) -> {
                            int offset = VTABLE_METHODS_OFFSET;
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
                                    System.out.println("object header size = " + HEADER_BYTES);
                                    System.out.println("    vptr offset = " + VPTR_OFFSET);
                                    System.out.println("    length offset = " + LENGTH_OFFSET
                                            + " (object = " + NON_ARRAY_LENGTH + ", array >= 0)");
                                    System.out.println("    forwarding offset = " + FORWARDING_OFFSET
                                            + " (initially null)");
                                    System.out.println("int[] elements offset = " + ARRAY_ELEMENTS_OFFSET
                                            + ", size = " + HEADER_BYTES + " + length * " + WORD_BYTES
                                            + ", vptr = null, no reference elements");
                                    structs.forEach((s) -> {
                                        switch (s) {
                                            case Cfg.Struct.Singleton(
                                                    Id clsId,
                                                    List<Cfg.Dec.T> fields
                                            ) -> {
                                                System.out.println("class " + clsId.toString() + " size = " + sizeOfClassProp.get(clsId).toString());
                                                System.out.println("    gcMap = \"" + getClassGcMap(clsId) + "\" (user fields only)");
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
                                                System.out.println("    class GC map address offset = " + CLASS_GC_MAP_OFFSET
                                                        + " -> \"" + getClassGcMap(classId) + "\"");
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
