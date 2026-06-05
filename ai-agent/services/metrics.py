import time
import asyncio
import inspect
from functools import wraps
import grpc
from prometheus_client import Counter, Histogram
from loguru import logger

# Counters and Histograms
GRPC_REQUEST_COUNT = Counter(
    "grpc_requests_total",
    "Total number of gRPC requests",
    ["service", "method", "status"]
)

GRPC_REQUEST_LATENCY = Histogram(
    "grpc_request_latency_seconds",
    "gRPC request latency in seconds",
    ["service", "method"],
    buckets=(0.005, 0.01, 0.025, 0.05, 0.075, 0.1, 0.25, 0.5, 0.75, 1.0, 2.5, 5.0, 7.5, 10.0, 15.0, 30.0, float("inf"))
)

AI_INFERENCE_TTFT_LATENCY = Histogram(
    "ai_inference_ttft_latency_seconds",
    "AI Inference Time To First Token latency in seconds",
    ["model_name"],
    buckets=(0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 0.75, 1.0, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0, float("inf"))
)

def grpc_metric(service_name):
    def decorator(func):
        if inspect.isgeneratorfunction(func):
            @wraps(func)
            def generator_wrapper(self, request, context, *args, **kwargs):
                method_name = func.__name__
                start_time = time.time()
                status = "OK"
                try:
                    for val in func(self, request, context, *args, **kwargs):
                        yield val
                except grpc.RpcError as e:
                    status = e.code().name
                    raise
                except Exception as e:
                    status = "INTERNAL"
                    raise
                finally:
                    duration = time.time() - start_time
                    GRPC_REQUEST_LATENCY.labels(service=service_name, method=method_name).observe(duration)
                    GRPC_REQUEST_COUNT.labels(service=service_name, method=method_name, status=status).inc()
            return generator_wrapper
            
        elif asyncio.iscoroutinefunction(func):
            @wraps(func)
            async def async_wrapper(self, request, context, *args, **kwargs):
                method_name = func.__name__
                start_time = time.time()
                status = "OK"
                try:
                    return await func(self, request, context, *args, **kwargs)
                except grpc.RpcError as e:
                    status = e.code().name
                    raise
                except Exception as e:
                    status = "INTERNAL"
                    raise
                finally:
                    duration = time.time() - start_time
                    GRPC_REQUEST_LATENCY.labels(service=service_name, method=method_name).observe(duration)
                    GRPC_REQUEST_COUNT.labels(service=service_name, method=method_name, status=status).inc()
            return async_wrapper
            
        else:
            @wraps(func)
            def sync_wrapper(self, request, context, *args, **kwargs):
                method_name = func.__name__
                start_time = time.time()
                status = "OK"
                try:
                    return func(self, request, context, *args, **kwargs)
                except grpc.RpcError as e:
                    status = e.code().name
                    raise
                except Exception as e:
                    status = "INTERNAL"
                    raise
                finally:
                    duration = time.time() - start_time
                    GRPC_REQUEST_LATENCY.labels(service=service_name, method=method_name).observe(duration)
                    GRPC_REQUEST_COUNT.labels(service=service_name, method=method_name, status=status).inc()
            return sync_wrapper
    return decorator
