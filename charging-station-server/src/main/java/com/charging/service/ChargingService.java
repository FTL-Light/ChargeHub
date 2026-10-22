package com.charging.service;

import com.charging.dto.StartChargingDTO;
import com.charging.dto.StopChargingDTO;
import com.charging.entity.Order;

/**
 * 充电业务门面
 *
 * <p>把"加锁 → 开事务 → 业务处理"三段职责集中在此：
 * 分布式锁必须在事务**外层**获取/释放。若锁在事务内获取，
 * 事务提交发生在方法返回之后，锁已释放，其它节点仍可能读到未提交的旧状态。
 */
public interface ChargingService {

    /**
     * 开始充电
     *
     * @param userId 当前用户
     * @param dto    充电桩/枪参数
     * @return 新建订单
     */
    Order startCharging(Long userId, StartChargingDTO dto);

    /**
     * 停止充电并结算
     *
     * @param userId 当前用户
     * @param dto    订单参数
     * @return 结算后的订单
     */
    Order stopCharging(Long userId, StopChargingDTO dto);
}
