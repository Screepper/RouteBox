import axios from "axios";
import type { AxiosError, InternalAxiosRequestConfig } from "axios";
import { ElLoading, ElMessage } from "element-plus";

const requestTypeJson = "application/json";
const requestTypeForm = "multipart/form-data";
const responseTypeJson = "json";

let loading: ReturnType<typeof ElLoading.service> | null = null;

// Custom Request Config Type
interface MyRequestConfig extends InternalAxiosRequestConfig {
  headers: any;
  showLoading?: boolean;
  requestType?: string;
  onError?: (error: any) => void;
}

// Unify Response Type
interface R {
  code: number;
  mes: string;
  data: any;
}

const ErrorR = {
  code: 500,
  mes: "Server Processing Error",
  data: null,
};

const instance = axios.create({
  withCredentials: true,
  baseURL: "/api",
  timeout: 15 * 1000,
});

// Request Interceptor
instance.interceptors.request.use(
  (config: MyRequestConfig) => {
    if (config.showLoading) {
      loading = ElLoading.service({
        lock: true,
        text: "Loading......",
        background: "rgba(0, 0, 0, 0.7)",
      });
    }

    return config;
  },
  (error: AxiosError) => {
    const config = error.config as MyRequestConfig;

    if (config?.showLoading && loading) {
      loading.close();
    }
    if (!config.onError) {
      ElMessage.error("Request Send Failed");
    }

    return Promise.resolve(ErrorR);
  },
);

// Response Interceptor
instance.interceptors.response.use(
  (response: any) => {
    const { showLoading } = response.config as MyRequestConfig;

    if (showLoading && loading) {
      loading.close();
    }

    return response;
  },
  (error: AxiosError) => {
    const config = error.config as MyRequestConfig;

    if (config?.showLoading && loading) {
      loading.close();
    }
    if (!config.onError) {
      ElMessage.error("Server Processing Error");
    }

    return Promise.resolve(ErrorR);
  },
);

export default async (
  config: Omit<MyRequestConfig, "headers"> & { headers?: any },
) => {
  const {
    url,
    data,
    headers,
    requestType = requestTypeJson,
    showLoading = true,
    responseType = responseTypeJson,
    onError,
  } = config;

  if (!url) throw new Error("Url is required");
  let requestData = data;

  if (requestType === requestTypeForm) {
    const formData = new FormData();
    for (const key in data) {
      formData.append(key, data[key] == undefined ? "" : data[key]);
    }
    requestData = formData;
  }

  const newHeaders = Object.assign({ "Content-Type": requestType }, headers);
  const newConfig: MyRequestConfig = {
    headers: newHeaders,
    showLoading,
    onError,
    responseType,
  };

  const result = (await instance.post(url, requestData, newConfig)) as any;

  // Error Response (often occurs when server is down)
  if (result === ErrorR) {
    if (onError) onError(result);

    return null;
  }

  // FileStream Response
  if (responseType === "arraybuffer" || responseType === "blob")
    return { code: 200, mes: "", data: result };

  // Unify Response Data
  const responseData = result.data as R;

  // Unify Error Handling
  if (responseData.code !== 200) {
    if (onError) {
      onError(responseData);
    } else {
      ElMessage.error(responseData.mes);
    }

    // Return error information for consistency
    return responseData;
  } else return responseData;
};
