<template>
  <div class="page-container">
    <el-card>
      <div class="table-toolbar">
        <span>会议室管理</span>
        <div class="filter-bar">
          <el-radio-group v-model="uxViewMode" size="small">
            <el-radio-button :value="'table'">表格视图</el-radio-button>
            <el-radio-button :value="'card'">卡片视图</el-radio-button>
          </el-radio-group>
          <el-button
            v-if="uxViewMode === 'table'"
            type="success"
            :loading="uxBatchLoading"
            @click="uxBatchToggle(1)"
          >
            批量启用
          </el-button>
          <el-button
            v-if="uxViewMode === 'table'"
            type="warning"
            :loading="uxBatchLoading"
            @click="uxBatchToggle(0)"
          >
            批量禁用
          </el-button>
          <el-button type="primary" @click="openAddDialog">新增会议室</el-button>
        </div>
      </div>

      <el-table
        v-show="uxViewMode === 'table'"
        :data="tableData"
        border
        v-loading="loading"
        style="width: 100%"
        @selection-change="uxOnSelectionChange"
      >
        <el-table-column type="selection" width="46" />
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="roomName" label="会议室名称" min-width="140" />
        <el-table-column prop="location" label="位置" min-width="140" />
        <el-table-column prop="capacity" label="容纳人数" width="100" />
        <el-table-column prop="description" label="描述" min-width="160" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openEditDialog(row)">编辑</el-button>
            <el-button
              v-if="row.status === 1"
              type="warning"
              link
              @click="handleToggle(row, 0)"
            >
              禁用
            </el-button>
            <el-button
              v-else
              type="success"
              link
              @click="handleToggle(row, 1)"
            >
              启用
            </el-button>
            <el-button type="danger" link @click="uxPrecheckAndDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 卡片视图（新增）：复用已有字段 + 照片占位，未新增任何后端字段 -->
      <div v-show="uxViewMode === 'card'" class="room-card-grid" v-loading="loading">
        <el-card v-for="room in tableData" :key="room.id" class="room-card" shadow="hover">
          <div class="room-photo">
            <el-icon><Picture /></el-icon>
            <span>会议室照片占位</span>
          </div>
          <div class="room-card-body">
            <div class="room-card-title">{{ room.roomName }}</div>
            <div class="room-card-meta">容纳人数：{{ room.capacity }} 人</div>
            <div class="room-card-meta">位置：{{ room.location || '-' }}</div>
            <div class="room-card-desc">{{ room.description || '暂无描述' }}</div>
            <el-tag :type="room.status === 1 ? 'success' : 'danger'">
              {{ room.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </div>
        </el-card>
        <el-empty v-if="!tableData.length" description="暂无会议室数据" />
      </div>

      <div class="pagination">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :page-sizes="[5, 10, 20]"
          :total="total"
          layout="total, sizes, prev, pager, next"
          @current-change="loadTable"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>

    <!-- 新增 / 编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="form.id ? '编辑会议室' : '新增会议室'"
      width="480px"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="会议室名称" prop="roomName">
          <el-input v-model="form.roomName" placeholder="请输入会议室名称" />
        </el-form-item>
        <el-form-item label="位置" prop="location">
          <el-input v-model="form.location" placeholder="请输入位置" />
        </el-form-item>
        <el-form-item label="容纳人数" prop="capacity">
          <el-input-number v-model="form.capacity" :min="1" :max="1000" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getRoomPage,
  addRoom,
  updateRoom,
  deleteRoom,
  enableRoom,
  disableRoom
} from '@/api/room'
import { getAdminBookingPage } from '@/api/booking'
import { Picture } from '@element-plus/icons-vue'

const loading = ref(false)
const submitting = ref(false)
const tableData = ref([])
const total = ref(0)
const dialogVisible = ref(false)
const formRef = ref(null)

const query = ref({ pageNum: 1, pageSize: 5 })

const defaultForm = () => ({
  id: null,
  roomName: '',
  location: '',
  capacity: 10,
  description: '',
  status: 1
})

const form = ref(defaultForm())

const rules = {
  roomName: [{ required: true, message: '请输入会议室名称', trigger: 'blur' }],
  location: [{ required: true, message: '请输入位置', trigger: 'blur' }],
  capacity: [{ required: true, message: '请输入容纳人数', trigger: 'blur' }]
}

// ===== 本次升级「新增」：双视图切换 / 批量启停 / 删除前预校验未完结预约 =====
const uxViewMode = ref('table')
const uxSelectedRows = ref([])
const uxBatchLoading = ref(false)

const uxOnSelectionChange = (rows) => {
  uxSelectedRows.value = rows
}

const uxBatchToggle = async (nextStatus) => {
  if (!uxSelectedRows.value.length) {
    ElMessage.warning('请先勾选需要操作的会议室')
    return
  }
  const action = nextStatus === 1 ? '启用' : '禁用'
  await ElMessageBox.confirm(
    `确定批量${action}选中的 ${uxSelectedRows.value.length} 个会议室吗？`,
    '提示',
    { type: 'warning' }
  )
  uxBatchLoading.value = true
  try {
    const results = await Promise.all(
      uxSelectedRows.value.map((row) =>
        (nextStatus === 1 ? enableRoom(row.id) : disableRoom(row.id))
          .then(() => true)
          .catch(() => false)
      )
    )
    const okCount = results.filter(Boolean).length
    ElMessage.success(
      `批量${action}完成：成功 ${okCount} 个，失败 ${results.length - okCount} 个`
    )
    loadTable()
  } finally {
    uxBatchLoading.value = false
  }
}

// 删除前预校验（新增）：查询该会议室是否存在未完结（待审批0 / 已通过1）预约
const uxPrecheckAndDelete = async (row) => {
  try {
    const res = await getAdminBookingPage({ pageNum: 1, pageSize: 100, roomId: row.id })
    const unfinished = (res.data.records || []).filter(
      (b) => b.resStatus === 0 || b.resStatus === 1
    )
    if (unfinished.length) {
      ElMessage.warning(`该会议室存在 ${unfinished.length} 条未完结预约，无法删除`)
      return
    }
  } catch (err) {
    // 预校验请求失败时不阻断，仍交由后端删除接口做最终校验
  }
  handleDelete(row.id)
}

const loadTable = async () => {
  loading.value = true
  try {
    const res = await getRoomPage({ ...query.value })
    tableData.value = res.data.records || []
    total.value = res.data.total || 0
  } finally {
    loading.value = false
  }
}

const handleSizeChange = () => {
  query.value.pageNum = 1
  loadTable()
}

const openAddDialog = () => {
  form.value = defaultForm()
  dialogVisible.value = true
}

const openEditDialog = (row) => {
  form.value = { ...row }
  dialogVisible.value = true
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      if (form.value.id) {
        await updateRoom(form.value)
      } else {
        await addRoom(form.value)
      }
      ElMessage.success('保存成功')
      dialogVisible.value = false
      loadTable()
    } catch (err) {
      // 错误提示由响应拦截器统一处理
    } finally {
      submitting.value = false
    }
  })
}

