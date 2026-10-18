import request from '@/utils/request'

/**
 * 用户管理接口（管理员）。
 * 说明：后端 UserController（/api/user）已实现，以下接口均带 @RequireAdmin 校验，
 * 普通员工调用会返回无权限，由响应拦截器统一提示。
 */

// 分页查询用户
export function getUserPage(params) {
  return request.get('/user/page', { params })
}

// 修改用户角色 { id, role }
export function updateUserRole(data) {
  return request.put('/user/role', data)
}

// 修改用户状态 { id, enabled }
export function updateUserStatus(data) {
  return request.put('/user/status', data)
}

// 重置用户密码 { id, password }
export function resetUserPassword(data) {
  return request.put('/user/resetPassword', data)
}