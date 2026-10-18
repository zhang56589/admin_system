import request from '../utils/request'

export function getRoomPage(pageNum, pageSize){
  return request.get('/room/page',{
    params:{
      pageNum,
      pageSize
    }
  })
}

export function getRoomList(){
  return request.get('/room/list')
}

export function addRoom(data){
  return request.post('/room',data)
}

export function deleteRoom(id){
  return request.delete(`/room/${id}`)
}

export function updateRoom(data){
  return request.put('/room',data)
}
