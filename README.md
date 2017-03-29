# 前言

[原文地址：信号量与PV操作的Java讲解](https://yasicyu.com/newarticle/%E4%BF%A1%E5%8F%B7%E9%87%8F%E4%B8%8EPV%E6%93%8D%E4%BD%9C%E7%9A%84Java%E8%AE%B2%E8%A7%A3)

信号量（Semaphore）是由 Edsger Dijkstra 在 设计 THE Multiprogramming System 时提出的一种概念，用以解决进程通信或线程通信时的同步与互斥问题，主要包含两种操作， P 操作和 V 操作。

提到信号量就必须提到并发编程，并发是一种在时间上将多个逻辑控制流进行重叠的机制，也是现代计算机系统最显著和重要的特性。

程序计数器中指令的地址的过渡称为控制转移，控制转移的序列称为处理器的控制流。操作系统利用进程为每一个应用程序提供了一种独占处理器的假象，表现在时间上就是多个逻辑控制流的重叠，也即并发。更准确的定义是，逻辑控制流 X 和流 Y 互相并发，当且仅当 X 在 Y 开始之后和 Y 结束之前开始，或 Y 在 X 开始之后和 X 结束之前开始。

使用应用级并发的应用程序称为并发程序，现代操作系统提供了三种基本的构造并发程序的方法：

* 进程 由内核调度的逻辑控制流，进程间利用 IPC 进程间通信机制进行通信，共享数据很困难
* I/O 多路复用 应用创建自己的逻辑流，共享同一个进程的虚拟地址空间，并利用 I/O 多路复用来显式调用流，因此编码比较复杂，而且不能充分利用多核处理器
* 线程 运行在进程上下文中的控制流，由内核自动调度，共享所属进程的整个虚拟地址空间，结合了上面两种方法的特性

# 多线程与共享变量

在基于多线程的并发编程中绕不开对共享变量的使用。一个变量是共享的，当且仅当这个变量的实例被一个以上的线程引用。

一组并发线程运行在一个进程的上下文中，每一个线程都有自己的独立线程上下文，包括线程 ID、栈、栈指针、程序计数器 PC以及通用目的寄存器。每一个线程和其他线程一起共享进程上下文的剩余部分，包括用户虚拟地址空间和进程打开的文件集合。寄存器从不共享，但虚拟存储器总是共享。

共享变量对于多线程协作是非常方便的，但也因此带来了一些棘手的问题，主要有两方面：

* 互斥问题
* 同步问题

## 互斥问题

先从一个简单的例子出发。假设我们有一个银行 Bank，银行中预存了 10000 元钱，有两个人分别需要向银行存 10000 元钱，钱比较多，因此不能立刻存进去，我们可以用两个线程完成这个操作，我们的预期目标应该是最终银行里会有 30000 元钱。

首先我们定义一个银行 Bank。

```Java
public class Bank {
    private int money = 0;

    public Bank(int money){
        this.money = money;
    }

    public void addMoney(){
        this.money++;
    }

    public int readMoney(){
        return this.money;
    }
}
```

Bank 提供两个方法，addMoney 将银行的金额自增 1，而 readMoney 读出银行现在的总金额。

接下来，实现一个线程用来存钱。Java中实现线程的方法有三种：

* 继承 Thread 类
* 实现 Runnable 接口
* 实现 Callable 接口

一般来说实现接口比继承类的拓展性更强，这里我们采取第二种方式实现一个 AddMoneyThread 类。

```Java
public class AddMoneyThread implements Runnable {
    private Bank bank;
    private int addMoneyCount;
    public int id;

    public AddMoneyThread(Bank bank, int addMoneyCount, int id) {
        this.bank = bank;
        this.addMoneyCount = addMoneyCount;
        this.id = id;
    }

    @Override
    public void run() {
        if (bank != null) {
            int index = 1;
            while (index <= this.addMoneyCount) {
                bank.addMoney();
                index++;
            }
        }
    }
}
```

可以看到，在构造方法里我们传入了一个 Bank 实例，以及需要存入的金额，在 run 方法里，我们循环调用 Bank 实例的 addMoney 方法增加 Bank 的金额。

接下来是主类，主要是创建并执行线程。

```Java
public class MutualExclusion {

    public static void main(String[] args) {
        for (int i = 0; i < 5; i++) {
            Bank bank = new Bank(10000);
            new Thread(new AddMoneyThread(bank, 10000, 1)).start();
            new Thread(new AddMoneyThread(bank, 10000, 2)).start();
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                System.out.println("第" + (i + 1) + "次" + bank.readMoney());
            }
        }
    }
}
```

在这里可以看到我们初始化 Bank 的金额为 10000，而我们创建并执行了两个 AddMoneyThread 线程，分别向 Bank 里存入 10000 元钱。为了对比效果，我们将这个过程执行了 5 次。

最终运行的结果如下:

```
第1次:20949
第2次:22487
第3次:22481
第4次:22209
第5次:22425
```

可以看到，并不是我们期望的 30000 元，而且每一次执行的结果还不一样。

那么其中到底哪里出了问题呢？

并发编程最大的缺点就是很难复现每一次执行过程，对于多个线程，我们无法预测下一次的执行顺序，也不能依据某种执行顺序的假设来设计我们的代码。仔细分析线程部分代码，我们不加保护地进行了如下操作

```Java
bank.addMoney();

在 Bank 类中
public void addMoney(){
	this.money++;
}
```

其实这里对于 money 变量自增一的操作就带来了线程安全问题。

> 根据《Java Concurrency in Practice》的定义，一个线程安全的 class 应当满足以下三个条件：
• 多个线程同时访问时，其表现出正确的行为。
• 无论操作系统如何调度这些线程， 无论这些线程的执行顺序如何交织（interleaving）。
• 调用端代码无须额外的同步或其他协调动作。

而这里对 money 自增一的操作并不是原子操作，也就是说不是 __不可分割的操作__ ，之所以这样说是因为在 Java 中变量自增一其实经历了 "读取-修改-写入" 三个步骤

* 读取：读取 money 变量的值
* 修改：修改 值为原值加一
* 写入：将计算结果写回到 money 变量中

那么这里带来的线程安全问题就是，假如有两个线程 A 和 B 同时调用 addMoney 函数时，A完成了读取和修改步骤时，假设 money 等于 10，被 A 修改后为 11 了，然后 B 完成了读取步骤，因为 A 尚未写回新的值，B 读到 money 依然为 10，于是 B 将值增一，而后 A 和 B 分别写回 money ，结果 money 值变为了 11，凭空丢失掉了一次自增一操作。

具体可以看下图

|序列|线程|步骤|money|
|--|--|--|--|
|1|A|读取|10|
|2|A|修改|10|
|3|B|读取|10|
|4|B|修改|11|
|5|A|写入|11|
|6|B|写入|11|

这样的步骤是完全无法预测的，因而结果也就不尽相同了。究其原因，还是因为 Bank 实例只有一个，money 也只有一个，两个线程同时访问并修改共享变量时互相之间的行为是互斥的，它们互相竞争对资源的读写访问控制，这一类问题被称为并发编程的 __互斥问题__。

## 同步问题

再说一个不一样的例子。现在我们有一个仓库 Repository，Repository 的大小为 10， 有 A 和 B 两个人，A 负责向 Repsitory 里存入 10000 份货物 Goods，B 负责从 Repository 里拿出所有的 Goods。我们可以用两个线程分别完成 A 和 B 的工作，我们的预期目标是最终 B 应该可以拿出来 10000 份货物。

当然很明显，这里仓库的大小相对于 A 要存入的货物量是非常小的，所以 A 有可能遇到仓库放满了货物而不能存入的情景，而 B 则可能遇到 仓库里没有货物可以取出的情景，让我们看看会出现什么问题吧。

首先让我们定义一个仓库 Repository

```Java
public class Repository {
    private Goods[] goodsArray;
    private int usedSize = 0;

    public Repository(int goodSize){
        goodsArray = new Goods[goodSize];
    }

    public void saveGood(int goodId) {
        if (usedSize < goodsArray.length) {
            goodsArray[usedSize] = new Goods(goodId);
            usedSize++;
        }
    }

    public Goods takeGood(){
        if (usedSize == 0){
            return null;
        }
        return goodsArray[--usedSize];
    }
}
```

Repository 提供了两个方法，saveGoood 用来向仓库存放一份货物，而 takeGood 用来拿出一份 Reposotory 存放的货物。这里我们使用了一个数组存放货物，数组大小在构建时确定其大小，同时我们需要一个哨兵变量 usedSize 来维护数组中真正存放了货物的大小。

接下来是存放货物的线程

```Java
public class SaveGoodThread implements Runnable{
    private Repository repository;
    private int saveNumber = 0;

    public SaveGoodThread(Repository repository, int saveNumber){
        this.repository = repository;
        this.saveNumber = saveNumber;
    }

    @Override
    public void run(){
        if (repository != null){
            int i = 0;
            while (i < this.saveNumber){
                repository.saveGood(i + 1);
                i++;
            }
        }
    }
}
```

然后是拿取货物的线程

```Java
public class TakeGoodThread implements Runnable {
    private int number = 0;
    private List<Goods> goodsList = null;
    private Repository repository;

    public TakeGoodThread(Repository repository, List<Goods> goodsList) {
        this.repository = repository;
        this.goodsList = goodsList;
    }

    @Override
    public void run() {
        while (true) {
            Goods temp = repository.takeGood();
            number++;
            if (temp != null){
                goodsList.add(temp);
            }
        }
    }
}
```

在这里要注意我们取出的是有效货物，因此当仓库返回的货物是 null 的时候我们就跳过。我们最终会把货物转存进一个 List 中，这个 goodsList 在构造函数中设置，是由主函数调用并传入的。

下面就是我们的主函数部分。

```Java
public class Synchronization {
    public static void main(String[] args) {
        for (int i = 0; i < 5; i++){
            Repository repository = new Repository(10);
            List<Goods> goodsList = new ArrayList<>();
            Thread saveGood = new Thread(new SaveGoodThread(repository, 10000));
            Thread takeGood = new Thread(new TakeGoodThread(repository, goodsList));
            saveGood.start();
            takeGood.start();

            try {
                Thread.sleep(2000);
                saveGood.suspend();
                takeGood.suspend();
                System.out.println("第" + (i + 1) + "次" + goodsList.size());
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
```

同样的，我们运行了 5 次，来看看最终的结果吧。

```
第1次1796
第2次3526
第3次3731
第4次10
第5次2657
```

可以看出，最终拿出来的有效货物总数小于我们存入的 10000 份货物，并且每一次拿出的货物总数也不尽相等，问题出现在哪里了呢？

事实上，正如前面所说的，我们有 A 和 B 两个线程分别完成 save 和 take 两个动作，但是其实这两个动作之间又互相制约，当 Repository 中没有足够空间存放 Goods 时，A 的 save 动作就可能失败，而当 Repository 中没有存放任何 Goods 时，B  的 take 动作也会因此失败。所以其实 save 与 take 动作之间是有暗含的先后关系的，我们称为 __同步关系__ ，由于没有正确处理同步关系而导致的上述问题我们称为 __同步问题__ 。

# 信号量与 PV 操作

为了解决多线程间的互斥问题和同步问题，聪明的 Dijkstra 发明了经典的信号量 Semaphore，Semaphore 只能由两种特殊的操作来处理，即 P 操作和 V 操作。这里我们用 S 代替信号量 Semaphore。

## P 操作

* 如果 S 非零，将 S 减 1
* 如果 S 小于等于 零，挂起当前线程

## V 操作

* 将 S 加 1
* 如果 S 小于等于 0，随机重启一个被 P 操作挂起的线程。

当然也有其他版本的 PV 操作可能对于 S 的操作略有不同，但其基本思想都是一致的：利用 PV 操作，将具体的资源数目抽象为信号量。P 操作通过减少信号量来占用多余的资源，当资源资源不足时，则会阻止线程进一步执行从而引发错误。V 操作增加信号量释放已经使用完的资源，并恢复由于资源不足而阻塞等待的线程。如果我们将信号量看作为资源设置的 __资源锁__，那么 P 操作相当于 __加锁操作__，而 V 操作相当于 __解锁操作__ ，由此可以推出，PV 操作都是成对出现的，先通过 P 操作对某资源进行加锁，操作完目标资源后，再通过 V 操作解锁释放资源，具体如下

```
P(S) //资源加锁

---资源操作---

V(S) //资源解锁
```

那么接下来就来看看信号量和 PV 操作是如何解决互斥问题和同步问题的。

## 原子操作

同样的，PV 操作有一个很苛刻的条件就是原子性，即 PV 操作是不可以被中断的，一般在操作系统中会通过 __关中断__ 来实现原子性，关中断是指禁止处理机响应中断源的中断请求，可以通过硬件或执行一条“关中断”指令来实现。

在偏应用层的编程语言中，比如 Java，会屏蔽掉底层的中断细节，对于共享变量的互斥保护转而以更强大和的封装去代替，比如在 Java 中提供了锁机制，有对象锁和类锁，有 synchronized 关键字，因此接下来的实现代码中对于 PV 操作我们会使用这些机制来保证其原子性，主要还是为了最终的实现效果。

# 信号量的运用

## 互斥控制

回到上面提到的互斥问题，当 A 和 B 两个线程同时向 Bank 存入 money 时，由于对 money 不加保护，导致在某些时刻 A 和 B 会同时修改 money 值。现在我们尝试对 money 资源进行 PV 保护。

首先我们实现一个信号量对象 MutualExclusionSemaphore

```Java
public class MutualExclusionSemaphore {
    private static int S = 1;

    public MutualExclusionSemaphore(int S) {
        this.S = S;
    }

    public synchronized void P(){
        S--;
        if (S < 0){
            try {
                this.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public synchronized void V(){
        S++;
        if (S <= 0){
            notify();
        }
    }
}
```

可以看到这里我们定义了一个信号量 S 等于 1，之所以等于 1 是因为共享变量 money 只有一个。

我们使用了 synchronized 关键字来确保 PV 操作的原子性，即当一个线程进行 P 操作时会获得 MutualExclusionSemaphore 对象的对象锁，从而确保不会由于更新 S 值而带来新的互斥问题。

我们使用 wait() 和 notify() 来实现阻塞线程和随机重启线程的步骤。

> wait(): Causes the current thread to wait until another thread invokes the notify() method or the notifyAll() method for this object.

> notify(): Wakes up a single thread that is waiting on this object's monitor.

接下来我们需要修改执行线程，加入 PV 操作

```Java
public class AddMoneyThread implements Runnable {
    private MutualExclusionSemaphore mutualExclusionSemaphore;
    private Bank bank;
    private int addMoneyCount;
    public int id;

    public AddMoneyThread(Bank bank, int addMoneyCount, int id, MutualExclusionSemaphore mutualExclusionSemaphore) {
        this.bank = bank;
        this.addMoneyCount = addMoneyCount;
        this.id = id;
        this.mutualExclusionSemaphore = mutualExclusionSemaphore;
    }

    @Override
    public void run() {
        if (bank != null) {
            int index = 1;
            while (index <= this.addMoneyCount) {
                mutualExclusionSemaphore.P();
                bank.addMoney();
                index++;
                mutualExclusionSemaphore.V();
            }
        }
    }
}
```

这里要注意各个线程应该共用同一个 MutualExclusionSemaphore 对象。我们在 addMoney 方法上下加上 PV 操作，然后执行主函数看看输出。

```
第1次30000
第2次30000
第3次30000
第4次30000
第5次30000
```

由于 PV 操作的存在，现在不会出现两个线程同时操作 money 变量带来的互斥问题了。当 S 大于等于 0 时，我们可以理解为可用的资源数目，当 S 小于 0 时，我们可以将其理解为正在等待被重启的线程数目。

## 同步控制

现在我们讨论一下同步问题。我们前面提过，在同步问题中，是由于 save 和 take 之间有一个暗含的同步关系，即只有当仓库中有空闲位时才可以 save，只有当有仓库中存有货物时才可以 take。

save 和 take 操作针对的资源其实是不一样的，save 利用的是空闲位资源，而 take 利用的是存储位资源。所以我们其实应该设置两个信号量，一个表示有空闲位，一个表示有存储位，我们可以分别定义为 idle 和 used。初始状态时，由于仓库是空的，所以 idle 等于仓库大小，used 等于 0。

接下来分别考虑 save 和 take 操作。

当我们进行 save 操作时，如果操作成功，则存储位加 1，如果没有空闲位则 save 会被阻塞。

```
P(idle)

save()

V(used)
```

当我们进行 take 操作时，如果操作成功，则空闲位加 1，如果没有存储位则 take 会被阻塞。

```
P(used)

take()

V(used)
```

接下来我们实现一个信号量对象 SynchronizationSemaphore

```Java
public class SynchronizationSemaphore {
    private static int idle = 0;
    private static int used = 0;

    public SynchronizationSemaphore(int idle, int used){
        this.idle = idle;
        this.used = used;
    }

    public synchronized void PEmpty(){
        idle--;
        if (idle < 0){
            try {
                this.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public synchronized void VEmpty(){
        idle++;
        if (idle <= 0){
            notify();
        }
    }

    public synchronized void PFull(){
        used--;
        if (used < 0){
            try {
                this.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public synchronized void VFull(){
        used++;
        if (used <= 0){
            notify();
        }
    }
}
```

然后我们更改 saveGoodThread 和 takeGoodThread 的 run 方法

```Java
	/* saveGoodThread */
    @Override
    public void run(){
        if (repository != null){
            int i = 0;
            while (i < this.saveNumber){
                synchronizationSemaphore.PEmpty();
                repository.saveGood(i + 1);
                i++;
                synchronizationSemaphore.VFull();
            }
        }
    }
```

```Java
	/* takeGoodThread */
    @Override
    public void run() {
        while (true) {
            synchronizationSemaphore.PFull();
            Goods temp = repository.takeGood();
            number++;
            if (temp != null){
                goodsList.add(temp);
                synchronizationSemaphore.VEmpty();
            }
        }
    }
```

那么运行结果怎样呢？

```
第1次9991
第2次10000
第3次10000
第4次9994
第5次10000
```

可以看到，第 2、3、5 次都得到了我们期望的结果，可以第一次和第四次表现的依旧不是很好，那么这里的问题出在哪里了呢？

### 隐藏的互斥问题

其实这个问题很隐蔽，让我们回忆下整个过程，我们虽然用 PV 操作确保了 save 时仓库一定有空闲位，take 时仓库一定有存储位，但是 saveGoodThread 线程和 takeGoodThread 线程对于仓库的操作都涉及到一个变量 usedSize，正是这个小变量没有被保护，所以带来了一个隐蔽的互斥问题，根据上一部分的讲解，其实解决方式也很简单，我们在 SynchronizationSemaphore 中增加一个互斥锁变量 mutex，具体如下

```Java
public class SynchronizationSemaphore {
    private static int idle = 0;
    private static int used = 0;
    private static int mutex = 1;

    public SynchronizationSemaphore(int idle, int used){
        this.idle = idle;
        this.used = used;
    }

    public synchronized void PEmpty(){
        idle--;
        if (idle < 0){
            try {
                this.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public synchronized void VEmpty(){
        idle++;
        if (idle <= 0){
            notify();
        }
    }

    public synchronized void PFull(){
        used--;
        if (used < 0){
            try {
                this.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public synchronized void VFull(){
        used++;
        if (used <= 0){
            notify();
        }
    }

    public synchronized void PMutex(){
        mutex--;
        if (mutex < 0){
            try {
                this.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public synchronized void VMutex(){
        mutex++;
        if (mutex <= 0){
            notify();
        }
    }
}
```

然后继续修改 saveGoodThread 和 takeGoodThread 的 run 方法

```Java
	/* saveGoodThread */
    @Override
    public void run(){
        if (repository != null){
            int i = 0;
            while (i < this.saveNumber){
                synchronizationSemaphore.PEmpty();
                synchronizationSemaphore.PMutex();
                repository.saveGood(i + 1);
                i++;
                synchronizationSemaphore.VMutex();
                synchronizationSemaphore.VFull();            }
        }
    }
```

```Java
	/* takeGoodThread */
    @Override
    public void run() {
        while (true) {
			synchronizationSemaphore.PFull();
            synchronizationSemaphore.PMutex();
            Goods temp = repository.takeGood();
            number++;
            if (temp != null){
                goodsList.add(temp);
                synchronizationSemaphore.VEmpty();
            }
            synchronizationSemaphore.VMutex();
            }
        }
    }
```

### 隐藏的死锁问题

这里我们其实是这样实现 PV 操作的。

当我们进行 save 操作时，如果操作成功，则存储位加 1，如果没有空闲位则 save 会被阻塞。

```
P(idle)
P(mutex)

save()

V(mutex)
V(used)
```

当我们进行 take 操作时，如果操作成功，则空闲位加 1，如果没有存储位则 take 会被阻塞。

```
P(used)
P(mutex)

take()

V(mutex)
V(used)
```

这里也有一个问题，对于 mutex 值的操作是否必须要在 used 和 idle 操作之间呢，比如是否可以进行如下顺序操作呢？

```
P(mutex)
P(idle)

save()

V(used)
V(mutex)
```

其实这样会引起一个非常严重的问题，即 __死锁__ ，死锁是指一组线程都被阻塞了，并等待一个永远不为真的条件。死锁是一个相当困难的问题，因为它总是不能预测，也许代码可以运行上万小时都不会出错，但接下来一分钟就发生了死锁。

而在这里，我们可以分析一种情况，如果 saveGoodThread 先通过 P(mutex) 操作获得了对 Repository 的资源锁，这时 takeGoodThread 将会在 P(mutex) 阻塞，但 saveGoodThread 接下来发现 Repostitory 的数组满了，所以它会阻塞并等待 takeGoodThread 释放一些空闲位，而 takeGoodThread 此时已经没有办法被重启了。同理也可以推出其他几种死锁情况。

现在让我们运行下主程序。

```
第1次10000
第2次10000
第3次10000
第4次10000
第5次10000
```

这一次完美获得了期望结果。

# 结尾

可以看到互斥问题和同步问题其实经常是交织在一起的，比如经典的"生产者与消费者问题"，并发编程由于其调试的困难和运行流程的不可预测性而给开发者带来了很多挑战，但同时良好的并发编程又能大大提升代码运行效率和计算资源的利用率。

[源码地址](https://github.com/Yasic/SemaphoreTutorialWithJava)
