import sun.misc.Unsafe;

import java.lang.reflect.Field;

public class UnsafeTest {
    private volatile int a = 0; // 共享变量，初始值为 0
    private static final Unsafe unsafe;
    private static final long fieldOffset;

    static {
        try {
            // 获取 Unsafe 实例
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            unsafe = (Unsafe) theUnsafe.get(null);
            // 获取 a 字段的内存偏移量
            fieldOffset = unsafe.objectFieldOffset(UnsafeTest.class.getDeclaredField("a"));
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize Unsafe or field offset", e);
        }
    }

    public static void main(String[] args) {
        UnsafeTest casTest = new UnsafeTest();

        Thread t1 = new Thread(() -> {
            for (int i = 1; i <= 4; i++) {
                casTest.incrementAndPrint(i);
            }
        });

        Thread t2 = new Thread(() -> {
            for (int i = 5; i <= 9; i++) {
                casTest.incrementAndPrint(i);
            }
        });

        t1.start();
        t2.start();

        // 等待线程结束，以便观察完整输出 (可选，用于演示)
        try {
            t1.join();
            t2.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // 将递增和打印操作封装在一个原子性更强的方法内
    private void incrementAndPrint(int targetValue) {
        while (true) {
            int currentValue = a; // 读取当前 a 的值
            // 只有当 a 的当前值等于目标值的前一个值时，才尝试更新
            if (currentValue == targetValue - 1) {
                if (unsafe.compareAndSwapInt(this, fieldOffset, currentValue, targetValue)) {
                    // CAS 成功，说明成功将 a 更新为 targetValue
                    System.out.print(targetValue + " ");
                    break; // 成功更新并打印后退出循环
                }
                // 如果 CAS 失败，意味着在读取 currentValue 和执行 CAS 之间，a 的值被其他线程修改了，
                // 此时 currentValue 已经不是 a 的最新值，需要重新读取并重试。
            }
            // 如果 currentValue != targetValue - 1，说明还没轮到当前线程更新，
            // 或者已经被其他线程更新超过了，让出CPU给其他线程机会。
            // 对于严格顺序递增的场景，如果 current > targetValue - 1，可能意味着逻辑错误或死循环，
            // 但在此示例中，我们期望线程能按顺序执行。
            Thread.yield(); // 提示CPU调度器可以切换线程，减少无效自旋
        }
    }
}
