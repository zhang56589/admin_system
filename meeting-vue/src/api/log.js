import request from '@/utils/request'

/**
 * 操作日志接口（管理员）。
 * 说明：后端 LogController（/api/log/page）已实现，仅管理员可访问（@RequireAdmin）。
 * 该接口当前仅支持 pageNum / pageSize 两个参数，无按用户过滤能力。
 */

// 分页查询全部操作日志
export function getLogPage(params) {
  return request.get('/log/page', { params })
}