const handleToggle = async (row, status) => {
  try {
    if (status === 1) {
      await enableRoom(row.id)
      ElMessage.success('已启用')
    } else {
      await disableRoom(row.id)
      ElMessage.success('已禁用')
    }
    loadTable()
  } catch (err) {
    // 错误提示由响应拦截器统一处理
  }
}

const handleDelete = async (id) => {
  await ElMessageBox.confirm('确定要删除该会议室吗？', '提示', { type: 'warning' })
  try {
    await deleteRoom(id)
    ElMessage.success('删除成功')
    loadTable()
  } catch (err) {
    // 存在未完结预约时后端会返回提示，此处无需额外处理
  }
}

onMounted(loadTable)
</script>

<style scoped>
/* 本次升级「新增」样式：卡片视图 */
.room-card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 16px;
}

.room-card :deep(.el-card__body) {
  padding: 0;
}

.room-photo {
  height: 120px;
  border-radius: 8px 8px 0 0;
  background: linear-gradient(135deg, #eef4fe 0%, #dbe7fd 100%);
  color: var(--app-color-primary);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-size: 12px;
}

.room-photo .el-icon {
  font-size: 28px;
}

.room-card-body {
  padding: 12px 16px 16px;
}

.room-card-title {
  font-size: 15px;
  font-weight: 600;
  color: #1f2329;
  margin-bottom: 8px;
}

.room-card-meta {
  font-size: 13px;
  color: #4e5969;
  margin-bottom: 4px;
}

.room-card-desc {
  font-size: 12px;
  color: #86909c;
  margin: 8px 0;
  min-height: 32px;
}
</style>