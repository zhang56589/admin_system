<template>
  <div class="page-container">
    <el-card>
      <div class="table-toolbar">
        <span>操作日志</span>
      </div>

      <el-table :data="tableData" border v-loading="loading" style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="操作人" width="120">
          <template #default="{ row }">{{ row.username || '-' }}</template>
        </el-table-column>
        <el-table-column prop="operateType" label="操作类型" min-width="140">
          <template #default="{ row }">{{ row.operateType || '-' }}</template>
        </el-table-column>
        <el-table-column prop="method" label="请求方法" min-width="140">
          <template #default="{ row }">{{ row.method || '-' }}</template>
        </el-table-column>
        <el-table-column prop="content" label="请求参数" min-width="200">
          <template #default="{ row }">
            <span class="ellipsis">{{ row.content || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="ip" label="IP" width="130">
          <template #default="{ row }">{{ row.ip || '-' }}</template>
        </el-table-column>
        <el-table-column prop="costTime" label="耗时(ms)" width="100">
          <template #default="{ row }">
            {{ row.costTime != null ? row.costTime : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="操作时间" min-width="170" />
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next"
          @current-change="loadTable"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getLogPage } from '@/api/log'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)

const query = ref({ pageNum: 1, pageSize: 10 })

const loadTable = async () => {
  loading.value = true
  try {
    const res = await getLogPage({ ...query.value })
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

onMounted(loadTable)
</script>

<style scoped>
.ellipsis {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
}
</style>