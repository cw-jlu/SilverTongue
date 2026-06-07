import axios from 'axios';

const api = axios.create({ baseURL: '/api' });

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

api.interceptors.response.use(
  (res) => {
    // 如果响应体不是对象，或者没有标准的 ApiResult 字段，直接返回原始数据
    if (!res.data || typeof res.data !== 'object' || !('code' in res.data)) {
      return res.data;
    }

    const { code, data, message } = res.data;
    // 业务成功：剥离包装，只返回内部的 data
    if (code === 200) return data;
    // 业务失败：抛出异常，由组件的 catch 块处理
    return Promise.reject({ message: message || 'Unknown error', code });
  },
  (err) => {
    if (err.response?.status === 401) {
      localStorage.removeItem('token');
      window.location.href = '/login';
    }
    return Promise.reject(err.response?.data || err);
  }
);

export default api;

