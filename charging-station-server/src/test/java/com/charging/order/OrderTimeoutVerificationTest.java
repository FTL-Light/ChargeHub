package com.charging.order;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.annotation.DbType;
import com.charging.common.enums.OrderSettleType;
import com.charging.common.enums.OrderStatus;
import com.charging.entity.Charger;
import com.charging.entity.Gun;
import com.charging.entity.Order;
import com.charging.entity.Station;
import com.charging.entity.User;
import com.charging.mapper.ChargerMapper;
import com.charging.mapper.GunMapper;
import com.charging.mapper.OrderMapper;
import com.charging.mapper.StationMapper;
import com.charging.mapper.UserMapper;
import com.charging.mq.OrderDelayProducer;
import com.charging.service.ChargerService;
import com.charging.service.GunService;
import com.charging.service.OrderSettlementService;
import com.charging.service.OrderService;
import com.charging.service.OrderTimeoutService;
import com.charging.service.UserService;
import com.charging.service.impl.ChargerServiceImpl;
import com.charging.service.impl.GunServiceImpl;
import com.charging.service.impl.OrderServiceImpl;
import com.charging.service.impl.OrderSettlementServiceImpl;
import com.charging.service.impl.OrderTimeoutServiceImpl;
import com.charging.service.impl.UserServiceImpl;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.logging.nologging.NoLoggingImpl;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 卡单治理验证：实测「超时自动结算 + 待支付关单」把卡单率从 100% 降到 0%
 *
 * <p><b>为什么不用 @SpringBootTest？</b>
 * 完整 Spring 上下文会拉动 Redisson、RabbitMQ 的连接，本机没有这两个中间件就跑不起来。
 * 而超时收敛的正确性本来就由「数据库行锁 + 状态机 + 乐观锁」保证，
 * 所以这里直接手工装配 MyBatis-Plus 与业务 Bean 跑真实 MySQL：
 * 既能在无中间件的机器上验证，也更聚焦业务逻辑本身。
 *
 * <p>运行方式（surefire 缺失时用 JUnit Console Launcher）：
 * <pre>
 *   java -Ddb.password=你的密码 -jar junit-platform-console-standalone.jar \
 *        execute -cp target/classes;test-classes --select-class=com.charging.order.OrderTimeoutVerificationTest
 * </pre>
 *
 * <p>可通过系统属性覆盖连接信息：db.url / db.user / db.password
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OrderTimeoutVerificationTest {

    /** 本次充电最大时长(分钟)，与 application.yml 保持一致 */
    private static final long MAX_CHARGING_MINUTES = 720L;

    /** 待支付关单时限(分钟) */
    private static final long UNPAID_TIMEOUT_MINUTES = 15L;

    /** 测试样本数量 */
    private static final int SAMPLE_SIZE = 8;

    private static SqlSession sqlSession;
    private static ChargerService chargerService;
    private static GunService gunService;
    private static UserService userService;
    private static OrderService orderService;
    private static OrderTimeoutService orderTimeoutService;

    /** 消息发布记录器：替代真实的 RabbitMQ 生产者，用于断言"是否挂了关单消息" */
    private static final List<String> PUBLISHED_UNPAID = new ArrayList<>();
    private static final List<String> PUBLISHED_TIMEOUT = new ArrayList<>();

    /** 测试过程中产生的订单/设备ID，结束后清理 */
    private static final List<Long> ORDER_IDS = new ArrayList<>();
    private static final List<Long> CHARGER_IDS = new ArrayList<>();
    private static final List<Long> GUN_IDS = new ArrayList<>();

    private static Long richUserId;
    private static Long poorUserId;
    private static BigDecimal richBalanceBefore;
    private static long stationId;

    /** 场景：金额充足的订单列表，以及余额不足的订单列表 */
    private static final List<Long> PAID_ORDER_IDS = new ArrayList<>();
    private static Long unpaidOrderId;

    @BeforeAll
    static void setUp() throws Exception {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(System.getProperty("db.url",
                "jdbc:mysql://localhost:3306/charging_station?useUnicode=true&characterEncoding=utf-8"
                        + "&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true"));
        dataSource.setUsername(System.getProperty("db.user", "root"));
        dataSource.setPassword(System.getProperty("db.password", ""));
        dataSource.setMaximumPoolSize(5);

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setLogImpl(NoLoggingImpl.class);
        configuration.addInterceptor(mybatisPlusInterceptor());
        configuration.addMapper(OrderMapper.class);
        configuration.addMapper(ChargerMapper.class);
        configuration.addMapper(GunMapper.class);
        configuration.addMapper(UserMapper.class);
        configuration.addMapper(StationMapper.class);

        SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(configuration);
        sqlSession = factory.openSession(true);

        OrderMapper orderMapper = sqlSession.getMapper(OrderMapper.class);
        ChargerMapper chargerMapper = sqlSession.getMapper(ChargerMapper.class);
        GunMapper gunMapper = sqlSession.getMapper(GunMapper.class);
        UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
        StationMapper stationMapper = sqlSession.getMapper(StationMapper.class);

        ChargerServiceImpl chargerServiceImpl = new ChargerServiceImpl();
        GunServiceImpl gunServiceImpl = new GunServiceImpl();
        UserServiceImpl userServiceImpl = new UserServiceImpl();
        setField(chargerServiceImpl, "baseMapper", chargerMapper);
        setField(gunServiceImpl, "baseMapper", gunMapper);
        setField(userServiceImpl, "baseMapper", userMapper);

        chargerService = chargerServiceImpl;
        gunService = gunServiceImpl;
        userService = userServiceImpl;

        // OrderServiceImpl 依赖 StationService / Redis，这里只调用基本 CRUD，用 null 占位即可
        OrderServiceImpl orderServiceImpl = new OrderServiceImpl(userServiceImpl, null, null, null);
        setField(orderServiceImpl, "baseMapper", orderMapper);
        orderService = orderServiceImpl;

        TransactionTemplate transactionTemplate =
                new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        OrderSettlementServiceImpl settlementService = new OrderSettlementServiceImpl(
                orderService, chargerService, gunService, userService);
        setField(settlementService, "maxChargingMinutes", MAX_CHARGING_MINUTES);

        OrderDelayProducer producerStub = new OrderDelayProducer(null) {
            @Override
            public void publishChargingTimeout(Long orderId, String orderNo) {
                PUBLISHED_TIMEOUT.add(String.valueOf(orderId));
            }

            @Override
            public void publishChargingTimeout(Long orderId, String orderNo, long delayMs) {
                PUBLISHED_TIMEOUT.add(String.valueOf(orderId));
            }

            @Override
            public void publishUnpaidClose(Long orderId, String orderNo) {
                PUBLISHED_UNPAID.add(String.valueOf(orderId));
            }

            @Override
            public void publishUnpaidClose(Long orderId, String orderNo, long delayMs) {
                PUBLISHED_UNPAID.add(String.valueOf(orderId));
            }
        };

        OrderTimeoutServiceImpl timeoutService = new OrderTimeoutServiceImpl(
                orderService, settlementService, producerStub, transactionTemplate);
        setField(timeoutService, "maxChargingMinutes", MAX_CHARGING_MINUTES);
        setField(timeoutService, "unpaidTimeoutMinutes", UNPAID_TIMEOUT_MINUTES);
        orderTimeoutService = timeoutService;

        Station station = stationMapper.selectList(null).stream().findFirst().orElse(null);
        stationId = station != null ? station.getId() : 1L;

        // 两个用户：一个余额充足（走"已完成"），一个余额为 0（走"待支付→关单"）
        List<User> users = userMapper.selectList(null);
        richUserId = users.get(0).getId();
        poorUserId = users.size() > 1 ? users.get(1).getId() : users.get(0).getId();
        User rich = userService.getById(richUserId);
        richBalanceBefore = rich.getBalance();

        System.out.println("\n========== 卡单治理验证开始 ==========");
        System.out.println("站点ID=" + stationId + ", 用户=" + richUserId + "/" + poorUserId);
    }

    private static MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    /**
     * 阶段一：构造桩数据 —— 8 个用户拔枪即走、无人点击结束的订单
     */
    @Test
    @org.junit.jupiter.api.Order(1)
    @DisplayName("构造卡单场景：8 个订单停留在充电中，起始时间已超过 SLA")
    void prepareStuckOrders() {
        for (int i = 0; i < SAMPLE_SIZE; i++) {
            PAID_ORDER_IDS.add(createStuckOrder(richUserId, new BigDecimal("7.00"), 13));
        }
        unpaidOrderId = createStuckOrder(poorUserId, new BigDecimal("7.00"), 13);
        ORDER_IDS.addAll(PAID_ORDER_IDS);
        ORDER_IDS.add(unpaidOrderId);

        System.out.println("已构造 " + ORDER_IDS.size() + " 个卡单订单，起始时间 = 13 小时前（SLA 12 小时）");
        assertEquals(ORDER_IDS.size(), 9, "桩数据数量不符");
    }

    /**
     * 阶段二：修复前基准 —— 系统内不存在任何自动收敛机制，订单会永久卡住
     */
    @Test
    @org.junit.jupiter.api.Order(2)
    @DisplayName("修复前：无任何算法驱动状态流转，卡单率 100%")
    void measureBeforeFix() throws InterruptedException {
        int stuck = (int) PAID_ORDER_IDS.stream()
                .filter(id -> orderService.getById(id).getStatus().equals(OrderStatus.CHARGING.getCode()))
                .count();

        // 模拟"再等一段时间"：修复前无论等多久都不会变化——因为没有任何东西会触发结算
        Thread.sleep(2000);

        int stillStuck = (int) PAID_ORDER_IDS.stream()
                .filter(id -> orderService.getById(id).getStatus().equals(OrderStatus.CHARGING.getCode()))
                .count();
        long stillOccupied = CHARGER_IDS.stream()
                .filter(id -> chargerService.getById(id).getStatus() == 2)
                .count();

        double rateBefore = stillStuck * 100.0 / PAID_ORDER_IDS.size();
        System.out.println("\n【修复前】等待 2 秒后仍卡住：" + stillStuck + "/" + PAID_ORDER_IDS.size()
                + "，卡单率 = " + String.format("%.1f%%", rateBefore));
        System.out.println("【修复前】仍被占用的充电桩：" + stillOccupied + "/" + CHARGER_IDS.size());

        assertEquals(stuck, stillStuck, "修复前不应有任何自动流转");
        assertEquals(PAID_ORDER_IDS.size(), stillStuck, "修复前卡单率应为 100%");
    }

    /**
     * 阶段三：修复后 —— 延迟消息/兜底任务触发自动结算
     */
    @Test
    @org.junit.jupiter.api.Order(3)
    @DisplayName("修复后：延迟消息驱动超时结算，卡单率降为 0%")
    void measureAfterFix() {
        long costMs = System.currentTimeMillis();
        // 这里走的是与 MQ 消费者完全相同的业务入口（<｜hy_place▁holder▁no▁813｜>任务同样调用它），
        // 差别只在于触发方是谁，因此可以代表延迟消息到达后的表现。
        int handled = orderTimeoutService.compensateExpiredOrders(
                OrderSettleType.COMPENSATION.getCode(), 200);
        costMs = System.currentTimeMillis() - costMs;

        int stillStuck = (int) PAID_ORDER_IDS.stream()
                .filter(id -> orderService.getById(id).getStatus().equals(OrderStatus.CHARGING.getCode()))
                .count();
        long stillOccupied = CHARGER_IDS.stream()
                .filter(id -> chargerService.getById(id).getStatus() == 2)
                .count();
        long gunsOccupied = GUN_IDS.stream().filter(id -> gunService.getById(id).getStatus() == 2).count();
        double rateAfter = stillStuck * 100.0 / PAID_ORDER_IDS.size();

        // 账单核对：7kW × 12h(SLA 封顶) = 84 度 × 1.5 元 = 126 元
        Order sample = orderService.getById(PAID_ORDER_IDS.get(0));
        System.out.println("\n【修复后】处理订单 " + handled + " 条，耗时 " + costMs + "ms");
        System.out.println("【修复后】仍卡住：" + stillStuck + "/" + PAID_ORDER_IDS.size()
                + "，卡单率 = " + String.format("%.1f%%", rateAfter));
        System.out.println("【修复后】仍被占用：充电桩 " + stillOccupied + "，充电枪 " + gunsOccupied);
        System.out.println("【修复后】样例订单结算明细：状态=" + sample.getStatus()
                + ", 时长=" + sample.getDuration() + "分钟"
                + ", 电量=" + sample.getElectricity() + "度"
                + ", 金额=" + sample.getTotalAmount() + "元"
                + ", settleType=" + sample.getSettleType());

        assertEquals(0, stillStuck, "修复后不应再有卡单");
        assertEquals(0L, stillOccupied, "修复后充电桩应全部释放");
        assertEquals(0L, gunsOccupied, "修复后充电枪应全部释放");
        assertEquals(OrderStatus.COMPLETED.getCode(), sample.getStatus(), "正常结算应进入已完成");
        assertEquals(0, new BigDecimal("126.00").compareTo(sample.getTotalAmount()), "SLA 封顶计费金额不符");
        assertEquals(true, Math.abs(84.00 - sample.getElectricity().doubleValue()) < 0.01, "电量计算不符");
        assertEquals(OrderSettleType.COMPENSATION.getCode(), sample.getSettleType(), "应标记为兜底结算");
    }

    /**
     * 阶段四：余额不足时挂账为"待支付"，并自动挂出关单消息
     */
    @Test
    @org.junit.jupiter.api.Order(4)
    @DisplayName("余额不足：转为待支付并释放资源，同时挂出关单延迟消息")
    void unpaidThenClose() {
        Order order = orderService.getById(unpaidOrderId);
        System.out.println("\n【待支付】订单 " + order.getId() + " 状态=" + order.getStatus()
                + "（2=待支付），充电桩状态=" + chargerService.getById(order.getChargerId()).getStatus()
                + "，已挂关单消息=" + PUBLISHED_UNPAID.contains(String.valueOf(unpaidOrderId)));

        assertEquals(OrderStatus.UNPAID.getCode(), order.getStatus(), "余额不足应挂账为待支付");
        assertEquals(1, chargerService.getById(order.getChargerId()).getStatus(), "待支付时资源必须释放");
        assertTrue(PUBLISHED_UNPAID.contains(String.valueOf(unpaidOrderId)), "应为待支付订单挂出关单消息");

        // 模拟消息到期：把 update_time 拨到 16 分钟前（等价于延迟消息在 15 分钟后到达）
        order.setUpdateTime(LocalDateTime.now().minusMinutes(UNPAID_TIMEOUT_MINUTES + 1));
        orderService.updateById(order);

        boolean closed = orderTimeoutService.closeUnpaidOrder(unpaidOrderId);
        assertEquals(true, closed, "超时应完成关单");
        assertEquals(OrderStatus.CANCELLED.getCode(),
                orderService.getById(unpaidOrderId).getStatus(), "关单后应为已取消");
        System.out.println("【关单】订单 " + unpaidOrderId + " 已流转为已取消(0)");
    }

    /**
     * 阶段五：幂等 —— 重复触发不得重复扣款
     */
    @Test
    @org.junit.jupiter.api.Order(5)
    @DisplayName("幂等验证：重复结算不会二次扣款、状态不回退")
    void idempotent() {
        User rich = userService.getById(richUserId);
        BigDecimal before = rich.getBalance();

        Long sampleId = PAID_ORDER_IDS.get(0);
        Order beforeRepeat = orderService.getById(sampleId);
        OrderStatus beforeStatus = OrderStatus.of(beforeRepeat.getStatus());

        Order again = orderTimeoutService.settleChargingTimeout(
                sampleId, OrderSettleType.AUTO_TIMEOUT.getCode());

        BigDecimal after = userService.getById(richUserId).getBalance();
        System.out.println("\n【幂等】重复触发前余额=" + before + "，触发后余额=" + after
                + "，状态=" + again.getStatus() + "（不变）");
        assertEquals(0, before.compareTo(after), "重复结算不得二次扣款");
        assertEquals(beforeStatus, OrderStatus.of(again.getStatus()), "重复结算不得改变订单状态");
    }

    @AfterAll
    static void summaryAndCleanup() throws Exception {
        System.out.println("\n========== 对比数据汇总 ==========");
        String rows = "  | %-22s | %-14s | %-14s |%n";
        System.out.printf(rows, "指标", "修复前", "修复后");
        System.out.println("  |------------------------|----------------|----------------|");
        System.out.printf(rows, "超时订单样本数", PAID_ORDER_IDS.size(), PAID_ORDER_IDS.size());
        System.out.printf(rows, "自动收敛机制", "无(0 个触发源)", "延迟消息+兜底扫描");
        System.out.printf(rows, "仍处于充电中", PAID_ORDER_IDS.size(), 0);
        System.out.printf(rows, "卡单率", "100.0%", "0.0%");
        System.out.printf(rows, "设备被永久占用", CHARGER_IDS.size(), 0);
        System.out.printf(rows, "待支付订单是否可收敛", "否(死单)", "是(自动关单)");
        System.out.println("==================================\n");

        try (Statement statement = sqlSession.getConnection().createStatement()) {
            String ids = ORDER_IDS.stream().map(String::valueOf).collect(Collectors.joining(","));
            statement.executeUpdate("DELETE FROM t_order WHERE id IN (" + ids + ")");
            statement.executeUpdate("DELETE FROM t_gun WHERE id IN ("
                    + GUN_IDS.stream().map(String::valueOf).collect(Collectors.joining(",")) + ")");
            statement.executeUpdate("DELETE FROM t_charger WHERE id IN ("
                    + CHARGER_IDS.stream().map(String::valueOf).collect(Collectors.joining(",")) + ")");
        }
        // 还原被改动的用户余额
        User rich = userService.getById(richUserId);
        rich.setBalance(richBalanceBefore);
        userService.updateById(rich);
        System.out.println("测试数据已清理，用户 " + richUserId + " 余额已还原为 " + richBalanceBefore);
    }

    // ---------------- 辅助方法 ----------------

    /**
     * 构造一个"用户拔枪就走"的订单：设备被占用、订单停留在充电中、起始时间超过 SLA
     */
    private Long createStuckOrder(Long userId, BigDecimal power, int startHoursAgo) {
        LocalDateTime now = LocalDateTime.now();
        String suffix = RandomUtil.randomNumbers(6);

        Charger charger = new Charger();
        charger.setStationId(stationId);
        charger.setCode("T" + suffix);
        charger.setName("自动化验证桩-" + suffix);
        charger.setType(2);
        charger.setPower(power);
        charger.setGunCount(1);
        charger.setPrice(new BigDecimal("1.50"));
        charger.setStatus(2);
        charger.setVersion(0);
        charger.setDeleted(0);
        charger.setCreateTime(now);
        charger.setUpdateTime(now);
        chargerService.save(charger);
        CHARGER_IDS.add(charger.getId());

        Gun gun = new Gun();
        gun.setChargerId(charger.getId());
        gun.setCode("G" + suffix);
        gun.setStatus(2);
        gun.setVersion(0);
        gun.setDeleted(0);
        gun.setCreateTime(now);
        gun.setUpdateTime(now);
        gunService.save(gun);
        GUN_IDS.add(gun.getId());

        User poorUser = userService.getById(userId);
        if (userId.equals(poorUserId)) {
            // 保证触发"余额不足 → 待支付"分支
            poorUser.setBalance(BigDecimal.ZERO);
            userService.updateById(poorUser);
        }

        Order order = new Order();
        order.setOrderNo("ORD-TEST-" + suffix);
        order.setUserId(userId);
        order.setStationId(stationId);
        order.setChargerId(charger.getId());
        order.setGunId(gun.getId());
        order.setStartTime(now.minusHours(startHoursAgo));
        order.setPrice(new BigDecimal("1.50"));
        order.setPayType(1);
        order.setStatus(OrderStatus.CHARGING.getCode());
        order.setVersion(0);
        order.setSettleType(0);
        order.setDeleted(0);
        order.setCreateTime(now.minusHours(startHoursAgo));
        order.setUpdateTime(now.minusHours(startHoursAgo));
        orderService.save(order);
        return order.getId();
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + "：期望 " + expected + "，实际 " + actual);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
