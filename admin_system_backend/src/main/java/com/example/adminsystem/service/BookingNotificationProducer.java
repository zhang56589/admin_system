package com.example.adminsystem.service;

import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 预约通知异步生产者（可选：仅当 rocketmq.name-server 配置时加载）。
 * 解耦消息推送：预约提交 / 审批完成后异步发送通知，不阻塞主流程。
 *
 * 启用方式：application.yml 取消 rocketmq.name-server 行的注释并填地址。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "rocketmq.name-server")
public class BookingNotificationProducer {

    public static final String TOPIC_SUBMIT = "booking-notify-submit";
    public static final String TOPIC_APPROVE = "booking-notify-approve";

    /**
     * 注入方式与 Producer 本身保持一致：
     * - Producer 类上有 @ConditionalOnProperty(name="rocketmq.name-server")
     * - RocketMQTemplate 的自动装配同样需要该属性存在
     * - 两者同时命中才创建，此处加 required=false 作为防御层
     */
    @Autowired(required = false)
    private RocketMQTemplate rocketMQTemplate;

    /**
     * 预约提交通知 —— 推送申请人
     * @param bookingId  预约ID
     * @param username   申请人账号
     * @param roomName   会议室名称
     * @param startTime  开始时间(字符串)
     * @param endTime    结束时间(字符串)
     * @param operatorId 操作人ID
     */
    public void sendSubmitNotify(Long bookingId, String username, String roomName,
                                 String startTime, String endTime, Long operatorId) {
        Map<String, Object> body = Map.of(
                "event", "SUBMIT",
                "bookingId", bookingId,
                "username", username,
                "roomName", roomName,
                "startTime", startTime,
                "endTime", endTime,
                "operatorId", operatorId,
                "ts", System.currentTimeMillis()
        );
        asyncSend(TOPIC_SUBMIT, body);
    }

    /**
     * 预约审批通知 —— 推送申请人（PASS/REJECT）
     * @param bookingId   预约ID
     * @param username    申请人账号
     * @param result      PASS / REJECT
     * @param rejectReason 驳回原因（REJECT 时有效）
     * @param operatorId  审批人ID
     */
    public void sendApproveNotify(Long bookingId, String username, String result,
                                  String rejectReason, Long operatorId) {
        Map<String, Object> body = Map.of(
                "event", "APPROVE",
                "bookingId", bookingId,
                "username", username,
                "result", result,
                "rejectReason", rejectReason == null ? "" : rejectReason,
                "operatorId", operatorId,
                "ts", System.currentTimeMillis()
        );
        asyncSend(TOPIC_APPROVE, body);
    }

    private void asyncSend(String topic, Object body) {
        if (rocketMQTemplate == null) {
            log.warn("RocketMQTemplate 未就绪，跳过 MQ 通知 topic={}", topic);
            return;
        }
        try {
            String payload = JSONUtil.toJsonStr(body);
            Long bookingId = (Long) ((Map<String, Object>) body).get("bookingId");
            // timeout 1000ms：异步发送，主线程只等 callback 不阻塞
            rocketMQTemplate.asyncSend(topic,
                    MessageBuilder.withPayload(payload).build(),
                    new org.apache.rocketmq.client.producer.SendCallback() {
                        @Override public void onSuccess(org.apache.rocketmq.client.producer.SendResult sendResult) {
                            log.debug("MQ异步通知topic={} 成功, bookingId={}", topic, bookingId);
                        }
                        @Override public void onException(Throwable e) {
                            log.warn("MQ异步通知topic={} 失败, bookingId={}: {}", topic, bookingId, e.getMessage());
                        }
                    },
                    1000);
        } catch (Exception ex) {
            log.warn("MQ通知发送异常, topic={}: {}", topic, ex.getMessage());
        }
    }
}
