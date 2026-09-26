# Tiger：实验内容、编译、运行与测试

## 编译 Tiger

检查环境：

```sh
java -version
javac -version
```

使用 gradle 进行编译，jar会自动先执行需要的编译。

```
因为会使用 ~/.gradle 存缓存，
./gradlew jar
```

运行

```
java -cp build/libs/tiger-1.0.jar Tiger benchmark/Factorial.java
```

-cp 是指定去哪里找编译的类，所以后面要加 .jar 的那个玩意，Tiger是入口类名字，然后是传给Tiger的参数，要编译的程序路径。

调试参数：`-trace`，是 Tiger 自己定义的调试参数，用来打印某个处理阶段的输入和输出。

```
java -cp build/libs/tiger-1.0.jar Tiger benchmark/Factorial.java -trace cfg.Translate.doitProgram
```

## Lab 2

对于 Type Check 来说，需要建立 class table 和 method table。其中根据需要我们需要设计：class 要记录父类、field、方法签名。针对函数，我们需要记录返回值、参数、函数id。

然后至于子类需不需要在当前class里面继承父类的field记录，也是根据人设计的，以及重载问题怎么解决的，都需要设计。

为什么做 type check 需要用到这些，因为要核对这些类的使用正不正确，是否存在。

以一个最为简单的例子来说：

```
class A {
    int i;
    int j;
    public int f() { return j; }
}

class B extends A {
    int i;
    int b_k;
    // 定义了 g()、k()、f()
}
```

## Lab 3

### Exercise 1 阅读 CFG，补充代码
